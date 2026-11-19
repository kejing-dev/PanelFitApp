package io.embodit.panelfit.ui.pf.scene

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.System
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import io.embodit.panelfit.domain.model.BodyAnchor
import io.embodit.panelfit.domain.model.PlaceMode
import io.embodit.panelfit.domain.model.PolarPose
import io.embodit.panelfit.domain.model.Quat4
import io.embodit.panelfit.domain.model.Specimen
import io.embodit.panelfit.domain.model.Vec3
import io.embodit.panelfit.domain.usecase.DragLine
import io.embodit.panelfit.domain.usecase.FacePanelUseCase
import io.embodit.panelfit.domain.usecase.PanelFacing
import io.embodit.panelfit.domain.usecase.PfReducer
import io.embodit.panelfit.domain.usecase.PfState
import io.embodit.panelfit.domain.usecase.Phase
import io.embodit.panelfit.domain.usecase.Placement
import io.embodit.panelfit.domain.usecase.Rect3
import io.embodit.panelfit.domain.usecase.StickAxis
import io.embodit.panelfit.domain.usecase.StickMap
import io.embodit.panelfit.domain.usecase.ViewPose
import io.embodit.panelfit.domain.usecase.deg
import io.embodit.panelfit.domain.usecase.rad
import io.embodit.panelfit.platform.PfGraph
import io.embodit.panelfit.platform.PfLog
import io.embodit.panelfit.platform.tracking.HeadPose
import io.embodit.panelfit.ui.pf.PfEvent
import io.embodit.panelfit.ui.pf.PfShared
import io.embodit.panelfit.ui.pf.debug.PfProbe
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** The framework instantiates the system itself, so the running scene is reached through this holder. */
internal object ActiveScene {
    @Volatile var scene: PfScene? = null
}

/** The one per-frame ECS system of the app: head, stick, panel poses and overlays run here, not in Compose. */
class PfFrameSystem : System() {
    override fun update(context: SceneUpdateContext) {
        ActiveScene.scene?.onFrame(context)
    }
}

/**
 * The Stage side: specimen panels (with the resize swap), their size labels, the "open panel" chip, the overlays, the
 * head and stick. Compose state arrives through [render]; per-frame work happens in [onFrame] only; the scene talks to
 * the model only through [sink] (at most 5 Hz for head and live poses). Main thread only; everything started in
 * [attach] stops in [detach].
 */
class PfScene(private val sink: (PfEvent) -> Unit) : PfProbe.SceneProbe {
    /** Panel key -> in view: a pooled panel composes its content only then (PanelRig rule 3). */
    val panelShown = mutableStateMapOf<String, Boolean>()

    /** Bumped to make `SpatialView.update` run again while panel entities are still missing. */
    val bindTick = mutableIntStateOf(0)

    /** Attachment panels the Stage declares, in order; at most two new ones per frame (design: panel pool <= 2 / frame). */
    val declared = mutableStateListOf<String>()

    private val main = Handler(Looper.getMainLooper())
    private val headTracker = PfGraph.head
    private val controller = PfGraph.controller

    private var content: SpatialViewContent? = null
    private var root: Entity? = null
    private var shared: SharedResources? = null
    private var rig: PanelRig? = null
    private var specimens: SpecimenRig? = null
    private var overlays: OverlayViews? = null
    private var sensing = false

    private var ui = PfState()

    // head
    private var head: HeadPose? = null
    private var override: HeadPose? = null
    private var frozen = false
    private var validRun = 0
    private var lastSample: Vec3? = null
    private var startMs = 0L
    private var readySent = false
    private var readyFallback = false
    private var readyMs = 0L
    private val startupLogs = longArrayOf(1_000L, 3_000L, 5_000L)
    private var startupLogged = 0
    private var lastHeadSentMs = 0L
    private var sentEye: Vec3? = null
    private var sentYaw = Float.NaN
    private var sentOk = true
    private var lastTickMs = 0L
    private var lastFrameMs = 0L

    // stick
    private var stickLocked = StickAxis.NONE
    private var stickId: Int? = null
    private var stickPose: PolarPose? = null
    private var lastLiveMs = 0L
    private var injectX = 0f
    private var injectY = 0f
    private var injectUntilMs = 0L

    // drag on the label: the system pointer hits a collider in front of the label; the gesture sits on the Stage view
    private var dragId: Int? = null
    private var contentProbe: Entity? = null
    private val labelHits = arrayOfNulls<Entity>(PfState.MAX_SPECIMENS)
    private val labelHitK = FloatArray(PfState.MAX_SPECIMENS)
    private val labelHitShown = BooleanArray(PfState.MAX_SPECIMENS)
    private var hitSpecimen: Int? = null
    private var lastTested: Any? = null
    private var dragTotalPx = Vec3.ZERO
    private var dragPrevHand = Vec3.ZERO
    private var dragDir: Vec3? = null
    private var dragDist = 0f
    private var dragDepth = 0f
    private var dragBegun = false
    private var dragMoves = 0

    /**
     * Debug builds only: a file `files/hold_prep` (written by `adb shell run-as`) holds the "preparing" page (S00) for
     * 12 s after start, so the QA screenshot can catch it; the emulator's window animation outlasts the real ~2 s.
     */
    private val holdPrepUntil: Long =
        if ((PfGraph.appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
            java.io.File(PfGraph.appContext.filesDir, "hold_prep").exists()
        ) SystemClock.elapsedRealtime() + 12_000L else 0L
    private var dragCenter: Vec3? = null
    private var dragWorldYaw = 0f
    private var dragPose: PolarPose? = null

    // resize: committed size per specimen and when its size last changed
    private val committed = HashMap<Int, Pair<Int, Int>>()
    private val sizeChangedMs = HashMap<Int, Long>()
    private val lastSize = HashMap<Int, Pair<Int, Int>>()

    // label slots: specimen id -> label pool index
    private val labelSlot = HashMap<Int, Int>()

    // =========================================================================================
    // lifecycle
    // =========================================================================================

    fun attach(content: SpatialViewContent, attachments: SpatialViewAttachments) {
        if (root != null) detach()
        this.content = content
        val r = Entity().named("stageRoot")
        content.addEntity(r)
        // An entity added to the SpatialView is not at the Stage origin: move the root once so that its local space
        // equals Stage space (Spatial Ruler).
        val offset = r.convertPositionFrom(Vector3.ZERO, null)
        val rotation = r.convertRotationFrom(Quat.identity(), null)
        r.components[TransformComponent::class.java]?.apply {
            setPosition(offset)
            setQuaternion(rotation)
        }
        root = r
        PfLog.i("STAGE root offset=${PfLog.pos(offset.domain())}")
        contentProbe = Entity().named("contentProbe").also { content.addEntity(it) }
        for (i in 0 until PfState.MAX_SPECIMENS) {
            labelHits[i] = Entity().named("hit:label$i").also { e ->
                r.addChild(e)
                e.enabled = false
            }
            labelHitK[i] = 0f
            labelHitShown[i] = false
        }
        val panelRig = PanelRig(r) { id, shown -> panelShown[id] = shown }
        for (i in 0 until PfState.MAX_SPECIMENS) panelRig.world(labelId(i), LABEL_W, LABEL_H).autoFace = false
        panelRig.world(CHIP, CHIP_W, CHIP_H).autoFace = false
        rig = panelRig
        specimens = SpecimenRig(r) { key, shown -> panelShown[key] = shown }
        val sh = SharedResources()
        shared = sh
        runCatching { overlays = OverlayViews(r, sh) }.onFailure { PfLog.e("SCENE overlays failed: $it", it) }
        startSensing()
        startMs = SystemClock.elapsedRealtime()
        ActiveScene.scene = this
        bindPanels(attachments)
        PfLog.i("SCENE attached")
    }

    private fun startSensing() {
        if (sensing) return
        sensing = true
        headTracker.start()
        controller.start()
    }

    private fun stopSensing() {
        if (!sensing) return
        sensing = false
        headTracker.stop()
        controller.stop()
    }

    /** Leaving for the background stops the trackers and saves; coming back waits for a valid head again (S18). */
    fun onBackground() {
        stopSensing()
        PfLog.i("SENSING stopped (background)")
    }

    fun onForeground() {
        if (root == null) return
        startSensing()
        validRun = 0
        readySent = false
        readyFallback = false
        startMs = SystemClock.elapsedRealtime()
        startupLogged = 0
        PfLog.i("SENSING started (foreground)")
    }

    /** Called from `SpatialView(update = ...)`: parents freshly composed panel entities under their holders. */
    fun bindPanels(attachments: SpatialViewAttachments) {
        val rig = rig ?: return
        val spec = specimens ?: return
        val now = SystemClock.elapsedRealtime()
        val labelIds = declared.filter { it.startsWith(LABEL) || it == CHIP }
        var complete = rig.bind(attachments, labelIds, now)
        for (key in declared) {
            if (!key.startsWith(SPEC)) continue
            val id = key.substringAfter(':').substringBefore(':').toIntOrNull() ?: continue
            if (!spec.bind(attachments, id, key, now)) complete = false
        }
        if (!complete) {
            main.removeCallbacks(rebind)
            main.postDelayed(rebind, REBIND_DELAY_MS)
        }
    }

    private val rebind = Runnable { if (root != null) bindTick.intValue++ }

    fun detach() {
        if (ActiveScene.scene === this) ActiveScene.scene = null
        main.removeCallbacksAndMessages(null)
        stopSensing()
        panelShown.clear()
        runCatching { rig?.release() }
        runCatching { specimens?.release() }
        runCatching { overlays?.destroy() }
        runCatching { root?.destroy(true) }
        runCatching { contentProbe?.destroy(true) }
        contentProbe = null
        labelHits.fill(null)
        dragId = null
        hitSpecimen = null
        shared?.close()
        shared = null
        root = null
        rig = null
        specimens = null
        overlays = null
        content = null
        PfLog.i("SCENE detached")
    }

    // =========================================================================================
    // model -> scene
    // =========================================================================================

    fun render(state: PfState) {
        val now = SystemClock.elapsedRealtime()
        val spec = specimens
        val ids = state.specimens.map { it.id }.toSet()
        if (spec != null) for (gone in spec.ids() - ids) {
            spec.remove(gone)
            committed.remove(gone)
            labelSlot.remove(gone)
        }
        for (sp in state.specimens) {
            val size = sp.wDp to sp.hDp
            if (lastSize[sp.id] != size) {
                lastSize[sp.id] = size
                sizeChangedMs[sp.id] = now
            }
            if (sp.id !in state.resizing) committed[sp.id] = size
            else if (committed[sp.id] == null) committed[sp.id] = size
            if (sp.id !in labelSlot) {
                val used = labelSlot.values.toSet()
                (0 until PfState.MAX_SPECIMENS).firstOrNull { it !in used }?.let { labelSlot[sp.id] = it }
            }
        }
        labelSlot.keys.retainAll(ids)
        if (stickId != null && stickId !in ids) stickId = null
        if (dragId != null && dragId !in ids) dragId = null
        ui = state
    }

    /** Keys the Stage should declare now: chip, the six labels, every specimen's committed panel and in-flight ones. */
    private fun wantedKeys(): List<String> {
        val out = ArrayList<String>()
        out += CHIP
        for (i in 0 until PfState.MAX_SPECIMENS) out += labelId(i)
        val live = specimens?.liveKeys() ?: emptySet()
        for (sp in ui.specimens) {
            val (w, h) = committed[sp.id] ?: (sp.wDp to sp.hDp)
            out += SpecimenRig.key(sp.id, w, h)
        }
        for (k in live) if (k !in out) out += k
        return out
    }

    /** At most two new panels per frame; panels nobody wants any more are dropped at once. */
    private fun syncDeclared() {
        val wanted = wantedKeys()
        val drop = declared.filter { it !in wanted }
        if (drop.isNotEmpty()) declared.removeAll(drop)
        var added = 0
        for (k in wanted) {
            if (added >= NEW_PANELS_PER_FRAME) break
            if (k !in declared) {
                declared += k
                added++
            }
        }
        if (added > 0) bindTick.intValue++
    }

    // =========================================================================================
    // frame
    // =========================================================================================

    fun onFrame(context: SceneUpdateContext) {
        val r = root ?: return
        if (r.scene == null || context.scene != r.scene) return
        val now = SystemClock.elapsedRealtime()
        val dt = if (lastFrameMs == 0L) 0f else ((now - lastFrameMs) / 1000f).coerceIn(0f, 0.1f)
        lastFrameMs = now
        readHead(now)
        syncDeclared()
        rig?.onFrame(now)
        specimens?.onFrame(now)
        resizeFrame(now)
        val state = ui
        val anchor = state.anchor
        val view = viewPose()
        if (anchor != null) {
            stickFrame(state, now, dt)
            placeSpecimens(state, anchor, view)
            placeChip(state, anchor, view)
            overlaysFrame(state, anchor, view)
        }
        view?.let { rig?.stowBehind(ViewPose(it.position, it.rotation)) }
        headEvents(now)
        frontFrame(state, now)
        if (now - lastTickMs >= TICK_MS) {
            lastTickMs = now
            sink(PfEvent.Tick(now))
        }
        startupLog(now, state, anchor, view)
    }

    private fun viewPose(): HeadPose? = override ?: head

    /** Milliseconds since the Stage's last frame, or null before its first (the window level gate's watchdog). */
    fun frameAgeMs(now: Long): Long? = if (lastFrameMs == 0L) null else now - lastFrameMs

    /** Head pitch for the window level gate, degrees (+ up); null while there is no valid head pose yet. */
    fun levelPitchDeg(): Float? =
        viewPose()?.forward?.y?.coerceIn(-1f, 1f)?.let { Math.toDegrees(kotlin.math.asin(it.toDouble())).toFloat() }

    /**
     * Head pose. The first frames can be all zero or the identity at the Stage origin (factory lesson): a pose counts
     * once three samples in a row are non-zero and move less than 1 cm; after 2 s without one the app goes on with a
     * guess and S16 (the panel still works).
     */
    private fun readHead(now: Long) {
        if (override != null) {
            if (!readySent) ready(override!!, true, now)
            return
        }
        if (frozen && readySent) return
        val p = headTracker.pose()
        val valid = p != null && p.position.isFinite() && p.position.length > 1e-3f && !(p.rotation == Quat4.IDENTITY && p.position.y < 0.3f)
        if (valid) {
            val prev = lastSample
            validRun = if (prev != null && prev.distanceTo(p!!.position) < 0.01f) validRun + 1 else 1
            lastSample = p!!.position
            head = p
        }
        if (!readySent) {
            if (holdPrepUntil > now) return // debug builds only: keeps S00 on screen for the QA shot (see holdPrepUntil)
            if (validRun >= VALID_RUN) ready(head!!, true, now)
            else if (now - startMs > HEAD_WAIT_MS) {
                val guess = head ?: HeadPose(Vec3(0f, 1.5f, 0f), Quat4.IDENTITY)
                head = guess
                readyFallback = true
                ready(guess, false, now)
            }
        } else if (readyFallback && validRun >= VALID_RUN) {
            readyFallback = false
            val h = head!!
            PfLog.i("HEAD valid after the fallback: back to the front")
            sink(PfEvent.Head(h.position, h.forward, true))
            sink(PfEvent.FrontSettled(h.position, h.forward))
        }
    }

    private fun ready(p: HeadPose, ok: Boolean, now: Long) {
        readySent = true
        readyMs = now
        startupLogged = 0
        PfLog.i("HEAD ready ok=$ok eye=${PfLog.pos(p.position)} fwd=${PfLog.pos(p.forward)} after_ms=${now - startMs}")
        sink(PfEvent.Ready(p.position, p.forward, ok))
        sentEye = p.position
        sentYaw = FacePanelUseCase.yawOf(p.forward, 0f)
        sentOk = ok
    }

    /** At most 5 Hz, and only when the head moved 1 cm / 0.5 degrees or tracking changed. */
    private fun headEvents(now: Long) {
        if (!readySent) return
        val p = viewPose() ?: return
        val ok = !headTracker.forceLost && !readyFallback
        val yaw = FacePanelUseCase.yawOf(p.forward, sentYaw.takeIf { !it.isNaN() } ?: 0f)
        val moved = sentEye?.let { it.distanceTo(p.position) > 0.01f } ?: true
        val turned = sentYaw.isNaN() || abs(yaw - sentYaw) > 0.5f.rad()
        if ((moved || turned || ok != sentOk) && now - lastHeadSentMs >= HEAD_EVENT_MS) {
            lastHeadSentMs = now
            sentEye = p.position
            sentYaw = yaw
            sentOk = ok
            sink(PfEvent.Head(p.position, p.forward, ok))
        }
    }

    // -- "回到面前" waits for the head to rest (D29) ---------------------------------------------------------------

    private val restYaw = ArrayDeque<Pair<Long, Float>>()
    private var restPos: Vec3? = null
    private var restSince: Long? = null

    /**
     * After the press, wait at least [FRONT_MIN_MS], then for the head's yaw to stay within 1.5 degrees (and its
     * position within 2 cm) for [FRONT_REST_MS]; after [FRONT_MAX_MS] take the head as it is.
     */
    private fun frontFrame(state: PfState, now: Long) {
        val asked = state.pendingFrontMs
        if (asked == null) {
            restSince = null
            restYaw.clear()
            return
        }
        val p = viewPose() ?: return
        val yaw = FacePanelUseCase.yawOf(p.forward, 0f).deg()
        if (restSince != asked) {
            restSince = asked
            restYaw.clear()
            restPos = p.position
        }
        if ((restPos?.distanceTo(p.position) ?: 0f) > 0.02f) {
            restYaw.clear()
            restPos = p.position
        }
        restYaw.addLast(now to yaw)
        while (restYaw.size > 1 && now - restYaw.first().first > FRONT_REST_MS) restYaw.removeFirst()
        val sinceAsk = now - asked
        val yaws = restYaw.map { it.second }
        val steady = restYaw.size >= 2 && now - restYaw.first().first >= FRONT_REST_MS * 0.8f && (yaws.max() - yaws.min()) < 1.5f
        if ((sinceAsk >= FRONT_MIN_MS && steady) || sinceAsk >= FRONT_MAX_MS) {
            PfLog.i("FRONT settled after_ms=$sinceAsk yaw=${PfLog.f(yaw, 1)} steady=$steady")
            restSince = null
            restYaw.clear()
            sink(PfEvent.FrontSettled(p.position, p.forward))
        }
    }

    // -- stick ----------------------------------------------------------------------------------------------------

    private fun stickFrame(state: PfState, now: Long, dt: Float) {
        val sp = state.selectedSpecimen
        val (x, y) = if (now < injectUntilMs) injectX to injectY else controller.stick()
        val out = StickMap.map(x, y, stickLocked)
        val active = stickId
        if (sp == null || sp.mode != PlaceMode.PLACING || dragId != null) {
            if (active != null) endStick()
            return
        }
        if (out.axis == StickAxis.NONE) {
            if (active != null) endStick()
            return
        }
        if (active != sp.id) {
            stickId = sp.id
            stickPose = sp.pose
            stickLocked = out.axis
            PfLog.i("STICK start id=${sp.id} axis=${out.axis} x=${PfLog.f(x)} y=${PfLog.f(y)}")
            sink(PfEvent.StickActive(sp.id))
        }
        stickLocked = out.axis
        val pose = PfReducer.stickStep(stickPose ?: sp.pose, out, dt, sp.widthM)
        stickPose = pose
        if (now - lastLiveMs >= LIVE_MS) {
            lastLiveMs = now
            sink(PfEvent.LivePose(sp.id, pose))
        }
    }

    private fun endStick() {
        val id = stickId ?: return
        val pose = stickPose
        stickId = null
        stickPose = null
        stickLocked = StickAxis.NONE
        PfLog.i("STICK release id=$id dist=${pose?.let { PfLog.f(it.dist) }} facing=${pose?.let { PfLog.f(it.facingDeg, 1) }}")
        if (pose != null) sink(PfEvent.StickReleased(id, pose))
    }

    // -- drag (I5: pinch / trigger on the size label) -----------------------------------------------------------------

    /**
     * Label i's collider: a box a little larger than the label, 3 cm in front of it (toward the eye), so the system
     * pointer's ray hits it before the label panel. It is rebuilt only when the label's scale changes by more than 8 %.
     */
    private fun placeLabelHit(i: Int, at: Vec3, yaw: Float, k: Float, eye: Vec3) {
        val e = labelHits[i] ?: return
        if (labelHitK[i] == 0f || abs(k / labelHitK[i] - 1f) > HIT_REBUILD) {
            // a little larger than the label: at the label's own size a press on its top or bottom 15 % missed
            val size = Vec3(LABEL_W * PanelRig.DP_M * k * HIT_GROW_W, LABEL_H * PanelRig.DP_M * k * HIT_GROW_H, HIT_DEPTH_M)
            runCatching { e.components.set(CollisionComponent(listOf(ShapeResource.createBox(size.sdk())), PhysicsMaterialResource())) }
                .onFailure { PfLog.e("HIT label$i collider failed: $it", it) }
            // the interactable goes on after the collider, as on MRI rehearsal's hit boxes
            runCatching { e.components.set(InteractableComponent()) }
            PfLog.i("HIT label$i collider size=${PfLog.pos(size)}")
            labelHitK[i] = k
        }
        val toEye = (eye - at).let { Vec3(it.x, 0f, it.z) }.normalized()
        e.setLocal(position = at + toEye * HIT_FRONT_M, rotation = PanelFacing(yaw, 0f).rotation)
        if (!labelHitShown[i]) {
            e.enabled = true
            labelHitShown[i] = true
        }
    }

    private fun hideLabelHit(i: Int) {
        if (!labelHitShown[i]) return
        labelHits[i]?.enabled = false
        labelHitShown[i] = false
    }

    /** Gesture filter on the Stage view: accepts the label colliders and records which specimen was hit. */
    fun acceptLabelHit(e: Entity): Boolean {
        val i = labelHits.indexOfFirst { it != null && it.id == e.id }
        if (e.id != lastTested) {
            lastTested = e.id
            val n = runCatching { e.getName() }.getOrNull()
            PfLog.i("HIT test name=$n label=$i")
        }
        if (i < 0) return false
        hitSpecimen = labelSlot.entries.firstOrNull { it.value == i }?.key
        return hitSpecimen != null
    }

    /** A press on a label without a drag selects its specimen (the label used to take this press itself). */
    fun onLabelTap() {
        val id = hitSpecimen ?: return
        PfLog.i("TAP label id=$id")
        sink(PfEvent.Select(id))
    }

    /**
     * The drag on the label collider, from the Stage view's `detectSpatialDragGesture` (which does not move, so the
     * gesture's increments are the hand's own) or from the debug `drag` command, which feeds increments of the same
     * kind. [amountPx] is the increment in view-local pixels; their running sum is converted to Stage metres, and the
     * specimen follows the pointing line at the distance it had when grabbed (DragLine). Its yaw is frozen while held
     * and turns to the viewer on release (profile D21).
     */
    fun onLabelGesture(phase: String, amountPx: Vec3) {
        val state = ui
        val anchor = state.anchor ?: return
        when (phase) {
            "start" -> {
                val id = hitSpecimen ?: return
                val sp = state.specimens.firstOrNull { it.id == id } ?: return
                if (state.selected != id || sp.mode != PlaceMode.PLACING) sink(PfEvent.Select(id))
                val w = Placement.world(anchor, sp.pose)
                val view = viewPose()
                val eye = view?.position ?: anchor.eye
                dragCenter = w.center
                dragWorldYaw = w.facing.yaw
                dragPose = sp.pose
                dragTotalPx = Vec3.ZERO
                dragPrevHand = Vec3.ZERO
                dragDir = null
                dragBegun = false
                dragMoves = 0
                dragDist = eye.distanceTo(w.center)
                val labelAt = labelSlot[id]?.let { labelHits[it] }?.let { runCatching { it.convertPositionTo(Vector3.ZERO, root).domain() }.getOrNull() } ?: w.center
                val pitch = view?.forward?.y?.coerceIn(-1f, 1f)?.let { Math.toDegrees(kotlin.math.asin(it.toDouble())).toFloat() } ?: 0f
                dragDepth = DragLine.equivalentDepth(eye.distanceTo(labelAt), pitch)
                dragId = id
                PfLog.i("DRAG start id=$id center=${PfLog.pos(w.center)} dist=${PfLog.f(dragDist)} depth=${PfLog.f(dragDepth)}")
            }
            "move" -> {
                val id = dragId ?: return
                val view = viewPose() ?: return
                dragMoves++
                dragTotalPx += amountPx
                val hand = handMetres(dragTotalPx) ?: return
                if (!dragBegun) {
                    if (hand.length < DRAG_BEGIN_M) return
                    val c = dragCenter ?: return
                    val to = c - view.position
                    if (to.length < 1e-3f) return
                    dragBegun = true
                    dragDir = to.normalized()
                    dragDist = to.length
                    dragPrevHand = hand
                    PfLog.i("DRAG begin id=$id dist=${PfLog.f(dragDist)}")
                    // S06 (and the release's snap) only once the hand has moved: a tap is a select, not a drag
                    sink(PfEvent.DragStart(id))
                    return
                }
                val dir = dragDir ?: return
                val turned = DragLine.turn(dir, hand - dragPrevHand, dragDepth, view.right, view.up, view.forward)
                dragPrevHand = hand
                dragDir = turned
                val next = view.position + turned * dragDist
                dragCenter = next
                val pose = Placement.polar(anchor, next, dragPose ?: return)
                dragPose = pose
                if (dragMoves % 10 == 1) PfLog.i("DRAG move id=$id center=${PfLog.pos(next)}")
                val now = SystemClock.elapsedRealtime()
                if (now - lastLiveMs >= LIVE_MS) {
                    lastLiveMs = now
                    sink(PfEvent.LivePose(id, pose))
                }
            }
            else -> {
                val id = dragId ?: return
                val c = dragCenter
                val moved = dragBegun
                dragId = null
                dragCenter = null
                dragPose = null
                dragDir = null
                dragBegun = false
                PfLog.i("DRAG $phase id=$id moved=$moved center=${PfLog.pos(c)}")
                if (moved && c != null) sink(PfEvent.DragReleased(id, c))
            }
        }
    }

    /** View-local pixels of the gesture -> Stage metres of hand travel (Spatial Ruler / MRI rehearsal). */
    private fun handMetres(px: Vec3): Vec3? {
        val moved = pxToStage(px) ?: return null
        val origin = pxToStage(Vec3.ZERO) ?: return null
        return moved - origin
    }

    private fun pxToStage(px: Vec3): Vec3? {
        val c = content ?: return null
        val probe = contentProbe ?: return null
        return runCatching {
                val local = c.convertPosition(px.sdk(), ViewCoordinateSpace.Local, c.localSpatialCoordinateSpace)
                probe.convertPositionTo(local, null).domain()
            }
            .getOrNull()
            ?.takeIf { it.isFinite() }
    }

    private fun stageToPx(p: Vec3): Vec3? {
        val c = content ?: return null
        val probe = contentProbe ?: return null
        return runCatching {
                val local = probe.convertPositionFrom(p.sdk(), null)
                c.convertPosition(local, c.localSpatialCoordinateSpace, ViewCoordinateSpace.Local).domain()
            }
            .getOrNull()
            ?.takeIf { it.isFinite() }
    }

    // -- placing --------------------------------------------------------------------------------------------------

    private fun placeSpecimens(state: PfState, anchor: BodyAnchor, view: HeadPose?) {
        val spec = specimens ?: return
        val rig = rig ?: return
        val stow = rig.let { (view?.position ?: anchor.eye) - Placement.direction(BodyAnchor(anchor.eye, FacePanelUseCase.yawOf(view?.forward ?: Vec3.AHEAD, anchor.yaw)), 0f) * 30f }
        val usedLabels = HashSet<Int>()
        val eye = view?.position ?: anchor.eye
        for (sp in state.specimens) {
            val pose = livePose(sp)
            var world = Placement.world(anchor, pose)
            if (dragId == sp.id) world = world.copy(facing = PanelFacing(dragWorldYaw, world.facing.pitch))
            val visible = !sp.hidden
            spec.place(sp.id, world.center, world.rotation, sp.scale, visible, stow)
            spec.fade(sp.id, visible && (sp.id in state.inside || sp.id in state.blocksWindow))
            val slotIndex = labelSlot[sp.id] ?: continue
            val label = rig[labelId(slotIndex)] ?: continue
            usedLabels += slotIndex
            if (!visible) {
                label.hide()
                hideLabelHit(slotIndex)
                continue
            }
            val dist = eye.distanceTo(world.center).coerceAtLeast(0.3f)
            val k = LABEL_SCALE_PER_M * dist
            val labelHalfH = LABEL_H * PanelRig.DP_M * k / 2f
            val at = world.center - world.facing.up * (sp.heightPanelM / 2f + LABEL_GAP_PER_M * dist + labelHalfH)
            val yaw = atan2(eye.x - at.x, eye.z - at.z)
            label.place(at, PanelFacing(yaw, 0f).rotation, k)
            placeLabelHit(slotIndex, at, yaw, k, eye)
        }
        for (i in 0 until PfState.MAX_SPECIMENS) if (i !in usedLabels) {
            rig[labelId(i)]?.hide()
            hideLabelHit(i)
        }
    }

    /** A specimen's pose this frame: the stick's or the drag's live pose while one is moving it. */
    private fun livePose(sp: Specimen): PolarPose =
        when (sp.id) {
            stickId -> stickPose ?: sp.pose
            dragId -> dragPose ?: sp.pose
            else -> sp.pose
        }

    /**
     * "打开面板": while the tool window is closed, low and ahead of the body (0.8 m, 28 degrees below the eye). While a
     * window open waits for a level head (D9) the same slot is the "平视前方" card, on the line of sight (pitch clamped
     * to ±40°, 0.75 m), upright and turned by yaw only.
     */
    private fun placeChip(state: PfState, anchor: BodyAnchor, view: HeadPose?) {
        val slot = rig?.get(CHIP) ?: return
        if (PfShared.levelHint && view != null) {
            val f = view.forward
            val yaw = kotlin.math.atan2(-f.x, -f.z)
            val pitch = kotlin.math.asin(f.y.coerceIn(-1f, 1f)).coerceIn(-LEVEL_CARD_PITCH, LEVEL_CARD_PITCH)
            val dir = Vec3(-kotlin.math.sin(yaw) * kotlin.math.cos(pitch), kotlin.math.sin(pitch), -kotlin.math.cos(yaw) * kotlin.math.cos(pitch))
            slot.place(view.position + dir * LEVEL_CARD_M, PanelFacing(yaw, 0f).rotation, 0.75f)
            return
        }
        // also when the window never came up (8 s after the head was ready): the app must stay operable
        val now = SystemClock.elapsedRealtime()
        val show = state.phase != Phase.PREPARING && !PfShared.windowOpen && (PfShared.window != "never" || (readySent && now - readyMs > CHIP_FALLBACK_MS))
        if (!show) {
            slot.hide()
            return
        }
        val w = Placement.world(anchor, Placement.ahead(0.8f, 0f, 28f))
        slot.place(w.center, PanelFacing(w.facing.yaw, 0f).rotation, 0.75f)
    }

    private fun overlaysFrame(state: PfState, anchor: BodyAnchor, view: HeadPose?) {
        val ov = overlays ?: return
        val shown = state.overlaysShown
        val sel = state.selectedSpecimen?.takeIf { !it.hidden }
        val ruler =
            if (shown.ruler && sel != null) {
                val w = Placement.world(anchor, livePose(sel)).let { if (dragId == sel.id) it.copy(facing = PanelFacing(dragWorldYaw, it.facing.pitch)) else it }
                Rect3.of(sel.id, w, sel.widthM, sel.heightPanelM)
            } else null
        val frames =
            if (state.settings.frames && state.page != io.embodit.panelfit.domain.usecase.Page.RATE) {
                // the specimen being dragged or pushed is not "taken": the frame it heads for stays visible until it snaps
                val taken = state.specimens.filter { !it.hidden && it.id != dragId && it.id != stickId }.map { sp -> livePose(sp).let { p -> Triple(p, sp.widthM, sp.heightPanelM) } }
                Placement.frames(state.windowYawDeg).filter { f -> taken.none { (p, w, h) -> Placement.overlapsSeen(f.pose, f.wM, f.hM, p, w, h) } }.map { f ->
                    val w = Placement.world(anchor, f.pose)
                    Rect3.of(0, w, f.wM, f.hM) to true
                }
            } else emptyList()
        val masks =
            state.overlaps.mapNotNull { o ->
                val back = state.specimens.firstOrNull { it.id == o.back } ?: return@mapNotNull null
                val w = Placement.world(anchor, livePose(back))
                val f = w.facing
                val c = w.center + f.right * ((o.u0 + o.u1) / 2f) + f.up * ((o.v0 + o.v1) / 2f)
                Rect3(o.back, c, f.right, f.up, f.normal, (o.u1 - o.u0) / 2f, (o.v1 - o.v0) / 2f)
            }
        ov.frame(OverlayViews.Input(anchor, view?.position, state.settings.armM, shown.reach, shown.neck, ruler, frames, masks))
    }

    /** The new size is committed after the stepper rests for 0.5 s (design `rules.placement.resize`). */
    private fun resizeFrame(now: Long) {
        for (id in ui.resizing) {
            val since = sizeChangedMs[id] ?: continue
            if (now - since >= RESIZE_REST_MS) {
                sizeChangedMs[id] = Long.MAX_VALUE / 2
                PfLog.i("RESIZE commit id=$id size=${lastSize[id]}")
                sink(PfEvent.Resized(id))
            }
        }
    }

    /** Start-up placement check (D1): 1 / 3 / 5 s after the head became ready, where everything is. */
    private fun startupLog(now: Long, state: PfState, anchor: BodyAnchor?, view: HeadPose?) {
        if (!readySent || startupLogged >= startupLogs.size) return
        if (now - readyMs < startupLogs[startupLogged]) return
        val t = startupLogs[startupLogged] / 1000
        startupLogged++
        val eye = view?.position
        val fwdYaw = view?.let { FacePanelUseCase.yawOf(it.forward, 0f) } ?: 0f
        PfLog.i("PF-STATE t=${t}s head=${PfLog.pos(eye)} yaw=${PfLog.f(fwdYaw.deg(), 1)} window=${PfShared.window} windowYaw=${state.windowYawDeg?.let { PfLog.f(it, 1) }} anchor=${anchor?.let { PfLog.pos(it.eye) + "/" + PfLog.f(it.yaw.deg(), 1) }}")
        if (anchor == null || eye == null) return
        for (sp in state.specimens) {
            val w = Placement.world(anchor, sp.pose)
            val rel = w.center - eye
            val bearing = Placement.bearing(BodyAnchor(eye, fwdYaw), eye, w.center)
            PfLog.i(
                String.format(
                    Locale.US, "PF-STATE t=%ds specimen id=%d pos=%s dist=%.2f bearing=%.1f elev=%.1f visible=%s",
                    t, sp.id, PfLog.pos(w.center), rel.length, bearing, atan2(rel.y, hypot(rel.x, rel.z)).deg(), !sp.hidden,
                ),
            )
        }
    }

    /** Label pool index of specimen [id] (the Stage composes label i for the specimen in slot i). */
    fun labelIndexOf(id: Int): Int? = labelSlot[id]

    // =========================================================================================
    // probe (debug build)
    // =========================================================================================

    override fun poseLine(): String {
        val p = viewPose()
        return "head=${PfLog.pos(p?.position)} fwd=${PfLog.pos(p?.forward)} override=${override != null} frozen=$frozen ready=$readySent fallback=$readyFallback"
    }

    override fun panelLines(): List<String> {
        val anchor = ui.anchor ?: return emptyList()
        val eye = viewPose()?.position ?: anchor.eye
        return ui.specimens.map { sp ->
            val w = Placement.world(anchor, livePose(sp))
            String.format(
                Locale.US, "id=%d t=%s key=%s center=%s dist=%.3f widthM=%.4f shown=%s",
                sp.id, sp.template.id, specimens?.currentKey(sp.id), PfLog.pos(w.center), w.center.distanceTo(eye), sp.widthM, panelShown[specimens?.currentKey(sp.id) ?: ""],
            )
        } + "declared=${declared.size} ${declared.joinToString(",")}"
    }

    override fun overrideHead(x: Float, y: Float, z: Float, yawDeg: Float, pitchDeg: Float) {
        val rot = Quat4.axisAngle(Vec3.UP, yawDeg.rad()) * Quat4.axisAngle(Vec3.RIGHT, pitchDeg.rad())
        override = HeadPose(Vec3(x, y, z), rot)
        frozen = true
        PfLog.i("CMD head override ${PfLog.pos(Vec3(x, y, z))} yaw=$yawDeg pitch=$pitchDeg")
    }

    override fun clearHeadOverride() {
        override = null
        frozen = false
    }

    override fun freeze(on: Boolean) {
        frozen = on
    }

    override fun injectStick(x: Float, y: Float, durS: Float) {
        injectX = x
        injectY = y
        injectUntilMs = SystemClock.elapsedRealtime() + (durS * 1000f).toLong()
    }

    /**
     * `drag x y z` (metres the specimen should move, the view's right / up): presses the selected specimen's label
     * collider and feeds the gesture handler view-local pixels of the hand travel that moves it that far, i.e. the
     * same increments and the same conversion as a real pinch. Depth is not dragged (the stick / window set it).
     */
    override fun injectDrag(dx: Float, dy: Float, dz: Float) {
        val id = ui.selected ?: ui.specimens.lastOrNull()?.id ?: return
        val view = viewPose() ?: return
        if (dragId != id) {
            hitSpecimen = id
            onLabelGesture("start", Vec3.ZERO)
            if (dragId != id) return
        }
        // as a hand does, the first 1.5 cm only begins the drag (it is not applied); the rest moves the specimen
        val shift = view.right * dx + view.up * dy
        val hand = DragLine.handFor(shift, dragDist, dragDepth)
        if (!dragBegun) feedHand(view.right * (DRAG_BEGIN_M * 1.5f))
        val steps = 8
        for (n in 0 until steps) feedHand(hand / steps.toFloat())
        if (dz != 0f) PfLog.i("CMD drag z=$dz ignored (depth is the stick's)")
        PfLog.i("CMD drag id=$id shift=${PfLog.pos(shift)} hand=${PfLog.pos(hand)}")
    }

    /** One gesture increment for a hand step in Stage metres (inverse of [handMetres]). */
    private fun feedHand(step: Vec3) {
        val total = handMetres(dragTotalPx) ?: Vec3.ZERO
        val target = stageToPx((pxToStage(Vec3.ZERO) ?: return) + total + step) ?: return
        onLabelGesture("move", target - dragTotalPx)
    }

    override fun releaseDrag() {
        if (dragId != null) onLabelGesture("end", Vec3.ZERO)
    }

    override fun blocked(on: Boolean) {
        headTracker.forceLost = on
    }

    companion object {
        const val CHIP = "openChip"
        const val LABEL = "label"
        const val SPEC = "spec:"
        const val LABEL_W = 320f
        const val LABEL_H = 64f
        const val CHIP_W = 220f
        private const val LEVEL_CARD_M = 0.75f
        private val LEVEL_CARD_PITCH = Math.toRadians(40.0).toFloat()
        const val CHIP_H = 64f
        const val LABEL_SCALE_PER_M = 0.75f
        const val LABEL_GAP_PER_M = 0.03f

        fun labelId(i: Int): String = "$LABEL$i"

        private const val NEW_PANELS_PER_FRAME = 2
        private const val CHIP_FALLBACK_MS = 8_000L
        private const val FRONT_MIN_MS = 700L
        private const val FRONT_REST_MS = 800L
        private const val FRONT_MAX_MS = 5_000L
        private const val REBIND_DELAY_MS = 250L
        private const val TICK_MS = 500L
        private const val VALID_RUN = 3
        private const val HEAD_WAIT_MS = 2_000L
        private const val HEAD_EVENT_MS = 200L
        private const val LIVE_MS = 200L
        private const val RESIZE_REST_MS = 500L
        private const val DRAG_BEGIN_M = 0.01f
        private const val HIT_DEPTH_M = 0.02f
        private const val HIT_FRONT_M = 0.03f
        private const val HIT_REBUILD = 0.08f
        private const val HIT_GROW_W = 1.05f
        private const val HIT_GROW_H = 1.5f
    }
}
