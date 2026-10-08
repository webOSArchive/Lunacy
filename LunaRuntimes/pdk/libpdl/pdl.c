/*
 * Lunacy's libpdl: Palm's PDL API (the PDK's PDL.h) for a PDK app running under Lunacy
 * (Docs/pdk.md). The app's binary calls these exactly as it called Palm's libpdl.so; what
 * they do here is answer from the environment the shell set up, or send a request down
 * the control socket the SDL video driver opened (LPDK_PDL) and wait for the shell's
 * reply. Anything not yet backed by the shell says so once on stderr and answers
 * PDL_NOERROR, because a game that keeps running without, say, vibration beats one that
 * stops; the honest errors are for calls whose answer the app will act on.
 *
 * Built against Palm's own headers, so the signatures and structs are the PDK's.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <unistd.h>
#include <errno.h>
#include <poll.h>
#include <pthread.h>

#include "PDL.h"
#include "lunacy_protocol.h"

/* From the SDL video driver (SDL_lunacyvideo.c), in the same process. */
extern int LUNACY_control_sock;
extern int LUNACY_Connect(const char *hello);
extern int LUNACY_Send(int sock, uint32_t type, uint32_t a, const void *payload, uint32_t len);

/*
 * The connection a request goes down: the video driver's, once SDL has a video mode, or
 * one of libpdl's own before then - Transformers G1 asks for the screen timeout right after
 * SDL_Init(timer) and quits if that fails. With LUNACY_PDK_NOSHELL set (desk tests under
 * qemu, no shell to reach) every request succeeds unsent.
 */
static int pdl_sock = -1;
static int shell_sock(void)
{
	if (LUNACY_control_sock >= 0) return LUNACY_control_sock;
	if (pdl_sock < 0 && !getenv("LUNACY_PDK_NOSHELL")) pdl_sock = LUNACY_Connect("P");
	return pdl_sock;
}

static char last_error[256] = "";
static int inited = 0;

static void note(const char *what) { fprintf(stderr, "[pdl] %s: not backed by the shell yet\n", what); }

static PDL_Err fail(const char *msg) { snprintf(last_error, sizeof last_error, "%s", msg); return PDL_ECONNECTION; }

static const char *env_or(const char *name, const char *dflt) { const char *v = getenv(name); return v && *v ? v : dflt; }

static PDL_Err copy_out(char *buffer, int bufferLen, const char *value)
{
	if (!buffer || bufferLen <= 0) return PDL_INVALIDINPUT;
	snprintf(buffer, bufferLen, "%s", value ? value : "");
	return PDL_NOERROR;
}

/* ---- lifecycle ---- */

static int js_link(void);
PDL_Err PDL_Init(unsigned int flags) { inited = 1; if (getenv("LUNACY_PDK_PLUGIN")) js_link(); return PDL_NOERROR; }
void PDL_Quit(void) { inited = 0; }
const char *PDL_GetError(void) { return last_error; }
int PDL_GetPDKVersion(void) { return 300; }   /* the TouchPad's PDK, 3.0.x */
/* A hybrid app's plugin: the shell started it for a page (LUNACY_PDK_PLUGIN). */
PDL_bool PDL_IsPlugin(void) { return getenv("LUNACY_PDK_PLUGIN") ? PDL_TRUE : PDL_FALSE; }
PDL_bool PDL_IsFullscreenPlugin(void) { return PDL_FALSE; }

/* ---- the device, from what the shell put in the environment ---- */

PDL_Err PDL_GetScreenMetrics(PDL_ScreenMetrics *m)
{
	if (!m) return PDL_INVALIDINPUT;
	m->horizontalPixels = atoi(env_or("LUNACY_PDK_SCREEN_W", "1024"));
	m->verticalPixels = atoi(env_or("LUNACY_PDK_SCREEN_H", "768"));
	m->horizontalDPI = atoi(env_or("LUNACY_PDK_DPI", "132"));
	m->verticalDPI = m->horizontalDPI;
	m->aspectRatio = 1.0;
	return PDL_NOERROR;
}

PDL_Err PDL_GetOSVersion(PDL_OSVersion *v)
{
	const char *s = env_or("LUNACY_PDK_OS_VERSION", "3.0.5");
	if (!v) return PDL_INVALIDINPUT;
	memset(v, 0, sizeof *v);
	sscanf(s, "%d.%d.%d", &v->majorVersion, &v->minorVersion, &v->revision);
	snprintf(v->versionStr, sizeof v->versionStr, "%s", s);
	return PDL_NOERROR;
}

int PDL_GetHardwareID(void) { return atoi(env_or("LUNACY_PDK_HARDWARE_ID", "101")); }   /* HARDWARE_TOUCHPAD */
const char *PDL_GetHardware(void) { return env_or("LUNACY_PDK_HARDWARE", "TouchPad"); }
PDL_Err PDL_GetDeviceName(char *buffer, int bufferLen) { return copy_out(buffer, bufferLen, env_or("LUNACY_PDK_DEVICE_NAME", "TouchPad")); }
PDL_Err PDL_GetUniqueID(char *buffer, int bufferLen) { return copy_out(buffer, bufferLen, env_or("LUNACY_PDK_NDUID", "")); }
PDL_Err PDL_GetLanguage(char *buffer, int bufferLen) { return copy_out(buffer, bufferLen, env_or("LUNACY_PDK_LANGUAGE", "en_US")); }
PDL_Err PDL_GetRegionCountryCode(char *buffer, int bufferLen) { return copy_out(buffer, bufferLen, env_or("LUNACY_PDK_COUNTRY", "US")); }
PDL_Err PDL_GetRegionCountryName(char *buffer, int bufferLen) { return copy_out(buffer, bufferLen, env_or("LUNACY_PDK_COUNTRY_NAME", "United States")); }

/* The app's own folder: webOS gave /media/cryptofs/apps/usr/palm/applications/<id>, which
   is where the shell starts the process, so the calling path is the working directory. */
PDL_Err PDL_GetCallingPath(char *buffer, int bufferLen)
{
	const char *p = getenv("LUNACY_PDK_APP_DIR");
	char cwd[1024];
	if (!p) p = getcwd(cwd, sizeof cwd);
	return copy_out(buffer, bufferLen, p);
}

/* Where an app keeps its data: webOS's /media/internal/.app-storage/<id>/, or whatever the
   shell names; created by the shell before launch. */
PDL_Err PDL_GetDataFilePath(const char *dataFileName, char *buffer, int bufferLen)
{
	const char *dir = env_or("LUNACY_PDK_DATA_DIR", ".");
	if (!buffer || bufferLen <= 0) return PDL_INVALIDINPUT;
	snprintf(buffer, bufferLen, "%s/%s", dir, dataFileName ? dataFileName : "");
	return PDL_NOERROR;
}

PDL_Err PDL_GetAppinfoValue(const char *name, char *buffer, int bufferLen)
{
	/* appinfo.json lives beside the binary; the common keys are in the environment. */
	char key[128];
	snprintf(key, sizeof key, "LUNACY_PDK_APPINFO_%s", name ? name : "");
	return copy_out(buffer, bufferLen, env_or(key, ""));
}

PDL_Err PDL_CheckLicense(void) { return PDL_NOERROR; }

/* ---- input and the window: told to the shell, which does what the TouchPad did ---- */

static PDL_Err tell(const char *json)
{
	int s = shell_sock();
	if (s < 0) {
		if (getenv("LUNACY_PDK_NOSHELL")) { fprintf(stderr, "[pdl] (no shell) %s\n", json); return PDL_NOERROR; }
		return fail("not connected to the shell");
	}
	if (LUNACY_Send(s, LPDK_PDL, 0, json, strlen(json)) < 0) { if (s == pdl_sock) { close(pdl_sock); pdl_sock = -1; } return fail("the shell went away"); }
	return PDL_NOERROR;
}

static PDL_Err tell_f(const char *fmt, ...)
{
	char buf[512];
	va_list ap;
	va_start(ap, fmt);
	vsnprintf(buf, sizeof buf, fmt, ap);
	va_end(ap);
	return tell(buf);
}

PDL_Err PDL_GesturesEnable(PDL_bool Enable) { return tell_f("{\"call\":\"gestures\",\"on\":%s}", Enable ? "true" : "false"); }
PDL_Err PDL_SetTouchAggression(PDL_TouchAggression a) { return tell_f("{\"call\":\"touchAggression\",\"value\":%d}", (int)a); }
PDL_Err PDL_SetOrientation(PDL_Orientation o) { return tell_f("{\"call\":\"orientation\",\"value\":%d}", (int)o); }
PDL_Err PDL_ScreenTimeoutEnable(PDL_bool Enable) { return tell_f("{\"call\":\"screenTimeout\",\"on\":%s}", Enable ? "true" : "false"); }
PDL_Err PDL_SetKeyboardState(PDL_bool visible) { return tell_f("{\"call\":\"keyboard\",\"on\":%s}", visible ? "true" : "false"); }
PDL_Err PDL_Vibrate(int periodMS, int durationMS) { return tell_f("{\"call\":\"vibrate\",\"period\":%d,\"duration\":%d}", periodMS, durationMS); }
PDL_Err PDL_Minimize(void) { return tell("{\"call\":\"minimize\"}"); }
PDL_Err PDL_LaunchBrowser(const char *url) { return tell_f("{\"call\":\"browser\",\"url\":\"%s\"}", url ? url : ""); }
PDL_Err PDL_LaunchEmail(const char *subject, const char *body) { return tell_f("{\"call\":\"email\",\"subject\":\"%s\"}", subject ? subject : ""); }
PDL_Err PDL_LaunchEmailTo(const char *subject, const char *body, int numRecipients, const char **recipients) { return PDL_LaunchEmail(subject, body); }
PDL_Err PDL_BannerMessagesEnable(PDL_bool Enable) { return PDL_NOERROR; }
PDL_Err PDL_CustomPauseUiEnable(PDL_bool Enable) { return PDL_NOERROR; }
PDL_Err PDL_NotifyMusicPlaying(PDL_bool MusicPlaying) { return PDL_NOERROR; }
PDL_Err PDL_SetAutomaticSoundPausing(PDL_bool AutomaticallyPause) { return PDL_NOERROR; }
PDL_Err PDL_EnableCompass(PDL_bool activate) { note("PDL_EnableCompass"); return PDL_NOERROR; }
PDL_Err PDL_EnableLocationTracking(PDL_bool activate) { note("PDL_EnableLocationTracking"); return PDL_NOERROR; }
PDL_Err PDL_GetLocation(PDL_Location *loc) { if (loc) memset(loc, 0, sizeof *loc); return PDL_NOTALLOWED; }
PDL_Err PDL_GetCompass(PDL_Compass *c) { if (c) memset(c, 0, sizeof *c); return PDL_NOTALLOWED; }
const char *PDL_GetKeyName(PDL_key Key) { return SDL_GetKeyName((SDLKey)Key); }

void PDL_Log(const char *format, ...)
{
	va_list ap;
	fputs("[pdl] ", stderr);
	va_start(ap, format);
	vfprintf(stderr, format, ap);
	va_end(ap);
	fputc('\n', stderr);
}

/* ---- the bus: not yet carried to the shell ---- */

PDL_Err PDL_ServiceCall(const char *uri, const char *payload) { note("PDL_ServiceCall"); return PDL_NOTALLOWED; }
PDL_Err PDL_ServiceCallWithCallback(const char *uri, const char *payload, PDL_ServiceCallbackFunc callback, void *user, PDL_bool removeAfterResponse) { note("PDL_ServiceCallWithCallback"); return PDL_NOTALLOWED; }
PDL_Err PDL_UnregisterServiceCallback(PDL_ServiceCallbackFunc callback) { return PDL_NOERROR; }
PDL_bool PDL_ParamExists(PDL_ServiceParameters *parms, const char *name) { return PDL_FALSE; }
void PDL_GetParamString(PDL_ServiceParameters *parms, const char *name, char *buffer, int bufferLen) { if (buffer && bufferLen > 0) buffer[0] = 0; }
int PDL_GetParamInt(PDL_ServiceParameters *parms, const char *name) { return 0; }
double PDL_GetParamDouble(PDL_ServiceParameters *parms, const char *name) { return 0; }
PDL_bool PDL_GetParamBool(PDL_ServiceParameters *parms, const char *name) { return PDL_FALSE; }
const char *PDL_GetParamJson(PDL_ServiceParameters *parms) { return "{}"; }

/* ---- sensors: none yet ---- */

PDL_bool PDL_SensorExists(PDL_SensorType sensor) { return PDL_FALSE; }
PDL_Err PDL_EnableSensor(PDL_SensorType sensor, PDL_bool bEnable) { return PDL_NOTALLOWED; }
PDL_Err PDL_PollSensor(PDL_SensorType sensor, PDL_SensorEvent *event) { return PDL_NOTALLOWED; }
PDL_Err PDL_PollActiveSensors(PDL_SensorEvent *event) { return PDL_NOTALLOWED; }

/* ---- the JS side of a hybrid app (Docs/pdk.md, "Hybrid apps") ----
 *
 * A plugin is a PDK binary a web page embeds as <object type="application/x-palm-remote">.
 * The shell starts it for the page and it opens a link of its own, greeting 'J'. Its
 * handlers' names go to the shell when registration is complete, and become methods on the
 * page's object; a call from the page arrives here, runs the handler and goes back with what
 * the handler replied, while the page's script waits, as it waited on webOS. As the PDK's
 * PDL_JS.h has it, a handler registered with PDL_RegisterJSHandler runs at once on this
 * library's own thread, and one registered with PDL_RegisterPollingJSHandler waits in a
 * queue for the app's PDL_HandleJSCalls, announced by an SDL_USEREVENT with code
 * PDL_PENDING_JS. PDL_CallJS calls a function the page set on the object.
 */

struct PDL_JSParameters {
	uint32_t id;
	const char *name;
	int argc;
	const char **argv;
	char *buf;             /* the call's payload, which name and argv point into */
	int kind;              /* 0 replied, 1 an exception, 2 nothing yet */
	char *reply;
	int poller;
	PDL_JSHandlerFunc fn;
	struct PDL_JSParameters *next;
};

struct js_handler { char *name; PDL_JSHandlerFunc fn; int poller; };
static struct js_handler *js_handlers;
static int js_count, js_complete;
static int js_sock = -1;
static pthread_mutex_t js_send_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t js_queue_lock = PTHREAD_MUTEX_INITIALIZER;
static struct PDL_JSParameters *js_queue, *js_queue_tail;

/* The JS link, opened when the plugin starts (PDL_Init) or first needs it. */
static int js_link(void)
{
	if (js_sock < 0 && getenv("LUNACY_PDK_PLUGIN") && !getenv("LUNACY_PDK_NOSHELL")) js_sock = LUNACY_Connect("J");
	return js_sock;
}

static int js_send(uint32_t type, uint32_t a, const void *payload, uint32_t len)
{
	int r;
	pthread_mutex_lock(&js_send_lock);
	r = LUNACY_Send(js_link(), type, a, payload, len);
	pthread_mutex_unlock(&js_send_lock);
	return r;
}

static PDL_Err js_register(const char *functionName, PDL_JSHandlerFunc function, int poller)
{
	struct js_handler *h;
	if (!functionName || !*functionName || !function) return PDL_INVALIDINPUT;
	if (js_complete) return fail("PDL_JSRegistrationComplete has been called");
	h = realloc(js_handlers, (js_count + 1) * sizeof *h);
	if (!h) return PDL_EMEMORY;
	js_handlers = h;
	js_handlers[js_count].name = strdup(functionName);
	js_handlers[js_count].fn = function;
	js_handlers[js_count].poller = poller;
	js_count++;
	return PDL_NOERROR;
}

PDL_Err PDL_RegisterJSHandler(const char *functionName, PDL_JSHandlerFunc function) { return js_register(functionName, function, 0); }
PDL_Err PDL_RegisterPollingJSHandler(const char *functionName, PDL_JSHandlerFunc function) { return js_register(functionName, function, 1); }

/* Sends what the handler answered, and frees the call. */
static void js_finish(struct PDL_JSParameters *p)
{
	size_t n = p->reply ? strlen(p->reply) : 0;
	char *out = malloc(n + 1);
	if (out) {
		out[0] = (char)p->kind;
		if (n) memcpy(out + 1, p->reply, n);
		js_send(LPDK_JS_REPLY, p->id, out, n + 1);
		free(out);
	}
	free(p->reply); free(p->argv); free(p->buf); free(p);
}

static void js_call(uint32_t id, char *buf, uint32_t len)
{
	struct PDL_JSParameters *p = calloc(1, sizeof *p);
	int i, n = 0;
	uint32_t at;
	if (!p) { free(buf); return; }
	p->id = id; p->buf = buf; p->kind = 2;
	for (at = 0; at < len; at++) if (!buf[at]) n++;
	p->name = buf;
	p->argc = n > 0 ? n - 1 : 0;
	p->argv = calloc(p->argc + 1, sizeof *p->argv);
	for (at = strlen(buf) + 1, i = 0; i < p->argc && at < len; i++) { p->argv[i] = buf + at; at += strlen(buf + at) + 1; }
	for (i = 0; i < js_count; i++) if (!strcmp(js_handlers[i].name, p->name)) break;
	if (i == js_count) {
		p->kind = 1; p->reply = strdup("no such method");
		js_finish(p);
		return;
	}
	p->fn = js_handlers[i].fn;
	p->poller = js_handlers[i].poller;
	if (!p->poller) {
		p->fn(p);
		js_finish(p);
		return;
	}
	pthread_mutex_lock(&js_queue_lock);
	if (js_queue_tail) js_queue_tail->next = p; else js_queue = p;
	js_queue_tail = p;
	pthread_mutex_unlock(&js_queue_lock);
	{
		SDL_Event e;
		memset(&e, 0, sizeof e);
		e.type = SDL_USEREVENT;
		e.user.code = PDL_PENDING_JS;
		SDL_PushEvent(&e);
	}
}

static int read_all(int fd, void *p, size_t n)
{
	char *c = p;
	while (n) {
		ssize_t r = read(fd, c, n);
		if (r < 0 && errno == EINTR) continue;
		if (r <= 0) return -1;
		c += r; n -= r;
	}
	return 0;
}

/* The link's reader: the page's calls, until the shell closes it. */
static void *js_reader(void *unused)
{
	for (;;) {
		uint32_t h[3];
		char *buf;
		if (read_all(js_sock, h, sizeof h) < 0) break;
		buf = malloc(h[2] + 1);
		if (!buf) break;
		if (h[2] && read_all(js_sock, buf, h[2]) < 0) { free(buf); break; }
		buf[h[2]] = 0;
		if (h[0] == LPDK_JS_CALL) js_call(h[1], buf, h[2]); else free(buf);
	}
	/* The page has gone: the plugin goes with it, as it did when webOS took the object away. */
	{
		SDL_Event e;
		memset(&e, 0, sizeof e);
		e.type = SDL_QUIT;
		SDL_PushEvent(&e);
	}
	return NULL;
}

PDL_Err PDL_JSRegistrationComplete(void)
{
	size_t len = 0, at = 0;
	char *names;
	int i;
	pthread_t t;
	if (js_complete) return PDL_NOERROR;
	js_complete = 1;
	/* A plain PDK app has no page to answer: done, as before there were plugins. */
	if (!getenv("LUNACY_PDK_PLUGIN") || getenv("LUNACY_PDK_NOSHELL")) return PDL_NOERROR;
	if (js_link() < 0) return fail("the page went away");
	for (i = 0; i < js_count; i++) len += strlen(js_handlers[i].name) + 1;
	names = malloc(len + 1);
	if (!names) return PDL_EMEMORY;
	for (i = 0; i < js_count; i++) { strcpy(names + at, js_handlers[i].name); at += strlen(js_handlers[i].name) + 1; }
	js_send(LPDK_JS_READY, 0, names, len);
	free(names);
	if (pthread_create(&t, NULL, js_reader, NULL) == 0) pthread_detach(t);
	return PDL_NOERROR;
}

int PDL_HandleJSCalls(void)
{
	int n = 0;
	for (;;) {
		struct PDL_JSParameters *p;
		pthread_mutex_lock(&js_queue_lock);
		p = js_queue;
		if (p) { js_queue = p->next; if (!js_queue) js_queue_tail = NULL; }
		pthread_mutex_unlock(&js_queue_lock);
		if (!p) break;
		p->fn(p);
		js_finish(p);
		n++;
	}
	return n;
}

const char *PDL_GetJSFunctionName(PDL_JSParameters *parms) { return parms && parms->name ? parms->name : ""; }
PDL_bool PDL_IsPoller(PDL_JSParameters *parms) { return parms && parms->poller ? PDL_TRUE : PDL_FALSE; }
int PDL_GetNumJSParams(PDL_JSParameters *parms) { return parms ? parms->argc : 0; }
const char *PDL_GetJSParamString(PDL_JSParameters *parms, int paramNum)
{
	return parms && paramNum >= 0 && paramNum < parms->argc ? parms->argv[paramNum] : "";
}
int PDL_GetJSParamInt(PDL_JSParameters *parms, int paramNum) { return atoi(PDL_GetJSParamString(parms, paramNum)); }
double PDL_GetJSParamDouble(PDL_JSParameters *parms, int paramNum) { return atof(PDL_GetJSParamString(parms, paramNum)); }

static PDL_Err js_answer(PDL_JSParameters *parms, const char *reply, int kind)
{
	if (!parms) return PDL_INVALIDINPUT;
	free(parms->reply);
	parms->reply = strdup(reply ? reply : "");
	parms->kind = kind;
	return PDL_NOERROR;
}
PDL_Err PDL_JSReply(PDL_JSParameters *parms, const char *reply) { return js_answer(parms, reply, 0); }
PDL_Err PDL_JSException(PDL_JSParameters *parms, const char *reply) { return js_answer(parms, reply, 1); }

PDL_Err PDL_CallJS(const char *functionName, const char **params, int numParams)
{
	size_t len, at;
	char *buf;
	int i;
	if (!functionName || !*functionName || numParams < 0) return PDL_INVALIDINPUT;
	if (!getenv("LUNACY_PDK_PLUGIN")) return PDL_NOTALLOWED;   /* no page to call */
	if (getenv("LUNACY_PDK_NOSHELL")) return PDL_NOERROR;
	if (js_link() < 0) return fail("the page went away");
	len = strlen(functionName) + 1;
	for (i = 0; i < numParams; i++) len += strlen(params[i] ? params[i] : "") + 1;
	buf = malloc(len);
	if (!buf) return PDL_EMEMORY;
	strcpy(buf, functionName); at = strlen(functionName) + 1;
	for (i = 0; i < numParams; i++) { const char *v = params[i] ? params[i] : ""; strcpy(buf + at, v); at += strlen(v) + 1; }
	i = js_send(LPDK_CALL_JS, 0, buf, len);
	free(buf);
	return i < 0 ? fail("the page went away") : PDL_NOERROR;
}

PDL_Err PDL_DismissFullscreen(void) { return PDL_NOERROR; }

/* ---- purchases: the catalog is gone ---- */

PDL_ItemCollection *PDL_GetAvailableItems(void) { fail("the App Catalog's store is gone"); return NULL; }
PDL_ItemInfo *PDL_GetItemInfo(const char *itemID) { fail("the App Catalog's store is gone"); return NULL; }
PDL_ItemReceipt *PDL_PurchaseItem(const char *itemID, int qty, const char *usr) { fail("the App Catalog's store is gone"); return NULL; }
PDL_ItemReceipt *PDL_GetPendingPurchaseInfo(const char *orderNo) { fail("the App Catalog's store is gone"); return NULL; }
const char *PDL_GetItemJSON(PDL_ItemInfo *itemInfo) { return "{}"; }
const char *PDL_GetItemReceiptJSON(PDL_ItemReceipt *receipt) { return "{}"; }
const char *PDL_GetItemCollectionJSON(PDL_ItemCollection *c) { return "[]"; }
void PDL_FreeItemInfo(PDL_ItemInfo *itemInfo) { }
void PDL_FreeItemReceipt(PDL_ItemReceipt *itemInfo) { }
void PDL_FreeItemCollection(PDL_ItemCollection *c) { }
