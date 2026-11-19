package org.webosarchive.lunacy.core

import java.io.File

/**
 * The host's own folders that stand for a webOS device's under /media/internal (downloads, music,
 * photos ...): on Android its shared storage; none on the desktop, where /media/internal is
 * Luna DE's own folder. UserFiles resolves paths through it.
 */
interface HostFolders {
    /** The webOS folder names that are mapped, in lower case. */
    val names: List<String>
    /** The host's folder for [name], whether or not it exists. */
    fun dir(name: String): File?
}
