package io.embodit.panelfit.domain.usecase

import io.embodit.panelfit.domain.model.BodyAnchor
import io.embodit.panelfit.domain.model.Language
import io.embodit.panelfit.domain.model.Overlays
import io.embodit.panelfit.domain.model.PfSettings
import io.embodit.panelfit.domain.model.PlaceMode
import io.embodit.panelfit.domain.model.PolarPose
import io.embodit.panelfit.domain.model.Rating
import io.embodit.panelfit.domain.model.RatingEntry
import io.embodit.panelfit.domain.model.Specimen
import io.embodit.panelfit.domain.model.Surface
import io.embodit.panelfit.domain.model.Template
import io.embodit.panelfit.domain.model.Vec3
import kotlin.math.max
import kotlin.math.roundToInt

enum class Phase { PREPARING, WELCOME, READY }

/** Tool window pages (TabBar items; settings is the template's last tab). */
enum class Page { LIBRARY, ADJUST, RATE, CARD, SETTINGS }

/** Pages reached from the library with a back header (design: "← 返回 · 页名"). */
enum class Sub { NONE, LIST, IMPORT }

enum class Confirm { QUIT, CLEAR }

/** Stepper rows of the adjust page (design `rules.placement.stepper`). */
enum class Field { DIST, HEIGHT, SIDE, FACING, TILT, WIDTH, HEIGHT_DP, SCALE }

/** A message in the hint row that outlives the frame (S09 save results, S15:error, S18). */
data class Toast(val kind: Kind, val arg: String = "", val untilMs: Long) {
    enum class Kind { SAVED_PUBLIC, SAVED_PRIVATE, SAVED_NONE, IMPORT_BAD, RESTORED }

    val isSave: Boolean
        get() = kind == Kind.SAVED_PUBLIC || kind == Kind.SAVED_PRIVATE || kind == Kind.SAVED_NONE
}

/** One picture in files/import/. */
data class ImportFile(val name: String, val wPx: Int, val hPx: Int, val ok: Boolean)

/** The whole app state; the ViewModel owns one and the reducer turns events into the next one. */
data class PfState(
    val phase: Phase = Phase.PREPARING,
    val page: Page = Page.LIBRARY,
    val sub: Sub = Sub.NONE,
    val adjustPage: Int = 0,
    val cardPage: Int = 0,
    val rateGroup: Int = 3,
    val settingsPage: Int = 0,
    val specimens: List<Specimen> = emptyList(),
    val nextId: Int = 1,
    val selected: Int? = null,
    val anchor: BodyAnchor? = null,
    val eye: Vec3? = null,
    /** Bearing of the head's forward right of the body anchor, degrees (null without a head pose). */
    val headYawDeg: Float? = null,
    /** "回到面前" was pressed at this time and waits for the head to come to rest (D29). */
    val pendingFrontMs: Long? = null,
    val headOk: Boolean = true,
    val settings: PfSettings = PfSettings(),
    val overlays: Overlays = Overlays(),
    val confirm: Confirm? = null,
    val deleteAsk: Int? = null,
    val toast: Toast? = null,
    val lastSaved: String? = null,
    /** Tool window direction right of body forward when it was (re)opened; null while it is closed. */
    val windowYawDeg: Float? = 0f,
    val stickActive: Boolean = false,
    val dragging: Int? = null,
    /** Specimens whose new size waits for the 0.5 s pause before the panel is swapped. */
    val resizing: Set<Int> = emptySet(),
    val importFiles: List<ImportFile> = emptyList(),
    val readings: Map<Int, Readings> = emptyMap(),
    val overlaps: List<Overlap> = emptyList(),
    val blocksWindow: Set<Int> = emptySet(),
    val inside: Set<Int> = emptySet(),
    val roles: List<TypeRole> = emptyList(),
    val fontScale: Float = 1f,
) {
    val selectedSpecimen: Specimen?
        get() = selected?.let { id -> specimens.firstOrNull { it.id == id } }

    val full: Boolean
        get() = specimens.size >= MAX_SPECIMENS

    val english: Boolean
        get() = settings.language == Language.EN

    /** Overlays drawn right now: none on the legibility page (design `overlays.reachArc`). */
    val overlaysShown: Overlays
        get() = if (page == Page.RATE) Overlays() else overlays

    /** "Your" threshold from all self-ratings, null before five. */
    val mine: Float?
        get() = Verdicts.myThreshold(specimens.flatMap { s -> s.ratings.values.map { it.arcmin to it.rating } })

    companion object {
        const val MAX_SPECIMENS = 6
        const val TOAST_MS = 6_000L
        const val SHORT_TOAST_MS = 3_000L
    }
}

/**
 * Event -> next state. Pure: time, the head and file results come in as arguments, so the debug broadcast and the
 * real buttons run the same transitions (profile D29) and the tests drive it directly.
 */
object PfReducer {
    const val DIST_STEP = 0.05f
    const val HEIGHT_STEP = 0.05f
    const val ANGLE_STEP = 5f
    const val DP_STEP = 8
    const val SCALE_STEP = 0.05f

    // -- start-up ------------------------------------------------------------------------------------------------

    /** The head pose became valid (or the 2 s wait ran out with [eye] / [forward] as the best guess). */
    fun ready(s: PfState, eye: Vec3, forward: Vec3, headOk: Boolean, restored: Boolean, nowMs: Long): PfState {
        val anchor = Placement.placeUpright(eye, forward, s.anchor?.yaw ?: 0f)
        val phase = if (s.settings.welcomed) Phase.READY else Phase.WELCOME
        val toast = if (restored && s.specimens.isNotEmpty()) Toast(Toast.Kind.RESTORED, untilMs = nowMs + PfState.SHORT_TOAST_MS) else s.toast
        // the window opened in front of the head at start; after a return from the background it stays where it was
        val old = s.anchor
        val window = s.windowYawDeg?.let { w -> if (old == null) 0f else wrapDeg(w + (anchor.yaw - old.yaw).deg()) }
        return s.copy(phase = phase, anchor = anchor, eye = eye, headOk = headOk, windowYawDeg = window, toast = toast)
    }

    // -- library -------------------------------------------------------------------------------------------------

    /** I1: adds a specimen of [t] in front (S03) and opens the adjust page on it (S04). */
    fun add(s: PfState, t: Template, image: ImportFile? = null): PfState {
        if (s.full) return s
        val anchor = s.anchor ?: return s
        var w = t.wDp
        var h = t.hDp
        var scaled = false
        if (image != null) {
            w = image.wPx
            h = image.hPx
            if (h > MAX_H_DP) {
                w = (w * MAX_H_DP.toFloat() / h).roundToInt()
                h = MAX_H_DP
                scaled = true
            }
            w = w.coerceIn(MIN_W_DP, MAX_W_DP)
        }
        val widthM = w * Specimen.DP_M * t.scale
        // the arc starts at body forward, not at the head's yaw when the card was pressed (D29: the user looks at the
        // tool window while pressing; its direction must not leak into the placement)
        val taken = s.specimens.filter { !it.hidden }.map { it.pose.yawDeg to it.widthM }
        val pose = Placement.arrange(widthM, taken, s.windowYawDeg)
        val sp =
            Specimen(
                id = s.nextId,
                template = t,
                wDp = w,
                hDp = h,
                scale = t.scale,
                surface = Surface.GLASS,
                pose = pose,
                image = image?.name,
                imageScaled = scaled,
            )
        val locked = s.specimens.map { it.copy(mode = PlaceMode.LOCKED) }
        return s.copy(
            specimens = locked + sp,
            nextId = s.nextId + 1,
            selected = sp.id,
            page = Page.ADJUST,
            sub = Sub.NONE,
            adjustPage = 0,
            phase = if (s.phase == Phase.WELCOME) Phase.READY else s.phase,
            settings = s.settings.copy(welcomed = true),
        )
    }

    /** I2: selects a specimen (PLACING) and locks every other one; null = none ([完成], S04:none). */
    fun select(s: PfState, id: Int?): PfState {
        val target = id?.takeIf { i -> s.specimens.any { it.id == i } }
        return s.copy(
            selected = target,
            specimens = s.specimens.map { it.copy(mode = if (it.id == target) PlaceMode.PLACING else PlaceMode.LOCKED) },
            deleteAsk = null,
        )
    }

    fun done(s: PfState): PfState = select(s, null)

    // -- adjust --------------------------------------------------------------------------------------------------

    /** I6: one stepper press on the selected specimen. Locked or none selected: nothing moves. */
    fun step(s: PfState, field: Field, dir: Int): PfState {
        val sp = s.selectedSpecimen ?: return s
        if (sp.mode != PlaceMode.PLACING) return s
        val p = sp.pose
        val next =
            when (field) {
                Field.DIST -> sp.copy(pose = Placement.pushTo(p, Placement.clampUserDist(round2(p.dist + dir * DIST_STEP), sp.widthM)))
                Field.HEIGHT -> sp.copy(pose = Placement.raise(p, dir * HEIGHT_STEP).let { q -> if (q.dist < Placement.minUserDist(sp.widthM) || q.dist > Placement.MAX_DIST) p else q })
                Field.SIDE -> sp.copy(pose = p.copy(yawDeg = wrapDeg(p.yawDeg + dir * ANGLE_STEP)))
                Field.FACING -> sp.copy(pose = p.copy(facingDeg = wrapDeg(p.facingDeg + dir * ANGLE_STEP)))
                Field.TILT -> sp.copy(pose = p.copy(tiltDeg = (p.tiltDeg + dir * ANGLE_STEP).coerceIn(-60f, 60f)))
                Field.WIDTH -> sp.copy(wDp = (sp.wDp + dir * DP_STEP).coerceIn(MIN_W_DP, MAX_W_DP))
                Field.HEIGHT_DP -> sp.copy(hDp = (sp.hDp + dir * DP_STEP).coerceIn(MIN_H_DP, MAX_H_DP))
                Field.SCALE -> sp.copy(scale = round2(sp.scale + dir * SCALE_STEP).coerceIn(MIN_SCALE, MAX_SCALE))
            }
        val resizing = if (field == Field.WIDTH || field == Field.HEIGHT_DP) s.resizing + sp.id else s.resizing
        return replace(s, next).copy(resizing = resizing)
    }

    /** "朝向眼睛": tilts the face towards the eye. */
    fun tiltToEye(s: PfState): PfState {
        val sp = s.selectedSpecimen ?: return s
        if (sp.mode != PlaceMode.PLACING) return s
        return replace(s, sp.copy(pose = sp.pose.copy(tiltDeg = Placement.tiltToEye(sp.pose))))
    }

    fun surface(s: PfState, surface: Surface): PfState {
        val sp = s.selectedSpecimen ?: return s
        return replace(s, sp.copy(surface = surface))
    }

    /** The 0.5 s pause after a size change is over: the panel is swapped (scene) and the label shows numbers again. */
    fun resized(s: PfState, id: Int): PfState = s.copy(resizing = s.resizing - id)

    /**
     * I3 / I4 / I5 live pose of the selected specimen while the stick or a drag moves it (no snapping yet). Ignored
     * for a locked specimen (profile D1: locked content does not move).
     */
    fun livePose(s: PfState, id: Int, pose: PolarPose): PfState {
        val sp = s.specimens.firstOrNull { it.id == id } ?: return s
        if (sp.mode != PlaceMode.PLACING) return s
        return replace(s, sp.copy(pose = pose))
    }

    /** Stick released: facing within 10 degrees snaps square, distance within 3 cm of a preset snaps to it. */
    fun stickReleased(s: PfState, id: Int, pose: PolarPose): PfState {
        val sp = s.specimens.firstOrNull { it.id == id } ?: return s.copy(stickActive = false)
        if (sp.mode != PlaceMode.PLACING) return s.copy(stickActive = false)
        val dist = Placement.snapDist(pose.dist)
        val snapped = Placement.pushTo(pose, dist).copy(facingDeg = Placement.snapFacing(pose.facingDeg))
        return replace(s, sp.copy(pose = snapped)).copy(stickActive = false)
    }

    /**
     * Applies one frame of stick input to a pose (the same mapping for the real stick and the debug command):
     * turn about its own vertical, push along eye -> centre, clamped to the user range.
     */
    fun stickStep(pose: PolarPose, out: StickOut, dtS: Float, widthM: Float): PolarPose =
        when (out.axis) {
            StickAxis.NONE -> pose
            StickAxis.TURN -> pose.copy(facingDeg = wrapDeg(pose.facingDeg + out.turnDegPerS * dtS))
            StickAxis.PUSH -> Placement.pushTo(pose, Placement.clampUserDist(pose.dist + out.pushMps * dtS, widthM))
        }

    /** Drag released at room point [center]: within 0.15 m of a frame it snaps in (position and facing). */
    fun dragReleased(s: PfState, id: Int, center: Vec3): PfState {
        val sp = s.specimens.firstOrNull { it.id == id } ?: return s.copy(dragging = null)
        val anchor = s.anchor ?: return s.copy(dragging = null)
        if (sp.mode != PlaceMode.PLACING) return s.copy(dragging = null)
        val frame = if (s.settings.frames) Placement.snapFrame(anchor, center, Placement.frames(s.windowYawDeg)) else null
        val pose =
            if (frame != null) frame.pose
            else Placement.polar(anchor, center, sp.pose).let { p -> if (p.dist < Placement.minUserDist(sp.widthM)) Placement.pushTo(p, Placement.minUserDist(sp.widthM)) else if (p.dist > Placement.MAX_DIST) Placement.pushTo(p, Placement.MAX_DIST) else p }
        return replace(s, sp.copy(pose = pose)).copy(dragging = null)
    }

    // -- overlays, rating ----------------------------------------------------------------------------------------

    fun overlay(s: PfState, key: String, on: Boolean?): PfState =
        when (key) {
            "reach" -> s.copy(overlays = s.overlays.copy(reach = on ?: !s.overlays.reach))
            "neck" -> s.copy(overlays = s.overlays.copy(neck = on ?: !s.overlays.neck))
            "ruler" -> s.copy(overlays = s.overlays.copy(ruler = on ?: !s.overlays.ruler))
            "frames" -> s.copy(settings = s.settings.copy(frames = on ?: !s.settings.frames))
            else -> s
        }

    /** I8: self-rating of [role] on the selected specimen, stored with its arc minutes now. */
    fun rate(s: PfState, role: String, rating: Rating): PfState {
        val sp = s.selectedSpecimen ?: return s
        val arc = s.readings[sp.id]?.styles?.firstOrNull { it.role.role == role }?.arcmin ?: s.readings[sp.id]?.let { minArc(it) } ?: 0f
        return replace(s, sp.copy(ratings = sp.ratings + (role to RatingEntry(rating, arc))))
    }

    private fun minArc(r: Readings): Float = r.styles.minOfOrNull { it.arcmin } ?: 0f

    // -- list ----------------------------------------------------------------------------------------------------

    /**
     * "移到面前": the free place on the arc nearest body forward, 1.0 m, keeping the size. Body forward, not the head's
     * yaw at the press: the list is in the tool window, which the user is looking at (D29).
     */
    fun moveToFront(s: PfState, id: Int): PfState {
        val sp = s.specimens.firstOrNull { it.id == id } ?: return s
        val taken = s.specimens.filter { it.id != id && !it.hidden }.map { it.pose.yawDeg to it.widthM }
        val pose = Placement.arrange(sp.widthM, taken, s.windowYawDeg)
        return replace(s, sp.copy(pose = pose, hidden = false))
    }

    fun toggleHidden(s: PfState, id: Int): PfState {
        val sp = s.specimens.firstOrNull { it.id == id } ?: return s
        val next = replace(s, sp.copy(hidden = !sp.hidden))
        return if (!sp.hidden && s.selected == id) select(next, null) else next
    }

    fun askDelete(s: PfState, id: Int?): PfState = s.copy(deleteAsk = id)

    fun delete(s: PfState, id: Int): PfState {
        val rest = s.specimens.filter { it.id != id }
        return s.copy(specimens = rest, selected = s.selected.takeIf { it != id }, deleteAsk = null, resizing = s.resizing - id)
    }

    // -- settings ------------------------------------------------------------------------------------------------

    fun settings(s: PfState, next: PfSettings): PfState = s.copy(settings = next)

    fun confirm(s: PfState, c: Confirm?): PfState = s.copy(confirm = c)

    fun clearAll(s: PfState): PfState = s.copy(specimens = emptyList(), selected = null, confirm = null, page = Page.LIBRARY, sub = Sub.NONE, resizing = emptySet())

    /**
     * "全部样张回到面前", step 1 (D29): the press only asks; the hint row tells the user to look ahead, and the head is
     * sampled once it has come to rest ([allToFront]), not at the press, when the user was looking at the button.
     */
    fun requestFront(s: PfState, nowMs: Long): PfState = s.copy(pendingFrontMs = nowMs)

    /** Step 2 (and S18): the body anchor moves to the settled head; the layout keeps its shape. */
    fun allToFront(s: PfState, eye: Vec3?, forward: Vec3?): PfState {
        if (eye == null || forward == null) return s.copy(pendingFrontMs = null)
        val anchor = Placement.placeUpright(eye, forward, s.anchor?.yaw ?: 0f)
        val old = s.anchor
        val window = s.windowYawDeg?.let { w -> if (old == null) w else wrapDeg(w + (anchor.yaw - old.yaw).deg()) }
        return s.copy(anchor = anchor, windowYawDeg = window, pendingFrontMs = null, headYawDeg = 0f, eye = eye)
    }

    /** The tool window was (re)opened in front of the head: its sector follows the head's bearing. */
    fun windowOpened(s: PfState): PfState = s.copy(windowYawDeg = s.headYawDeg ?: 0f)

    fun windowClosed(s: PfState): PfState = s.copy(windowYawDeg = null)

    /** S19 [把它挪开]: moves the specimen out of the tool window's sector, on the arc at its distance. */
    fun moveOutOfWindow(s: PfState, id: Int): PfState {
        val sp = s.specimens.firstOrNull { it.id == id } ?: return s
        val taken = s.specimens.filter { it.id != id && !it.hidden }.map { it.pose.yawDeg to it.widthM }
        val pose = Placement.arrange(sp.widthM, taken, s.windowYawDeg, startDist = max(sp.pose.dist, Placement.FIRST_DIST))
        return replace(s, sp.copy(pose = pose.copy(facingDeg = 0f, tiltDeg = 0f)))
    }

    // -- navigation ----------------------------------------------------------------------------------------------

    fun page(s: PfState, p: Page): PfState = s.copy(page = p, sub = Sub.NONE, deleteAsk = null, confirm = if (p == Page.SETTINGS) s.confirm else null)

    fun sub(s: PfState, sub: Sub): PfState = s.copy(page = Page.LIBRARY, sub = sub, deleteAsk = null)

    // -- head ----------------------------------------------------------------------------------------------------

    /**
     * New eye position (at most 5 times a second, or when something changed): recomputes readings, overlaps, S14 and
     * S19 for every visible specimen.
     */
    fun derive(s: PfState, eye: Vec3?, headYawDeg: Float?): PfState {
        val anchor = s.anchor ?: return s.copy(eye = eye, headYawDeg = headYawDeg)
        val e = eye ?: anchor.eye
        val visible = s.specimens.filter { !it.hidden }
        val readings = visible.associate { it.id to Report.readings(it, anchor, e, s.settings, rolesFor(s, it), s.fontScale, s.mine) }
        val rects = visible.map { Rect3.of(it.id, Placement.world(anchor, it.pose), it.widthM, it.heightPanelM) }
        val overlaps = Occlusion.all(e, rects)
        val blocks = rects.filter { Occlusion.blocksWindow(it, e, anchor.yaw, s.windowYawDeg) }.map { it.id }.toSet()
        val inside = rects.filter { Occlusion.inside(e, it) }.map { it.id }.toSet()
        return s.copy(eye = eye, headYawDeg = headYawDeg, readings = readings, overlaps = overlaps, blocksWindow = blocks, inside = inside)
    }

    /** Type roles a specimen is rated by: a picture is rated as a whole; the others by all 15 PicoTheme roles. */
    fun rolesFor(s: PfState, sp: Specimen): List<TypeRole> =
        if (sp.template.layout == io.embodit.panelfit.domain.model.Layout.IMAGE) emptyList() else s.roles

    // -- helpers -------------------------------------------------------------------------------------------------

    private fun replace(s: PfState, sp: Specimen): PfState = s.copy(specimens = s.specimens.map { if (it.id == sp.id) sp else it })

    private fun round2(v: Float): Float = (v * 100f).roundToInt() / 100f

    const val MIN_W_DP = 120
    const val MAX_W_DP = 1600
    const val MIN_H_DP = 80
    const val MAX_H_DP = 720
    const val MIN_SCALE = 0.5f
    const val MAX_SCALE = 1.5f
}
