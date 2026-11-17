package io.embodit.panelfit.domain.usecase

import io.embodit.panelfit.domain.model.BodyAnchor
import io.embodit.panelfit.domain.model.PolarPose
import io.embodit.panelfit.domain.model.Quat4
import io.embodit.panelfit.domain.model.Specimen
import io.embodit.panelfit.domain.model.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

internal const val DEG = (PI / 180.0).toFloat()

internal fun Float.rad(): Float = this * DEG

internal fun Float.deg(): Float = this / DEG

/** Wraps degrees into -180..180. */
internal fun wrapDeg(d: Float): Float {
    var v = d % 360f
    if (v > 180f) v -= 360f
    if (v < -180f) v += 360f
    return v
}

/** Where a specimen stands in the room: centre and how it is turned (yaw about the vertical, then tilt; no roll). */
data class WorldPose(val center: Vec3, val facing: PanelFacing) {
    val rotation: Quat4
        get() = facing.rotation
}

/**
 * Placement rules (design `rules.placement`, profile D9 / D17 / D21): one yaw-only `placeUpright`, the body anchor,
 * polar <-> world, the arc arrangement that skips the tool window, the recommended frames and the snap rules.
 */
object Placement {
    const val FIRST_DIST = 1.0f
    const val FIRST_BELOW_DEG = 10f
    const val MIN_USER_DIST = 0.5f
    const val MIN_WIDE_DIST = 0.8f
    const val WIDE_M = 0.6f
    const val MAX_DIST = 3.0f
    const val ARC_STEP_OUT = 0.25f
    const val MIN_GAP_DEG = 3f
    const val GAP_SHARE = 0.3f
    const val FRAME_SNAP_M = 0.15f
    val PRESET_DISTS = floatArrayOf(0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 3.0f)
    const val PRESET_SNAP_M = 0.03f
    const val FACE_SNAP_DEG = 10f

    /**
     * The system tool window (deviation from the design's attachment tool panel, native-window rule 2026-11-02): it
     * opens about 1.75 m straight ahead of the head and cannot be placed by the app. Its sector is estimated from the
     * head when it was opened: 560 x 680 dp at 1 mm per dp per metre of distance, plus a 3 degree margin.
     */
    const val WINDOW_DIST = 1.75f
    const val WINDOW_HALF_YAW_DEG = 15.6f
    const val WINDOW_HALF_PITCH_DEG = 18.8f
    const val WINDOW_MARGIN_DEG = 3f

    /**
     * Body anchor from a head pose: the eye position and the yaw of the head's forward, flattened (pitch and roll
     * dropped). Looking straight up or down keeps [fallbackYaw].
     */
    fun placeUpright(eye: Vec3, forward: Vec3, fallbackYaw: Float = 0f): BodyAnchor =
        BodyAnchor(eye, FacePanelUseCase.yawOf(forward, fallbackYaw))

    /** Horizontal unit direction [yawRightDeg] to the right of the anchor's forward. */
    fun direction(anchor: BodyAnchor, yawRightDeg: Float): Vec3 {
        val y = anchor.yaw - yawRightDeg.rad()
        return Vec3(-sin(y), 0f, -cos(y))
    }

    /** Polar pose relative to [anchor] -> centre and facing in the room. */
    fun world(anchor: BodyAnchor, p: PolarPose): WorldPose {
        val h = p.heightM.coerceIn(-p.dist * 0.98f, p.dist * 0.98f)
        val horiz = sqrt(max(p.dist * p.dist - h * h, 0f))
        val center = anchor.eye + direction(anchor, p.yawDeg) * horiz + Vec3.UP * h
        val toEyeYaw = anchor.yaw - p.yawDeg.rad()
        return WorldPose(center, PanelFacing(toEyeYaw - p.facingDeg.rad(), p.tiltDeg.rad()))
    }

    /** Room centre -> polar pose relative to [anchor], keeping [keep]'s facing and tilt. */
    fun polar(anchor: BodyAnchor, center: Vec3, keep: PolarPose): PolarPose {
        val rel = center - anchor.eye
        val dist = rel.length.coerceAtLeast(0.05f)
        val worldYaw = atan2(-rel.x, -rel.z)
        val yawDeg = wrapDeg((anchor.yaw - worldYaw).deg())
        return keep.copy(dist = dist, heightM = rel.y, yawDeg = yawDeg)
    }

    /** First pose: [dist] ahead of the body, [belowDeg] below the eye, facing the eye, upright. */
    fun ahead(dist: Float = FIRST_DIST, yawDeg: Float = 0f, belowDeg: Float = FIRST_BELOW_DEG): PolarPose =
        PolarPose(dist = dist, heightM = -dist * sin(belowDeg.rad()), yawDeg = yawDeg)

    /** Tilt that turns the face towards the eye ("朝向眼睛"). */
    fun tiltToEye(p: PolarPose): Float {
        val horiz = sqrt(max(p.dist * p.dist - p.heightM * p.heightM, 1e-6f))
        return atan2(-p.heightM, horiz).deg()
    }

    /** Half the angular width of a panel [widthM] wide at [dist], degrees. */
    fun halfAngle(widthM: Float, dist: Float): Float = atan(widthM / 2f / dist).deg()

    /** Do two panels (pose, width, height) overlap as seen from the body anchor (bearing and elevation spans)? */
    fun overlapsSeen(a: PolarPose, aW: Float, aH: Float, b: PolarPose, bW: Float, bH: Float): Boolean {
        fun elev(p: PolarPose) = atan2(p.heightM, p.dist).deg()
        val yaw = kotlin.math.abs(a.yawDeg - b.yawDeg) < halfAngle(aW, a.dist) + halfAngle(bW, b.dist)
        val up = kotlin.math.abs(elev(a) - elev(b)) < halfAngle(aH, a.dist) + halfAngle(bH, b.dist)
        return yaw && up
    }

    /** Closest distance a user push may reach for a panel [widthM] wide (design `rules.placement.clearance`). */
    fun minUserDist(widthM: Float): Float = if (widthM > WIDE_M) MIN_WIDE_DIST else MIN_USER_DIST

    /** An angular interval to the right of body forward, degrees. */
    data class Span(val from: Float, val to: Float) {
        fun overlaps(o: Span, gap: Float): Boolean = from < o.to + gap && o.from < to + gap
    }

    /** The tool window's sector, degrees right of body forward, or null when the window is closed. */
    fun windowSpan(windowYawDeg: Float?): Span? =
        windowYawDeg?.let { Span(it - WINDOW_HALF_YAW_DEG - WINDOW_MARGIN_DEG, it + WINDOW_HALF_YAW_DEG + WINDOW_MARGIN_DEG) }

    /**
     * Where a new panel [widthM] wide goes (D17): on the arc at [startDist] (>= 1.0 m), first the free place nearest
     * body forward searching right, then left, every two panels at least max(3, 0.3 x angular width) apart, outside
     * the tool window's sector; when the arc is full, 0.25 m further out.
     */
    fun arrange(widthM: Float, taken: List<Pair<Float, Float>>, windowYawDeg: Float?, startDist: Float = FIRST_DIST, belowDeg: Float = FIRST_BELOW_DEG): PolarPose {
        var dist = max(startDist, FIRST_DIST)
        while (dist <= MAX_DIST + 1e-3f) {
            val half = halfAngle(widthM, dist)
            val gap = max(MIN_GAP_DEG, GAP_SHARE * 2f * half)
            val spans = taken.map { (yaw, w) -> val hw = halfAngle(w, dist); Span(yaw - hw, yaw + hw) }
            val sector = windowSpan(windowYawDeg)
            val found = searchArc(half, gap, spans, sector)
            if (found != null) return ahead(dist, found, belowDeg)
            dist += ARC_STEP_OUT
        }
        return ahead(MAX_DIST, 0f, belowDeg)
    }

    private fun searchArc(half: Float, gap: Float, spans: List<Span>, sector: Span?): Float? {
        fun free(yaw: Float): Boolean {
            val me = Span(yaw - half, yaw + half)
            if (sector != null && me.overlaps(sector, 0f)) return false
            return spans.none { me.overlaps(it, gap) }
        }
        if (free(0f)) return 0f
        // candidates: the edges next to every obstacle, searched outwards right first, then left
        val edges = ArrayList<Float>()
        for (s in spans) {
            edges += s.to + gap + half
            edges += s.from - gap - half
        }
        if (sector != null) {
            edges += sector.to + half
            edges += sector.from - half
        }
        val right = edges.filter { it > 0f && it + half <= ARC_LIMIT_DEG }.sorted()
        val left = edges.filter { it < 0f && it - half >= -ARC_LIMIT_DEG }.sortedDescending()
        right.firstOrNull { free(it) }?.let { return it }
        left.firstOrNull { free(it) }?.let { return it }
        return null
    }

    /** How far out the arc search may go on either side, degrees. */
    const val ARC_LIMIT_DEG = 70f

    /** A recommended frame (design `rules.placement.recommendedFrames`): pose and the template size it is drawn at. */
    data class Frame(val id: String, val pose: PolarPose, val wM: Float, val hM: Float)

    /**
     * The three recommended frames: main panel (1.0 m, 10 degrees below the eye, T1 size), side panel (0.9 m, 12 degrees
     * below, right 40, T2 size) and large window (1.5 m, eye level, T4 size). All keep out of the tool window's
     * sector: a frame that falls in it moves to the free arc next to it (main and side to the right, the large one to
     * the left), the same rule as automatic placement.
     */
    fun frames(windowYawDeg: Float?): List<Frame> {
        val t1w = 400 * Specimen.DP_M * 0.75f
        val t1h = 252 * Specimen.DP_M * 0.75f
        val t2w = 520 * Specimen.DP_M * 0.75f
        val t2h = 308 * Specimen.DP_M * 0.75f
        val t4w = 1280 * Specimen.DP_M
        val t4h = 720 * Specimen.DP_M
        val sector = windowSpan(windowYawDeg)
        val placed = ArrayList<Span>()
        fun place(yaw: Float, dist: Float, w: Float, preferRight: Boolean): Float {
            val half = halfAngle(w, dist)
            val gap = max(MIN_GAP_DEG, GAP_SHARE * 2f * half)
            fun free(y: Float): Boolean {
                val me = Span(y - half, y + half)
                if (sector != null && me.overlaps(sector, 0f)) return false
                return placed.none { me.overlaps(it, gap) }
            }
            var y = yaw
            var guard = 0
            while (!free(y) && guard++ < 400) y += if (preferRight) 0.5f else -0.5f
            placed += Span(y - half, y + half)
            return y
        }
        val f1 = place(0f, 1.0f, t1w, true)
        val f2 = place(40f, 0.9f, t2w, true)
        val f3 = place(0f, 1.5f, t4w, false)
        return listOf(
            Frame("main", ahead(1.0f, f1, 10f), t1w, t1h),
            Frame("side", ahead(0.9f, f2, 12f), t2w, t2h),
            Frame("large", ahead(1.5f, f3, 0f), t4w, t4h),
        )
    }

    /** The frame whose centre is within 0.15 m of [center], nearest first. */
    fun snapFrame(anchor: BodyAnchor, center: Vec3, frames: List<Frame>): Frame? =
        frames.map { it to world(anchor, it.pose).center.distanceTo(center) }.filter { it.second <= FRAME_SNAP_M }.minByOrNull { it.second }?.first

    /** Distance after a push: clamped to the user range of a panel [widthM] wide. */
    fun clampUserDist(dist: Float, widthM: Float): Float = dist.coerceIn(minUserDist(widthM), MAX_DIST)

    /** On release: a preset distance within 3 cm wins. */
    fun snapDist(dist: Float): Float = PRESET_DISTS.firstOrNull { abs(it - dist) <= PRESET_SNAP_M } ?: dist

    /** On release: facing within 10 degrees of square snaps to square. */
    fun snapFacing(facingDeg: Float): Float = if (abs(wrapDeg(facingDeg)) <= FACE_SNAP_DEG) 0f else wrapDeg(facingDeg)

    /** Pushes a pose along the eye-to-centre line to [newDist] (keeps the direction). */
    fun pushTo(p: PolarPose, newDist: Float): PolarPose = p.copy(dist = newDist, heightM = p.heightM * newDist / p.dist)

    /** Moves the centre up / down by [dh] metres keeping the horizontal distance. */
    fun raise(p: PolarPose, dh: Float): PolarPose {
        val horiz = sqrt(max(p.dist * p.dist - p.heightM * p.heightM, 1e-6f))
        val h = p.heightM + dh
        return p.copy(dist = sqrt(horiz * horiz + h * h), heightM = h)
    }

    /** Angle in degrees between a panel's normal and the direction from its centre to [eye]. */
    fun facingOff(pose: WorldPose, eye: Vec3): Float {
        val to = (eye - pose.center).normalized()
        val c = pose.facing.normal.dot(to).coerceIn(-1f, 1f)
        return acos(c).deg()
    }

    /** Elevation of [point] seen from [eye], degrees (positive above eye level). */
    fun elevation(eye: Vec3, point: Vec3): Float {
        val rel = point - eye
        return atan2(rel.y, hypot(rel.x, rel.z)).deg()
    }

    /** Bearing of [point] right of the anchor's forward, degrees. */
    fun bearing(anchor: BodyAnchor, eye: Vec3, point: Vec3): Float {
        val rel = point - eye
        return wrapDeg((anchor.yaw - atan2(-rel.x, -rel.z)).deg())
    }
}
