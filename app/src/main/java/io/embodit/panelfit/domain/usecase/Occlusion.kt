package io.embodit.panelfit.domain.usecase

import io.embodit.panelfit.domain.model.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A specimen as a rectangle in the room: centre, unit axes and half sizes, metres. */
data class Rect3(val id: Int, val center: Vec3, val right: Vec3, val up: Vec3, val normal: Vec3, val halfW: Float, val halfH: Float) {
    val corners: List<Vec3>
        get() = listOf(center - right * halfW - up * halfH, center + right * halfW - up * halfH, center + right * halfW + up * halfH, center - right * halfW + up * halfH)

    companion object {
        fun of(id: Int, pose: WorldPose, widthM: Float, heightM: Float): Rect3 =
            Rect3(id, pose.center, pose.facing.right, pose.facing.up, pose.facing.normal, widthM / 2f, heightM / 2f)
    }
}

/** Which part of a panel is covered (design: "挡住了右边那块的下半"). */
enum class Part { WHOLE, TOP, BOTTOM, LEFT, RIGHT, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, MIDDLE }

/** Where the covered panel is, seen from the one in front. */
enum class Side { LEFT, RIGHT, BEHIND }

/**
 * One occlusion: [front] covers [back]; [u0]..[u1] x [v0]..[v1] is the covered area on [back], in its own metres
 * (u to the right, v up, from its centre).
 */
data class Overlap(val front: Int, val back: Int, val u0: Float, val u1: Float, val v0: Float, val v1: Float, val part: Part, val side: Side)

/**
 * Occlusion check (design `rules.verdicts.occlusion`): from the current eye, every pair of specimens; the nearer one's
 * corners are projected along the lines of sight onto the farther one's plane and the covered rectangle is clipped to
 * that panel. Also the two other "seen from the eye" checks: inside a panel (S14) and in front of the tool window
 * (S19).
 */
object Occlusion {
    fun all(eye: Vec3, rects: List<Rect3>): List<Overlap> {
        val out = ArrayList<Overlap>()
        for (i in rects.indices) for (j in i + 1 until rects.size) {
            val a = rects[i]
            val b = rects[j]
            val da = a.center.distanceTo(eye)
            val db = b.center.distanceTo(eye)
            val (front, back) = if (da <= db) a to b else b to a
            covered(eye, front, back)?.let { out += it }
        }
        return out
    }

    fun covered(eye: Vec3, front: Rect3, back: Rect3): Overlap? {
        var u0 = Float.MAX_VALUE
        var u1 = -Float.MAX_VALUE
        var v0 = Float.MAX_VALUE
        var v1 = -Float.MAX_VALUE
        for (c in front.corners) {
            val dir = c - eye
            val denom = dir.dot(back.normal)
            if (abs(denom) < 1e-6f) return null
            val t = (back.center - eye).dot(back.normal) / denom
            // the front corner must lie between the eye and the back plane
            if (t < 1f || t > 1e3f) return null
            val hit = eye + dir * t - back.center
            val u = hit.dot(back.right)
            val v = hit.dot(back.up)
            u0 = min(u0, u); u1 = max(u1, u); v0 = min(v0, v); v1 = max(v1, v)
        }
        val cu0 = max(u0, -back.halfW)
        val cu1 = min(u1, back.halfW)
        val cv0 = max(v0, -back.halfH)
        val cv1 = min(v1, back.halfH)
        if (cu1 - cu0 <= MIN_M || cv1 - cv0 <= MIN_M) return null
        return Overlap(front.id, back.id, cu0, cu1, cv0, cv1, part(cu0, cu1, cv0, cv1, back.halfW, back.halfH), side(eye, front, back))
    }

    /** Which part of a panel [hw] x [hh] (half sizes) the rectangle covers. */
    fun part(u0: Float, u1: Float, v0: Float, v1: Float, hw: Float, hh: Float): Part {
        val wideShare = (u1 - u0) / (2f * hw)
        val tallShare = (v1 - v0) / (2f * hh)
        val cu = (u0 + u1) / 2f / hw
        val cv = (v0 + v1) / 2f / hh
        val horiz = if (wideShare > 0.8f) 0 else if (cu < -0.15f) -1 else if (cu > 0.15f) 1 else 0
        val vert = if (tallShare > 0.8f) 0 else if (cv < -0.15f) -1 else if (cv > 0.15f) 1 else 0
        return when {
            horiz == 0 && vert == 0 -> if (wideShare > 0.8f && tallShare > 0.8f) Part.WHOLE else Part.MIDDLE
            horiz == 0 -> if (vert < 0) Part.BOTTOM else Part.TOP
            vert == 0 -> if (horiz < 0) Part.LEFT else Part.RIGHT
            vert < 0 -> if (horiz < 0) Part.BOTTOM_LEFT else Part.BOTTOM_RIGHT
            else -> if (horiz < 0) Part.TOP_LEFT else Part.TOP_RIGHT
        }
    }

    private fun side(eye: Vec3, front: Rect3, back: Rect3): Side {
        val toFront = (front.center - eye).let { Vec3(it.x, 0f, it.z) }.normalized()
        val toBack = (back.center - eye).let { Vec3(it.x, 0f, it.z) }.normalized()
        // positive cross.y: back lies to the left (counter-clockwise from above)
        val cross = toFront.cross(toBack).y
        return when {
            cross > 0.02f -> Side.LEFT
            cross < -0.02f -> Side.RIGHT
            else -> Side.BEHIND
        }
    }

    /** S14: the eye is within 0.1 m of the panel's plane and inside its rectangle (plus 0.1 m). */
    fun inside(eye: Vec3, r: Rect3): Boolean {
        val rel = eye - r.center
        if (abs(rel.dot(r.normal)) >= INSIDE_M) return false
        return abs(rel.dot(r.right)) <= r.halfW + INSIDE_M && abs(rel.dot(r.up)) <= r.halfH + INSIDE_M
    }

    /**
     * S19: the panel lies between the eye and the tool window and covers part of it, seen from the eye. The window is
     * an angular rectangle [windowYawDeg] right of body forward at eye level ([Placement.WINDOW_HALF_YAW_DEG] x
     * [Placement.WINDOW_HALF_PITCH_DEG]) at [Placement.WINDOW_DIST].
     */
    fun blocksWindow(r: Rect3, eye: Vec3, bodyYaw: Float, windowYawDeg: Float?): Boolean {
        if (windowYawDeg == null) return false
        if (r.center.distanceTo(eye) >= Placement.WINDOW_DIST) return false
        val anchor = io.embodit.panelfit.domain.model.BodyAnchor(eye, bodyYaw)
        var y0 = Float.MAX_VALUE
        var y1 = -Float.MAX_VALUE
        var p0 = Float.MAX_VALUE
        var p1 = -Float.MAX_VALUE
        for (c in r.corners) {
            val y = Placement.bearing(anchor, eye, c)
            val p = Placement.elevation(eye, c)
            y0 = min(y0, y); y1 = max(y1, y); p0 = min(p0, p); p1 = max(p1, p)
        }
        val wy0 = windowYawDeg - Placement.WINDOW_HALF_YAW_DEG
        val wy1 = windowYawDeg + Placement.WINDOW_HALF_YAW_DEG
        val wp0 = -Placement.WINDOW_HALF_PITCH_DEG
        val wp1 = Placement.WINDOW_HALF_PITCH_DEG
        return y0 < wy1 && wy0 < y1 && p0 < wp1 && wp0 < p1
    }

    private const val MIN_M = 0.004f
    const val INSIDE_M = 0.1f
}
