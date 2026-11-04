package org.webosarchive.lunacy.card

import org.json.JSONObject
import java.io.File

/**
 * webOS 3's portability, as the TouchPad's libnapp did it: an app's `metadata.json`
 * (`{"version": 1, "devices": [601]}`) lists the devices it was made for, and on a device not
 * in the list the app is shown one that is: `PDL_GetHardwareID` answers that device, the screen
 * is its screen, and its OpenGL surface is forced to that size (libnapp's `final_spoofing`,
 * `final_ogl`), which the card turns and fits like any other-shape buffer.
 *
 * The list's numbers are libnapp's own, not PDL's hardware ids: read from the reference
 * TouchPad's libnapp.so (2026-10-19), each device has an id, a machine name, a screen held its
 * own way up, and the devices it can stand in for, most alike first - the TouchPad (701,
 * "topaz") 601, 103, 102, 101, 401, 202, 201. The first of those the app lists is emulated.
 * Measured: Worms (devices [601], the Pre3) gets a 480 x 800 screen on the TouchPad and is
 * shown upright; ExZeus ([101], the Pre) gets 320 x 480, landscape with black bars.
 */
object Portability {
    class Device(val id: Int, val width: Int, val height: Int)

    /** libnapp's table: id to its screen and the devices it stands in for, in order. */
    private class Napp(val width: Int, val height: Int, val standsInFor: List<Int>)
    private val napp = mapOf(
        101 to Napp(320, 480, listOf(401, 202, 201)),                         // Pre ("castle")
        102 to Napp(320, 480, listOf(101, 401, 202, 201)),                    // Pre Plus
        103 to Napp(320, 480, listOf(102, 101, 401, 202, 201)),               // Pre 2 ("roadrunner")
        201 to Napp(320, 400, emptyList()),                                   // Pixi ("pixie")
        202 to Napp(320, 400, listOf(201)),                                   // Pixi Plus
        401 to Napp(320, 400, listOf(202, 201)),                              // Veer ("broadway")
        601 to Napp(480, 800, listOf(103, 102, 101, 401, 202, 201)),          // Pre3 ("mantaray")
        701 to Napp(1024, 768, listOf(601, 103, 102, 101, 401, 202, 201)),    // TouchPad ("topaz")
        702 to Napp(1024, 768, listOf(701, 601, 103, 102, 101, 401, 202, 201))) // TouchPad Go ("opal")

    /** libnapp's id to PDL's hardware id (PDL_types.h), which PDL_GetHardwareID answers. */
    private val pdlId = mapOf(101 to 101, 102 to 102, 103 to 401, 201 to 201, 202 to 201, 401 to 301,
        601 to 501, 701 to 601, 702 to 701)

    /** The profile's device in libnapp's numbers: the TouchPad, or the Pre3 on the phone profile. */
    fun nappId(pdlHardwareId: Int): Int = if (pdlHardwareId == 501) 601 else 701

    fun emulate(appDir: File, native: Int): Device? {
        val meta = File(appDir, "metadata.json").takeIf { it.isFile } ?: return null
        val json = runCatching { JSONObject(meta.readText()) }.getOrNull() ?: return null
        if (!json.has("version")) return null
        val list = json.optJSONArray("devices") ?: return null
        val devices = (0 until list.length()).map { list.optInt(it, -1) }
        val here = nappId(native)
        if (devices.isEmpty() || here in devices) return null
        val id = napp.getValue(here).standsInFor.firstOrNull { it in devices } ?: return null
        val d = napp.getValue(id)
        return Device(pdlId.getValue(id), d.width, d.height)
    }
}
