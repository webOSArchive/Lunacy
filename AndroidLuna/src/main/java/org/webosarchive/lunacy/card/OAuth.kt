package org.webosarchive.lunacy.card

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors

/**
 * palm://org.webosarchive.lunacy.oauth: signing mail accounts in with OAuth 2, and keeping
 * them signed in (Docs/oauth.md).
 *
 * Google and Microsoft no longer take a password from a mail client; Palm's mail services
 * know nothing else. So an account signed in here is given, as its password, a handle
 * (`oauth2:` and 32 hex digits) naming its tokens in this service, and the services' logins
 * are turned into XOAUTH2 under them, in Lunacy's preload (LunaRuntimes/pdk/libpreload/
 * mailauth.h), which asks [token] for the access token at each login. The services, Email
 * and Accounts stay as Palm made them; the handle is what Accounts keeps as the password.
 *
 * Two ways to sign in, the provider's choice of the two where it has one ([Provider]):
 * - **On the device**: authorization code with PKCE. The sign-in page shows the provider's
 *   own page in its web view and catches the redirect (webOS's `redirects`, as webOS apps
 *   signed in to Facebook), or, for a provider that refuses web views (Google), Android's
 *   browser opens it and the redirect comes back to a listener on this device's loopback.
 * - **Through the broker** (webOSArchive's oauth-broker-for-webos): for an engine too old for
 *   the provider's page. The page shows a short code and an address; the person signs in on
 *   any other device; this service polls the broker for the tokens, and refreshes through it,
 *   since the broker holds the client secret.
 *
 * Methods: `listProviders`, `signIn` (a subscription: the steps, then the account), `redirected`
 * (the page's caught redirect), `forget` - for Lunacy's own apps and the bundled apps the sign-in
 * page runs inside - and `token`, on the private bus only, which a handle is the key to.
 *
 * **Ratchet item:** tokens are kept in a file in Lunacy's private storage, like the key
 * manager's keys; a later target can wrap them with Android's keystore.
 */
class OAuth(
    private val context: Context,
    private val store: File,
    private val ownApp: (String) -> Boolean,
    /** Whether an app is the bundled one, not one installed over it. */
    private val bundled: (String) -> Boolean,
    devBuild: Boolean,
) {
    /** A provider, from assets/oauth/providers.json (and, on a development build, files/oauth-providers.json). */
    class Provider(val id: String, val j: JSONObject) {
        val title: String = j.optString("title", id)
        val clientId: String? = j.optString("clientId").takeIf { it.isNotEmpty() }
        val clientSecret: String? = j.optString("clientSecret").takeIf { it.isNotEmpty() }
        /** "page" (the sign-in page's web view) or "browser" (Android's, for a provider that refuses web views). */
        val onDevice: String = j.optString("onDevice", "page")
        val brokerApp: String? = j.optString("brokerApp").takeIf { it.isNotEmpty() }
        val canSignInHere get() = clientId != null && j.has("authorizeUrl") && j.has("tokenUrl")
        val canUseBroker get() = brokerApp != null
    }

    private class Account(val j: JSONObject) {
        val provider get() = j.optString("provider")
        var access: String get() = j.optString("access"); set(v) { j.put("access", v) }
        var expires: Long get() = j.optLong("expires"); set(v) { j.put("expires", v) }
        var refresh: String? get() = j.optString("refresh").takeIf { it.isNotEmpty() }; set(v) { j.put("refresh", v ?: "") }
        /** When the access token was had. */
        var got: Long get() = j.optLong("got"); set(v) { j.put("got", v) }
        val viaBroker get() = j.optBoolean("broker")
    }

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "oauth").apply { isDaemon = true } }
    private val random = SecureRandom()
    private val providers: Map<String, Provider>
    /** handle -> account. Touched on the worker thread only. */
    private val accounts: JSONObject = runCatching { JSONObject(store.readText()) }.getOrDefault(JSONObject())
    /** Sign-ins waiting for a redirect, by state. Main thread. */
    private val pending = HashMap<String, Pending>()
    private val broker: String

    private class Pending(val call: Bus.Call, val provider: Provider, val verifier: String, val redirectUri: String) {
        var listener: ServerSocket? = null
    }

    init {
        val all = LinkedHashMap<String, Provider>()
        fun read(text: String) {
            val j = JSONObject(text)
            j.optJSONObject("providers")?.let { ps -> for (id in ps.keys()) all[id] = Provider(id, ps.getJSONObject(id)) }
        }
        var brokerUrl = DEFAULT_BROKER
        runCatching { context.assets.open("oauth/providers.json").bufferedReader().use { it.readText() } }
            .onSuccess { t -> read(t); JSONObject(t).optString("broker").takeIf { it.isNotEmpty() }?.let { brokerUrl = it } }
            .onFailure { Log.w(AppServer.TAG, "oauth: no providers.json: $it") }
        // The client secrets the build was given (oauth.properties, kept out of the repository),
        // laid over the providers they belong to.
        runCatching { context.assets.open("oauth/clients.json").bufferedReader().use { it.readText() } }.getOrNull()?.let { t ->
            val ps = runCatching { JSONObject(t).optJSONObject("providers") }.getOrNull() ?: return@let
            for (id in ps.keys()) all[id]?.let { p -> val extra = ps.getJSONObject(id); for (k in extra.keys()) p.j.put(k, extra.get(k)) }
            for (id in ps.keys()) all[id]?.let { all[id] = Provider(id, it.j) }
        }
        // A development build also reads providers kept on the device: test servers (Workbench/oauth-test).
        if (devBuild) File(context.filesDir, "oauth-providers.json").takeIf { it.isFile }?.let { f ->
            runCatching { val t = f.readText(); read(t); JSONObject(t).optString("broker").takeIf { it.isNotEmpty() }?.let { brokerUrl = it } }
                .onFailure { Log.w(AppServer.TAG, "oauth: ${f.name}: $it") }
        }
        providers = all
        broker = brokerUrl.trimEnd('/')
    }

    fun register(bus: Bus) {
        this.bus = bus
        // A removed account's tokens go once Lunacy has started (the accounts service is up by then).
        main.postDelayed({ prune() }, 30_000)
        bus.own("listProviders") { c -> c.reply(listProviders()) }
        bus.own("signIn") { c -> signIn(c) }
        bus.own("redirected") { c -> redirected(c) }
        bus.own("forget") { c -> val h = c.params.optString("handle"); worker.execute { forget(h) }; c.reply(Bus.ok()) }
        bus.registerPrivate(SERVICE, "token", wholeService = false) { c -> token(c) }
    }

    /**
     * Lunacy's own apps, and the bundled apps a mail account's sign-in page runs inside: the
     * page (validator.customUI) is loaded by the app adding the account and calls the bus as
     * that app, as the Android account's page does through Contacts and Calendar.
     */
    private fun Bus.own(method: String, h: Bus.CallHandler) = register(SERVICE, method, Bus.CallHandler { c ->
        if (ownApp(c.appId) || (c.appId in PAGE_HOSTS && bundled(c.appId))) h.handle(c)
        else c.reply(Bus.error("Only Lunacy's own apps can sign accounts in (asked by ${c.appId})", -1))
    })

    private fun listProviders(): String {
        val list = org.json.JSONArray()
        for (p in providers.values) {
            // A provider that needs Android's browser is offered here only where there is one.
            val here = p.canSignInHere && (p.onDevice != "browser" || hasBrowser())
            list.put(JSONObject().put("id", p.id).put("title", p.title)
                .put("onDevice", if (here) p.onDevice else JSONObject.NULL).put("broker", p.canUseBroker))
        }
        return Bus.ok(mapOf("providers" to list))
    }

    private fun hasBrowser(): Boolean = context.packageManager.resolveActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/")).addCategory(Intent.CATEGORY_BROWSABLE), 0) != null

    // ---- signing in ----

    /**
     * `{provider, way: "device"|"broker"}`, a subscription. Its replies, in turn:
     * `{step: "page", url, redirect}` (load url in a web view, send `redirected` the address
     * that starts with redirect), or `{step: "browser"}` (Android's browser has it), or
     * `{step: "code", code, url}` (the broker's: show both); then `{step: "done", handle,
     * email, config}`, or an error. Cancelling the call ends the sign-in.
     */
    private fun signIn(call: Bus.Call) {
        val p = providers[call.params.optString("provider")] ?: return call.reply(Bus.error("Unknown provider", -2))
        when (call.params.optString("way", "device")) {
            "broker" -> if (p.canUseBroker) viaBroker(call, p) else call.reply(Bus.error("${p.title} has no broker sign-in", -3))
            else -> if (p.canSignInHere) onDevice(call, p) else call.reply(Bus.error("${p.title} isn't set up for sign-in on the device", -3))
        }
    }

    private fun hex(n: Int) = ByteArray(n).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private fun b64url(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun form(vararg kv: Pair<String, String?>) = kv.filter { it.second != null }.joinToString("&") { "${enc(it.first)}=${enc(it.second!!)}" }

    private fun onDevice(call: Bus.Call, p: Provider) {
        val verifier = b64url(ByteArray(32).also { random.nextBytes(it) })
        val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val state = hex(16)
        val browser = p.onDevice == "browser"
        var listener: ServerSocket? = null
        val redirectUri = if (browser) {
            // The loopback, at a port of the moment (RFC 8252 7.3): Google's and Microsoft's native clients allow it.
            listener = try { ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")) } catch (e: IOException) {
                return call.reply(Bus.error("Can't listen for the sign-in: $e", -4))
            }
            "http://${p.j.optString("loopbackHost", "127.0.0.1")}:${listener.localPort}/"
        } else p.j.optString("redirectUri", "http://localhost/lunacy-oauth")
        val extra = p.j.optJSONObject("authorizeParams")
        val q = mutableListOf("response_type" to "code", "client_id" to p.clientId!!, "redirect_uri" to redirectUri,
            "scope" to p.j.optString("scope"), "state" to state, "code_challenge" to challenge, "code_challenge_method" to "S256")
        extra?.keys()?.forEach { k -> q += k to extra.optString(k) }
        call.params.optString("hint").takeIf { it.isNotEmpty() }?.let { q += "login_hint" to it }
        val url = p.j.getString("authorizeUrl") + "?" + form(*q.toTypedArray())
        val waiting = Pending(call, p, verifier, redirectUri).also { it.listener = listener }
        pending[state] = waiting
        call.onCancel { main.post { pending.remove(state)?.listener?.let { runCatching { it.close() } } } }
        if (browser) {
            listenForRedirect(listener!!, state)
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                pending.remove(state); runCatching { listener.close() }
                return call.reply(Bus.error("No browser to sign in with", -5))
            }
            call.reply(Bus.ok(mapOf("step" to "browser")))
        } else {
            call.reply(Bus.ok(mapOf("step" to "page", "url" to url, "redirect" to redirectUri)))
        }
    }

    /** The loopback listener: the browser's one request with the code, answered with a page that says to go back. */
    private fun listenForRedirect(server: ServerSocket, state: String) = Thread({
        server.soTimeout = 15 * 60 * 1000
        while (!server.isClosed) {
            val s = try { server.accept() } catch (e: SocketTimeoutException) { break } catch (e: IOException) { break }
            val line = runCatching { s.getInputStream().bufferedReader().readLine() }.getOrNull().orEmpty()
            val target = line.split(' ').getOrNull(1).orEmpty()
            val query = parseQuery(target.substringAfter('?', ""))
            if (query["state"] != state) { runCatching { s.close() }; continue }   // a favicon, or someone else's
            val ok = query.containsKey("code")
            val page = "<!DOCTYPE html><html><head><meta name=viewport content=\"width=device-width\"><title>Lunacy</title></head>" +
                "<body style=\"font-family:sans-serif;margin:2em\"><h2>" + (if (ok) "Signed in" else "Not signed in") +
                "</h2><p>You can close this page and go back to Lunacy.</p></body></html>"
            runCatching {
                s.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\n" +
                    "Content-Length: ${page.toByteArray().size}\r\n\r\n$page").toByteArray())
                s.close()
            }
            runCatching { server.close() }
            main.post { finishRedirect(state, query) }
            backToLunacy()
            break
        }
    }, "oauth-loopback").apply { isDaemon = true }.start()

    /** Brings Lunacy back over the browser, where Android lets an app do that from behind. */
    private fun backToLunacy() = runCatching {
        context.startActivity(Intent(context, org.webosarchive.lunacy.shell.ShellActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    private fun parseQuery(q: String): Map<String, String> = q.split('&').filter { it.contains('=') }.associate {
        URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
    }

    /** `{url}`: the address the sign-in page's web view was sent to, which carries the code. */
    private fun redirected(c: Bus.Call) {
        val url = c.params.optString("url")
        val query = parseQuery(url.substringAfter('?', "").substringBefore('#'))
        val state = query["state"]
        if (state == null || !pending.containsKey(state)) return c.reply(Bus.error("No sign-in is waiting for that", -6))
        c.reply(Bus.ok())
        finishRedirect(state, query)
    }

    private fun finishRedirect(state: String, query: Map<String, String>) {
        val w = pending.remove(state) ?: return
        val code = query["code"]
        if (code == null) {
            val why = query["error_description"] ?: query["error"] ?: "no code"
            return w.call.reply(Bus.error("Sign-in didn't finish: $why", -7))
        }
        worker.execute {
            val r = runCatching {
                val tokens = post(w.provider.j.getString("tokenUrl"), form("grant_type" to "authorization_code", "code" to code,
                    "redirect_uri" to w.redirectUri, "client_id" to w.provider.clientId, "client_secret" to w.provider.clientSecret,
                    "code_verifier" to w.verifier))
                done(w.provider, tokens, viaBroker = false)
            }
            main.post { w.call.reply(r.getOrElse { Bus.error("Sign-in failed: ${it.message}", -8) }) }
        }
    }

    private fun viaBroker(call: Bus.Call, p: Provider) {
        val app = p.brokerApp!!
        var cancelled = false
        call.onCancel { cancelled = true }
        worker.execute {
            val start = runCatching { get("$broker/get-code.php?app=${enc(app)}") }
            val code = start.getOrNull()?.optString("code").orEmpty()
            if (code.isEmpty()) {
                main.post { call.reply(Bus.error("The sign-in helper didn't answer: ${start.exceptionOrNull()?.message ?: start.getOrNull()?.optString("error")}", -9)) }
                return@execute
            }
            val wait = start.getOrNull()!!.optInt("pollSeconds", 3).coerceIn(2, 30) * 1000L
            main.post { call.reply(Bus.ok(mapOf("step" to "code", "code" to code, "url" to start.getOrNull()!!.optString("useUrl")))) }
            // The broker keeps a code for two hours; the page is left open for much less.
            Thread({
                val until = System.currentTimeMillis() + 30 * 60 * 1000
                while (!cancelled && System.currentTimeMillis() < until) {
                    Thread.sleep(wait)
                    val r = runCatching { get("$broker/check-code.php?app=${enc(app)}&code=${enc(code)}") }.getOrNull() ?: continue
                    if (r.optString("status") == "pending") continue
                    val reply = if (r.has("access_token")) runCatching { onWorker { done(p, r, viaBroker = true) } }
                        .getOrElse { Bus.error("Sign-in failed: ${it.message}", -8) }
                        else Bus.error("The sign-in helper: ${r.optString("error", "the code ran out")}", -10)
                    main.post { call.reply(reply) }
                    return@Thread
                }
                if (!cancelled) main.post { call.reply(Bus.error("The code ran out before sign-in finished", -10)) }
            }, "oauth-broker").apply { isDaemon = true }.start()
        }
    }

    /** f on the worker thread, waited for (the broker's poller is a thread of its own). */
    private fun <T> onWorker(f: () -> T): T = worker.submit<T> { f() }.get()

    /** Stores a new account's tokens (worker thread) and says what the sign-in page needs. */
    private fun done(p: Provider, tokens: JSONObject, viaBroker: Boolean): String {
        val access = tokens.optString("access_token").takeIf { it.isNotEmpty() } ?: throw IOException(tokens.optString("error_description", tokens.optString("error", "no token")))
        val email = emailOf(tokens) ?: throw IOException("${p.title} didn't say which account this is")
        // Google lets the person untick a permission on its consent page (granular consent); a
        // token without the mail scope would only fail later, at the mail server, as a bad login.
        val granted = tokens.optString("scope")
        Log.i(AppServer.TAG, "oauth: ${p.id} granted: $granted")
        p.j.optString("requiredScope").takeIf { it.isNotEmpty() && granted.isNotEmpty() }?.let { need ->
            if (need !in granted.split(' ')) throw IOException("${p.title} wasn't given access to your mail. Sign in again and tick the box that lets webOS Email read and send your email.")
        }
        // Signing the same account in again (its sign-in stopped working) keeps its handle, which
        // is the account's password: only the tokens behind it change.
        // Every handle this account was given before (an account kept, a sign-in abandoned) is
        // pointed at the new tokens, so whichever one Accounts holds as the password works again.
        // One record holds them: a provider that hands out a new refresh token with each refresh
        // (Microsoft) would leave copies stale.
        val same = accounts.keys().asSequence().filter { h ->
            accounts.optJSONObject(h)?.let { it.optString("provider") == p.id && it.optString("email").equals(email, ignoreCase = true) } == true
        }.toList()
        val existing = same.firstOrNull()
        val handle = existing ?: (HANDLE + hex(16))
        accounts.put(handle, JSONObject().put("provider", p.id).put("email", email).put("access", access).put("got", System.currentTimeMillis())
            .put("expires", System.currentTimeMillis() + tokens.optLong("expires_in", 3600) * 1000)
            .put("refresh", tokens.optString("refresh_token")).put("broker", viaBroker))
        for (h in same) if (h != handle) accounts.put(h, JSONObject().put("provider", p.id).put("email", email).put("same", handle))
        save()
        Log.i(AppServer.TAG, "oauth: signed in to ${p.id}${if (viaBroker) " through the broker" else ""}")
        val config = p.j.optJSONObject("mail")?.let { JSONObject(it.toString()) } ?: JSONObject()
        config.put("email", email).put("username", email)
        config.optJSONObject("smtpConfig")?.put("username", email)
        return Bus.ok(mapOf("step" to "done", "handle" to handle, "existing" to (existing != null), "email" to email, "config" to config,
            "templateId" to p.j.optString("templateId")))
    }

    /** The account's address, from the OpenID id_token both providers send with the tokens. */
    private fun emailOf(tokens: JSONObject): String? {
        val idToken = tokens.optString("id_token").takeIf { it.isNotEmpty() } ?: return null
        val claims = runCatching {
            JSONObject(String(Base64.decode(idToken.split('.')[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)))
        }.getOrNull() ?: return null
        return listOf("email", "preferred_username", "upn").map { claims.optString(it) }.firstOrNull { it.contains('@') }
    }

    // ---- keeping signed in ----

    /**
     * `{handle}` (private bus): the access token for a mail service's login, refreshed first
     * when it has less than five minutes left. `{accessToken: ""}` when the account's sign-in
     * no longer works (the refresh was refused): the login then fails as a wrong password,
     * and Email asks for the account to be signed in again.
     */
    private fun token(c: Bus.Call) {
        val handle = c.params.optString("handle")
        worker.execute {
            // A handle given again by a later sign-in of the same account points at its tokens.
            val held = accounts.optJSONObject(handle)?.optString("same")?.takeIf { it.isNotEmpty() } ?: handle
            val a = accounts.optJSONObject(held)?.let { Account(it) }
            // `stale`: the server refused the token last given for this handle (the provider ended
            // it early: a password change, a revocation). Refreshed then, unless it is brand new.
            val stale = c.params.optBoolean("stale") && System.currentTimeMillis() - (a?.got ?: 0) > 60 * 1000
            val reply = when {
                a == null -> Bus.error("Unknown handle", -11)
                !stale && a.expires - System.currentTimeMillis() > 5 * 60 * 1000 -> Bus.ok(mapOf("accessToken" to a.access))
                else -> Bus.ok(mapOf("accessToken" to refreshed(held, a)))
            }
            main.post { c.reply(reply) }
        }
    }

    /** The account's new access token, or "" when the provider refused the refresh. Worker thread. */
    private fun refreshed(handle: String, a: Account): String {
        val p = providers[a.provider]
        val refresh = a.refresh ?: return ""
        val r = runCatching {
            if (a.viaBroker) post("$broker/refresh.php?app=${enc(p?.brokerApp ?: a.provider)}", form("refresh_token" to refresh))
            else post(p!!.j.getString("tokenUrl"), form("grant_type" to "refresh_token", "refresh_token" to refresh,
                "client_id" to p.clientId, "client_secret" to p.clientSecret))
        }
        val t = r.getOrNull()
        if (t != null && t.optString("access_token").isNotEmpty()) {
            a.access = t.getString("access_token")
            a.got = System.currentTimeMillis()
            a.expires = a.got + t.optLong("expires_in", 3600) * 1000
            t.optString("refresh_token").takeIf { it.isNotEmpty() }?.let { a.refresh = it }
            save()
            Log.i(AppServer.TAG, "oauth: refreshed a ${a.provider} token")
            return a.access
        }
        // Refused (a revoked or expired refresh token) is the end of this sign-in; a network
        // failure isn't: the old token is tried, and the next login asks again.
        if (t != null && (t.optString("error") == "invalid_grant" || t.optString("status") == "invalid_grant")) {
            Log.w(AppServer.TAG, "oauth: ${a.provider} refused the refresh; the account needs signing in again")
            a.refresh = null; a.access = ""; save()
            return ""
        }
        Log.w(AppServer.TAG, "oauth: couldn't refresh a ${a.provider} token: ${r.exceptionOrNull()?.message ?: t}")
        return a.access
    }

    private var bus: Bus? = null

    /**
     * Drops the tokens of accounts that are gone (Remove Account), on start: an account is
     * named by its type and address, as Accounts keeps it. A sign-in less than a day old is
     * left, since its account may still be being made. Main thread.
     */
    private fun prune() {
        val b = bus ?: return
        val types = providers.values.associate { it.j.optString("templateId") to it.id }.filterKeys { it.isNotEmpty() }
        val q = JSONObject().put("from", "com.palm.account:1")
        b.call("com.palm.service.accounts", "palm://com.palm.db/find", JSONObject().put("query", q).toString(), privateBus = true) { reply ->
            val r = runCatching { JSONObject(reply) }.getOrNull()
            if (r == null || !r.optBoolean("returnValue")) return@call
            val kept = HashSet<String>()
            val rs = r.optJSONArray("results") ?: org.json.JSONArray()
            for (i in 0 until rs.length()) {
                val a = rs.optJSONObject(i) ?: continue
                if (a.optBoolean("beingDeleted")) continue
                val provider = types[a.optString("templateId")] ?: continue
                kept += provider + " " + a.optString("username").lowercase()
            }
            worker.execute {
                val old = System.currentTimeMillis() - 24 * 3600 * 1000L
                val gone = accounts.keys().asSequence().filter { h ->
                    val a = accounts.optJSONObject(h) ?: return@filter true
                    (a.optString("provider") + " " + a.optString("email").lowercase()) !in kept && a.optLong("got") < old
                }.toList()
                if (gone.isNotEmpty()) {
                    gone.forEach { accounts.remove(it) }
                    save()
                    Log.i(AppServer.TAG, "oauth: forgot the tokens of ${gone.size} removed account(s)")
                }
            }
        }
    }

    private fun forget(handle: String) {
        if (accounts.remove(handle) != null) save()
    }

    private fun save() {
        val tmp = File(store.path + ".tmp")
        store.parentFile?.mkdirs()
        tmp.writeText(accounts.toString())
        tmp.renameTo(store)
    }

    // ---- HTTP ----

    private fun get(url: String): JSONObject = request("GET", url, null)
    private fun post(url: String, body: String): JSONObject = request("POST", url, body)

    /** The JSON a token endpoint or the broker answers, error statuses included (they carry `error`). */
    private fun request(method: String, url: String, body: String?): JSONObject {
        val c = Http.connect(Http.Request(method, url,
            listOf("Accept" to "application/json", "Cookie" to "") +
                (if (body != null) listOf("Content-Type" to "application/x-www-form-urlencoded") else emptyList()),
            body?.toByteArray(), connectTimeoutMs = 20_000, readTimeoutMs = 30_000))
        try {
            val text = (if (c.responseCode >= 400) c.errorStream else c.inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            return runCatching { JSONObject(text) }.getOrElse { throw IOException("HTTP ${c.responseCode} from ${Uri.parse(url).host}") }
        } finally { c.disconnect() }
    }

    companion object {
        const val SERVICE = "org.webosarchive.lunacy.oauth"
        /** Kept in mailauth.h too (MAILAUTH_HANDLE). */
        const val HANDLE = "oauth2:"
        const val DEFAULT_BROKER = "https://oauth.wosa.link"
        /** The apps that add mail accounts: Email's own wizard, and Accounts in Settings. */
        private val PAGE_HOSTS = setOf("com.palm.app.email", "com.palm.app.accounts")
    }
}
