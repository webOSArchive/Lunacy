package org.webosarchive.lunacy.core

import java.io.File

/** Paths in the webOS root that every part of Lunacy names. */
object WebosPaths {
    /** Where packages unpack their apps, under /media/cryptofs/apps (Packages.APPS on Android). */
    const val APPS = "usr/palm/applications"

    /**
     * Whether [f] is [root] or inside it, by their canonical paths: a link or a ".." can't take
     * it out, and a sibling whose name starts the same ("apps2" beside "apps") isn't inside.
     * The one check every webOS path from a page or a service goes through (UserFiles.inside).
     */
    fun inside(f: File, root: File): Boolean = try {
        val c = f.canonicalPath
        val r = root.canonicalPath
        c == r || c.startsWith(r + File.separator)
    } catch (e: Exception) { false }
}
