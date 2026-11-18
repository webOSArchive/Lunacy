package org.webosarchive.lunacy.core

import java.io.File

/**
 * What a media file says about itself, which each platform reads with its own library: Android's
 * ExifInterface and MediaMetadataRetriever (AndroidMediaTags), the desktop's own small readers.
 * Set on [Platform.mediaTags]. The media index (MediaIndex) makes filenotifyd's records from it.
 */
interface MediaTags {
    /**
     * A picture's EXIF: its date as written ("2026:10:01 12:00:00": DateTime, else
     * DateTimeOriginal), the sub-seconds that go with it (SubSecTime, else SubSecTimeOriginal),
     * and where its embedded thumbnail is in the file (0 and 0 for none).
     */
    class Exif(val dateTime: String?, val subSec: String?, val thumbnailOffset: Long, val thumbnailLength: Long)

    /** A movie: its length, its own title if it has one, and its creation time as "yyyyMMdd'T'HHmmss.SSS'Z'" (UTC). */
    class Video(val durationMs: Long, val title: String?, val date: String?)

    /** A sound file's tags, each as written or null: track and disc may be "3/12". */
    class Audio(val title: String?, val artist: String?, val album: String?, val albumArtist: String?,
                val genre: String?, val track: String?, val disc: String?)

    /** Null for a file with no EXIF, or one that can't be read. */
    fun exif(f: File): Exif?
    /** Null for a file that isn't a movie with a video track, or can't be read. */
    fun video(f: File): Video?
    /** Null for a file whose tags can't be read. */
    fun audio(f: File): Audio?
}
