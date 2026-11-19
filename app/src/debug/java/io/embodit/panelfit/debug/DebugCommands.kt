package io.embodit.panelfit.debug

import android.content.Context
import io.embodit.panelfit.domain.model.Language
import io.embodit.panelfit.domain.model.PolarPose
import io.embodit.panelfit.domain.model.Rating
import io.embodit.panelfit.domain.model.Surface
import io.embodit.panelfit.domain.model.Template
import io.embodit.panelfit.domain.usecase.Confirm
import io.embodit.panelfit.domain.usecase.Field
import io.embodit.panelfit.domain.usecase.Page
import io.embodit.panelfit.domain.usecase.Placement
import io.embodit.panelfit.domain.usecase.Sub
import io.embodit.panelfit.platform.PfGraph
import io.embodit.panelfit.platform.PfLog
import io.embodit.panelfit.ui.pf.PfEvent
import io.embodit.panelfit.ui.pf.PfViewModel
import io.embodit.panelfit.ui.pf.debug.PfProbe
import java.io.File
import java.util.Locale
import kotlin.math.sin

/**
 * QA hooks of the debug build (design `qaHooks`). Every command goes through the same [PfEvent]s as a press in the
 * window or on the Stage (profile D29); one log line answers each (tag `PanelFit`, prefix `CMD`).
 *
 *   adb shell am broadcast -a io.embodit.panelfit.DEBUG -p io.embodit.panelfit --es cmd add --es t T1
 *
 * - `add --es t T1..T8`                         add a specimen from the library (T8 = first picture in files/import/)
 * - `select --ei i <id>` (-1 = none)            I2; `done` = [完成]
 * - `place --ef dist --ef yaw --ef elev --ef facing --ef tilt`   pose of the selected (else the last) specimen,
 *                                               through the stick's release path (snaps like a real release)
 * - `stick --ef x --ef y --ef dur`              feeds the stick mapping for dur seconds (same function as the controller)
 * - `drag --ef x --ef y --ef z` / `release`     moves the selected specimen as a label drag would (metres: x right, y up, z away)
 * - `head --ef x --ef y --ef z --ef yaw [--ef pitch]` / `head --es off 1`   override the head pose (frozen)
 * - `overlay --es k reach|neck|ruler|frames --ei on 1|0`
 * - `overlap`                                   two specimens, one covering the other (S11)
 * - `rate --es role bodyMedium --es v ok|meh|no`
 * - `save`, `saveFail --es where public|all`   S09 (saveFail keeps failing until `saveFail --es where none`)
 * - `import --es path <file name in files/import/>`
 * - `fill6`, `deleteAll`, `blocked --ei on 1|0`, `quit`, `quitYes`, `lang zh|en`, `recall`, `front`
 * - `step --es f dist|height|side|facing|tilt|width|heightDp|scale --ei d 1|-1`, `surface glass|solid`, `tiltToEye`
 * - `page library|adjust|rate|card|settings|list|import`, `adjustPage 0|1`, `cardPage 0|1`, `group 0..4`,
 *   `settingsPage 0|1|2` (calibrate / general / how to)
 * - `state <S00..S19>[:variant]` (S02:full, S04:none, S07:none, S08:none, S13b:quit, S13b:clear, S15:error):
 *   straight to a designed state through the same events; real sensing is frozen unless `--ei live 1`
 * - `ui <control>`                              presses a window / panel control by its probe name
 * - `dump` (DUMP {...}), `hmd`, `panels`, `unfreeze`
 */
internal object DebugCommands {

    fun run(context: Context, extras: Map<String, String>) {
        val raw = extras["cmd"]?.trim() ?: return
        val tokens = raw.split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        if (tokens.isEmpty()) return
        var name = tokens.removeAt(0)
        val args = ArrayList<String>()
        if (name.contains('=')) {
            args += name.substringAfter('=').split(',').filter { it.isNotEmpty() }
            name = name.substringBefore('=')
        }
        // extras written inside the command string ("place --ef dist 0.5", what tools/qa_shot.sh sends) count as extras
        val all = HashMap(extras)
        var k = 0
        while (k < tokens.size) {
            val t = tokens[k]
            if (t.startsWith("--e") && k + 2 < tokens.size + 1 && k + 1 < tokens.size) {
                val key = tokens[k + 1]
                val value = tokens.getOrNull(k + 2) ?: ""
                all[key] = value
                k += 3
                continue
            }
            args += t.split(',').filter { it.isNotEmpty() }
            k++
        }
        val host = PfProbe.host
        if (host == null) {
            PfLog.i("CMD $name ignored: the Stage is not running")
            return
        }
        try {
            dispatch(context, host, name, args, all)
        } catch (t: Throwable) {
            PfLog.e("CMD $name failed: $t", t)
        }
    }

    private fun f(extras: Map<String, String>, key: String): Float? = extras[key]?.toFloatOrNull()

    private fun i(extras: Map<String, String>, key: String): Int? = extras[key]?.toFloatOrNull()?.toInt()

    private fun dispatch(context: Context, host: PfProbe.Host, name: String, args: List<String>, ex: Map<String, String>) {
        val e: (PfEvent) -> Unit = { host.dispatch(it) }
        val scene = host.scene()
        val s0 = host.uiState()
        when (name.lowercase(Locale.US)) {
            "add" -> {
                val t = Template.of(ex["t"] ?: args.firstOrNull() ?: "T1") ?: Template.T1
                if (t == Template.T8) {
                    val first = PfGraph.files.listImports().firstOrNull()
                    if (first != null) e(PfEvent.ImportPick(first.name)) else e(PfEvent.GoSub(Sub.IMPORT))
                } else e(PfEvent.Add(t))
            }
            "select" -> {
                val id = i(ex, "i") ?: args.firstOrNull()?.toIntOrNull() ?: -1
                e(PfEvent.Select(if (id < 0) null else id))
            }
            "done" -> e(PfEvent.Done)
            "place" -> {
                val id = s0.selected ?: s0.specimens.lastOrNull()?.id ?: return log("place: no specimen")
                if (s0.selected != id) e(PfEvent.Select(id))
                val sp = host.uiState().specimens.first { it.id == id }
                val dist = f(ex, "dist") ?: sp.pose.dist
                val elev = f(ex, "elev")
                val height = if (elev != null) dist * sin(Math.toRadians(elev.toDouble())).toFloat() else sp.pose.heightM * dist / sp.pose.dist
                val pose = PolarPose(dist, height, f(ex, "yaw") ?: sp.pose.yawDeg, f(ex, "facing") ?: sp.pose.facingDeg, f(ex, "tilt") ?: sp.pose.tiltDeg)
                e(PfEvent.LivePose(id, pose))
                e(PfEvent.StickReleased(id, pose))
            }
            "stick" -> scene?.injectStick(f(ex, "x") ?: 0f, f(ex, "y") ?: 0f, f(ex, "dur") ?: 1f)
            "drag" -> scene?.injectDrag(f(ex, "x") ?: 0f, f(ex, "y") ?: 0f, f(ex, "z") ?: 0f)
            "release" -> scene?.releaseDrag()
            "head" ->
                if (ex.containsKey("off") || args.firstOrNull() == "off") scene?.clearHeadOverride()
                else scene?.overrideHead(f(ex, "x") ?: 0f, f(ex, "y") ?: 1.6f, f(ex, "z") ?: 0f, f(ex, "yaw") ?: 0f, f(ex, "pitch") ?: 0f)
            "unfreeze" -> {
                scene?.clearHeadOverride()
                scene?.freeze(false)
            }
            "overlay" -> e(PfEvent.Overlay(ex["k"] ?: args.firstOrNull() ?: "reach", (i(ex, "on") ?: args.getOrNull(1)?.toIntOrNull() ?: 1) != 0))
            "overlap" -> overlap(host)
            "rate" -> {
                val v = when ((ex["v"] ?: args.getOrNull(1) ?: "ok").lowercase(Locale.US)) {
                    "meh" -> Rating.MEH
                    "no" -> Rating.NO
                    else -> Rating.OK
                }
                e(PfEvent.Rate(ex["role"] ?: args.firstOrNull() ?: "bodyMedium", v))
            }
            "save" -> {
                PfGraph.files.failMode = null
                e(PfEvent.Save)
            }
            "savefail" -> {
                val where = ex["where"] ?: args.firstOrNull() ?: "public"
                PfGraph.files.failMode = if (where == "none") null else where
                if (where != "none") e(PfEvent.Save)
            }
            "import" -> {
                val path = ex["path"] ?: args.firstOrNull() ?: return log("import: path missing")
                val f = File(path)
                val name0 = if (f.isAbsolute) {
                    val target = File(PfGraph.files.importDir, f.name)
                    if (f.isFile && f.absolutePath != target.absolutePath) runCatching { f.copyTo(target, overwrite = true) }
                    f.name
                } else path
                e(PfEvent.GoSub(Sub.IMPORT))
                e(PfEvent.ImportPick(name0))
            }
            "fill6" -> fill6(host)
            "deleteall" -> e(PfEvent.ClearAll)
            "blocked" -> scene?.blocked((i(ex, "on") ?: args.firstOrNull()?.toIntOrNull() ?: 1) != 0)
            "quit" -> e(PfEvent.Ask(Confirm.QUIT))
            "quityes" -> e(PfEvent.QuitYes)
            "lang" -> e(PfEvent.Lang(if ((ex["v"] ?: args.firstOrNull() ?: "zh").lowercase(Locale.US) == "en") Language.EN else Language.ZH))
            "recall" -> e(PfEvent.RecallWindow)
            "front" -> e(PfEvent.AllToFront)
            "step" -> {
                val field = when ((ex["f"] ?: args.firstOrNull() ?: "dist").lowercase(Locale.US)) {
                    "height" -> Field.HEIGHT
                    "side" -> Field.SIDE
                    "facing" -> Field.FACING
                    "tilt" -> Field.TILT
                    "width" -> Field.WIDTH
                    "heightdp" -> Field.HEIGHT_DP
                    "scale" -> Field.SCALE
                    else -> Field.DIST
                }
                e(PfEvent.Step(field, i(ex, "d") ?: args.getOrNull(1)?.toIntOrNull() ?: 1))
            }
            "surface" -> e(PfEvent.SetSurface(if ((args.firstOrNull() ?: ex["v"]) == "solid") Surface.SOLID else Surface.GLASS))
            "tilttoeye" -> e(PfEvent.TiltToEye)
            "page" -> page(e, ex["p"] ?: args.firstOrNull() ?: "library")
            "adjustpage" -> e(PfEvent.AdjustPage(args.firstOrNull()?.toIntOrNull() ?: i(ex, "i") ?: 0))
            "cardpage" -> e(PfEvent.CardPage(args.firstOrNull()?.toIntOrNull() ?: i(ex, "i") ?: 0))
            "group" -> e(PfEvent.RateGroup(args.firstOrNull()?.toIntOrNull() ?: i(ex, "i") ?: 3))
            "settingspage" -> e(PfEvent.SettingsPage(args.firstOrNull()?.toIntOrNull() ?: i(ex, "i") ?: 0))
            "state" -> goState(host, ex["s"] ?: args.firstOrNull() ?: "S02", (i(ex, "live") ?: 0) != 0)
            "ui" -> {
                val control = ex["name"] ?: args.firstOrNull() ?: return log("ui: name missing")
                log("ui $control pressed=${host.pressControl(control)}")
                return
            }
            "dump" -> {
                val json = host.dumpJson()
                runCatching { File(context.filesDir, "dump.json").writeText(json) }
                PfLog.i("DUMP $json")
                return
            }
            "hmd" -> {
                log(scene?.poseLine() ?: "no scene")
                return
            }
            "panels" -> {
                scene?.panelLines()?.forEach { PfLog.i("PANELS $it") }
                return
            }
            else -> return log("unknown command $name")
        }
        log("$name ok -> ${PfViewModel.stateName(host.uiState())}")
    }

    private fun page(e: (PfEvent) -> Unit, p: String) {
        when (p.lowercase(Locale.US)) {
            "list" -> e(PfEvent.GoSub(Sub.LIST))
            "import" -> e(PfEvent.GoSub(Sub.IMPORT))
            "adjust" -> e(PfEvent.GoPage(Page.ADJUST))
            "rate" -> e(PfEvent.GoPage(Page.RATE))
            "card" -> e(PfEvent.GoPage(Page.CARD))
            "settings" -> e(PfEvent.GoPage(Page.SETTINGS))
            else -> e(PfEvent.GoPage(Page.LIBRARY))
        }
    }

    /** Six specimens of different kinds (S02:full, fill6 cold starts). */
    private fun fill6(host: PfProbe.Host) {
        val order = listOf(Template.T1, Template.T2, Template.T3, Template.T5B, Template.T6, Template.T4)
        for (t in order) {
            if (host.uiState().full) break
            host.dispatch(PfEvent.Add(t))
        }
        host.dispatch(PfEvent.Done)
        host.dispatch(PfEvent.GoPage(Page.LIBRARY))
    }

    /** Two specimens, the nearer one covering the lower part of the farther one (S11). */
    private fun overlap(host: PfProbe.Host) {
        while (host.uiState().specimens.size < 2) host.dispatch(PfEvent.Add(if (host.uiState().specimens.isEmpty()) Template.T1 else Template.T2))
        val list = host.uiState().specimens.takeLast(2)
        val front = list[0]
        val back = list[1]
        fun put(id: Int, pose: PolarPose) {
            host.dispatch(PfEvent.Select(id))
            host.dispatch(PfEvent.LivePose(id, pose))
            host.dispatch(PfEvent.StickReleased(id, pose))
        }
        val yaw = 27f // both clear of the tool window's sector (+-18.6 deg), so S11 is shown without S19
        put(back.id, Placement.ahead(1.6f, yaw + 2f, 4f))
        put(front.id, Placement.ahead(1.0f, yaw, 8f))
        host.dispatch(PfEvent.Select(front.id))
    }

    /** Straight to a designed state through the same events a user would cause (sensing frozen unless [live]). */
    private fun goState(host: PfProbe.Host, spec: String, live: Boolean) {
        val scene = host.scene()
        if (!live) scene?.freeze(true)
        val e: (PfEvent) -> Unit = { host.dispatch(it) }
        val base = spec.substringBefore('+')
        fun ensureOne() {
            if (host.uiState().specimens.isEmpty()) e(PfEvent.Add(Template.T1))
        }
        fun selectLast() {
            ensureOne()
            e(PfEvent.Select(host.uiState().specimens.last().id))
        }
        // past the welcome gate the way a user leaves it ([试一块] = add T1), so every later state is reachable
        if (host.uiState().phase == io.embodit.panelfit.domain.usecase.Phase.WELCOME && base.uppercase(Locale.US) !in setOf("S00", "S01")) e(PfEvent.TryOne)
        when (base.uppercase(Locale.US)) {
            "S01" -> e(PfEvent.ShowWelcome)
            "S02" -> e(PfEvent.GoPage(Page.LIBRARY))
            "S02:FULL" -> {
                fill6(host)
                e(PfEvent.GoPage(Page.LIBRARY))
            }
            "S03", "S04" -> {
                selectLast()
                e(PfEvent.GoPage(Page.ADJUST))
            }
            "S04:NONE" -> {
                e(PfEvent.Select(null))
                e(PfEvent.GoPage(Page.ADJUST))
            }
            "S07" -> {
                selectLast()
                e(PfEvent.GoPage(Page.RATE))
            }
            "S07:NONE" -> {
                e(PfEvent.Select(null))
                e(PfEvent.GoPage(Page.RATE))
            }
            "S08" -> {
                selectLast()
                e(PfEvent.GoPage(Page.CARD))
            }
            "S08:NONE" -> {
                e(PfEvent.Select(null))
                e(PfEvent.GoPage(Page.CARD))
            }
            "S09" -> {
                selectLast()
                e(PfEvent.GoPage(Page.CARD))
                e(PfEvent.Save)
            }
            "S10" -> {
                selectLast()
                e(PfEvent.Overlay("reach", true))
                e(PfEvent.Overlay("neck", true))
                e(PfEvent.Overlay("ruler", true))
            }
            "S11" -> overlap(host)
            "S12" -> e(PfEvent.GoSub(Sub.LIST))
            "S13" -> e(PfEvent.GoPage(Page.SETTINGS))
            "S13B:QUIT", "S13B" -> e(PfEvent.Ask(Confirm.QUIT))
            "S13B:CLEAR" -> {
                ensureOne()
                e(PfEvent.Ask(Confirm.CLEAR))
            }
            "S15" -> e(PfEvent.GoSub(Sub.IMPORT))
            "S15:ERROR" -> {
                val bad = File(PfGraph.files.importDir, "broken.png")
                runCatching { bad.writeText("not an image") }
                e(PfEvent.GoSub(Sub.IMPORT))
                e(PfEvent.ImportPick(bad.name))
            }
            "S16" -> scene?.blocked(true)
            "S17" -> e(PfEvent.Lang(Language.EN))
            "S19" -> {
                selectLast()
                val id = host.uiState().selected ?: return
                val pose = Placement.ahead(1.2f, host.uiState().windowYawDeg ?: 0f, 10f)
                e(PfEvent.LivePose(id, pose))
                e(PfEvent.StickReleased(id, pose))
            }
            else -> log("state $spec: unknown")
        }
    }

    private fun log(line: String) {
        PfLog.i("CMD $line")
    }
}
