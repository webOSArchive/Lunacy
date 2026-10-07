/*
 * Lunacy's liblunaservice.so: webOS's Luna service bus client library (LS2), for native
 * system services that run as they shipped - the TouchPad's own mojomail-imap, mojomail-smtp
 * and filecache - under the PDK's glibc runtime (Docs/architecture.md, "Native services").
 *
 * On webOS this library spoke to ls-hubd over Unix sockets. Lunacy has no hub; its bus is in
 * the shell, which starts the service with the bus on its stdin and stdout, one JSON message
 * per line: the same link Lunacy's JS service host speaks (assets/lunacy/services/host.js).
 *
 *   shell -> service  {"t":"request","id":N,"service","category","method","payload","sender","subscribe","outside","fromService"}
 *                     {"t":"cancel","id":N}           the caller of request N went away
 *                     {"t":"callResponse","id":"cK","payload"}
 *   service -> shell  {"t":"response","id":N,"payload"}
 *                     {"t":"call","id":"cK","url","payload","subscribe","appId"?}
 *                     {"t":"cancelCall","id":"cK"}
 *                     {"t":"ready"}               the service attached to its main loop
 *
 * The ABI is the TouchPad's (HP webOS 3.0.5): the structures match Open webOS's
 * luna-service2 header, and LSMethod is 12 bytes (name, function, flags), which is the stride
 * the TouchPad's own LSRegisterCategoryAppend walks. Only what the services in the ROM call
 * is here; anything else is absent, and the loader says which symbol a new service lacks.
 *
 * A message is dispatched on the thread running the service's glib main loop; responses and
 * calls may come from any thread.
 */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <syslog.h>
#include <unistd.h>

/* ---- glib, as the TouchPad's libglib-2.0.so.0 has it (only opaque types are needed) ---- */

typedef struct _GMainLoop GMainLoop;
typedef struct _GMainContext GMainContext;
typedef struct _GSource GSource;
typedef struct _GIOChannel GIOChannel;
typedef int gboolean;
typedef void *gpointer;
typedef gboolean (*GSourceFunc)(gpointer);
typedef gboolean (*GIOFunc)(GIOChannel *, int, gpointer);
enum { G_IO_IN = 1, G_IO_ERR = 8, G_IO_HUP = 16 };
GMainContext *g_main_loop_get_context(GMainLoop *loop);
GIOChannel *g_io_channel_unix_new(int fd);
GSource *g_io_create_watch(GIOChannel *channel, int condition);
void g_source_set_callback(GSource *source, GSourceFunc func, gpointer data, void (*notify)(gpointer));
unsigned g_source_attach(GSource *source, GMainContext *context);
void g_source_unref(GSource *source);
void g_main_context_wakeup(GMainContext *context);

/* ---- the public types (lunaservice.h) ---- */

typedef unsigned long LSMessageToken;
typedef struct LSHandle LSHandle;
typedef struct LSMessage LSMessage;
typedef struct LSPalmService LSPalmService;

typedef struct LSError {
	int error_code;
	char *message;
	const char *file;
	int line;
	const char *func;
	void *padding;
	unsigned long magic;
} LSError;
#define LSERROR_MAGIC 0x1eb4

typedef bool (*LSMethodFunction)(LSHandle *sh, LSMessage *msg, void *category_context);
typedef bool (*LSFilterFunc)(LSHandle *sh, LSMessage *reply, void *ctx);
typedef struct { const char *name; LSMethodFunction function; int flags; } LSMethod;
typedef struct { const char *name; int flags; } LSSignal;

/* ---- Lunacy's state ---- */

struct Category {
	char *name;
	const LSMethod *methods[4];   /* the arrays registered for it, each ending in a NULL name */
	int count;
	void *data;
	struct Category *next;
};

struct LSHandle {
	char *name;
	bool isPublic;          /* reached by callers outside the system (apps without com.palm. ids) */
	LSPalmService *palm;    /* the pair it belongs to, if it was made by LSRegisterPalmService */
	struct Category *categories;
	LSFilterFunc cancelFunction;
	void *cancelContext;
};

struct LSPalmService { LSHandle *pub, *priv; };

struct LSMessage {
	int refs;
	LSHandle *handle;
	LSMessageToken token;          /* a request's id from the shell */
	LSMessageToken responseToken;  /* a reply's: the call it answers */
	char *sender, *senderService, *appId, *category, *method, *payload;
	bool subscribe;
};

/* An open call the service made. */
struct Call {
	LSMessageToken token;
	LSHandle *handle;
	LSFilterFunc callback;
	void *context;
	bool oneReply;
	char *service;
	struct Call *next;
};

/* A request a subscription was kept for, so its caller's going away can be told. */
struct Kept {
	LSMessageToken token;
	LSHandle *handle;
	char *sender, *senderService, *appId, *category, *method;
	struct Kept *next;
};

static int bus_in = -1, bus_out = -1;
static pthread_mutex_t out_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;   /* handles, calls, kept */
static LSHandle *handles[16];
static int handle_count;
static struct Call *calls;
static struct Kept *kept;
static LSMessageToken next_token = 1;
static bool attached;

/* The bus is the process's stdin and stdout, taken before main: anything else the service
   prints goes to stderr, the shell's log, and never onto the link. */
__attribute__((constructor)) static void take_link(void)
{
	if (!getenv("LUNACY_BUS_STDIO")) return;
	unsetenv("LUNACY_BUS_STDIO");   /* a child process the service starts has stdin and stdout of its own */
	bus_in = dup(0);
	bus_out = dup(1);
	fcntl(bus_in, F_SETFD, FD_CLOEXEC);
	fcntl(bus_out, F_SETFD, FD_CLOEXEC);
	int null = open("/dev/null", O_RDONLY);
	if (null >= 0) { dup2(null, 0); close(null); }
	dup2(2, 1);
}

/* ---- errors ---- */

static bool fail(LSError *e, int code, const char *func, const char *fmt, ...)
{
	if (e) {
		va_list ap; va_start(ap, fmt);
		free(e->message);
		if (vasprintf(&e->message, fmt, ap) < 0) e->message = NULL;
		va_end(ap);
		e->error_code = code; e->func = func; e->file = __FILE__; e->line = 0;
	}
	return false;
}
#define FAIL(e, ...) fail(e, -1, __func__, __VA_ARGS__)

bool LSErrorInit(LSError *e)
{
	if (!e) return false;
	memset(e, 0, sizeof *e);
	e->magic = LSERROR_MAGIC;
	return true;
}

void LSErrorFree(LSError *e)
{
	if (!e) return;
	free(e->message);
	LSErrorInit(e);
}

bool LSErrorIsSet(LSError *e) { return e && e->error_code != 0; }

void LSErrorPrint(LSError *e, FILE *out)
{
	if (e && out) fprintf(out, "LUNASERVICE ERROR %d: %s (%s)\n", e->error_code, e->message ? e->message : "", e->func ? e->func : "");
}

/* ---- JSON: enough for the link's envelopes, which are flat ---- */

struct Out { char *buf; size_t len, cap; };

static void put(struct Out *o, const char *s, size_t n)
{
	if (o->len + n + 1 > o->cap) {
		o->cap = (o->len + n + 1) * 2;
		o->buf = realloc(o->buf, o->cap);
	}
	memcpy(o->buf + o->len, s, n);
	o->len += n;
	o->buf[o->len] = 0;
}
static void puts_(struct Out *o, const char *s) { put(o, s, strlen(s)); }

static void put_string(struct Out *o, const char *s)
{
	put(o, "\"", 1);
	for (const unsigned char *p = (const unsigned char *)(s ? s : ""); *p; p++) {
		char esc[8];
		switch (*p) {
		case '"': puts_(o, "\\\""); break;
		case '\\': puts_(o, "\\\\"); break;
		case '\n': puts_(o, "\\n"); break;
		case '\r': puts_(o, "\\r"); break;
		case '\t': puts_(o, "\\t"); break;
		default:
			if (*p < 0x20) { snprintf(esc, sizeof esc, "\\u%04x", *p); puts_(o, esc); }
			else put(o, (const char *)p, 1);
		}
	}
	put(o, "\"", 1);
}

static void send_line(struct Out *o)
{
	put(o, "\n", 1);
	pthread_mutex_lock(&out_lock);
	size_t done = 0;
	while (done < o->len) {
		ssize_t n = write(bus_out, o->buf + done, o->len - done);
		if (n < 0 && errno == EINTR) continue;
		if (n <= 0) break;
		done += n;
	}
	pthread_mutex_unlock(&out_lock);
	free(o->buf);
}

static void put_utf8(struct Out *o, unsigned c)
{
	char b[4]; int n;
	if (c < 0x80) { b[0] = c; n = 1; }
	else if (c < 0x800) { b[0] = 0xc0 | c >> 6; b[1] = 0x80 | (c & 0x3f); n = 2; }
	else if (c < 0x10000) { b[0] = 0xe0 | c >> 12; b[1] = 0x80 | (c >> 6 & 0x3f); b[2] = 0x80 | (c & 0x3f); n = 3; }
	else { b[0] = 0xf0 | c >> 18; b[1] = 0x80 | (c >> 12 & 0x3f); b[2] = 0x80 | (c >> 6 & 0x3f); b[3] = 0x80 | (c & 0x3f); n = 4; }
	put(o, b, n);
}

/* The value of `key` in a flat JSON object: a string (unescaped, malloc'd), or the literal
   text of a number or boolean. NULL if absent. */
static char *field(const char *json, const char *key)
{
	size_t klen = strlen(key);
	const char *p = json;
	while ((p = strchr(p, '"'))) {
		const char *k = p + 1;
		/* skip this string */
		const char *e = k;
		while (*e && *e != '"') e += (*e == '\\' && e[1]) ? 2 : 1;
		if (!*e) return NULL;
		const char *after = e + 1;
		while (*after == ' ') after++;
		bool isKey = *after == ':';
		if (isKey && (size_t)(e - k) == klen && strncmp(k, key, klen) == 0) {
			const char *v = after + 1;
			while (*v == ' ') v++;
			struct Out o = {0};
			if (*v == '"') {
				for (v++; *v && *v != '"'; v++) {
					if (*v != '\\') { put(&o, v, 1); continue; }
					v++;
					switch (*v) {
					case 'n': put(&o, "\n", 1); break;
					case 'r': put(&o, "\r", 1); break;
					case 't': put(&o, "\t", 1); break;
					case 'b': put(&o, "\b", 1); break;
					case 'f': put(&o, "\f", 1); break;
					case 'u': {
						unsigned c = 0;
						if (sscanf(v + 1, "%4x", &c) != 1) break;
						v += 4;
						if (c >= 0xd800 && c < 0xdc00 && v[1] == '\\' && v[2] == 'u') {
							unsigned lo = 0;
							if (sscanf(v + 3, "%4x", &lo) == 1 && lo >= 0xdc00 && lo < 0xe000) {
								c = 0x10000 + ((c - 0xd800) << 10) + (lo - 0xdc00);
								v += 6;
							}
						}
						put_utf8(&o, c);
						break;
					}
					default: put(&o, v, 1);
					}
				}
				if (!o.buf) put(&o, "", 0);
				return o.buf;
			}
			const char *end = v;
			while (*end && *end != ',' && *end != '}' && *end != ' ') end++;
			put(&o, v, end - v);
			return o.buf;
		}
		p = isKey ? after + 1 : after;
		if (!isKey) continue;
	}
	return NULL;
}

/* ---- messages ---- */

static LSMessage *message_new(void)
{
	LSMessage *m = calloc(1, sizeof *m);
	m->refs = 1;
	return m;
}

void LSMessageRef(LSMessage *m) { if (m) __sync_add_and_fetch(&m->refs, 1); }

void LSMessageUnref(LSMessage *m)
{
	if (!m || __sync_sub_and_fetch(&m->refs, 1) > 0) return;
	free(m->sender); free(m->senderService); free(m->appId);
	free(m->category); free(m->method); free(m->payload);
	free(m);
}

LSHandle *LSMessageGetConnection(LSMessage *m) { return m ? m->handle : NULL; }
const char *LSMessageGetApplicationID(LSMessage *m) { return m ? m->appId : NULL; }
const char *LSMessageGetSender(LSMessage *m) { return m ? m->sender : NULL; }
const char *LSMessageGetSenderServiceName(LSMessage *m) { return m ? m->senderService : NULL; }
const char *LSMessageGetCategory(LSMessage *m) { return m ? m->category : NULL; }
const char *LSMessageGetMethod(LSMessage *m) { return m ? m->method : NULL; }
const char *LSMessageGetPayload(LSMessage *m) { return m ? m->payload : NULL; }
LSMessageToken LSMessageGetToken(LSMessage *m) { return m ? m->token : 0; }
LSMessageToken LSMessageGetResponseToken(LSMessage *m) { return m ? m->responseToken : 0; }
bool LSMessageIsSubscription(LSMessage *m) { return m && m->subscribe; }
bool LSMessageIsPublic(LSPalmService *psh, LSMessage *m) { return m && m->handle && m->handle->isPublic; }

const char *LSMessageGetUniqueToken(LSMessage *m)
{
	static __thread char buf[32];
	snprintf(buf, sizeof buf, "%lu", m ? m->token : 0);
	return buf;
}

bool LSMessageRespond(LSMessage *m, const char *payload, LSError *e)
{
	if (!m || !m->token) return FAIL(e, "not a request");
	if (bus_out < 0) return FAIL(e, "no bus");
	char id[24];
	snprintf(id, sizeof id, "%lu", m->token);
	struct Out o = {0};
	puts_(&o, "{\"t\":\"response\",\"id\":"); puts_(&o, id);
	puts_(&o, ",\"payload\":"); put_string(&o, payload);
	puts_(&o, "}");
	send_line(&o);
	return true;
}

bool LSMessageReply(LSHandle *sh, LSMessage *m, const char *payload, LSError *e) { return LSMessageRespond(m, payload, e); }

/* ---- registration ---- */

static LSHandle *handle_new(const char *name, bool isPublic)
{
	LSHandle *h = calloc(1, sizeof *h);
	h->name = name ? strdup(name) : NULL;
	h->isPublic = isPublic;
	pthread_mutex_lock(&lock);
	if (handle_count < (int)(sizeof handles / sizeof *handles)) handles[handle_count++] = h;
	pthread_mutex_unlock(&lock);
	return h;
}

static void handle_free(LSHandle *h)
{
	if (!h) return;
	pthread_mutex_lock(&lock);
	for (int i = 0; i < handle_count; i++) if (handles[i] == h) { handles[i] = handles[--handle_count]; break; }
	pthread_mutex_unlock(&lock);
	for (struct Category *c = h->categories, *n; c; c = n) { n = c->next; free(c->name); free(c); }
	free(h->name);
	free(h);
}

bool LSRegister(const char *name, LSHandle **sh, LSError *e)
{
	if (!sh) return FAIL(e, "no handle");
	*sh = handle_new(name, false);
	return true;
}

bool LSRegisterPubPriv(const char *name, LSHandle **sh, bool publicBus, LSError *e)
{
	if (!sh) return FAIL(e, "no handle");
	*sh = handle_new(name, publicBus);
	return true;
}

bool LSUnregister(LSHandle *sh, LSError *e) { handle_free(sh); return true; }

const char *LSHandleGetName(LSHandle *sh) { return sh ? sh->name : NULL; }

bool LSRegisterPalmService(const char *name, LSPalmService **psh, LSError *e)
{
	if (!psh) return FAIL(e, "no handle");
	LSPalmService *p = calloc(1, sizeof *p);
	p->pub = handle_new(name, true);
	p->priv = handle_new(name, false);
	p->pub->palm = p->priv->palm = p;
	*psh = p;
	return true;
}

bool LSUnregisterPalmService(LSPalmService *psh, LSError *e)
{
	if (!psh) return true;
	handle_free(psh->pub);
	handle_free(psh->priv);
	free(psh);
	return true;
}

LSHandle *LSPalmServiceGetPublicConnection(LSPalmService *psh) { return psh ? psh->pub : NULL; }
LSHandle *LSPalmServiceGetPrivateConnection(LSPalmService *psh) { return psh ? psh->priv : NULL; }

/* The category named `name` ("/" or "/foo"; a missing slash is added), made if absent. */
static struct Category *category(LSHandle *sh, const char *name, bool make)
{
	char norm[256];
	snprintf(norm, sizeof norm, "%s%s", name && name[0] == '/' ? "" : "/", name ? name : "");
	size_t n = strlen(norm);
	if (n > 1 && norm[n - 1] == '/') norm[n - 1] = 0;
	for (struct Category *c = sh->categories; c; c = c->next) if (strcmp(c->name, norm) == 0) return c;
	if (!make) return NULL;
	struct Category *c = calloc(1, sizeof *c);
	c->name = strdup(norm);
	c->next = sh->categories;
	sh->categories = c;
	return c;
}

static void add_methods(struct Category *c, const LSMethod *methods)
{
	if (methods && c->count < (int)(sizeof c->methods / sizeof *c->methods)) c->methods[c->count++] = methods;
}

bool LSRegisterCategoryAppend(LSHandle *sh, const char *name, LSMethod *methods, LSSignal *signals, void *properties, LSError *e)
{
	if (!sh) return FAIL(e, "no handle");
	pthread_mutex_lock(&lock);
	add_methods(category(sh, name, true), methods);
	pthread_mutex_unlock(&lock);
	return true;
}

bool LSRegisterCategory(LSHandle *sh, const char *name, LSMethod *methods, LSSignal *signals, void *properties, LSError *e)
{
	return LSRegisterCategoryAppend(sh, name, methods, signals, properties, e);
}

bool LSCategorySetData(LSHandle *sh, const char *name, void *data, LSError *e)
{
	if (!sh) return FAIL(e, "no handle");
	pthread_mutex_lock(&lock);
	category(sh, name, true)->data = data;
	pthread_mutex_unlock(&lock);
	return true;
}

/* Public methods are on both connections, private ones only on the private one, as
   LSPalmServiceRegisterCategory registered them with ls-hubd. */
bool LSPalmServiceRegisterCategory(LSPalmService *psh, const char *name, LSMethod *pub, LSMethod *priv, LSSignal *signals, void *data, LSError *e)
{
	if (!psh) return FAIL(e, "no handle");
	pthread_mutex_lock(&lock);
	struct Category *c = category(psh->pub, name, true);
	add_methods(c, pub);
	c->data = data;
	c = category(psh->priv, name, true);
	add_methods(c, pub);
	add_methods(c, priv);
	c->data = data;
	pthread_mutex_unlock(&lock);
	return true;
}

bool LSPushRole(LSHandle *sh, const char *path, LSError *e) { return true; }
bool LSPushRolePalmService(LSPalmService *psh, const char *path, LSError *e) { return true; }

/* ---- subscriptions ---- */

bool LSSubscriptionSetCancelFunction(LSHandle *sh, LSFilterFunc f, void *ctx, LSError *e)
{
	if (!sh) return FAIL(e, "no handle");
	sh->cancelFunction = f;
	sh->cancelContext = ctx;
	return true;
}

static char *dup0(const char *s) { return s ? strdup(s) : NULL; }

bool LSSubscriptionAdd(LSHandle *sh, const char *key, LSMessage *m, LSError *e)
{
	if (!m) return FAIL(e, "no message");
	struct Kept *k = calloc(1, sizeof *k);
	k->token = m->token; k->handle = m->handle;
	k->sender = dup0(m->sender); k->senderService = dup0(m->senderService); k->appId = dup0(m->appId);
	k->category = dup0(m->category); k->method = dup0(m->method);
	pthread_mutex_lock(&lock);
	k->next = kept; kept = k;
	pthread_mutex_unlock(&lock);
	return true;
}

/* ---- calls the service makes ---- */

static bool call(LSHandle *sh, const char *uri, const char *payload, const char *appId, bool oneReply,
                 LSFilterFunc callback, void *ctx, LSMessageToken *token, LSError *e)
{
	if (!uri || bus_out < 0) return FAIL(e, "no bus");
	const char *s = strstr(uri, "://");
	if (!s) return FAIL(e, "Invalid URI: %s", uri);
	s += 3;
	const char *slash = strchr(s, '/');
	struct Call *c = calloc(1, sizeof *c);
	c->handle = sh; c->callback = callback; c->context = ctx; c->oneReply = oneReply;
	c->service = slash ? strndup(s, slash - s) : strdup(s);
	pthread_mutex_lock(&lock);
	c->token = next_token++;
	c->next = calls; calls = c;
	pthread_mutex_unlock(&lock);
	if (token) *token = c->token;

	char id[32];
	snprintf(id, sizeof id, "\"c%lu\"", c->token);
	struct Out o = {0};
	puts_(&o, "{\"t\":\"call\",\"id\":"); puts_(&o, id);
	puts_(&o, ",\"url\":"); put_string(&o, uri);
	puts_(&o, ",\"payload\":"); put_string(&o, payload ? payload : "{}");
	puts_(&o, oneReply ? ",\"subscribe\":false" : ",\"subscribe\":true");
	if (appId) { puts_(&o, ",\"appId\":"); put_string(&o, appId); }
	puts_(&o, "}");
	send_line(&o);
	return true;
}

bool LSCall(LSHandle *sh, const char *uri, const char *payload, LSFilterFunc cb, void *ctx, LSMessageToken *token, LSError *e)
{ return call(sh, uri, payload, NULL, false, cb, ctx, token, e); }
bool LSCallOneReply(LSHandle *sh, const char *uri, const char *payload, LSFilterFunc cb, void *ctx, LSMessageToken *token, LSError *e)
{ return call(sh, uri, payload, NULL, true, cb, ctx, token, e); }
bool LSCallFromApplication(LSHandle *sh, const char *uri, const char *payload, const char *appId, LSFilterFunc cb, void *ctx, LSMessageToken *token, LSError *e)
{ return call(sh, uri, payload, appId, false, cb, ctx, token, e); }
bool LSCallFromApplicationOneReply(LSHandle *sh, const char *uri, const char *payload, const char *appId, LSFilterFunc cb, void *ctx, LSMessageToken *token, LSError *e)
{ return call(sh, uri, payload, appId, true, cb, ctx, token, e); }

static struct Call *take_call(LSMessageToken token)
{
	for (struct Call **p = &calls; *p; p = &(*p)->next)
		if ((*p)->token == token) { struct Call *c = *p; *p = c->next; return c; }
	return NULL;
}

bool LSCallCancel(LSHandle *sh, LSMessageToken token, LSError *e)
{
	pthread_mutex_lock(&lock);
	struct Call *c = take_call(token);
	pthread_mutex_unlock(&lock);
	if (!c) return true;
	free(c->service); free(c);
	char line[64];
	struct Out o = {0};
	snprintf(line, sizeof line, "{\"t\":\"cancelCall\",\"id\":\"c%lu\"}", token);
	puts_(&o, line);
	send_line(&o);
	return true;
}

/* ---- the link ---- */

static const LSMethod *find_method(LSHandle *h, const char *cat, const char *method, void **data)
{
	struct Category *c = category(h, cat, false);
	if (!c) return NULL;
	for (int i = 0; i < c->count; i++)
		for (const LSMethod *m = c->methods[i]; m->name; m++)
			if (strcmp(m->name, method) == 0) { *data = c->data; return m; }
	return NULL;
}

static void respond_error(const char *id, const char *text)
{
	struct Out o = {0}, p = {0};
	puts_(&p, "{\"returnValue\":false,\"errorCode\":-1,\"errorText\":"); put_string(&p, text); puts_(&p, "}");
	puts_(&o, "{\"t\":\"response\",\"id\":"); puts_(&o, id); puts_(&o, ",\"payload\":"); put_string(&o, p.buf); puts_(&o, "}");
	free(p.buf);
	send_line(&o);
}

static bool is_true(const char *v) { return v && strcmp(v, "true") == 0; }

static void request(const char *line)
{
	char *id = field(line, "id"), *service = field(line, "service"), *cat = field(line, "category"),
	     *method = field(line, "method"), *outside = field(line, "outside"), *fromService = field(line, "fromService");
	if (!id || !method) goto out;
	bool pub = is_true(outside);
	LSHandle *target = NULL;
	const LSMethod *m = NULL;
	void *data = NULL;
	bool known = false;
	pthread_mutex_lock(&lock);
	for (int i = 0; i < handle_count && !m; i++) {
		LSHandle *h = handles[i];
		if (!h->name || (service && strcmp(h->name, service) != 0)) continue;
		if (pub && !h->isPublic) continue;
		if (!pub && h->isPublic && h->palm) continue;   /* a private caller uses the private side */
		known = true;
		if ((m = find_method(h, cat ? cat : "/", method, &data))) target = h;
	}
	pthread_mutex_unlock(&lock);
	if (!m) {
		char text[512];
		if (!known) snprintf(text, sizeof text, "Service does not exist: %s.", service ? service : "");
		else snprintf(text, sizeof text, "Unknown method \"%s\" for category \"%s\"", method, cat ? cat : "/");
		respond_error(id, text);
		goto out;
	}
	LSMessage *msg = message_new();
	msg->handle = target;
	msg->token = strtoul(id, NULL, 10);
	msg->payload = field(line, "payload");
	if (!msg->payload) msg->payload = strdup("{}");
	char *sender = field(line, "sender");
	msg->sender = sender ? strdup(sender) : strdup("");
	if (is_true(fromService)) msg->senderService = sender; else { msg->appId = sender; }
	if (!msg->appId && !msg->senderService) free(sender);
	msg->category = strdup(cat ? cat : "/");
	msg->method = strdup(method);
	char *sub = field(line, "subscribe");
	msg->subscribe = is_true(sub);
	free(sub);
	m->function(target, msg, data);
	LSMessageUnref(msg);
out:
	free(id); free(service); free(cat); free(method); free(outside); free(fromService);
}

static void cancel(const char *line)
{
	char *id = field(line, "id");
	if (!id) return;
	LSMessageToken token = strtoul(id, NULL, 10);
	free(id);
	struct Kept *found = NULL;
	pthread_mutex_lock(&lock);
	for (struct Kept **p = &kept; *p; p = &(*p)->next)
		if ((*p)->token == token) { found = *p; *p = found->next; break; }
	pthread_mutex_unlock(&lock);
	if (!found) return;
	LSHandle *h = found->handle;
	if (h && h->cancelFunction) {
		LSMessage *msg = message_new();
		msg->handle = h; msg->token = token;
		msg->sender = found->sender; msg->senderService = found->senderService; msg->appId = found->appId;
		msg->category = found->category; msg->method = found->method;
		msg->payload = strdup("{}");
		found->sender = found->senderService = found->appId = found->category = found->method = NULL;
		h->cancelFunction(h, msg, h->cancelContext);
		LSMessageUnref(msg);
	}
	free(found->sender); free(found->senderService); free(found->appId); free(found->category); free(found->method);
	free(found);
}

static void response(const char *line)
{
	char *id = field(line, "id");
	if (!id || id[0] != 'c') { free(id); return; }
	LSMessageToken token = strtoul(id + 1, NULL, 10);
	free(id);
	pthread_mutex_lock(&lock);
	struct Call *c = NULL;
	for (struct Call *x = calls; x; x = x->next) if (x->token == token) { c = x; break; }
	if (c && c->oneReply) take_call(token);
	LSFilterFunc cb = c ? c->callback : NULL;
	void *ctx = c ? c->context : NULL;
	LSHandle *h = c ? c->handle : NULL;
	char *service = c ? strdup(c->service) : NULL;
	if (c && c->oneReply) { free(c->service); free(c); }
	pthread_mutex_unlock(&lock);
	if (!cb) { free(service); return; }
	LSMessage *msg = message_new();
	msg->handle = h;
	msg->responseToken = token;
	msg->payload = field(line, "payload");
	if (!msg->payload) msg->payload = strdup("{}");
	msg->sender = service;
	msg->senderService = service ? strdup(service) : NULL;
	msg->category = strdup("/");
	msg->method = strdup("");
	cb(h, msg, ctx);
	LSMessageUnref(msg);
}

static char *inbuf;
static size_t inlen, incap;

static gboolean readable(GIOChannel *ch, int cond, gpointer data)
{
	char chunk[16384];
	ssize_t n = read(bus_in, chunk, sizeof chunk);
	if (n < 0 && (errno == EINTR || errno == EAGAIN)) return 1;
	if (n <= 0) {
		/* The shell closed the link: the service is to end, as when ls-hubd went away. */
		syslog(LOG_INFO, "lunaservice: bus closed, exiting");
		_exit(0);
	}
	if (inlen + n + 1 > incap) { incap = (inlen + n + 1) * 2; inbuf = realloc(inbuf, incap); }
	memcpy(inbuf + inlen, chunk, n);
	inlen += n;
	inbuf[inlen] = 0;
	char *start = inbuf, *nl;
	while ((nl = memchr(start, '\n', inbuf + inlen - start))) {
		*nl = 0;
		char *t = field(start, "t");
		if (t) {
			if (strcmp(t, "request") == 0) request(start);
			else if (strcmp(t, "cancel") == 0) cancel(start);
			else if (strcmp(t, "callResponse") == 0) response(start);
			free(t);
		}
		start = nl + 1;
	}
	inlen = inbuf + inlen - start;
	memmove(inbuf, start, inlen);
	inbuf[inlen] = 0;
	return 1;
}

static bool attach(GMainContext *ctx, LSError *e)
{
	if (bus_in < 0) return FAIL(e, "Lunacy's bus isn't connected (LUNACY_BUS_STDIO)");
	pthread_mutex_lock(&lock);
	bool first = !attached;
	attached = true;
	pthread_mutex_unlock(&lock);
	if (!first) return true;
	GIOChannel *ch = g_io_channel_unix_new(bus_in);
	GSource *src = g_io_create_watch(ch, G_IO_IN | G_IO_HUP | G_IO_ERR);
	g_source_set_callback(src, (GSourceFunc)(void *)readable, NULL, NULL);
	g_source_attach(src, ctx);
	g_source_unref(src);
	struct Out o = {0};
	puts_(&o, "{\"t\":\"ready\"}");
	send_line(&o);
	return true;
}

bool LSGmainAttach(LSHandle *sh, GMainLoop *loop, LSError *e) { return attach(loop ? g_main_loop_get_context(loop) : NULL, e); }
bool LSGmainContextAttach(LSHandle *sh, GMainContext *ctx, LSError *e) { return attach(ctx, e); }
bool LSGmainAttachPalmService(LSPalmService *psh, GMainLoop *loop, LSError *e) { return attach(loop ? g_main_loop_get_context(loop) : NULL, e); }
bool LSGmainContextAttachPalmService(LSPalmService *psh, GMainContext *ctx, LSError *e) { return attach(ctx, e); }
bool LSGmainSetPriority(LSHandle *sh, int priority, LSError *e) { return true; }
bool LSGmainSetPriorityPalmService(LSPalmService *psh, int priority, LSError *e) { return true; }
