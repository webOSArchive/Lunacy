package org.webosarchive.lunacy.card

import org.json.JSONObject
import java.io.File

/**
 * palm://com.palm.keymanager: webOS's store for secrets (passwords, auth tokens). The accounts
 * service keeps every account's credentials here, one key per account, and apps can keep their
 * own. Keys belong to their caller: another app asking for the same name finds nothing.
 *
 * Replies are the reference TouchPad's, measured with `Workbench/probe/keymanager-probe.sh`
 * (webOS CE 3.1.0), device quirks included: `keyInfo` answers the owner's id as `keyname`, and
 * `shared` follows `nohide`. A key stored without `nohide` can't be fetched back ("key is not
 * exportable"), only used, which Lunacy has no crypto calls for yet.
 *
 * **Ratchet item:** the device kept keys encrypted; Lunacy keeps them in a file in its own
 * private storage. A later target can wrap them with Android's keystore.
 */
class KeyManager(private val store: File) {
    /** owner -> keyname -> the key's record. Main thread. */
    private val keys: JSONObject = runCatching { JSONObject(store.readText()) }.getOrDefault(JSONObject())

    fun register(bus: Bus) {
        bus.register(SERVICE, "store") { app, p, reply -> reply(storeKey(app, p)) }
        bus.register(SERVICE, "fetchKey") { app, p, reply -> reply(fetch(app, p)) }
        bus.register(SERVICE, "keyInfo") { app, p, reply -> reply(info(app, p)) }
        bus.register(SERVICE, "remove") { app, p, reply -> reply(remove(app, p)) }
    }

    private fun named(p: JSONObject): String? = p.optString("keyname").takeIf { p.has("keyname") }

    private fun missingName() = JSONObject().put("errorCode", -986)
        .put("errorText", "required prop not found: 'keyname'").put("returnValue", false).toString()

    private fun failed(text: String) = JSONObject().put("errorText", text).put("returnValue", false).toString()

    private fun storeKey(owner: String, p: JSONObject): String {
        val name = named(p) ?: return missingName()
        val mine = keys.optJSONObject(owner) ?: JSONObject().also { keys.put(owner, it) }
        if (mine.has(name)) return failed("key exists")
        val nohide = p.optBoolean("nohide", false)
        mine.put(name, JSONObject()
            .put("keydata", p.optString("keydata")).put("type", p.optString("type", "ASCIIBLOB"))
            .put("nohide", nohide).put("backup", p.optBoolean("backup", false)).put("cloud", p.optBoolean("cloud", false))
            .put("noexport", p.optBoolean("noexport", false)))
        save()
        return Bus.ok()
    }

    private fun fetch(owner: String, p: JSONObject): String {
        val name = named(p) ?: return missingName()
        val k = keys.optJSONObject(owner)?.optJSONObject(name) ?: return failed("Key does not exist")
        if (!k.optBoolean("nohide")) return failed("key is not exportable")
        return describe(k, name).put("keydata", k.optString("keydata")).toString()
    }

    private fun info(owner: String, p: JSONObject): String {
        val name = named(p) ?: return missingName()
        val k = keys.optJSONObject(owner)?.optJSONObject(name) ?: return JSONObject().put("returnValue", false).toString()
        return describe(k, owner).toString()
    }

    private fun remove(owner: String, p: JSONObject): String {
        val name = named(p) ?: return missingName()
        val mine = keys.optJSONObject(owner)
        if (mine?.has(name) != true) return failed("unknown key")
        mine.remove(name)
        save()
        return Bus.ok()
    }

    /** The fields every reply about a key carries, in the device's shape. */
    private fun describe(k: JSONObject, keyname: String) = JSONObject()
        .put("backup", k.optBoolean("backup")).put("cloud", k.optBoolean("cloud")).put("keyname", keyname)
        .put("noexport", k.optBoolean("noexport")).put("nohide", k.optBoolean("nohide")).put("returnValue", true)
        .put("shared", k.optBoolean("nohide")).put("type", k.optString("type")).put("unwrap_only", false)

    private fun save() {
        val tmp = File(store.path + ".tmp")
        tmp.writeText(keys.toString())
        tmp.renameTo(store)
    }

    companion object { const val SERVICE = "com.palm.keymanager" }
}
