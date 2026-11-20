package org.webosarchive.lunacy.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Another system's share or mailto: as Email's launch parameters (ShareToEmail). */
class ShareToEmailTest {
    @Test fun aSharedPageIsASubjectAndPlainText() {
        val p = ShareToEmail.compose("A page's title", "https://example.com/a?b=1&c=2")!!
        assertEquals("A page's title", p.getString("summary"))
        assertEquals("https://example.com/a?b=1&c=2", p.getString("text"))
        assertFalse(p.getBoolean("isHtml"))
        assertFalse(p.has("recipients"))
    }

    @Test fun recipientsCarryEmailsRoles() {
        val r = ShareToEmail.compose(null, null, to = listOf("a@example.com", " "), cc = listOf("b@example.com"), bcc = listOf("c@example.com"))!!
            .getJSONArray("recipients")
        assertEquals(3, r.length())
        assertEquals("a@example.com", r.getJSONObject(0).getString("value"))
        assertEquals(listOf(1, 2, 3), (0 until 3).map { r.getJSONObject(it).getInt("role") })
    }

    @Test fun aSharedFileIsAnAttachmentByPath() {
        val p = ShareToEmail.compose(null, null, attachments = listOf(ShareToEmail.Attachment("/media/internal/.lunacy-shared/a/photo.jpg", "photo.jpg", "image/jpeg")))!!
        val a = p.getJSONArray("attachments").getJSONObject(0)
        assertEquals("/media/internal/.lunacy-shared/a/photo.jpg", a.getString("fullPath"))
        assertEquals("photo.jpg", a.getString("displayName"))
        assertEquals("image/jpeg", a.getString("mimeType"))
        assertFalse(p.has("text"))
    }

    @Test fun aSharedFilesNameCannotBeAPath() {
        assertEquals("passwd", ShareToEmail.safeName("../../etc/passwd"))
        assertEquals("hidden", ShareToEmail.safeName(".hidden"))
        assertEquals("attachment", ShareToEmail.safeName(".."))
        assertEquals("attachment", ShareToEmail.safeName(null))
        assertEquals("Holiday photo.jpg", ShareToEmail.safeName("Holiday photo.jpg"))
    }

    @Test fun oldCopiesGoWhenANewShareComes() {
        val media = java.nio.file.Files.createTempDirectory("shared").toFile()
        val first = ShareToEmail.newSharedFolder(media, now = 1_000L)
        java.io.File(first, "a.txt").writeText("a")
        first.setLastModified(1_000L)
        val second = ShareToEmail.newSharedFolder(media, now = 2_000L)
        assertEquals(true, first.exists() && second.isDirectory && first != second)
        ShareToEmail.newSharedFolder(media, now = 1_000L + ShareToEmail.KEEP_MS + 1)
        assertFalse(first.exists())
    }

    @Test fun nothingSharedIsNoLaunch() {
        assertNull(ShareToEmail.compose(null, ""))
        assertNull(ShareToEmail.mailto("https://example.com/"))
    }

    @Test fun aMailtoLinkGoesAsItIs() {
        assertEquals("mailto:a@example.com?subject=Hi", ShareToEmail.mailto("mailto:a@example.com?subject=Hi")!!.getString("target"))
    }

    @Test fun extrasFillWhatTheLinkLeavesOut() {
        assertEquals("mailto:a@example.com?subject=Hello%20there&body=One%26two",
            ShareToEmail.mailto("mailto:a@example.com", "Hello there", "One&two")!!.getString("target"))
        assertEquals("mailto:a@example.com?Subject=Hi&body=Text",
            ShareToEmail.mailto("mailto:a@example.com?Subject=Hi", "Other", "Text")!!.getString("target"))
    }
}
