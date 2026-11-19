package org.webosarchive.lunacy.card

import java.io.File

/**
 * Where package scripts and other commands run: Lunacy's webOS root with its commands, as
 * WebosRoot gives it on Android (busybox's sh, webOS's paths pointed into the root, luna-send
 * calling the bus as the script's package). What [Packages] needs of it.
 *
 * Luna DE doesn't run scripts yet ([runsScripts] false): a script there would run as the developer
 * on their own machine, and how it is kept inside the root isn't settled (Docs/luna-de.md, open
 * question 2). A package installs without its scripts, and the log says so.
 */
interface ScriptHost {
    val root: File
    /** Whether scripts run here at all. */
    val runsScripts: Boolean get() = true
    /** The root laid down and its commands in place. */
    fun prepare()
    /** luna-send's socket listening, for the script's bus calls. */
    fun startBus()
    /** Absolute webOS paths in a script's text, pointed into the root. */
    fun mapPaths(s: String): String
    /** The environment a process in the root gets, calling the bus as [caller]. */
    fun environment(caller: String): Map<String, String>
    /** The shell to use when the root has no /bin/sh. */
    val fallbackShell: String get() = "/bin/sh"

    // ---- JS services (JsServices) ----

    /** The Node that runs JS services: nodejs-mobile's on Android, a Node of the same major version on the desktop. */
    val node: File
    /** Lunacy's own scripts for Node: host.js (the service host), curl.js, luna-send.js. */
    val hostScripts: File
    /** A service's environment: [environment], and whatever else this host gives a service's process. */
    fun serviceEnvironment(name: String): Map<String, String> = environment(name)
}
