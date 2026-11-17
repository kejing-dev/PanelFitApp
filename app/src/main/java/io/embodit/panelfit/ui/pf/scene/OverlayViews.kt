package io.embodit.panelfit.ui.pf.scene

import androidx.compose.ui.graphics.Color
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import io.embodit.panelfit.domain.model.BodyAnchor
import io.embodit.panelfit.domain.model.Vec3
import io.embodit.panelfit.domain.usecase.Placement
import io.embodit.panelfit.domain.usecase.Rect3
import io.embodit.panelfit.domain.usecase.rad
import io.embodit.panelfit.ui.theme.PfColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * The overlays in the room (design `rules.overlays`, `entities3d`): reach arcs, the comfort zone on a 1.5 m sphere,
 * the 100 mm ruler, the three recommended frames and the occlusion masks. All are thin strips of one shared unit-band
 * mesh (one MeshResource for everything), unlit, without colliders (D24: nothing here takes the pointer). Strips are
 * made lazily, at most [PER_FRAME] per frame, so the idle emulator is never asked for a burst of entities.
 */
internal class OverlayViews(private val root: Entity, shared: SharedResources) {
    private val mesh: MeshResource = shared.keep(MeshFactory.unitBand("overlayBand"))
    private val green: Material = shared.keep(sheer(PfColors.Reach, 0.6f))
    private val yellow: Material = shared.keep(sheer(PfColors.Interaction, 0.4f))
    private val white: Material = shared.keep(solid(PfColors.White))
    private val black: Material = shared.keep(solid(PfColors.Black))
    private val red: Material = shared.keep(sheer(PfColors.Error, 0.25f))

    private inner class Group(val name: String, val material: Material) {
        val strips = ArrayList<Strip>()
        var used = 0

        fun begin() {
            used = 0
        }

        /** Next strip of the group, or null when this frame's budget for new entities is spent. */
        fun next(): Strip? {
            if (used < strips.size) return strips[used++]
            if (budget <= 0) return null
            budget--
            val s = Strip(root, mesh, material, "$name:${strips.size}")
            strips += s
            used++
            return s
        }

        fun end() {
            for (i in used until strips.size) strips[i].hide()
        }

        fun hideAll() {
            used = 0
            end()
        }

        fun destroy() {
            strips.forEach { it.destroy() }
            strips.clear()
        }
    }

    private val reach = Group("reach", green)
    private val neck = Group("neck", green)
    private val rulerBar = Group("rulerBar", white)
    private val rulerTicks = Group("rulerTicks", black)
    private val frames = Group("frames", yellow)
    private val masks = Group("masks", red)
    private val groups = listOf(reach, neck, rulerBar, rulerTicks, frames, masks)
    private var budget = PER_FRAME

    /** Everything an overlay frame needs; drawn again only when it changes (or a group is still being made). */
    data class Input(
        val anchor: BodyAnchor?,
        val eye: Vec3?,
        val armM: Float,
        val reachOn: Boolean,
        val neckOn: Boolean,
        val ruler: Rect3?,
        val frames: List<Pair<Rect3, Boolean>>,
        val masks: List<Rect3>,
    )

    private var last: Input? = null
    private var complete = false

    fun frame(input: Input) {
        budget = PER_FRAME
        if (input == last && complete) return
        last = input
        var ok = true
        val anchor = input.anchor
        val eye = input.eye ?: anchor?.eye
        if (anchor != null && eye != null && input.reachOn) ok = drawReach(anchor, input.armM, eye) && ok else reach.hideAll()
        if (anchor != null && eye != null && input.neckOn) ok = drawNeck(anchor, eye) && ok else neck.hideAll()
        if (input.ruler != null && eye != null) ok = drawRuler(input.ruler) && ok else {
            rulerBar.hideAll()
            rulerTicks.hideAll()
        }
        ok = drawFrames(input.frames, eye) && ok
        ok = drawMasks(input.masks) && ok
        complete = ok
    }

    private fun drawReach(anchor: BodyAnchor, arm: Float, eye: Vec3): Boolean {
        reach.begin()
        val radii = listOf(0.35f, 0.95f * arm)
        val heights = listOf(-0.10f, -0.50f)
        val center = anchor.eye
        fun at(r: Float, yawDeg: Float, dy: Float): Vec3 = center + Placement.direction(anchor, yawDeg) * r + Vec3(0f, dy, 0f)
        for (r in radii) for (dy in heights) {
            var y = -REACH_SPAN
            while (y < REACH_SPAN - 0.01f) {
                val a = at(r, y, dy)
                val b = at(r, y + REACH_STEP, dy)
                reach.next()?.draw(a, b, LINE_M, Vec3.UP) ?: return false
                y += REACH_STEP
            }
        }
        var y = -REACH_SPAN
        while (y <= REACH_SPAN + 0.01f) {
            val top = at(radii[1], y, heights[0])
            val bottom = at(radii[1], y, heights[1])
            reach.next()?.draw(top, bottom, LINE_M, eye - top) ?: return false
            y += 15f
        }
        reach.end()
        return true
    }

    /** Comfort zone on a 1.5 m sphere about the eye, locked to body forward: comfortable solid, acceptable dashed. */
    private fun drawNeck(anchor: BodyAnchor, eye: Vec3): Boolean {
        neck.begin()
        val c = anchor.eye
        fun sph(yawDeg: Float, elevDeg: Float): Vec3 {
            val e = elevDeg.rad()
            return c + Placement.direction(anchor, yawDeg) * (NECK_R * cos(e)) + Vec3(0f, NECK_R * sin(e), 0f)
        }
        fun arc(y0: Float, y1: Float, e0: Float, e1: Float, dashed: Boolean): Boolean {
            val steps = 12
            for (i in 0 until steps) {
                if (dashed && i % 2 == 1) continue
                val t0 = i / steps.toFloat()
                val t1 = (i + 1) / steps.toFloat()
                val a = sph(y0 + (y1 - y0) * t0, e0 + (e1 - e0) * t0)
                val b = sph(y0 + (y1 - y0) * t1, e0 + (e1 - e0) * t1)
                neck.next()?.draw(a, b, NECK_LINE_M, eye - a) ?: return false
            }
            return true
        }
        val ok =
            arc(-30f, 30f, 10f, 10f, false) && arc(-30f, 30f, -40f, -40f, false) && arc(-30f, -30f, -40f, 10f, false) && arc(30f, 30f, -40f, 10f, false) &&
                arc(-55f, 55f, 20f, 20f, true) && arc(-55f, 55f, -50f, -50f, true) && arc(-55f, -55f, -50f, 20f, true) && arc(55f, 55f, -50f, 20f, true)
        if (!ok) return false
        neck.end()
        return true
    }

    /** 100 mm bar 0.02 m right of the selected specimen, a tick every 10 mm (design `entities3d.rulerBar`). */
    private fun drawRuler(r: Rect3): Boolean {
        rulerBar.begin()
        rulerTicks.begin()
        val out = r.normal * 0.003f
        val x = r.center + r.right * (r.halfW + 0.02f + RULER_W / 2f) + out
        val bottom = x - r.up * 0.05f
        val top = x + r.up * 0.05f
        rulerBar.next()?.draw(bottom, top, RULER_W, r.normal) ?: return false
        for (i in 0..10) {
            val len = if (i % 5 == 0) 0.009f else 0.005f
            val p = bottom + r.up * (i * 0.01f) + r.normal * 0.001f - r.right * (RULER_W / 2f)
            rulerTicks.next()?.draw(p, p + r.right * len, 0.0012f, r.normal) ?: return false
        }
        rulerBar.end()
        rulerTicks.end()
        return true
    }

    /** Dashed rectangles of the recommended frames (yellow 40 %), [on] = drawn. */
    private fun drawFrames(list: List<Pair<Rect3, Boolean>>, eye: Vec3?): Boolean {
        frames.begin()
        for ((r, on) in list) {
            if (!on || eye == null) continue
            val c = r.corners
            for (k in 0 until 4) {
                val a = c[k]
                val b = c[(k + 1) % 4]
                for (d in 0 until DASHES) {
                    val t0 = d / DASHES.toFloat()
                    val t1 = t0 + 0.6f / DASHES
                    frames.next()?.draw(a.lerp(b, t0), a.lerp(b, t1), FRAME_LINE_M, r.normal) ?: return false
                }
            }
        }
        frames.end()
        return true
    }

    /** Covered areas: a red 25 % rectangle on the back panel, 3 mm in front of it. */
    private fun drawMasks(list: List<Rect3>): Boolean {
        masks.begin()
        for (m in list) {
            val out = m.normal * 0.003f
            val from = m.center - m.right * m.halfW + out
            val to = m.center + m.right * m.halfW + out
            masks.next()?.draw(from, to, m.halfH * 2f, m.normal) ?: return false
        }
        masks.end()
        return true
    }

    fun destroy() {
        groups.forEach { it.destroy() }
    }

    private companion object {
        const val PER_FRAME = 16
        const val LINE_M = 0.006f
        const val NECK_LINE_M = 0.008f
        const val FRAME_LINE_M = 0.005f
        const val RULER_W = 0.014f
        const val NECK_R = 1.5f
        const val REACH_SPAN = 75f
        const val REACH_STEP = 7.5f
        const val DASHES = 8

        fun sheer(color: Color, opacity: Float): UnlitMaterial =
            UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
                setBaseColor(color.copy(alpha = 1f).color4())
                setOpacity(opacity)
                runCatching { setApplyToneMapping(false) }
                setCullingMode(MaterialCullingMode.NONE)
            }

        fun solid(color: Color): UnlitMaterial =
            UnlitMaterial.create(BlendingMode.OPAQUE).apply {
                setBaseColor(color.color4())
                runCatching { setApplyToneMapping(false) }
                setCullingMode(MaterialCullingMode.NONE)
            }
    }
}
