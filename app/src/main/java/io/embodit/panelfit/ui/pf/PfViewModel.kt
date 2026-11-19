package io.embodit.panelfit.ui.pf

import android.os.Build
import android.os.SystemClock
import io.embodit.panelfit.data.PfFiles
import io.embodit.panelfit.data.PfStore
import io.embodit.panelfit.data.SaveResult
import io.embodit.panelfit.data.Saved
import io.embodit.panelfit.domain.model.Language
import io.embodit.panelfit.domain.model.Specimen
import io.embodit.panelfit.domain.model.Template
import io.embodit.panelfit.domain.usecase.Confirm
import io.embodit.panelfit.domain.usecase.FacePanelUseCase
import io.embodit.panelfit.domain.usecase.Page
import io.embodit.panelfit.domain.usecase.PfReducer
import io.embodit.panelfit.domain.usecase.PfState
import io.embodit.panelfit.domain.usecase.Phase
import io.embodit.panelfit.domain.usecase.SpecText
import io.embodit.panelfit.domain.usecase.Sub
import io.embodit.panelfit.domain.usecase.Toast
import io.embodit.panelfit.domain.usecase.deg
import io.embodit.panelfit.domain.usecase.wrapDeg
import io.embodit.panelfit.platform.PfLog
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The app's model, shared by the Stage and the tool window (process level, [PfShared]): closing the window with its
 * × keeps everything. Every input (window buttons, Stage taps, scene sensing, debug broadcast) arrives as a [PfEvent]
 * and goes through [PfReducer]; main thread only.
 */
class PfViewModel(private val store: PfStore, private val files: PfFiles) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state: MutableStateFlow<PfState>
    val state: StateFlow<PfState>
        get() = _state

    private val _effects = MutableSharedFlow<PfEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<PfEffect> = _effects

    private var persistJob: Job? = null
    private var lastStateName = ""
    private val lastMetric = HashMap<Int, String>()
    private var renderedSp = HashMap<String, Float>()

    init {
        val saved = runCatching { store.load() }.getOrNull()
        _state =
            MutableStateFlow(
                if (saved == null) PfState()
                else PfState(specimens = saved.specimens, nextId = saved.nextId, settings = saved.settings, overlays = saved.overlays, lastSaved = saved.lastSaved),
            )
        PfLog.i("PF-STATE init specimens=${_state.value.specimens.size} welcomed=${_state.value.settings.welcomed}")
    }

    fun onEvent(e: PfEvent) {
        val before = _state.value
        val now = SystemClock.elapsedRealtime()
        var s = reduce(before, e, now)
        // a specimen deleted (or all cleared) while its label is held: the drag hint must not outlive it
        if (s.dragging != null && s.specimens.none { it.id == s.dragging }) s = s.copy(dragging = null)
        if (s !== before) {
            if (needsDerive(before, s, e)) s = PfReducer.derive(s, s.eye, s.headYawDeg)
            _state.value = s
            if (persistable(before, s)) persistSoon()
            logState(s, e)
            logMetrics(before, s, e)
        }
    }

    private fun reduce(s: PfState, e: PfEvent, now: Long): PfState =
        when (e) {
            is PfEvent.Add -> PfReducer.add(s, e.template).also { if (it !== s) sound(SoundKind.TAP) }
            is PfEvent.ImportPick -> importPick(s, e.name, now)
            PfEvent.RefreshImports -> s.copy(importFiles = files.listImports())
            is PfEvent.GoPage -> PfReducer.page(s, e.page)
            is PfEvent.GoSub -> PfReducer.sub(s, e.sub).let { if (e.sub == Sub.IMPORT) it.copy(importFiles = files.listImports()) else it }
            is PfEvent.AdjustPage -> s.copy(adjustPage = e.index.coerceIn(0, 1))
            is PfEvent.CardPage -> s.copy(cardPage = e.index.coerceIn(0, 1))
            is PfEvent.RateGroup -> s.copy(rateGroup = e.index.coerceIn(0, 4))
            is PfEvent.SettingsPage -> s.copy(settingsPage = e.index.coerceIn(0, 2), confirm = null)
            PfEvent.TryOne -> PfReducer.add(s, Template.T1)
            is PfEvent.Select -> PfReducer.select(s, e.id).let { if (e.id != null && it.page == Page.LIBRARY && it.sub == Sub.NONE) it.copy(page = Page.ADJUST) else it }
            PfEvent.Done -> PfReducer.done(s)
            is PfEvent.Step -> PfReducer.step(s, e.field, e.dir)
            PfEvent.TiltToEye -> PfReducer.tiltToEye(s)
            is PfEvent.SetSurface -> PfReducer.surface(s, e.surface)
            is PfEvent.Overlay -> PfReducer.overlay(s, e.key, e.on)
            is PfEvent.Rate -> PfReducer.rate(s, e.role, e.rating)
            PfEvent.Save -> save(s, now)
            is PfEvent.MoveToFront -> PfReducer.moveToFront(s, e.id)
            is PfEvent.ToggleHidden -> PfReducer.toggleHidden(s, e.id)
            is PfEvent.AskDelete -> PfReducer.askDelete(s, e.id)
            is PfEvent.Delete -> PfReducer.delete(s, e.id)
            is PfEvent.MoveOutOfWindow -> PfReducer.moveOutOfWindow(s, e.id)
            is PfEvent.Settings -> PfReducer.settings(s, e.settings)
            is PfEvent.Lang -> PfReducer.settings(s, s.settings.copy(language = e.language))
            // the quit confirmation is in the settings footer, on every segment; clearing confirms in General
            is PfEvent.Ask ->
                PfReducer.confirm(s, e.confirm).let {
                    when (e.confirm) {
                        null -> it
                        Confirm.CLEAR -> it.copy(page = Page.SETTINGS, settingsPage = 1)
                        else -> it.copy(page = Page.SETTINGS)
                    }
                }
            PfEvent.ClearAll -> PfReducer.clearAll(s)
            PfEvent.QuitYes -> {
                persistNow(s)
                _effects.tryEmit(PfEffect.Exit)
                s.copy(confirm = null)
            }
            PfEvent.AllToFront -> PfReducer.requestFront(s, now)
            is PfEvent.FrontSettled -> {
                headForward = e.forward
                PfReducer.allToFront(s, e.eye, e.forward)
            }
            PfEvent.RecallWindow -> {
                _effects.tryEmit(PfEffect.RecallWindow)
                s
            }
            PfEvent.ShowWelcome -> s.copy(phase = Phase.WELCOME, page = Page.LIBRARY, sub = Sub.NONE, settings = s.settings.copy(welcomed = false))
            is PfEvent.Ready -> {
                headForward = e.forward
                val restored = s.phase != Phase.PREPARING || s.specimens.isNotEmpty()
                PfReducer.ready(s, e.eye, e.forward, e.headOk, restored, now).let { r ->
                    r.copy(headYawDeg = 0f, phase = if (s.phase == Phase.PREPARING) r.phase else s.phase)
                }
            }
            is PfEvent.Head -> {
                if (e.forward != null) headForward = e.forward
                val yaw = s.anchor?.let { a -> e.forward?.let { f -> wrapDeg((a.yaw - FacePanelUseCase.yawOf(f, a.yaw)).deg()) } }
                s.copy(eye = e.eye ?: s.eye, headYawDeg = yaw ?: s.headYawDeg, headOk = e.ok)
            }
            is PfEvent.LivePose -> PfReducer.livePose(s, e.id, e.pose)
            is PfEvent.StickActive -> if (s.stickActive) s else s.copy(stickActive = true)
            is PfEvent.StickReleased -> PfReducer.stickReleased(s, e.id, e.pose)
            is PfEvent.DragStart -> s.copy(dragging = e.id)
            is PfEvent.DragReleased -> PfReducer.dragReleased(s, e.id, e.center).also { sound(SoundKind.SNAP) }
            is PfEvent.Resized -> PfReducer.resized(s, e.id)
            is PfEvent.Roles -> s.copy(roles = e.roles.map { r -> r.copy(renderedSp = renderedSp[r.role]) }, fontScale = e.fontScale)
            is PfEvent.Rendered -> {
                renderedSp[e.role] = e.sp
                s.copy(roles = s.roles.map { if (it.role == e.role) it.copy(renderedSp = e.sp) else it })
            }
            is PfEvent.Window ->
                when (e.lifecycle) {
                    "ON_CREATE" -> PfReducer.windowOpened(s)
                    "ON_DESTROY" -> PfReducer.windowClosed(s)
                    else -> s
                }
            PfEvent.Background -> {
                persistNow(s)
                s
            }
            PfEvent.Foreground -> s
            is PfEvent.Tick -> if (s.toast != null && e.nowMs >= s.toast.untilMs) s.copy(toast = null) else s
        }

    /** Last head forward the scene reported (for "back to the front"). */
    private var headForward: io.embodit.panelfit.domain.model.Vec3? = null

    private fun needsDerive(before: PfState, after: PfState, e: PfEvent): Boolean =
        e is PfEvent.Head || e is PfEvent.Ready || before.specimens != after.specimens || before.anchor != after.anchor || before.settings != after.settings ||
            before.windowYawDeg != after.windowYawDeg || e is PfEvent.FrontSettled || before.roles != after.roles || before.fontScale != after.fontScale

    private fun persistable(before: PfState, after: PfState): Boolean =
        before.specimens != after.specimens || before.settings != after.settings || before.overlays != after.overlays || before.lastSaved != after.lastSaved

    // -- import / save -------------------------------------------------------------------------------------------

    private fun importPick(s: PfState, name: String, now: Long): PfState {
        val f = java.io.File(files.importDir, name)
        val info = if (f.isFile) files.bounds(f) else null
        if (info == null || !info.ok || files.decode(name, 64) == null) {
            PfLog.i("PF-IMPORT bad name=$name")
            sound(SoundKind.FAIL)
            return s.copy(toast = Toast(Toast.Kind.IMPORT_BAD, name, now + PfState.TOAST_MS), page = Page.LIBRARY, sub = Sub.IMPORT, importFiles = files.listImports())
        }
        PfLog.i("PF-IMPORT ok name=$name px=${info.wPx}x${info.hPx}")
        return PfReducer.add(s, Template.T8, info)
    }

    private fun save(s: PfState, now: Long): PfState {
        val sp = s.selectedSpecimen ?: return s
        val r = s.readings[sp.id]?.takeIf { s.headOk }
        val sentence = r?.let { SpecText.sentence(false, sp, it) } ?: io.embodit.panelfit.domain.usecase.Copy.noHead(false)
        val json =
            SpecText.json(
                sp, r, s.settings, s.mine, s.overlaps, sentence,
                sdkVersion = "Spatial SDK 6.1.9",
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                fontScale = s.fontScale,
            ).toString(2)
        val md = SpecText.markdown(sp, r, s.settings, s.overlaps, sentence)
        val base = SpecText.fileBase(sp, Date())
        scope.launch {
            val result = withContext(Dispatchers.IO) { files.save(base, json, md) }
            val t = SystemClock.elapsedRealtime() + PfState.TOAST_MS
            val next =
                when (result) {
                    is SaveResult.Public -> _state.value.copy(toast = Toast(Toast.Kind.SAVED_PUBLIC, result.path, t), lastSaved = result.path)
                    is SaveResult.Private -> _state.value.copy(toast = Toast(Toast.Kind.SAVED_PRIVATE, result.dir, t), lastSaved = result.path)
                    SaveResult.None -> _state.value.copy(toast = Toast(Toast.Kind.SAVED_NONE, "", t))
                }
            PfLog.i("PF-SAVE result=${result::class.simpleName} path=${(result as? SaveResult.Public)?.path ?: (result as? SaveResult.Private)?.path ?: "-"}")
            sound(if (result == SaveResult.None) SoundKind.FAIL else SoundKind.SAVED)
            _state.value = next
            persistSoon()
            logState(next, PfEvent.Save)
        }
        return s
    }

    // -- persistence ---------------------------------------------------------------------------------------------

    private fun persistSoon() {
        persistJob?.cancel()
        persistJob =
            scope.launch {
                delay(400)
                persistNow(_state.value)
            }
    }

    private fun persistNow(s: PfState) {
        runCatching { store.save(Saved(s.specimens, s.nextId, s.settings, s.overlays, s.lastSaved)) }.onFailure { PfLog.w("STORE save failed: $it") }
    }

    private fun sound(kind: SoundKind) {
        if (_state.value.settings.sound) _effects.tryEmit(PfEffect.Sound(kind))
    }

    // -- logs ----------------------------------------------------------------------------------------------------

    private fun logState(s: PfState, e: PfEvent) {
        val name = stateName(s)
        if (name != lastStateName) {
            lastStateName = name
            PfLog.i("PF-STATE $name via=${e::class.simpleName}")
        }
    }

    /** PF-METRIC: one line per placement or parameter change (not for head-only updates). */
    private fun logMetrics(before: PfState, after: PfState, e: PfEvent) {
        if (e is PfEvent.Head) return
        for (sp in after.specimens) {
            val old = before.specimens.firstOrNull { it.id == sp.id }
            if (old == sp && e !is PfEvent.Ready && e !is PfEvent.Roles) continue
            val r = after.readings[sp.id] ?: continue
            val body = r.styles.firstOrNull { it.role.role == "bodyMedium" }
            val line =
                String.format(
                    Locale.US,
                    "id=%d t=%s dp=%dx%d scale=%.2f dist=%.3f bearing=%.1f elev=%.1f ang=%.1fx%.1f eq=%dx%d facingOff=%.1f reach=%s neck=%s bodyMedium=%s",
                    sp.id, sp.template.id, sp.wDp, sp.hDp, sp.scale, r.dist, r.bearingDeg, r.elevDeg, r.angW, r.angH, r.eqW.roundToInt(), r.eqH.roundToInt(),
                    r.facingOff, r.reach, r.neck, body?.let { String.format(Locale.US, "%.1fsp/%.1f'", it.role.usedSp, it.arcmin) } ?: "-",
                )
            if (lastMetric[sp.id] != line) {
                lastMetric[sp.id] = line
                PfLog.i("PF-METRIC $line")
            }
        }
    }

    companion object {
        /** Design state name: base[+overlays][:template], e.g. "S04+S05:T1", "S08+S11". */
        fun stateName(s: PfState): String {
            val base =
                when {
                    s.phase == Phase.PREPARING -> "S00"
                    s.phase == Phase.WELCOME -> "S01"
                    s.confirm != null -> "S13b:" + s.confirm.name.lowercase(Locale.US)
                    s.page == Page.SETTINGS -> "S13"
                    s.sub == Sub.LIST -> "S12"
                    s.sub == Sub.IMPORT -> if (s.toast?.kind == Toast.Kind.IMPORT_BAD) "S15:error" else "S15"
                    s.page == Page.LIBRARY -> if (s.full) "S02:full" else "S02"
                    s.page == Page.ADJUST -> if (s.selected == null) "S04:none" else "S04"
                    s.page == Page.RATE -> if (s.selected == null) "S07:none" else "S07"
                    else -> if (s.selected == null) "S08:none" else if (s.toast?.isSave == true) "S09" else "S08"
                }
            val extra = StringBuilder()
            if (s.stickActive) extra.append("+S05")
            if (s.dragging != null) extra.append("+S06")
            if ((s.overlays.reach || s.overlays.neck || s.overlays.ruler || s.settings.frames) && s.page != Page.RATE) extra.append("+S10")
            if (s.overlaps.isNotEmpty()) extra.append("+S11")
            if (s.inside.isNotEmpty()) extra.append("+S14")
            if (!s.headOk) extra.append("+S16")
            if (s.settings.language == Language.EN) extra.append("+S17")
            if (s.toast?.kind == Toast.Kind.RESTORED) extra.append("+S18")
            if (s.blocksWindow.isNotEmpty()) extra.append("+S19")
            val tail = s.selectedSpecimen?.let { ":" + it.template.id } ?: ""
            return base + extra + tail
        }

    }

    /** For the debug dump. */
    fun readingsFor(id: Int): String? = _state.value.readings[id]?.let { r -> "dist=${r.dist} ang=${r.angW}x${r.angH} eq=${r.eqW}" }

    fun confirmOf(): Confirm? = _state.value.confirm
}
