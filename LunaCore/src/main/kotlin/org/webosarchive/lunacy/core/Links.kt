package org.webosarchive.lunacy.core

/**
 * Symbolic links, which the webOS root is full of (a framework's version/1.0 -> ../submission/48,
 * /media/cryptofs/apps). Android answers with android.system.Os, java.nio.file being Android 8's;
 * the desktop with java.nio.file.
 */
interface Links {
    /** What the link at [path] points to, or null if [path] isn't a link (or isn't there). */
    fun readlink(path: String): String?
    fun symlink(target: String, path: String)
}
