package org.webosarchive.lunacy.card

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.webosarchive.lunacy.core.FolderAssets
import org.webosarchive.lunacy.core.Scheduler
import org.webosarchive.lunacy.core.TestPlatform
import java.io.File
import java.nio.file.Files

/**
 * The apps' lifecycle (AppLifecycle), which Android's shell and Luna DE both run: the rules
 * LunaSysMgr kept and Lunacy measured on the reference TouchPad, each against windows and a
 * screen that only record what they were asked. A rule that changes here changes on both.
 */
class AppLifecycleTest {
    /** The shell's thread with a clock the test moves. */
    private class Clock : Scheduler {
        var now = 0L
        private val due = ArrayList<Pair<Long, Runnable>>()
        override fun isMainThread() = true
        override fun post(r: Runnable) { due += now to r }
        override fun postDelayed(r: Runnable, delayMs: Long) { due += (now + delayMs) to r }
        override fun cancel(r: Runnable) { due.removeAll { it.second === r } }
        fun advance(ms: Long) {
            // Each thing at its own time, so that what it posts in turn is timed from there.
            val until = now + ms
            while (true) {
                val next = due.filter { it.first <= until }.minByOrNull { it.first } ?: break
                due.remove(next); now = maxOf(now, next.first); next.second.run()
            }
            now = until
        }
    }

    private class Window(override val appId: String, override val type: WindowType? = WindowType.CARD, val native: Boolean = false) : LifecycleWindow {
        override val pid = nextPid++
        override var fixedOrientation: String? = null
        var loaded: String? = null
        var closed = false
        var destroyed = false
        val relaunches = ArrayList<String>()
        val mojo = ArrayList<String>()
        /** What the page answers a relaunch with, and does first. */
        var onRelaunch: () -> Boolean = { false }
        override fun load(url: String) { loaded = url }
        override fun relaunch(params: String, done: (Boolean) -> Unit) { relaunches += params; done(onRelaunch()) }
        override fun callMojo(fn: String, argsJson: String) { mojo += "$fn($argsJson)" }
        override fun close() { closed = true }
        override fun destroy() { destroyed = true }
        companion object { var nextPid = 1000 }
    }

    private inner class Screen : AppLifecycle.Screen<Window> {
        val cards = ArrayList<Window>()
        val placeholders = HashSet<Window>()
        val hidden = ArrayList<Window>()
        val dashboards = ArrayList<Window>()
        val alerts = ArrayList<Window>()
        val dock = ArrayList<Window>()
        val forward = ArrayList<String>()
        val direct = ArrayList<String>()
        val launchers = HashMap<String, String?>()
        override fun launchedBy(appId: String, launchedBy: String?) { launchers[appId] = launchedBy }
        override fun openDirect(appId: String) { direct += appId }
        override fun bringForward(appId: String) { forward += appId }
        override fun showAsCard(w: Window, parent: Window?, placeholder: Boolean, splash: Boolean) { cards += w; if (placeholder) placeholders += w }
        override fun cardOpened(parent: Window, child: Window) {
            // A headless app's first card fills the card its launch put up, and the root goes on headless.
            if (placeholders.remove(parent)) { cards[cards.indexOf(parent)] = child; hidden += parent } else cards += child
        }
        override fun hide(w: Window) { hidden += w }
        override fun isHidden(w: Window) = w in hidden
        override fun showDashboard(w: Window) { dashboards += w }
        override fun showAlert(w: Window) { alerts += w }
        override fun showInDock(w: Window) { dock += w }
        override fun windowClosed(w: Window) { cards -= w; dashboards -= w; alerts -= w; dock -= w }
        override fun detach(w: Window) { cards -= w; hidden -= w }
        override val empty get() = cards.isEmpty()
        override val slideMs = 300L
    }

    private inner class Host : AppLifecycle.Host<Window> {
        override val registry get() = this@AppLifecycleTest.registry
        override var profile = DeviceProfile.TOUCHPAD
        val launchPoints = HashMap<String, LaunchPoint>()
        val outside = HashSet<String>()
        val banners = ArrayList<String>()
        val made = ArrayList<Pair<Window, Boolean>>()
        var nativeRuns = true
        var nativeStarts = true
        var home = false
        var cardViews = 0
        val gone = ArrayList<Window>()
        var low = false
        override fun addedLaunchPoint(id: String) = launchPoints[id]
        override fun openedOutside(id: String) = id in outside
        override fun openedInstead(app: AppInfo) = app.androidSettings.isNotEmpty()
        override fun newWindow(app: AppInfo, emulated: Boolean) = Window(app.id).also { made += it to emulated }
        override fun newNativeWindow(app: AppInfo) = if (nativeRuns) Window(app.id, native = true) else { banners += "can't run ${app.id}"; null }
        override fun startNative(window: Window) = nativeStarts
        override fun systemBanner(appId: String, message: String) { banners += message }
        override fun shellIsHome() = home
        override fun onCardView() { cardViews++ }
        override fun windowGone(window: Window) { gone += window }
        override fun memoryLow() = low
    }

    private lateinit var registry: AppRegistry
    private lateinit var bus: Bus
    private val clock = Clock()
    private val screen = Screen()
    private val host = Host()
    private lateinit var lifecycle: AppLifecycle<Window>
    /** The app activities made and ended (WebAppBase::createActivity), by app. */
    private val activities = ArrayList<String>()

    private fun app(dir: File, id: String, extra: String = "") {
        File(dir, id).mkdirs()
        File(dir, "$id/appinfo.json").writeText("""{"id":"$id","title":"$id","main":"index.html","uiRevision":2$extra}""")
    }

    @Before fun setUp() {
        TestPlatform.install()
        val tmp = Files.createTempDirectory("lifecycle").toFile()
        val bundled = File(tmp, "bundled").apply { mkdirs() }
        app(bundled, "com.example.card")
        app(bundled, "com.example.old", ""","uiRevision":1""")
        app(bundled, "com.example.headless", ""","noWindow":true""")
        app(bundled, "com.example.native", ""","type":"pdk"""")
        app(bundled, "com.example.tall", ""","requestedWindowOrientation":"UP"""")
        app(bundled, "com.palm.app.email", ""","noWindow":true""")
        app(bundled, "com.palm.app.calendar", ""","noWindow":true""")
        registry = AppRegistry(AppFiles(FolderAssets(listOf("apps/" to bundled)), File(tmp, "installed").apply { mkdirs() }))
        bus = Bus()
        var nextActivity = 1
        bus.register("com.palm.activitymanager", "create", Bus.CallHandler { c ->
            val id = nextActivity++
            activities += "create ${c.params.getJSONObject("activity").getString("name")}"
            c.onCancel { activities += "end ${c.appId}" }
            c.reply(JSONObject().put("returnValue", true).put("activityId", id).toString())
        })
        for (m in listOf("focus", "unfocus")) bus.register("com.palm.activitymanager", m, Bus.CallHandler { c ->
            activities += "$m ${c.params.getInt("activityId")}"; c.reply("""{"returnValue":true}""")
        })
        lifecycle = AppLifecycle(bus, screen, host, clock)
    }

    private fun settle() { TestPlatform.scheduler.idle() }
    private fun windowsOf(id: String) = lifecycle.apps[id].orEmpty()

    @Test fun aLaunchPutsTheCardUpAndLoadsThePageBehindIt() {
        lifecycle.launch("com.example.card", JSONObject().put("a", "b c"))
        val w = windowsOf("com.example.card").single()
        assertEquals(listOf(w), screen.cards)
        assertNull("the page waits for its card's slide", w.loaded)
        clock.advance(screen.slideMs + 50)
        assertTrue(w.loaded!!.endsWith("/index.html?launchParams=%7B%22a%22%3A%22b%20c%22%7D"))
        assertEquals(false, host.made.single().second)
    }

    @Test fun aCardThatComesUpFirstLoadsAtOnce() {
        lifecycle.launch("com.example.card")
        val w = windowsOf("com.example.card").single()
        lifecycle.shown(w)
        assertTrue(w.loaded != null)
        w.loaded = null
        clock.advance(1000)
        assertNull("loaded once", w.loaded)
    }

    @Test fun anOldAppGetsAnEmulatedCardOnATouchPadOnly() {
        lifecycle.launch("com.example.old")
        assertEquals(true, host.made.single().second)
        lifecycle.windowClosed(windowsOf("com.example.old").single())
        host.profile = DeviceProfile.PRE3
        lifecycle.launch("com.example.old")
        assertEquals(false, host.made.last().second)
    }

    @Test fun aRunningAppIsRelaunchedAndItsFirstCardComesForward() {
        lifecycle.launch("com.example.card")
        val w = windowsOf("com.example.card").single()
        lifecycle.launch("com.example.card", JSONObject().put("again", true))
        assertEquals(listOf("""{"again":true}"""), w.relaunches)
        assertEquals(listOf("com.example.card"), screen.forward)
        assertEquals("no second window", 1, host.made.size)
    }

    @Test fun aRelaunchTheAppHandledBringsNothingForward() {
        lifecycle.launch("com.example.card")
        windowsOf("com.example.card").single().onRelaunch = { true }
        lifecycle.launch("com.example.card")
        assertTrue(screen.forward.isEmpty())
    }

    @Test fun aCardOpenedForTheRelaunchIsItsAnswer() {
        lifecycle.launch("com.example.card")
        val root = windowsOf("com.example.card").single()
        // Email's handler opens a compose card for a mailto: and returns nothing.
        root.onRelaunch = { lifecycle.windowOpened(root, Window("com.example.card")); false }
        lifecycle.launch("com.example.card")
        assertTrue(screen.forward.isEmpty())
        // A dashboard isn't a card: the first card still comes forward.
        root.onRelaunch = { lifecycle.windowOpened(root, Window("com.example.card", WindowType.DASHBOARD)); false }
        lifecycle.launch("com.example.card")
        assertEquals(listOf("com.example.card"), screen.forward)
    }

    @Test fun aRelaunchForTheDockLeavesTheCardViewAlone() {
        lifecycle.launch("com.example.card")
        lifecycle.launch("com.example.card", null, Placement.DOCK)
        assertTrue(screen.forward.isEmpty())
    }

    @Test fun eachWindowGoesWhereItsTypeSays() {
        lifecycle.launch("com.example.card")
        val root = windowsOf("com.example.card").single()
        val dash = Window("com.example.card", WindowType.DASHBOARD)
        val alert = Window("com.example.card", WindowType.POPUP_ALERT)
        val dock = Window("com.example.card", WindowType.DOCK_MODE)
        val card = Window("com.example.card", WindowType.CARD)
        val unknown = Window("com.example.card", null)
        listOf(dash, alert, dock, card, unknown).forEach { lifecycle.windowOpened(root, it) }
        assertEquals(listOf(dash), screen.dashboards)
        assertEquals(listOf(alert), screen.alerts)
        assertEquals(listOf(dock), screen.dock)
        assertEquals(listOf(root, card, unknown), screen.cards)
        assertEquals(6, windowsOf("com.example.card").size)
    }

    @Test fun anAppEndsWithItsLastWindow() {
        val ended = ArrayList<String>()
        lifecycle.onAppEnded = { ended += it }
        lifecycle.launch("com.example.card")
        settle()
        val root = windowsOf("com.example.card").single()
        assertEquals(1, lifecycle.activityId(root))
        val second = Window("com.example.card")
        lifecycle.windowOpened(root, second)
        lifecycle.windowClosed(root)
        assertTrue(root.closed)
        assertTrue(lifecycle.isRunning("com.example.card"))
        assertEquals(0, host.cardViews)
        lifecycle.windowClosed(second)
        assertFalse(lifecycle.isRunning("com.example.card"))
        assertEquals(listOf("com.example.card"), ended)
        assertEquals(listOf("create com.example.card", "end com.example.card"), activities)
        assertEquals(-1, lifecycle.activityId(root))
        assertEquals("the card view, with no card left", 1, host.cardViews)
        assertEquals(listOf(root, second), host.gone)
    }

    @Test fun theAppsActivityFollowsItsCardsFocus() {
        lifecycle.launch("com.example.card")
        settle()
        val root = windowsOf("com.example.card").single()
        lifecycle.stageFocused(root, true)
        lifecycle.stageFocused(root, false)
        settle()
        assertEquals(listOf("create com.example.card", "focus 1", "unfocus 1"), activities)
    }

    @Test fun aHeadlessAppsCardIsAPlaceholderItsFirstCardFills() {
        lifecycle.launch("com.example.headless")
        val root = windowsOf("com.example.headless").single()
        assertTrue(root in screen.placeholders)
        val card = Window("com.example.headless")
        lifecycle.windowOpened(root, card)
        assertEquals(listOf(card), screen.cards)
        assertTrue(screen.isHidden(root))
    }

    @Test fun aHeadlessAppOutlivesItsLastCardByAMoment() {
        lifecycle.launch("com.example.headless")
        val root = windowsOf("com.example.headless").single()
        val card = Window("com.example.headless")
        lifecycle.windowOpened(root, card)
        lifecycle.windowClosed(card)
        // The root is still there to hear the window go (a dashboard's onUserClose).
        assertTrue(lifecycle.isRunning("com.example.headless"))
        assertFalse(root.closed)
        clock.advance(AppLifecycle.HEADLESS_LINGER_MS)
        assertFalse(lifecycle.isRunning("com.example.headless"))
        assertTrue(root.closed)
    }

    @Test fun aWindowOpenedMeanwhileKeepsAHeadlessApp() {
        lifecycle.launch("com.example.headless")
        val root = windowsOf("com.example.headless").single()
        val card = Window("com.example.headless")
        lifecycle.windowOpened(root, card)
        lifecycle.windowClosed(card)
        lifecycle.windowOpened(root, Window("com.example.headless"))
        clock.advance(AppLifecycle.HEADLESS_LINGER_MS)
        assertTrue(lifecycle.isRunning("com.example.headless"))
    }

    @Test fun aKeepAliveAppKeepsItsHeadlessPage() {
        // Measured on the reference TouchPad: closing Email's last card leaves its headless page running.
        lifecycle.launch("com.palm.app.email")
        val root = windowsOf("com.palm.app.email").single()
        val card = Window("com.palm.app.email")
        lifecycle.windowOpened(root, card)
        lifecycle.windowClosed(card)
        clock.advance(60_000)
        assertTrue(lifecycle.isRunning("com.palm.app.email"))
        // Closing the headless page itself ends the app.
        lifecycle.windowClosed(root)
        assertFalse(lifecycle.isRunning("com.palm.app.email"))
    }

    @Test fun aLaunchWithNoCardHidesAHeadlessAppAndLoadsItAtOnce() {
        lifecycle.launch("com.example.headless", null, Placement.NO_CARD)
        val root = windowsOf("com.example.headless").single()
        assertTrue(screen.isHidden(root))
        assertTrue(screen.cards.isEmpty())
        assertTrue(root.loaded != null)
    }

    @Test fun aLaunchForTheDockGoesToTheDock() {
        lifecycle.launch("com.example.card", null, Placement.DOCK)
        assertEquals(windowsOf("com.example.card"), screen.dock)
        assertTrue(screen.cards.isEmpty())
        lifecycle.launch("com.example.native", null, Placement.DOCK)
        assertFalse("a native app has no Exhibition view", lifecycle.isRunning("com.example.native"))
    }

    @Test fun anAddedLaunchPointLaunchesItsAppWithItsParams() {
        host.launchPoints["lp1"] = LaunchPoint("com.example.card", "1", "t", "", "", """{"url":"x"}""", true)
        lifecycle.launch("lp1")
        clock.advance(1000)
        assertTrue(windowsOf("com.example.card").single().loaded!!.contains("launchParams=%7B%22url%22%3A%22x%22%7D"))
    }

    @Test fun aTileOfThePlatformsOwnIsNotAnApp() {
        host.outside += "android:com.example"
        lifecycle.launch("android:com.example")
        lifecycle.launch("no.such.app")
        assertTrue(lifecycle.apps.isEmpty())
    }

    @Test fun aNativeAppRunsInACardOrSaysWhyNot() {
        lifecycle.launch("com.example.native")
        val w = windowsOf("com.example.native").single()
        assertTrue(w.native)
        assertEquals(listOf(w), screen.cards)
        lifecycle.launch("com.example.native")
        assertEquals("running: brought forward, not relaunched", listOf("com.example.native"), screen.forward)
        assertTrue(w.relaunches.isEmpty())
        lifecycle.windowClosed(w)
        host.nativeStarts = false
        lifecycle.launch("com.example.native")
        assertFalse(lifecycle.isRunning("com.example.native"))
        assertEquals(listOf("Couldn't start com.example.native"), host.banners)
        host.nativeRuns = false
        lifecycle.launch("com.example.native")
        assertFalse(lifecycle.isRunning("com.example.native"))
    }

    @Test fun everyWindowOfAnAppStartsAtItsRequestedOrientation() {
        lifecycle.launch("com.example.tall")
        val root = windowsOf("com.example.tall").single()
        assertEquals("up", root.fixedOrientation)
        val child = Window("com.example.tall")
        lifecycle.windowOpened(root, child)
        assertEquals("up", child.fixedOrientation)
    }

    @Test fun openingFromOutsideComesUpAtOnceUnlessTheShellIsHome() {
        lifecycle.openDirect("com.example.card")
        assertEquals(listOf("com.example.card"), screen.direct)
        host.home = true
        lifecycle.openDirect("com.example.tall")
        assertEquals(listOf("com.example.card"), screen.direct)
        assertTrue(lifecycle.isRunning("com.example.tall"))
    }

    @Test fun theBootTimeAppsStartHeadlessAndOutsideLaunchesWaitForThem() {
        var settled = false
        lifecycle.bootSettled = { settled }
        val order = ArrayList<String>()
        lifecycle.launchFromOutside(booting = true) { order += "outside"; lifecycle.launch("com.palm.app.calendar") }
        assertTrue(order.isEmpty())
        lifecycle.launchBootTimeApps()
        // Phone and Messaging aren't installed here; Email and Calendar start with no card.
        assertEquals(setOf("com.palm.app.email", "com.palm.app.calendar"), lifecycle.apps.keys)
        assertTrue(screen.cards.isEmpty())
        assertTrue(windowsOf("com.palm.app.email").single().loaded!!.contains("launchedAtBoot"))
        clock.advance(1000)
        assertTrue("still waiting for the boot's activities", order.isEmpty())
        settled = true
        clock.advance(AppLifecycle.BOOT_SETTLE_STEP_MS)
        assertEquals(listOf("outside"), order)
        // It reached Calendar as a relaunch of the headless app, as on a device.
        assertEquals(1, windowsOf("com.palm.app.calendar").single().relaunches.size)
        // After the boot, a launch from outside goes straight through.
        lifecycle.launchFromOutside(booting = true) { order += "later" }
        assertEquals(listOf("outside", "later"), order)
    }

    @Test fun anOutsideLaunchNeverWaitsLongerThanTheBootIsGiven() {
        lifecycle.bootSettled = { false }
        var ran = false
        lifecycle.launchFromOutside(booting = true) { ran = true }
        lifecycle.launchBootTimeApps()
        clock.advance(AppLifecycle.BOOT_SETTLE_MAX_MS - AppLifecycle.BOOT_SETTLE_STEP_MS)
        assertFalse(ran)
        clock.advance(AppLifecycle.BOOT_SETTLE_STEP_MS)
        assertTrue(ran)
    }

    @Test fun theRootPagesHearTheMemoryStateWhenItChanges() {
        lifecycle.launch("com.example.card")
        val root = windowsOf("com.example.card").single()
        val child = Window("com.example.card")
        lifecycle.windowOpened(root, child)
        lifecycle.memoryPressure("low")
        lifecycle.memoryPressure("low")
        lifecycle.memoryPressure("critical")
        assertEquals(listOf("""lowMemoryNotification({"state":"low"})""", """lowMemoryNotification({"state":"critical"})"""), root.mojo)
        assertTrue("a child window is the same app", child.mojo.isEmpty())
        // The platform never says memory has eased: the shell looks, and waits while it is still low.
        host.low = true
        clock.advance(AppLifecycle.MEMORY_CHECK_MS)
        assertEquals(2, root.mojo.size)
        host.low = false
        clock.advance(AppLifecycle.MEMORY_CHECK_MS)
        assertEquals(2, root.mojo.size)
    }

    @Test fun destroyingTheShellDestroysEveryPage() {
        lifecycle.launch("com.example.card")
        val root = windowsOf("com.example.card").single()
        lifecycle.destroyAll()
        assertTrue(root.destroyed)
        assertTrue(lifecycle.apps.isEmpty())
        clock.advance(1000)
        assertNull("its load went with it", root.loaded)
    }

    @Test fun paramsAreEncodedAsAndroidsUriEncodeDoes() {
        assertEquals("%7B%22q%22%3A%22a%20b%2Bc%26d%3D%C3%A9%22%7D", AppLifecycle.encodeParam("""{"q":"a b+c&d=é"}"""))
        assertEquals("AZaz09_-!.~'()*", AppLifecycle.encodeParam("AZaz09_-!.~'()*"))
    }
}
