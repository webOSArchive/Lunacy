package org.webosarchive.lunacy.card

import android.content.Context
import org.json.JSONObject
import org.webosarchive.lunacy.shell.NowPlaying

/**
 * palm://com.palm.service.mediabroadcast: where a webOS player told the system what it was
 * playing, for Bluetooth AVRCP. Music registers as a broadcaster when it starts
 * (`registerBroadcaster`, subscribed) and sends `update` as the song, its pause state, shuffle
 * and repeat change ({event: "SONG_CHANGED", song: {title, artist, album, genre, currentTime,
 * duration}}, {event: "PAUSE_MODE_CHANGED", song: {pause: "ON" | "OFF"}}, ...). Lunacy hands
 * the song and its state to Android's media session ([NowPlaying]), which is what Android's
 * Bluetooth, lock screen and notification read.
 *
 * Measured on the reference TouchPad: a subscribed `registerBroadcaster` answers
 * {"registration":"complete","returnValue":true}; an `update` from a caller that hasn't
 * registered answers "broadcasterID required" (a bare string). What it answers a registered
 * Music is not yet measured; Lunacy answers {"returnValue":true}. The broadcaster ends when its
 * subscription does (the app closed).
 */
class MediaBroadcast(private val context: Context) {
    private val broadcasters = HashMap<String, Bus.Call>()

    fun register(bus: Bus) {
        bus.register(SERVICE, "registerBroadcaster", Bus.CallHandler { call ->
            call.reply(JSONObject().put("registration", "complete").put("returnValue", true).toString())
            if (!call.subscribe || call.cancelled) return@CallHandler
            broadcasters[call.appId] = call
            call.onCancel {
                if (broadcasters[call.appId] === call) {
                    broadcasters.remove(call.appId)
                    NowPlaying.end(context)
                }
            }
        })
        bus.register(SERVICE, "update", Bus.CallHandler { call ->
            if (call.appId !in broadcasters) return@CallHandler call.reply("\"broadcasterID required\"")
            update(call.params)
            call.reply(Bus.ok())
        })
    }

    private fun update(p: JSONObject) {
        val song = p.optJSONObject("song")
        when (p.optString("event")) {
            "SONG_CHANGED" -> {
                song ?: return
                NowPlaying.title = song.optString("title"); NowPlaying.artist = song.optString("artist")
                NowPlaying.album = song.optString("album")
                NowPlaying.durationMs = (song.optString("duration").toDoubleOrNull() ?: 0.0).toLong() * 1000
                NowPlaying.active = true
            }
            "PAUSE_MODE_CHANGED" -> {
                NowPlaying.playing = song?.optString("pause") == "OFF"
                if (NowPlaying.title.isNotEmpty()) NowPlaying.active = true
            }
            else -> return
        }
        NowPlaying.update(context)
    }

    companion object {
        const val SERVICE = "com.palm.service.mediabroadcast"
    }
}
