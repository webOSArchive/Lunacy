package org.webosarchive.lunacy.card

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Exchange's device security policies and the device lock they ask for: the part of
 * palm://com.palm.systemmanager that LunaSysMgr's EASPolicyManager and Security served
 * (getSecurityPolicy, getDeviceLockMode, setDevicePasscode, matchDevicePasscode).
 *
 * The policies are webOS's, kept as EASPolicyManager kept them (luna-sysmgr, Apache 2.0): every
 * com.palm.securitypolicy record (mojomail-eas writes com.palm.securitypolicy.eas, one per
 * account, isTemp while an account is being set up) merges into one aggregate, the strictest of
 * them, which is saved as the single com.palm.securitypolicy.device record with its status.
 * mojomail-eas reads that record to tell the server whether the device complies.
 *
 * The lock is Android's (codepoet, 2026-10-10; luna-deltas A21). Lunacy has no lock screen of
 * its own, and webOS's passcode would protect nothing, so a policy is enforced when Android has
 * a secure screen lock. Setting a PIN or password (Email's "Device Password Required", or Screen
 * & Lock) checks the passcode against the policy exactly as the TouchPad did, then opens
 * Android's own screen-lock setup, and answers by what Android has when the person comes back;
 * the passcode typed into webOS's dialog is not kept. Checking a passcode opens Android's own
 * confirmation. Replies are the reference TouchPad's, measured with luna-send (Docs/eas.md).
 */
class SecurityPolicy(private val bus: Bus, private val android: Host, private val store: File) {
    /** What Android provides. Each call is made on the main thread; done runs there too. */
    interface Host {
        /** Whether Android's screen lock is secure (a PIN, password or pattern). */
        fun isSecure(): Boolean
        /** Opens Android's screen-lock setup; done when the person is back in Lunacy. False if it can't be opened. */
        fun chooseLock(done: () -> Unit): Boolean
        /** Asks Android to confirm the person's credential; false if it can't be asked. */
        fun confirmLock(done: (Boolean) -> Unit): Boolean
    }

    /** EASPolicy: one policy, or the aggregate (the device policy). */
    private class Policy(val isDevice: Boolean = false) {
        var passwordRequired = false
        var maxRetries = 0
        var minLength = 1
        var alphaNumeric = false
        var allowSimple = true
        var inactivity = 9998 // the highest timeout allowed
        var id = ""
        var rev = 0L
        var deleted = false

        fun validMaxRetries() = passwordRequired && maxRetries > 1
        fun validMinLength() = passwordRequired && minLength > 1

        /** Rounded down to the lock timeouts webOS offered: 30 s steps below a minute, then minutes; 0 above 9998. */
        fun maxInactivity(): Int = when {
            inactivity < 60 -> inactivity - inactivity % 30
            inactivity < 9999 -> inactivity - inactivity % 60
            else -> 0
        }

        fun same(o: Policy) = passwordRequired == o.passwordRequired && maxRetries == o.maxRetries &&
            minLength == o.minLength && alphaNumeric == o.alphaNumeric && allowSimple == o.allowSimple &&
            inactivity == o.inactivity

        /** The stricter of the two (EASPolicy::merge). */
        fun merge(n: Policy) {
            if (!passwordRequired && n.passwordRequired) {
                passwordRequired = true
                maxRetries = n.maxRetries; minLength = n.minLength; alphaNumeric = n.alphaNumeric; allowSimple = n.allowSimple
                inactivity = n.maxInactivity()
            } else if (passwordRequired && n.passwordRequired) {
                if (n.inactivity < inactivity) inactivity = n.maxInactivity()
                if (n.validMaxRetries() && (!validMaxRetries() || n.maxRetries < maxRetries)) maxRetries = n.maxRetries
                if (!alphaNumeric && n.alphaNumeric) alphaNumeric = true
                if (allowSimple && !n.allowSimple) allowSimple = false
                if (n.validMinLength() && n.minLength > minLength) minLength = n.minLength
            }
        }

        /** The policy getSecurityPolicy answers (EASPolicy::toJSON). */
        fun toJson(): JSONObject {
            val password = JSONObject().put("enabled", passwordRequired).put("minLength", minLength)
                .put("maxRetries", maxRetries).put("alphaNumeric", alphaNumeric)
            if (passwordRequired && !alphaNumeric) password.put("allowSimplePassword", allowSimple)
            return JSONObject().put("password", password).put("inactivityInSeconds", inactivity).put("id", id)
        }

        /** The record's fields (EASPolicy::toNewJSON). */
        fun toRecord(): JSONObject = JSONObject()
            .put("devicePasswordEnabled", passwordRequired).put("minDevicePasswordLength", minLength)
            .put("maxDevicePasswordFailedAttempts", maxRetries).put("alphanumericDevicePasswordRequired", alphaNumeric)
            .put("allowSimpleDevicePassword", allowSimple).put("maxInactivityTimeDeviceLock", inactivity)
            .apply { if (id.isNotEmpty()) put("_id", id); if (isDevice) put("_kind", DEVICE_KIND) }

        companion object {
            /** A db8 record (EASPolicy::fromNewJSON), with its defaults for what it leaves out. */
            fun from(o: JSONObject) = Policy(o.optString("_kind") == DEVICE_KIND).apply {
                passwordRequired = o.optBoolean("devicePasswordEnabled", false)
                alphaNumeric = o.optBoolean("alphanumericDevicePasswordRequired", false)
                minLength = o.optInt("minDevicePasswordLength", 1)
                maxRetries = o.optInt("maxDevicePasswordFailedAttempts", 1)
                inactivity = o.optInt("maxInactivityTimeDeviceLock", 0)
                allowSimple = o.optBoolean("allowSimpleDevicePassword", true)
                id = (o.opt("_id") as? String).orEmpty()
                rev = if (id.isNotEmpty()) o.optLong("_rev", 0) else 0
                deleted = o.optBoolean("_del", false)
            }
        }
    }

    private var aggregate = Policy(true)
    private var enforced = false
    private var retriesLeft = 0
    private var lastRev = 0L
    private var watch: Bus.Call? = null
    private var loaded = false
    private val lockSubscribers = LinkedHashSet<Bus.Call>()
    /** The mode last set through webOS (pin or password), reported while Android's lock is secure. */
    private var chosenMode: String = runCatching { JSONObject(store.readText()).optString("lockMode") }.getOrDefault("")
    /** Whether Android's lock was secure when Lunacy last looked: a change made in Android's settings is noticed on return. */
    private var wasSecure: Boolean? = null

    fun register() {
        bus.register(SERVICE, "getSecurityPolicy", Bus.CallHandler { it.reply(policyReply()) })
        bus.register(SERVICE, "getDeviceLockMode", Bus.CallHandler { getDeviceLockMode(it) })
        bus.register(SERVICE, "setDevicePasscode", Bus.CallHandler { setDevicePasscode(it) })
        bus.register(SERVICE, "matchDevicePasscode", Bus.CallHandler { matchDevicePasscode(it) })
    }

    /**
     * EASPolicyManager::load, once db8 has its kinds: temporary policies (an account set up but
     * never finished) go, the saved device policy comes back, and every policy is read and
     * watched. Called again after the configurator runs, until a load has found the kinds.
     */
    fun load() {
        if (loaded) return
        db("del", JSONObject().put("query", JSONObject().put("from", BASE_KIND)
            .put("where", JSONArray().put(JSONObject().put("prop", "isTemp").put("op", "=").put("val", true))))) {
            db("find", JSONObject().put("query", JSONObject().put("from", DEVICE_KIND))) { r ->
                if (!r.optBoolean("returnValue")) { Log.w(AppServer.TAG, "security policy: no device policy kind yet: $r"); return@db }
                loaded = true
                val record = r.optJSONArray("results")?.optJSONObject(0)
                setDevicePolicy(record)
                queryPolicies()
            }
        }
    }

    /** The shell is in front again: Android's lock may have changed in its settings meanwhile. */
    fun recheck() {
        val secure = android.isSecure()
        if (wasSecure == secure) return
        wasSecure = secure
        if (!loaded) return
        val was = enforced
        enforced = satisfied()
        if (enforced != was) { Log.i(AppServer.TAG, "security policy: Android's lock ${if (secure) "set" else "removed"}; enforced $enforced"); save() }
        postLockMode()
    }

    // ---- the policies (EASPolicyManager) ----

    private fun setDevicePolicy(record: JSONObject?) {
        lastRev = 0
        aggregate = Policy(true)
        if (record == null) return policyChanged()
        aggregate = Policy.from(record)
        val status = record.optJSONObject("status")
        enforced = status?.optBoolean("enforced", false) ?: false
        retriesLeft = if (status?.has("retriesLeft") == true) status.optInt("retriesLeft") else aggregate.maxRetries
        // Android's lock may have changed while Lunacy wasn't running.
        if (enforced != satisfied()) { enforced = satisfied(); save() }
    }

    private fun queryPolicies() {
        db("find", JSONObject().put("query", JSONObject().put("from", BASE_KIND).put("incDel", true))) { r ->
            r.optJSONArray("results")?.let { updateDevicePolicy(it) }
        }
    }

    private fun updateDevicePolicy(policies: JSONArray) {
        val old = aggregate
        lastRev = 0
        aggregate = Policy(true).also { it.id = old.id }
        for (i in 0 until policies.length()) {
            val p = Policy.from(policies.optJSONObject(i) ?: continue)
            if (!p.isDevice && !p.deleted) aggregate.merge(p)
            if (p.rev > lastRev) lastRev = p.rev
        }
        if (old.maxRetries != aggregate.maxRetries) retriesLeft = aggregate.maxRetries
        if (old.same(aggregate)) watchPolicies() else policyChanged()
    }

    private fun watchPolicies() {
        watch?.cancel()
        val q = JSONObject().put("from", BASE_KIND).put("incDel", true)
            .put("where", JSONArray().put(JSONObject().put("prop", "_rev").put("op", ">").put("val", lastRev)))
        watch = bus.call(CALLER, "palm://com.palm.db/watch", JSONObject().put("query", q).toString(), privateBus = true) { reply ->
            val r = runCatching { JSONObject(reply) }.getOrNull() ?: return@call
            if (r.optBoolean("fired")) { watch = null; queryPolicies() }
        }
    }

    /** EASPolicyManager::notifyPolicyChanged: enforced at once if the lock already satisfies it. */
    private fun policyChanged() {
        enforced = satisfied()
        Log.i(AppServer.TAG, "security policy: password ${if (aggregate.passwordRequired) "required" else "not required"}; enforced $enforced")
        postLockMode()
        save()
    }

    /** Security::passcodeSatisfiesPolicy, with Android's screen lock as the passcode. */
    private fun satisfied() = !aggregate.passwordRequired || android.isSecure()

    /** The device policy record, written as EASPolicyManager::save did; its watch is set again after. */
    private fun save() {
        watch?.cancel(); watch = null
        val record = aggregate.toRecord().put("status", status())
        val method = if (aggregate.id.isEmpty()) "put" else "merge"
        db(method, JSONObject().put("objects", JSONArray().put(record))) { r ->
            r.optJSONArray("results")?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotEmpty() }?.let { aggregate.id = it }
            watchPolicies()
        }
    }

    private fun status() = JSONObject().put("enforced", enforced).put("retriesLeft", retriesLeft)

    private fun state() = if (enforced) "active" else "pending"

    /** EASPolicyManager::retriesLeft: meaningful only for an enforced policy with a retry limit. */
    private fun retriesReported() = if (!aggregate.validMaxRetries() || !enforced) 0 else retriesLeft

    // ---- the methods ----

    /** Measured: {"policy":{password, inactivityInSeconds, id, status}, "returnValue":true}, a default policy when no account sets one. */
    private fun policyReply(): String {
        val policy = aggregate.toJson().put("status", status())
        return JSONObject().put("policy", policy).put("returnValue", true).toString()
    }

    private fun lockMode(): String = if (!android.isSecure()) "none" else chosenMode.ifEmpty { "password" }

    private fun lockModeReply(subscribed: Boolean) = JSONObject().put("returnValue", true).put("subscribed", subscribed)
        .put("lockMode", lockMode()).put("policyState", state()).put("retriesLeft", retriesReported()).toString()

    private fun getDeviceLockMode(call: Bus.Call) {
        val subscribe = call.subscribe && !call.cancelled
        call.reply(lockModeReply(subscribe))
        if (subscribe) { lockSubscribers += call; call.onCancel { lockSubscribers.remove(call) } }
    }

    private fun postLockMode() {
        val reply = lockModeReply(true)
        lockSubscribers.toList().forEach { it.reply(reply) }
    }

    /**
     * {"lockMode":"none"|"pin"|"password","passCode":…}. The passcode is checked against the
     * policy as Security::setPasscode did, with its errors (measured: a 2-digit PIN under a
     * minimum of 4 is -2 "Passcode not minimum length", "abcd" as a PIN -5 "Pin invalid");
     * any other mode succeeds and changes nothing, as on the TouchPad. Then Android's
     * screen-lock setup, and the answer is what Android has afterwards.
     */
    private fun setDevicePasscode(call: Bus.Call) {
        val mode = call.params.optString("lockMode")
        val passcode = call.params.optString("passCode")
        val fail = validate(mode, passcode)
        if (fail != 0) return call.reply(failure(fail, mode))
        if (mode != "none" && mode != "pin" && mode != "password") return call.reply(Bus.ok())
        if (mode != "none" && passcode.isEmpty()) return call.reply(failure(EMPTY, mode, "Passcode empty"))
        val wantSecure = mode != "none"
        if (!wantSecure && !android.isSecure()) return done(call, mode)
        val opened = android.chooseLock {
            wasSecure = android.isSecure()
            if (android.isSecure() == wantSecure) done(call, mode)
            else call.reply(failure(SAVE, mode, if (wantSecure) "Android's screen lock was not set" else "Android's screen lock is still on"))
        }
        if (!opened) call.reply(failure(SAVE, mode, "Android's screen lock settings can't be opened"))
    }

    private fun done(call: Bus.Call, mode: String) {
        chosenMode = if (mode == "none") "" else mode
        runCatching { store.writeText(JSONObject().put("lockMode", chosenMode).toString()) }
        // EASPolicyManager::passwordEnforced
        if (aggregate.passwordRequired && !enforced && android.isSecure()) {
            enforced = true; retriesLeft = aggregate.maxRetries; save()
        } else if (aggregate.passwordRequired && enforced && !android.isSecure()) {
            enforced = false; save()
        }
        postLockMode()
        call.reply(Bus.ok())
    }

    /** {"passCode":…}: Android confirms the person's credential. No lock is never a match, as on the TouchPad. */
    private fun matchDevicePasscode(call: Bus.Call) {
        val miss = JSONObject().put("returnValue", false).put("lockedOut", false).put("retriesLeft", retriesReported()).toString()
        if (!call.params.has("passCode") || !android.isSecure()) return call.reply(miss)
        if (!android.confirmLock { ok -> call.reply(if (ok) Bus.ok() else miss) }) call.reply(miss)
    }

    /** Security::validatePasscode: 0, or the failure code. Only a policy that requires a password checks anything. */
    private fun validate(mode: String, passcode: String): Int {
        val p = aggregate
        if (!p.passwordRequired) return 0
        if (mode == "none" || passcode.isEmpty()) return EMPTY
        if (p.validMinLength() && passcode.length < p.minLength) return MIN_LENGTH
        if (p.alphaNumeric) {
            if (mode != "password") return INVALID_PASSWORD
            if (!passcode.any { it.isLetter() } || !passcode.any { it.isDigit() }) return ALPHANUMERIC
        } else if (mode == "pin" && !passcode.all { it.isDigit() }) return INVALID_PIN
        if (!p.allowSimple) return strength(passcode)
        return 0
    }

    /** Security::validateStrength: no run of repeated or consecutive characters longer than half the passcode. */
    private fun strength(s: String): Int {
        val max = s.length / 2
        var i = 0
        while (i < s.length - 1) {
            val direction = s[i + 1] - s[i]
            if (direction in -1..1) {
                var count = 2; var cur = s[i + 1]; var j = i + 2
                while (j < s.length) {
                    val diff = s[j] - cur
                    if (diff != direction) break
                    count++
                    if (count > max) return if (diff == 0) WEAK_REPEAT else WEAK_SEQUENCE
                    cur = s[j]; j++
                }
                i = j - 1
            }
            i++
        }
        return 0
    }

    private fun failure(code: Int, mode: String, text: String? = null): String {
        val t = text ?: when (code) {
            EMPTY -> "Passcode is empty"
            MIN_LENGTH -> "Passcode not minimum length"
            INVALID_PASSWORD, ALPHANUMERIC -> "Alphanumeric characters required"
            INVALID_PIN -> "Pin invalid"
            WEAK_REPEAT -> if (mode == "pin") "No repeating numbers (3333)" else "No repeating characters (aaaa)"
            WEAK_SEQUENCE -> if (mode == "pin") "No sequential numbers (1234)" else "No sequential characters (abcd)"
            else -> "Passcode general failure"
        }
        return JSONObject().put("returnValue", false).put("errorText", t).put("errorCode", code).toString()
    }

    private fun db(method: String, params: JSONObject, reply: (JSONObject) -> Unit) {
        bus.call(CALLER, "palm://com.palm.db/$method", params.toString(), privateBus = true) { r ->
            runCatching { JSONObject(r) }.getOrNull()?.let(reply)
        }
    }

    companion object {
        const val SERVICE = "com.palm.systemmanager"
        /** The device policy's owner, and so the identity db8 sees. */
        private const val CALLER = "com.palm.systemmanager"
        private const val BASE_KIND = "com.palm.securitypolicy:1"
        private const val DEVICE_KIND = "com.palm.securitypolicy.device:1"
        // Security::FailureCode
        private const val EMPTY = -1
        private const val MIN_LENGTH = -2
        private const val INVALID_PASSWORD = -3
        private const val ALPHANUMERIC = -4
        private const val INVALID_PIN = -5
        private const val SAVE = -7
        private const val WEAK_REPEAT = -8
        private const val WEAK_SEQUENCE = -9
    }
}
