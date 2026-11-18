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

    /**
     * The image [open] gives, at exactly [w] x [h]: scaled to that shape, or with [fill] scaled
     * until it covers it and cropped evenly from the centre; a JPEG if [jpeg], else a PNG. Null if
     * it can't be read. [open] may be called more than once. webOS's thumbnailer (Extractfs).
     */
    fun scaled(open: () -> InputStream?, w: Int, h: Int, fill: Boolean, jpeg: Boolean): ByteArray? = null

    /** A JPEG of [file] whose short side is about [px], or null if it can't be read. */
    fun jpegThumbnail(file: File, px: Int): ByteArray?

    /**
     * A JPEG of [file] scaled, up or down, until it covers a [px] square (a wallpaper's
     * thumbnail, as webOS's thumbs/ holds), or null if it can't be read.
     */
    fun coverThumbnail(file: File, px: Int): ByteArray?
}
