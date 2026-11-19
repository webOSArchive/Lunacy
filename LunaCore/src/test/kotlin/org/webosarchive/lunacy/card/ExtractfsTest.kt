package org.webosarchive.lunacy.card

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.webosarchive.lunacy.core.Images
import org.webosarchive.lunacy.core.Platform
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * webOS's thumbnailer's sizes (Extractfs), which are the reference TouchPad's, measured by
 * reading its synthetic files (the tables in Extractfs.kt): what each path asks the platform's
 * image library to make. The pixels are the platform's; the sizes are decided here, for Android
 * and Luna DE alike, so they are checked here with a library that only records what it is asked.
 */
class ExtractfsTest {
    private var before: Images? = null
    /** The picture's size, and what was last asked of it: "WxH fill jpeg". */
    private var source = 0 to 0
    private var asked = ""
    private var opened: String? = null

    @Before fun setUp() {
        before = Platform.images
        Platform.images = object : Images {
            override fun size(input: InputStream) = intArrayOf(source.first, source.second)
            override fun jpegThumbnail(file: File, px: Int): ByteArray? = null
            override fun coverThumbnail(file: File, px: Int): ByteArray? = null
            override fun scaled(open: () -> InputStream?, w: Int, h: Int, fill: Boolean, jpeg: Boolean): ByteArray? {
                asked = "${w}x$h" + (if (fill) " fill" else "") + (if (jpeg) " jpeg" else "")
                return byteArrayOf(1)
            }
        }
    }
    @After fun tearDown() { Platform.images = before }

    private fun read(spec: String, w: Int, h: Int): Extractfs.Result? {
        source = w to h; asked = ""; opened = null
        return Extractfs.read(spec, Extractfs.Opener { path -> opened = path; { ByteArrayInputStream(ByteArray(8)) } })
    }

    @Test fun theShortFormFitsTheBoxAndNeverGrows() {
        // Measured 2026-09-22: 480x480 in 56x56 is 56x56; 700x875 is 45x56, and 40x50 in 100x50.
        assertEquals("image/png", read("/media/internal/a.jpg:0:0:56:56:3", 480, 480)?.mime); assertEquals("56x56", asked)
        read("/media/internal/a.jpg:0:0:56:56:3", 700, 875); assertEquals("45x56", asked)
        read("/media/internal/a.jpg:0:0:100:50:3", 700, 875); assertEquals("40x50", asked)
        read("/media/internal/a.jpg:0:0:1024:1024:3", 300, 200); assertEquals("300x200", asked)
        assertEquals("/media/internal/a.jpg", opened)
    }

    @Test fun aPathWithColonsIsStillThePath() {
        read("/media/internal/photos/10:23 AM.png:0:0:64:64:3", 128, 128)
        assertEquals("/media/internal/photos/10:23 AM.png", opened); assertEquals("64x64", asked)
    }

    @Test fun theLongFormFitsAndGrowsOrFillsAndCrops() {
        // Measured 2026-10-08, JPEG (format 1): crop 3 fits and grows, crop 4 fills the box.
        val long = { box: String, crop: Int, format: Int -> "/media/internal/p.jpg::3:0:0:0:0:$format:0:0:$box:$crop" }
        assertEquals("image/jpeg", read(long("200:200", 3, 1), 1024, 1024)?.mime); assertEquals("200x200 jpeg", asked)
        read(long("200:200", 3, 1), 1024, 768); assertEquals("200x150 jpeg", asked)
        read(long("2000:2000", 3, 1), 1024, 768); assertEquals("2000x1500 jpeg", asked)
        read(long("500:200", 2, 1), 1024, 768); assertEquals("267x200 jpeg", asked)
        read(long("500:200", 4, 1), 1024, 1024); assertEquals("500x200 fill jpeg", asked)
        read(long("80:200", 4, 1), 800, 2400); assertEquals("80x200 fill jpeg", asked)
        assertEquals("image/png", read(long("200:200", 3, 2), 1024, 768)?.mime); assertEquals("200x150", asked)
    }

    @Test fun theLongFormsJsonSaysTheSizesWithoutMakingThePicture() {
        val r = read("/media/internal/p.jpg::3:0:0:0:0:3:0:0:200:200:3", 1024, 768)!!
        val j = JSONObject(String(r.bytes))
        assertEquals("application/json", r.mime)
        assertEquals(listOf(1024, 768, 200, 150), listOf("original-width", "original-height", "output-width", "output-height").map { j.getInt(it) })
        assertEquals("", asked)
    }

    @Test fun aBadBoxOrAMissingFileIsAFailedRead() {
        assertNull(read("/media/internal/a.jpg:0:0:0:56:3", 100, 100))
        assertNull(read("/media/internal/a.jpg:0:0:5000:56:3", 100, 100))
        source = 10 to 10
        assertNull(Extractfs.read("/media/internal/none.jpg:0:0:56:56:3", Extractfs.Opener { null }))
    }
}
