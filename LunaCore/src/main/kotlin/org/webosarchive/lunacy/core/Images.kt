package org.webosarchive.lunacy.core

import java.io.File
import java.io.InputStream

/**
 * Decoding and scaling images, which each platform does with its own library: Android's
 * BitmapFactory (AndroidImages), the desktop's javax.imageio. Set on [Platform.images].
 */
interface Images {
    /** The image's width and height, or null if it isn't one this platform decodes. */
    fun size(input: InputStream): IntArray?

    /** A JPEG of [file] whose short side is about [px], or null if it can't be read. */
    fun jpegThumbnail(file: File, px: Int): ByteArray?
}
