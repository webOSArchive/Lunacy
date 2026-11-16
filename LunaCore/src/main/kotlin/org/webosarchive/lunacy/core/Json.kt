package org.webosarchive.lunacy.core

import org.json.JSONObject

/**
 * An object's keys, as strings. LunaCore compiles against AOSP's org.json as it was published,
 * where keys() is a raw Iterator; Android's SDK declares the same method Iterator<String>. Every
 * key is a String in both.
 */
@Suppress("UNCHECKED_CAST")
fun JSONObject.keyNames(): Iterator<String> = keys() as Iterator<String>
