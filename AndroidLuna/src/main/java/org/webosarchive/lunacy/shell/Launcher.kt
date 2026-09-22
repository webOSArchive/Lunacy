package org.webosarchive.lunacy.shell

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import org.json.JSONArray
import org.json.JSONObject
import org.webosarchive.lunacy.card.AppInfo
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The launcher ("launcher3"): a tab bar over pages of app icons, sliding up over the cards.
 * Geometry and timings: Docs/luna-shell-reference.md §3. Lengths in TouchPad px.
 *
 * A tapped icon glows while its app launches (LunaCE's launch feedback). Holding an icon
 * enters edit mode, as LunaCE's reorder mode: icons get their frame, apps installed from
 * packages get the delete decorator, icons can be dragged to a new place, and Done (or the
 * home button) leaves it. The order is kept.
 */
@SuppressLint("ViewConstructor")
class Launcher(context: Context, private val luna: Luna, private val onLaunch: (AppInfo) -> Unit) : View(context) {
    object Params {
        const val TAB_BAR = 50f
        const val TAB_MAX_W = 150f
        const val CELL = 128f
        const val ICON = 64f
        const val ICON_DY = -11f          // icon centre above the cell centre
        const val LABEL_W = 100f
        const val LABEL_H = 40f
        const val LABEL_GAP = 2f
        const val MAX_COLUMNS = 7
        const val LEFT_MARGIN = 27f
        const val SPACE_ADJUST = 12       // IconHorizontalSpaceAdjustInPixels
        const val TOP_MARGIN = 20f
        const val ROW_GAP = 10f
        const val TAP_RADIUS = 25f
        const val SNAP_MS = 250L          // snap to page, InQuad
        const val FLICK_MIN_MS = 200L
        const val FLICK_MAX_MS = 1200L
        // LunaCE's IconGeometrySettings, DynamicsSettings and LayoutSettings, with the reference
        // TouchPad's /etc/palm/launcher3 overrides.
        const val FEEDBACK = 90f          // launcher-touch-feedback.png, centred on the icon
        const val FEEDBACK_MS = 3000L     // IconFeedbackTimeout
        const val DELETE_DX = -50f        // delete decorator's centre from the cell centre
        const val DELETE_DY = -50f        // the TouchPad's; LunaCE's default is -47
        const val DELETE_BOX = 32f
        const val MOVE_MS = 300L          // IconReorderIconMoveAnimTime, InQuad
        // Two-state sprites hold their states at rects inside a larger canvas, not as halves
        // of it (reference §3.8): edit-button-done.png is 100 × 80 with the button at
        // (1, 2, 98, 34) and its pressed state at (1, 42); edit-button-delete.png is 40 × 80
        // with the badge at (2, 1, 32, 32) and (2, 41). Checked against the reference
        // TouchPad's edit-mode screenshot: the button's art is 98 × 34 ending 7 px from the
        // screen's right edge and centred in the tab bar, and the badge's centre lands on the
        // cell centre + (-50, -50).
        const val DONE_W = 98f
        const val DONE_H = 34f
        const val DONE_RIGHT = 7f         // the art's right edge, in from the bar's right edge
        const val DONE_TEXT_DY = -1f      // the label's centre sits 1 px above the art's
        // LayoutSettings' page border activation areas and DynamicsSettings' timings: held this
        // close to a side, a dragged icon pans to the next page after pagePanForIconMoveDelayMs;
        // held this close to the top or bottom, the page scrolls pageScrollAmount every
        // pageScrollDelayMs, each step pageScrollAnimTime long.
        const val EDGE = 50f
        const val EDGE_MS = 1500L
        const val VEDGE = 20f
        const val VSCROLL = 150f
        const val VSCROLL_EVERY_MS = 800L
        const val VSCROLL_MS = 300L
        // KineticScroller: a flick's velocity (FlickGestureRecognizer's, a third of px/s) over
        // sFlickScalar is px/ms, slowed by kDefaultFriction px/ms²; at most 100 px of overscroll,
        // corrected over kOverScrollCorrectionTimeOut.
        const val FLICK_SCALAR = 2225f * 3f
        const val FRICTION = 8e-4f
        const val MAX_OVERSCROLL = 100f
        const val OVERSCROLL_FIX_MS = 350L
        const val OVERSCROLL_SNAP_MS = 500L
        // The empty page (ReorderableLayout on the reference TouchPad): the message in a
        // 350 × 200 box 190 px below the page's centre, Prelude 18 px bold #AAAAAA, over
        // launcher-empty-page.png at the centre.
        const val EMPTY_TEXT = "Tap and hold any app to drag it to this page."
        const val EMPTY_BOX_W = 350f
        const val EMPTY_BOX_H = 200f
        const val EMPTY_DY = 190f
        // The installing decorator: loading-strip.png, 19 frames of 32 × 32, at (+50, −50)
        // from the cell centre, over the icon at half opacity.
        const val INSTALL_FRAMES = 19
        const val INSTALL_DX = 50f
        const val INSTALL_DY = -50f
        // AppInfoDialog.qml
        const val DIALOG_EDGE = 11f
        const val DIALOG_MARGIN = 6f
        const val DIALOG_TOP = 4f
        const val DIALOG_W = 320f + 2 * DIALOG_EDGE
        const val DIALOG_BUTTON_H = 52f
        // [LunaCE] tab editing (pagetabbar.cpp): at most six tabs, the first four permanent; a
        // tab held (Qt's tap-and-hold, 700 ms) is renamed; the empty bar held 650 ms without
        // wandering 16 px shows a 28 px "+" 12 px right of the last tab.
        const val MAX_TABS = 6
        const val PERMANENT_TABS = 4
        const val TAB_HOLD_MS = 700L
        const val ADD_HOLD_MS = 650L
        const val ADD_SLOP = 16f
        const val ADD_BUTTON = 28f
        const val ADD_LEFT = 12f
        // [LunaCE] app groups (reorderablepage.cpp): the middle 60 % of an icon, held 300 ms.
        const val GROUP_CORE = 0.6f
        const val GROUP_DWELL_MS = 300L
        const val GROUP_NAME = "Group"
        /** How long a dragged icon's finger must rest before the layout is looked at. */
        const val SAMPLE_STILL_MS = 60L
    }

    /** [LunaCE] A group was tapped: open its panel, growing out of [from] (its icon, in this view). */
    var onOpenGroup: (Tile.Group, PointF) -> Unit = { _, _ -> }

    /** Where a group's icon is drawn, for the panel to grow out of. */
    private fun groupScreenCentre(g: Tile.Group): PointF {
        val page = currentPage()
        val c = cellCentre(page.tiles.indexOf(g))
        return PointF(c.x, c.y - page.scrollY + luna.px(Params.ICON_DY))
    }

    /** The group's panel changed it: a new name, or a member launched or taken out. */
    fun groupChanged(g: Tile.Group) {
        // GroupOverlay::slotMemberIconPoppedOut and the dissolve rule: one member left is an app.
        for (page in pages) {
            val i = page.tiles.indexOf(g)
            if (i < 0) continue
            when (g.members.size) {
                0 -> page.tiles.removeAt(i)
                1 -> page.tiles[i] = Tile.App(g.members[0])
            }
        }
        saveOrder(); invalidate()
    }

    /** A member held in the group's panel comes out onto the page, just after the group. */
    fun popOut(g: Tile.Group, app: AppInfo) {
        if (!g.members.remove(app)) return
        pages.firstOrNull { g in it.tiles }?.let { p -> p.tiles.add(p.tiles.indexOf(g) + 1, Tile.App(app)) }
        groupChanged(g)
    }

    /** A tab was held: rename it (and, for one past the first four, offer to delete it). */
    var onRenameTab: (index: Int, name: String, deletable: Boolean) -> Unit = { _, _, _ -> }
    /** The "+" was tapped: ask for a new tab's name. */
    var onAddTab: () -> Unit = {}

    fun renameTab(index: Int, name: String) {
        pages.getOrNull(index)?.title = name
        saveOrder(); invalidate()
    }

    /** LauncherObject::createUserTab: a new, empty page at the end, with a designator of its own. */
    fun addTab(name: String) {
        if (pages.size >= Params.MAX_TABS) return
        pages += Page("usertab_" + java.util.UUID.randomUUID(), name)
        saveOrder(); animatePage(pages.size - 1, Params.SNAP_MS, Easing.InQuad)
    }

    /** LauncherObject::deleteUserTab: its icons go to the first page, in order. */
    fun deleteTab(index: Int) {
        if (index < Params.PERMANENT_TABS || index >= pages.size) return
        val dying = pages.removeAt(index)
        pages[0].tiles += dying.tiles
        pagePos = pagePos.coerceAtMost(pages.size - 1f)
        saveOrder(); animatePage(pagePos.roundToInt().coerceIn(0, pages.size - 1), Params.SNAP_MS, Easing.InQuad)
    }

    /** A launcher cell: an app or, [LunaCE], a group of apps (LunaCE's GroupIcon). */
    sealed class Tile {
        abstract val id: String
        class App(val app: AppInfo) : Tile() { override val id get() = app.id }
        class Group(override val id: String, var name: String, val members: MutableList<AppInfo>) : Tile()
    }

    class Page(val designator: String, var title: String, val tiles: MutableList<Tile> = mutableListOf()) { var scrollY = 0f }

    /**
     * The pages, their names and the keyword map, from assets/luna/launcher-pages.json (which
     * says where each value comes from). LunaCE's four built-in pages, named as the reference
     * TouchPad names them: APPS, DOWNLOADS, GAMES, SETTINGS.
     */
    private val pageMap = runCatching {
        JSONObject(context.assets.open("luna/launcher-pages.json").bufferedReader().use { it.readText() })
    }.getOrDefault(JSONObject())

    val pages: MutableList<Page> = pageMap.optJSONArray("pages")?.let { a ->
        (0 until a.length()).map { i ->
            val p = a.getJSONObject(i)
            Page(p.optString("designator"), p.optString("name", p.optString("designator")))
        }.toMutableList()
    }?.takeIf { it.isNotEmpty() } ?: mutableListOf(
        Page("apps", "apps"), Page("downloads", "downloads"), Page("favorites", "games"), Page("prefs", "settings"))

    /**
     * Where a bundled app starts, from `layout` in that file: webOS shipped the same thing as
     * /etc/palm/default-launcher-page-layout.json, which is what put Palm's settings apps on
     * the prefs page. Page designator to app ids, in the order they should appear.
     */
    private val defaultLayout: List<Pair<String, List<String>>> =
        pageMap.optJSONArray("layout")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val items = o.optJSONArray("items")
                o.optString("title") to (0 until (items?.length() ?: 0))
                    .map { j -> items!!.optString(j).removeSuffix("_default") }
            }
        }.orEmpty()

    /** keyword (lower case) to page designator. */
    private val keywordPages: Map<String, String> = pageMap.optJSONObject("keywords")?.let { o ->
        o.keys().asSequence().associate { it.lowercase() to o.optString(it) }
    }.orEmpty()

    /**
     * The page an app with no place of its own belongs to, as LunaCE's
     * AppMonitor::pageDesignatorForWebOSApp works it out: its category first, then each of its
     * keywords. Anything unrecognised goes to the first page.
     */
    private fun pageFor(app: AppInfo): Int {
        val designator = keywordPages[app.category.lowercase()]
            ?: app.keywords.firstNotNullOfOrNull { keywordPages[it.lowercase()] }
            // LauncherObject::pageIndexForAppByPredefinedDesignators: an app the user installed
            // goes on the downloads page (installedAppsPageIndex), as every installed app does
            // on the reference TouchPad; the rest on the first.
            ?: if (app.userInstalled) "downloads" else return 0
        return pages.indexOfFirst { it.designator == designator }.takeIf { it >= 0 } ?: 0
    }

    // ---- packages being installed ----

    /** A package on its way in: shown on the downloads page, its icon faded, with its progress. */
    private class Installing(val title: String, var progress: Int)
    private val installs = LinkedHashMap<String, Installing>()
    private fun installPage() = pages.indexOfFirst { it.designator == "downloads" }.takeIf { it >= 0 } ?: 0
    private fun installsOn(page: Page) = if (pages.indexOf(page) == installPage()) installs.values.toList() else emptyList()

    fun startInstall(key: String, title: String) { installs[key] = Installing(title, 0); invalidate() }
    fun installProgress(key: String, percent: Int) { installs[key]?.progress = percent.coerceIn(0, 100); invalidate() }
    fun endInstall(key: String) { installs.remove(key); invalidate() }
    /** Current page position (fractional while dragging). */
    private var pagePos = 0f
    /** Height of the dock that sits over the launcher's bottom. */
    var dockHeight = 0f
    /** The user confirmed removing an app (the dialog's Remove). */
    var onRemove: (AppInfo) -> Unit = {}
    /** An icon dragged in edit mode was dropped on the dock, at x in the dock's coordinates. */
    var onDropOnDock: (AppInfo, Float) -> Unit = { _, _ -> }
    /** Edit mode began or ended here (the dock follows it). */
    var onEditModeChanged: (Boolean) -> Unit = {}

    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val tabText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = luna.px(16f); typeface = luna.fontBold; textAlign = Paint.Align.CENTER }
    private val doneText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = luna.px(15f); typeface = luna.fontBold; color = Color.WHITE; textAlign = Paint.Align.CENTER }
    private val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = luna.px(14f); typeface = luna.fontBold; color = Color.WHITE }
    private val labels = HashMap<String, StaticLayout>()

    /**
     * Each tab's order is the user's: apps they've placed keep their tab and place. The rest
     * fall back to the default layout (the tab and order a fresh install starts with), and
     * anything that names doesn't cover goes by its keywords, alphabetically.
     */
    fun setApps(apps: List<AppInfo>) {
        val byId = apps.associateBy { it.id }
        val placed = HashSet<String>()
        pages.forEach { it.tiles.clear() }
        fun app(id: String) = byId[id]?.takeIf { placed.add(it.id) }?.let { Tile.App(it) }
        /** A saved cell: an app's id, or a group ({"group": name, "uid": ..., "apps": [ids]}). */
        fun tile(entry: Any?): Tile? = when (entry) {
            is String -> app(entry)
            is JSONObject -> {
                val ids = entry.optJSONArray("apps")
                val members = (0 until (ids?.length() ?: 0)).mapNotNull { byId[ids!!.optString(it)]?.takeIf { a -> placed.add(a.id) } }
                // A group left with one member is that app again (it dissolves, as LunaCE's does).
                when (members.size) {
                    0 -> null
                    1 -> Tile.App(members[0])
                    else -> Tile.Group(entry.optString("uid").ifEmpty { "group_" + java.util.UUID.randomUUID() }, entry.optString("group", "Group"), members.toMutableList())
                }
            }
            else -> null
        }
        val tabs = runCatching { JSONArray(prefs.getString("tabs", null) ?: "") }.getOrNull()
        if (tabs != null) {
            // The user's own tabs: the stock ones by designator, keeping their names, and any
            // they added, in their order.
            for (i in 0 until tabs.length()) {
                val t = tabs.optJSONObject(i) ?: continue
                val d = t.optString("designator")
                val page = pages.firstOrNull { it.designator == d } ?: Page(d, t.optString("name")).also { pages += it }
                page.title = t.optString("name", page.title)
                val ids = t.optJSONArray("apps")
                for (j in 0 until (ids?.length() ?: 0)) tile(ids!!.opt(j))?.let { page.tiles += it }
            }
            val order = (0 until tabs.length()).mapNotNull { tabs.optJSONObject(it)?.optString("designator") }
            pages.sortBy { p -> order.indexOf(p.designator).let { if (it < 0) Int.MAX_VALUE else it } }
        } else {
            // Before tabs could change: each page's apps by index.
            val saved = runCatching { JSONArray(prefs.getString("pages", null) ?: "[" + (prefs.getString("order", null) ?: "[]") + "]") }.getOrDefault(JSONArray())
            for (pi in 0 until minOf(saved.length(), pages.size)) {
                val ids = saved.optJSONArray(pi) ?: continue
                for (i in 0 until ids.length()) app(ids.optString(i))?.let { pages[pi].tiles += it }
            }
        }
        // Then the apps Lunacy ships with, where and in the order the default layout puts them.
        for ((designator, ids) in defaultLayout) {
            val page = pages.indexOfFirst { it.designator == designator }.takeIf { it >= 0 } ?: continue
            for (id in ids) app(id)?.let { pages[page].tiles += it }
        }
        // Everything else by its keywords, alphabetically.
        for (app in apps.filter { it.id !in placed }.sortedBy { it.title.lowercase() }) {
            pages[pageFor(app)].tiles += Tile.App(app)
        }
        // A tile being dragged while the list changed is the new one with its id, if any.
        dragging = dragging?.let { d -> pages.flatMap { it.tiles }.firstOrNull { it.id == d.id } }
        invalidate()
    }

    /** Each page's designator, name and apps, in order: the tabs themselves are the user's now. */
    private fun saveOrder() = prefs.edit().putString("tabs", JSONArray(pages.map { p ->
        JSONObject().put("designator", p.designator).put("name", p.title).put("apps", JSONArray(p.tiles.map { t ->
            when (t) {
                is Tile.App -> t.app.id
                is Tile.Group -> JSONObject().put("group", t.name).put("uid", t.id).put("apps", JSONArray(t.members.map { it.id }))
            }
        }))
    }).toString()).apply()

    // ---- launch feedback ----

    private var feedbackId: String? = null
    private val clearFeedback = Runnable { feedbackId = null; invalidate() }

    /** The glow behind a tapped icon, until the launcher closes or LunaCE's 3 s run out. */
    fun showLaunchFeedback(app: AppInfo) {
        feedbackId = app.id
        removeCallbacks(clearFeedback)
        postDelayed(clearFeedback, Params.FEEDBACK_MS)
        invalidate()
    }

    fun cancelLaunchFeedback() { removeCallbacks(clearFeedback); feedbackId = null; invalidate() }

    // ---- geometry ----

    private fun tabBarH() = luna.px(Params.TAB_BAR)
    private fun pageTop() = tabBarH()
    private fun pageBottom() = height - dockHeight - 1
    /**
     * ReorderableIconLayout::calculateAndSetHorizontalSpaceParameters, in TouchPad px: from
     * MaxIconsPerRow down, the first count n whose gap ⌊free / (n − 1)⌋ is positive, where
     * free = pageWidth − 128·(n + 1) + 12·MaxIconsPerRow (the adjustment keeps counting the
     * original seven). On a 768-px-wide TouchPad that is 5 icons 149 px apart, as measured.
     */
    private fun columnLayout(): Pair<Int, Int> {
        val w = (width / luna.density).toInt()
        for (n in Params.MAX_COLUMNS downTo 2) {
            val free = w - Params.CELL.toInt() * (n + 1) + Params.SPACE_ADJUST * Params.MAX_COLUMNS
            val gap = if (free <= 0) 0 else free / (n - 1)
            if (gap > 0) return n to gap
        }
        return 1 to 0
    }
    private fun columns(): Int = columnLayout().first
    private fun columnPitch(): Float = luna.px(Params.CELL + columnLayout().second)
    private fun rowPitch() = luna.px(Params.CELL + Params.ROW_GAP)
    private fun cellCentre(i: Int): PointF {
        val n = columns()
        val x = luna.px(Params.LEFT_MARGIN) + (i % n) * columnPitch() + luna.px(Params.CELL) / 2
        val y = pageTop() + luna.px(Params.TOP_MARGIN) + (i / n) * rowPitch() + luna.px(Params.CELL) / 2
        return PointF(x, y)
    }
    /** The cell under a point in page coordinates. */
    private fun cellAt(x: Float, y: Float, count: Int): Int {
        val col = ((x - luna.px(Params.LEFT_MARGIN)) / columnPitch()).toInt().coerceIn(0, columns() - 1)
        val row = max(0, ((y - pageTop() - luna.px(Params.TOP_MARGIN)) / rowPitch()).toInt())
        return (row * columns() + col).coerceIn(0, max(0, count - 1))
    }
    private fun contentHeight(p: Page) = luna.px(Params.TOP_MARGIN) + ((p.tiles.size + installsOn(p).size + columns() - 1) / columns()) * rowPitch()
    private fun maxScroll(p: Page) = max(0f, contentHeight(p) - (pageBottom() - pageTop()))
    private fun tabWidth() = min(width.toFloat() / pages.size, luna.px(Params.TAB_MAX_W))
    private fun currentPage() = pages[pagePos.roundToInt().coerceIn(0, pages.size - 1)]
    private fun deleteCentre(c: PointF) = PointF(c.x + luna.px(Params.DELETE_DX), c.y + luna.px(Params.DELETE_DY))
    private fun doneRect(): RectF {
        val w = luna.px(Params.DONE_W); val h = luna.px(Params.DONE_H)
        val right = width - luna.px(Params.DONE_RIGHT); val top = (tabBarH() - h) / 2
        return RectF(right - w, top, right, top + h)
    }
    /** The Done button's touch target: its art, with LunaCE's usual slack around it. */
    private fun doneTouchRect() = RectF(doneRect()).apply { inset(-luna.px(8f), -luna.px(8f)) }

    // ---- icon positions, animated while reordering ----

    /** Where icons were drawn when the order last changed; they move from there to their cells. */
    private val movedFrom = HashMap<String, PointF>()
    private var moveT = 1f
    private var moveAnim: ValueAnimator? = null

    private fun drawnCentre(tile: Tile, i: Int): PointF {
        val target = cellCentre(i)
        val from = movedFrom[tile.id] ?: return target
        return PointF(from.x + (target.x - from.x) * moveT, from.y + (target.y - from.y) * moveT)
    }

    /** Changes the order, with every icon sliding from where it is to its new cell (300 ms InQuad). */
    private fun reorder(change: () -> Unit) {
        val page = currentPage()
        val now = page.tiles.mapIndexed { i, t -> t.id to drawnCentre(t, i) }.toMap()
        change()
        movedFrom.clear(); movedFrom.putAll(now)
        moveAnim?.cancel()
        moveT = 0f
        moveAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Params.MOVE_MS; interpolator = Easing.InQuad
            addUpdateListener { moveT = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    // ---- drawing ----

    override fun onDraw(c: Canvas) {
        luna.tile(c, "launcher3/launcher-bg.png", RectF(0f, 0f, width.toFloat(), height.toFloat()))

        // Pages, side by side.
        c.save(); c.clipRect(0f, pageTop(), width.toFloat(), pageBottom())
        pages.forEachIndexed { pi, page ->
            val dx = (pi - pagePos) * width
            if (abs(dx) >= width) return@forEachIndexed
            c.save(); c.translate(dx, 0f)
            // Page::paintShadows, under the icons: tab-shadow.png along the top,
            // quicklaunch-shadow.png along the bottom.
            luna.tile(c, "launcher3/tab-shadow.png", RectF(0f, pageTop(), width.toFloat(), pageTop() + luna.px(8f)))
            luna.tile(c, "launcher3/quicklaunch-shadow.png", RectF(0f, pageBottom() - luna.px(8f), width.toFloat(), pageBottom()))
            val installing = installsOn(page)
            if (page.tiles.isEmpty() && installing.isEmpty()) drawEmptyPage(c)
            c.translate(0f, -page.scrollY)
            page.tiles.forEachIndexed { i, t -> if (t != dragging) drawTile(c, t, drawnCentre(t, i)) }
            installing.forEachIndexed { i, inst -> drawInstalling(c, inst, cellCentre(page.tiles.size + i)) }
            c.restore()
        }
        c.restore()
        luna.tile(c, "launcher3/launcher-scrollfade-top.png", RectF(0f, pageTop(), width.toFloat(), pageTop() + luna.px(10f)))
        luna.tile(c, "launcher3/launcher-scrollfade-bottom.png", RectF(0f, pageBottom() - luna.px(20f), width.toFloat(), pageBottom()))

        drawTabBar(c)
        // The dragged icon, over everything, under the finger.
        dragging?.let { drawTile(c, it, PointF(dragX - grabDx, dragY - grabDy)) }
        dialogApp?.let { drawDialog(c, it) }
    }

    private val emptyText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = luna.px(18f); typeface = luna.fontBold; color = Color.rgb(0xAA, 0xAA, 0xAA) }
    private val emptyLayout by lazy {
        @Suppress("DEPRECATION")
        StaticLayout(Params.EMPTY_TEXT, emptyText, luna.px(Params.EMPTY_BOX_W).toInt(), Layout.Alignment.ALIGN_CENTER, 1f, 0f, false)
    }

    /** ReorderablePage's empty page: the picture at the centre, the message below it. */
    private fun drawEmptyPage(c: Canvas) {
        val cx = width / 2f; val cy = pageTop() + (pageBottom() - pageTop()) / 2f
        luna.image("launcher3/launcher-empty-page.png")?.let { c.drawBitmap(it, cx - it.width / 2f, cy - it.height / 2f, null) }
        // Centred in its box, which is centred on the offset point.
        val ty = cy + luna.px(Params.EMPTY_DY) - emptyLayout.height / 2f
        c.save(); c.translate(cx - emptyLayout.width / 2f, ty); emptyLayout.draw(c); c.restore()
    }

    /** A package being installed: the default icon at half opacity, its name, and the progress strip. */
    private fun drawInstalling(c: Canvas, inst: Installing, centre: PointF) {
        val half = luna.px(Params.ICON) / 2
        val iy = centre.y + luna.px(Params.ICON_DY)
        val faded = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 128 }
        luna.image("default-app-icon.png")?.let { c.drawBitmap(it, null, RectF(centre.x - half, iy - half, centre.x + half, iy + half), faded) }
        val layout = labels.getOrPut("installing:" + inst.title) { twoLineLabel(inst.title) }
        c.save(); c.translate(centre.x - layout.width / 2f, iy + half + luna.px(Params.LABEL_GAP)); layout.draw(c); c.restore()
        val frame = (inst.progress * (Params.INSTALL_FRAMES - 1) / 100).coerceIn(0, Params.INSTALL_FRAMES - 1)
        luna.sprite(c, "loading-strip.png", centre.x + luna.px(Params.INSTALL_DX), centre.y + luna.px(Params.INSTALL_DY), 0, frame * 32, 32, 32)
    }

    private fun drawTile(c: Canvas, t: Tile, centre: PointF) = when (t) {
        is Tile.App -> drawIcon(c, t.app, centre)
        is Tile.Group -> drawGroup(c, t, centre)
    }

    /**
     * [LunaCE] A group: GroupIcon's 68 px composite - a rounded, gradient backplate with the
     * first members as 26 px thumbnails, 2 by 2, "+N" in the fourth place past four - with the
     * group's name as its label.
     */
    private fun drawGroup(c: Canvas, g: Tile.Group, centre: PointF) {
        val cx = centre.x; val cy = centre.y
        val iy = cy + luna.px(Params.ICON_DY)
        if (editing) {
            val fh = luna.px(Params.CELL) / 2
            luna.image("launcher3/edit-icon-bg.png")?.let { c.drawBitmap(it, null, RectF(cx - fh, cy - fh, cx + fh, cy + fh), null) }
        }
        if (groupTarget == g.id) {
            val gh = luna.px(Params.FEEDBACK) / 2
            luna.image("launcher3/launcher-touch-feedback.png")?.let { c.drawBitmap(it, null, RectF(cx - gh, iy - gh, cx + gh, iy + gh), null) }
        }
        val composite = groupComposites.getOrPut(g.id + ":" + g.members.joinToString(",") { it.id }) { GroupArt.composite(luna, g.members) }
        val half = composite.width / 2f
        c.drawBitmap(composite, cx - half, iy - composite.height / 2f, null)
        val layout = labels.getOrPut("group:" + g.name) { twoLineLabel(g.name) }
        c.save(); c.translate(cx - layout.width / 2f, iy + luna.px(Params.ICON) / 2 + luna.px(Params.LABEL_GAP)); layout.draw(c); c.restore()
    }
    private val groupComposites = HashMap<String, android.graphics.Bitmap>()

    /** centre is the cell centre: the icon sits above it, its label below. */
    private fun drawIcon(c: Canvas, app: AppInfo, centre: PointF) {
        val cx = centre.x; val cy = centre.y
        val half = luna.px(Params.ICON) / 2
        val iy = cy + luna.px(Params.ICON_DY)
        if (editing) {
            val fh = luna.px(Params.CELL) / 2
            luna.image("launcher3/edit-icon-bg.png")?.let { c.drawBitmap(it, null, RectF(cx - fh, cy - fh, cx + fh, cy + fh), null) }
        }
        if (feedbackId == app.id || groupTarget == app.id) {
            val gh = luna.px(Params.FEEDBACK) / 2
            luna.image("launcher3/launcher-touch-feedback.png")?.let { c.drawBitmap(it, null, RectF(cx - gh, iy - gh, cx + gh, iy + gh), null) }
        }
        val bmp = luna.appIcon(app) ?: luna.image("default-app-icon.png")
        bmp?.let { c.drawBitmap(it, null, RectF(cx - half, iy - half, cx + half, iy + half), null) }
        val layout = labels.getOrPut(app.id) { twoLineLabel(app.title) }
        c.save(); c.translate(cx - layout.width / 2f, iy + half + luna.px(Params.LABEL_GAP)); layout.draw(c); c.restore()
        if (editing && app.userInstalled && (dragging as? Tile.App)?.app != app) {
            val d = deleteCentre(centre)
            drawDelete(c, d.x, d.y, pressed = pressedDelete == app.id)
        }
    }

    /** The delete badge, centred: 32 × 32 at (2, 1), pressed at (2, 41). */
    private fun drawDelete(c: Canvas, cx: Float, cy: Float, pressed: Boolean) =
        luna.sprite(c, "launcher3/edit-button-delete.png", cx, cy, 2, if (pressed) 41 else 1, 32, 32)

    /** The Done button's art, centred: 98 × 34 at (1, 2), pressed at (1, 42). */
    private fun drawDone(c: Canvas, r: RectF, pressed: Boolean) =
        luna.sprite(c, "launcher3/edit-button-done.png", r.centerX(), r.centerY(), 1, if (pressed) 42 else 2, 98, 34)

    /** Centred, wrapped to at most two lines, the second elided with "…" (API 21 has no maxLines). */
    @Suppress("DEPRECATION")
    private fun twoLineLabel(text: String): StaticLayout {
        val w = luna.px(Params.LABEL_W).toInt()
        fun make(t: CharSequence) = StaticLayout(t, label, w, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false)
        val full = make(text)
        if (full.lineCount <= 2) return full
        val second = text.substring(full.getLineStart(1))
        val elided = TextUtils.ellipsize(second, label, w.toFloat(), TextUtils.TruncateAt.END)
        return make(text.substring(0, full.getLineStart(1)) + elided)
    }

    private fun drawTabBar(c: Canvas) {
        val h = tabBarH(); val tw = tabWidth()
        luna.nine(c, "launcher3/tab-bg.png", RectF(0f, 0f, width.toFloat(), h), 4, 20, 4, 20)
        val selected = pagePos.roundToInt()
        pages.forEachIndexed { i, p ->
            val r = RectF(i * tw, 0f, (i + 1) * tw, h)
            // LunaCE's PageTab: the highlighted background (a dragged icon over the tab, or a
            // finger on it) stands in for the normal or selected one.
            if (i == highlightedTab) luna.nine(c, "launcher3/tab-highlight.png", r, 20, 20, 20, 20)
            else if (i == selected) luna.nine(c, "launcher3/tab-selected-bg.png", r, 20, 20, 20, 20)
            if (i > 0 && i != selected) luna.nine(c, "launcher3/tab-divider.png", RectF(r.left - luna.px(1f), 0f, r.left + luna.px(1f), h), 0, 20, 0, 20)
            tabText.color = if (i == selected) Color.WHITE else Color.rgb(0xC8, 0xC8, 0xC8)
            c.drawText(p.title.uppercase(), r.centerX(), h / 2 - (tabText.ascent() + tabText.descent()) / 2, tabText)
        }
        if (showAddTab) addTabRect()?.let { r ->
            // tab-add-icon.png's normal state is its upper half.
            luna.image("launcher3/tab-add-icon.png")?.let { b -> c.drawBitmap(b, Rect(0, 0, b.width, b.height / 2), r, null) }
        }
        if (editing) {
            val r = doneRect()
            drawDone(c, r, pressed = donePressed)
            c.drawText("DONE", r.centerX(), r.centerY() - (doneText.ascent() + doneText.descent()) / 2 + luna.px(Params.DONE_TEXT_DY), doneText)
        }
    }

    /** The "+" (PageTabBar::addTabButtonRect), in the bar's unused space, if there is room for it. */
    private fun addTabRect(): RectF? {
        val left = pages.size * tabWidth()
        val edge = luna.px(Params.ADD_BUTTON)
        if (width - left < edge + luna.px(8f)) return null
        val top = (tabBarH() - edge) / 2
        return RectF(left + luna.px(Params.ADD_LEFT), top, left + luna.px(Params.ADD_LEFT) + edge, top + edge)
    }
    private var showAddTab = false
    private var addRevealedByThisPress = false
    private var tabHeld = false
    private val tabHold = Runnable {
        val i = tabAt(downX, downY)
        if (drag != Drag.UNDECIDED || i < 0) return@Runnable
        tabHeld = true; highlightedTab = -1; invalidate()
        onRenameTab(i, pages[i].title, i >= Params.PERMANENT_TABS)
    }
    private val addHold = Runnable {
        if (drag != Drag.UNDECIDED) return@Runnable
        showAddTab = true; addRevealedByThisPress = true; invalidate()
    }

    /** The tab under a dragged icon or a pressing finger, or -1. */
    private var highlightedTab = -1
    private fun tabAt(x: Float, y: Float) = if (y < tabBarH()) (x / tabWidth()).toInt().takeIf { it in pages.indices } ?: -1 else -1

    // ---- edit mode ----

    var editing = false
        private set
    private var dragging: Tile? = null
    private var dragX = 0f; private var dragY = 0f
    private var grabDx = 0f; private var grabDy = 0f
    private var pressedDelete: String? = null
    private var donePressed = false

    fun enterEditMode() { if (!editing) { editing = true; cancelLaunchFeedback(); onEditModeChanged(true); invalidate() } }

    /** Done, the home button, or the launcher closing. */
    fun exitEditMode() {
        if (!editing) return
        editing = false; dragging = null; dialogApp = null; pressedDelete = null; donePressed = false
        removeCallbacks(edgeFlip)
        onEditModeChanged(false)
        invalidate()
    }

    private fun pickUp(tile: Tile, x: Float, y: Float) {
        val page = currentPage()
        val c = drawnCentre(tile, page.tiles.indexOf(tile))
        dragging = tile
        dragX = x; dragY = y
        grabDx = x - c.x; grabDy = y + page.scrollY - c.y
        drag = Drag.ICON
        invalidate()
    }

    /**
     * The dragged icon's cell follows the finger; the others make room. Over a tab, the icon
     * goes to that tab's page, as LunaCE's tab bar takes it; held at a side edge, to the next
     * page that way. Over the dock, it stays put until dropped.
     */
    private fun dragTo(x: Float, y: Float) {
        dragX = x; dragY = y
        val app = dragging ?: return
        highlightedTab = tabAt(x, y)
        if (y < tabBarH()) {
            val i = (x / tabWidth()).toInt()
            if (i in pages.indices && i != currentPage().let { pages.indexOf(it) }) moveDragged(i)
            invalidate(); return
        }
        if (y > pageBottom()) { removeCallbacks(edgeFlip); removeCallbacks(vScroll); vSide = 0; invalidate(); return }
        val vedge = luna.px(Params.VEDGE)
        val v = if (y < pageTop() + vedge) -1 else if (y > pageBottom() - vedge) 1 else 0
        if (v != vSide) { vSide = v; removeCallbacks(vScroll); if (v != 0) postDelayed(vScroll, Params.VSCROLL_EVERY_MS) }
        val edge = luna.px(Params.EDGE)
        val side = if (x < edge) -1 else if (x > width - edge) 1 else 0
        if (side != edgeSide) { edgeSide = side; removeCallbacks(edgeFlip); if (side != 0) postDelayed(edgeFlip, Params.EDGE_MS) }
        // ReorderablePage only looks at the layout while the finger is all but still (its
        // velocity sampling), so an icon passing over the others on its way doesn't shuffle
        // them. Android sends nothing while a finger rests, so look once it has stopped.
        removeCallbacks(sampleDrag); postDelayed(sampleDrag, Params.SAMPLE_STILL_MS)
        invalidate()
    }

    /**
     * Where the finger rests decides: over the middle 60 % of another icon it is aiming to drop
     * *onto* it - a group, once it has stayed 300 ms - and nothing moves; over the rest of a
     * cell the dragged icon takes that cell.
     */
    private val sampleDrag = Runnable {
        val app = dragging ?: return@Runnable
        if (dragY < pageTop() || dragY > pageBottom()) return@Runnable
        val page = currentPage()
        val fx = dragX; val fy = dragY + page.scrollY
        val hit = page.tiles.withIndex().firstOrNull { (i, _) ->
            val c = cellCentre(i); val half = luna.px(Params.CELL) / 2
            abs(fx - c.x) < half && abs(fy - c.y) < half
        }
        if (hit != null && hit.value !== app && app is Tile.App) {
            val c = cellCentre(hit.index); val core = luna.px(Params.CELL) * Params.GROUP_CORE / 2
            if (abs(fx - c.x) < core && abs(fy - c.y) < core) {
                if (hit.value.id != hovering) { hovering = hit.value.id; groupTarget = null; removeCallbacks(armGroup); postDelayed(armGroup, Params.GROUP_DWELL_MS) }
                invalidate(); return@Runnable
            }
        }
        hovering = null; groupTarget = null; removeCallbacks(armGroup)
        val to = cellAt(fx, fy, page.tiles.size)
        val from = page.tiles.indexOf(app)
        if (to != from && from >= 0) reorder { page.tiles.removeAt(from); page.tiles.add(to, app) }
        invalidate()
    }

    /** The tile the dragged icon is over the middle of, and - once it has stayed 300 ms - the group target. */
    private var hovering: String? = null
    private var groupTarget: String? = null
    private val armGroup = Runnable { if (dragging != null) { groupTarget = hovering; invalidate() } }

    private var edgeSide = 0
    private var vSide = 0
    /** Held at the page's top or bottom edge, the page scrolls 150 px at a time (Page's scroll FSM). */
    private val vScroll: Runnable = Runnable {
        val page = currentPage()
        if (dragging == null || vSide == 0) return@Runnable
        val to = (page.scrollY + vSide * luna.px(Params.VSCROLL)).coerceIn(0f, maxScroll(page))
        if (to != page.scrollY) {
            anim?.cancel()
            anim = ValueAnimator.ofFloat(page.scrollY, to).apply {
                duration = Params.VSCROLL_MS; interpolator = Easing.OutCubic
                addUpdateListener { page.scrollY = it.animatedValue as Float; dragTo(dragX, dragY) }
                start()
            }
        }
        postDelayed(vScroll, Params.VSCROLL_EVERY_MS)
    }
    private val edgeFlip: Runnable = Runnable {
        val target = pages.indexOf(currentPage()) + edgeSide
        if (dragging != null && edgeSide != 0 && target in pages.indices) { moveDragged(target); postDelayed(edgeFlip, Params.EDGE_MS) }
    }

    /** Moves the dragged icon to the end of another page, and shows that page. */
    private fun moveDragged(to: Int) {
        val app = dragging ?: return
        pages.forEach { it.tiles.remove(app) }
        pages[to].tiles.add(app)
        movedFrom.clear(); moveT = 1f
        animatePage(to, Params.SNAP_MS, Easing.InQuad)
    }

    private fun drop() {
        val app = dragging ?: return
        removeCallbacks(edgeFlip); edgeSide = 0
        removeCallbacks(vScroll); vSide = 0
        removeCallbacks(armGroup); removeCallbacks(sampleDrag)
        highlightedTab = -1
        val target = groupTarget; hovering = null; groupTarget = null
        if (dragY > pageBottom()) {
            // Onto the dock: it takes the app; the icon stays where it was in the launcher. A
            // group has no place there.
            dragging = null
            if (app is Tile.App) onDropOnDock(app.app, dragX)
            invalidate(); return
        }
        val page = currentPage()
        if (target != null && app is Tile.App) {
            // Onto another icon: into its group, or the two make a new one where it stood.
            val i = page.tiles.indexOfFirst { it.id == target }
            val onto = page.tiles.getOrNull(i)
            if (onto != null) {
                dragging = null
                page.tiles.remove(app)
                when (onto) {
                    is Tile.Group -> onto.members += app.app
                    is Tile.App -> page.tiles[page.tiles.indexOf(onto)] = Tile.Group("group_" + java.util.UUID.randomUUID(), Params.GROUP_NAME, mutableListOf(onto.app, app.app))
                }
                reorder { }
                saveOrder()
                return
            }
        }
        // The icon settles from under the finger into its cell.
        val finger = PointF(dragX - grabDx, dragY - grabDy + page.scrollY)
        dragging = null
        reorder { }
        movedFrom[app.id] = finger
        saveOrder()
    }

    // ---- the remove dialog (LunaCE's AppInfoDialog) ----

    private var dialogApp: AppInfo? = null
    private var dialogPressed = -1  // 0 Cancel, 1 Remove
    private val dialogTitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = luna.px(18f); typeface = luna.fontBold; color = Color.WHITE }
    private val dialogMessage = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = luna.px(14f); typeface = luna.fontBold; color = Color.WHITE }
    private val buttonText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = luna.px(16f); typeface = luna.fontBold; color = Color.WHITE; textAlign = Paint.Align.CENTER }

    @Suppress("DEPRECATION")
    private fun dialogText(t: String, p: TextPaint) =
        StaticLayout(t, p, luna.px(Params.DIALOG_W - 2 * (Params.DIALOG_EDGE + Params.DIALOG_MARGIN)).toInt(), Layout.Alignment.ALIGN_CENTER, 1f, 0f, false)

    private class DialogLayout(val frame: RectF, val title: StaticLayout, val titleY: Float, val message: StaticLayout, val messageY: Float, val buttons: List<RectF>)

    private fun dialogLayout(app: AppInfo): DialogLayout {
        val edge = luna.px(Params.DIALOG_EDGE); val margin = luna.px(Params.DIALOG_MARGIN)
        val title = dialogText("Remove Application?", dialogTitle)
        val message = dialogText(app.title + " - v." + app.version, dialogMessage)
        val bh = luna.px(Params.DIALOG_BUTTON_H)
        val w = luna.px(Params.DIALOG_W)
        val h = title.height + message.height + 2 * bh + 2 * edge + 4 * margin + luna.px(Params.DIALOG_TOP)
        val left = (width - w) / 2; val top = (height - h) / 2
        val titleY = top + edge + margin + luna.px(Params.DIALOG_TOP)
        val messageY = titleY + title.height + margin
        val bx = left + edge + margin + luna.px(1f); val bw = w - 2 * (edge + margin) - luna.px(1f)
        val cancel = RectF(bx, messageY + message.height + margin, bx + bw, messageY + message.height + margin + bh)
        val remove = RectF(bx, cancel.bottom, bx + bw, cancel.bottom + bh)
        return DialogLayout(RectF(left, top, left + w, top + h), title, titleY, message, messageY, listOf(cancel, remove))
    }

    private fun drawDialog(c: Canvas, app: AppInfo) {
        luna.image("scrim.png")?.let { c.drawBitmap(it, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), null) }
        val d = dialogLayout(app)
        luna.nine(c, "popup-bg.png", d.frame, 35, 40, 35, 40)
        c.save(); c.translate(d.frame.centerX() - d.title.width / 2f, d.titleY); d.title.draw(c); c.restore()
        c.save(); c.translate(d.frame.centerX() - d.message.width / 2f, d.messageY); d.message.draw(c); c.restore()
        listOf("Cancel", "Remove").forEachIndexed { i, caption ->
            val r = d.buttons[i]
            luna.nine(c, if (dialogPressed == i) "pin/button-black-press.png" else "pin/button-black.png", r, 10, 10, 10, 10)
            c.drawText(caption, r.centerX(), r.centerY() - (buttonText.ascent() + buttonText.descent()) / 2, buttonText)
        }
    }

    private fun dialogTouch(e: MotionEvent) {
        val app = dialogApp ?: return
        val hit = dialogLayout(app).buttons.indexOfFirst { it.contains(e.x, e.y) }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> { dialogPressed = hit; invalidate() }
            MotionEvent.ACTION_UP -> {
                dialogPressed = -1
                if (hit >= 0) { dialogApp = null; if (hit == 1) onRemove(app) }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { dialogPressed = -1; invalidate() }
        }
    }

    // ---- touch ----

    private enum class Drag { NONE, UNDECIDED, PAGE, SCROLL, ICON }
    private var drag = Drag.NONE
    private var downX = 0f; private var downY = 0f; private var downPage = 0f; private var downScroll = 0f
    private var downApp: Tile? = null
    private var velocity: VelocityTracker? = null
    private var anim: ValueAnimator? = null
    /** Holding an icon enters edit mode and picks the icon up. */
    private val longPress = Runnable {
        val app = downApp ?: return@Runnable
        if (drag != Drag.UNDECIDED) return@Runnable
        enterEditMode()
        pickUp(app, downX, downY)
    }

    private fun tileAt(x: Float, y: Float): Tile? {
        if (y < pageTop() || y > pageBottom() || abs(pagePos - pagePos.roundToInt()) > 0.01f) return null
        val page = currentPage()
        val half = luna.px(Params.CELL) / 2
        return page.tiles.withIndex().firstOrNull { (i, _) -> val c = cellCentre(i); abs(x - c.x) < half && abs(y + page.scrollY - c.y) < half }?.value
    }

    private fun deleteAt(x: Float, y: Float): AppInfo? {
        if (!editing) return null
        val page = currentPage()
        val r = luna.px(Params.DELETE_BOX) / 2 + luna.px(6f)
        return page.tiles.withIndex().firstOrNull { (i, t) ->
            val a = (t as? Tile.App)?.app ?: return@firstOrNull false
            if (!a.userInstalled) return@firstOrNull false
            val d = deleteCentre(cellCentre(i)); abs(x - d.x) < r && abs(y + page.scrollY - d.y) < r
        }?.value?.let { (it as Tile.App).app }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (dialogApp != null) { dialogTouch(e); return true }
        if (velocity == null) velocity = VelocityTracker.obtain()
        velocity!!.addMovement(e)
        val page = currentPage()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                anim?.cancel(); drag = Drag.UNDECIDED
                downX = e.x; downY = e.y; downPage = pagePos; downScroll = page.scrollY
                pressedDelete = deleteAt(e.x, e.y)?.id
                donePressed = editing && doneTouchRect().contains(e.x, e.y)
                downApp = if (pressedDelete == null && !donePressed) tileAt(e.x, e.y) else null
                highlightedTab = if (donePressed) -1 else tabAt(e.x, e.y)
                if (downApp != null && !editing) postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                tabHeld = false; addRevealedByThisPress = false
                if (!editing && !donePressed && e.y < tabBarH()) {
                    if (tabAt(e.x, e.y) >= 0) postDelayed(tabHold, Params.TAB_HOLD_MS)
                    else if (pages.size < Params.MAX_TABS) postDelayed(addHold, Params.ADD_HOLD_MS)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - downX; val dy = e.y - downY
                if (abs(dx) + abs(dy) > luna.px(Params.ADD_SLOP)) removeCallbacks(addHold)
                if (drag == Drag.UNDECIDED && max(abs(dx), abs(dy)) > luna.px(Params.TAP_RADIUS)) {
                    removeCallbacks(longPress); removeCallbacks(tabHold)
                    pressedDelete = null; donePressed = false; highlightedTab = -1
                    val app = downApp
                    // In edit mode an icon moves with the finger; otherwise the first axis past
                    // the tap radius wins (reference §3.7).
                    if (editing && app != null) pickUp(app, downX, downY)
                    else drag = if (abs(dx) >= abs(dy)) Drag.PAGE else Drag.SCROLL
                }
                when (drag) {
                    Drag.ICON -> dragTo(e.x, e.y)
                    Drag.PAGE -> { pagePos = (downPage - dx / width).coerceIn(-0.2f, pages.size - 0.8f); invalidate() }
                    Drag.SCROLL -> {
                        var y = downScroll - dy
                        val m = maxScroll(page)
                        if (y < 0) y /= 2 else if (y > m) y = m + (y - m) / 2  // half rate while overscrolled
                        page.scrollY = y; invalidate()
                    }
                    else -> {}
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress); removeCallbacks(tabHold); removeCallbacks(addHold)
                velocity!!.computeCurrentVelocity(1000)
                val vx = velocity!!.xVelocity; val vy = velocity!!.yVelocity
                velocity!!.recycle(); velocity = null
                when (drag) {
                    Drag.ICON -> drop()
                    Drag.PAGE -> {
                        val flick = abs(vx) > luna.px(500f)
                        if (flick) {
                            val target = (downPage.roundToInt() + if (vx < 0) 1 else -1).coerceIn(0, pages.size - 1)
                            val ms = (abs(target - pagePos) * width / (abs(vx) / 1000f)).toLong().coerceIn(Params.FLICK_MIN_MS, Params.FLICK_MAX_MS)
                            animatePage(target, ms, Easing.OutCubic)
                        } else animatePage(pagePos.roundToInt().coerceIn(0, pages.size - 1), Params.SNAP_MS, Easing.InQuad)
                    }
                    Drag.SCROLL -> fling(page, -vy)
                    Drag.UNDECIDED -> if (e.actionMasked == MotionEvent.ACTION_UP) tap(e.x, e.y)
                    Drag.NONE -> {}
                }
                pressedDelete = null; donePressed = false; highlightedTab = -1
                drag = Drag.NONE
                invalidate()
            }
        }
        return true
    }

    private fun tap(x: Float, y: Float) {
        if (tabHeld) return
        if (showAddTab) {
            // PageTabBar::mouseReleaseEvent: the release that revealed it leaves it up; a tap on
            // it asks for a new tab; a tap anywhere else puts it away.
            if (addRevealedByThisPress) return
            val onIt = addTabRect()?.contains(x, y) == true
            showAddTab = false; invalidate()
            if (onIt) { onAddTab(); return }
        }
        if (editing) {
            if (doneTouchRect().contains(x, y)) { exitEditMode(); return }
            deleteAt(x, y)?.let { dialogApp = it; return }
        }
        if (y < tabBarH()) {
            val i = (x / tabWidth()).toInt()
            if (i in pages.indices) animatePage(i, Params.SNAP_MS, Easing.InQuad)
            return
        }
        if (editing) return  // icons don't launch while being arranged
        when (val t = tileAt(x, y)) {
            is Tile.App -> { showLaunchFeedback(t.app); onLaunch(t.app) }
            is Tile.Group -> onOpenGroup(t, groupScreenCentre(t))
            null -> {}
        }
    }

    private fun animatePage(target: Int, ms: Long, easing: android.animation.TimeInterpolator) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(pagePos, target.toFloat()).apply {
            duration = ms; interpolator = easing
            addUpdateListener { pagePos = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    /** The velocity of the flick still running, for LunaCE's "keep increasing velocity". */
    private var flickV = 0f

    /**
     * KineticScroller: a flick starts at its velocity over sFlickScalar and slows under a
     * constant friction, eased OutCubic over the time it takes to stop; a flick in the same
     * direction as one still running adds to it. Past the end it may run on up to 100 px, then
     * comes back over 350 ms; let go while overscrolled without a flick, 500 ms OutCubic.
     */
    private fun fling(page: Page, v: Float) {
        anim?.cancel()
        val m = maxScroll(page)
        val start = page.scrollY
        if (abs(v) < luna.px(500f)) {
            flickV = 0f
            if (start < 0 || start > m) settle(page, Params.OVERSCROLL_SNAP_MS)
            return
        }
        // In TouchPad px per ms.
        var v0 = (v / luna.density) / Params.FLICK_SCALAR
        // flickV is only still set if the last flick was stopped by this touch, not run out.
        if (flickV != 0f && Math.signum(flickV) == Math.signum(v0)) v0 += flickV
        v0 = v0.coerceIn(-100f, 100f)
        flickV = v0
        val t = abs(v0) / Params.FRICTION
        val distance = v0 * v0 / (2 * Params.FRICTION) * Math.signum(v0) * luna.density
        val over = luna.px(Params.MAX_OVERSCROLL)
        val target = (start + distance).coerceIn(-over, m + over)
        anim = ValueAnimator.ofFloat(start, target).apply {
            duration = t.toLong().coerceAtLeast(1L); interpolator = Easing.OutCubic
            addUpdateListener { page.scrollY = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                var cancelled = false
                override fun onAnimationCancel(a: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(a: android.animation.Animator) {
                    if (cancelled) return
                    flickV = 0f
                    if (page.scrollY < 0 || page.scrollY > m) settle(page, Params.OVERSCROLL_FIX_MS)
                }
            })
            start()
        }
    }

    private fun settle(page: Page, ms: Long) {
        val m = maxScroll(page)
        anim = ValueAnimator.ofFloat(page.scrollY, page.scrollY.coerceIn(0f, m)).apply {
            duration = ms; interpolator = Easing.OutCubic
            addUpdateListener { page.scrollY = it.animatedValue as Float; invalidate() }
            start()
        }
    }
}
