package io.embodit.panelfit.ui.pf.scene

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Component
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import io.embodit.panelfit.domain.model.Quat4
import io.embodit.panelfit.domain.model.Vec3
import io.embodit.panelfit.platform.PfLog

/**
 * Specimen panels. An attachment panel is declared once with a fixed size (Spatial Ruler finding), so a new size is a
 * new panel: [key] names the panel ("spec:<id>:<w>x<h>"), the Stage declares it, and the rig swaps it in (design
 * `rules.placement.resize`):
 *
 * 1. bound: under its own sub-holder, enabled, parked at 1/1000 size until it has had 6 frames / 0.6 s (a panel under a
 *    disabled holder never comes up; PanelRig rule 1);
 * 2. behind: full size 2 mm behind the current panel; its content composes two frames later (PanelRig rule 3);
 * 3. current: after 4 more frames the old panel is let go (its key leaves the declared list) and the new one moves
 *    to the plane.
 *
 * Every specimen has one holder carrying the pose and the scale; hidden = holder disabled and stowed far behind
 * (PanelRig rule 2). The specimen itself has no collider: moving is the tool window's, the stick's or the system
 * pointer's on the size label's collider (PfScene.placeLabelHit).
 */
internal class SpecimenRig(private val root: Entity, private val onShown: (key: String, shown: Boolean) -> Unit) {
    private enum class Step { PARKED, BEHIND, CURRENT, RETIRED }

    private inner class Entry(val key: String, val sub: Entity, val panel: Entity, val frame: Long, val ms: Long) {
        var step = Step.PARKED
        var stepFrame = frame
        var reported = false
        var hitRemoved: List<Component> = emptyList()
    }

    private inner class Holder(val id: Int, val entity: Entity) {
        val entries = ArrayList<Entry>()
        var enabled = true
        var opacity = 1f
        var hittable = true
    }

    private val holders = HashMap<Int, Holder>()
    private var frame = 0L

    /** Keys whose panels the Stage must keep declared (current and in-flight ones). */
    fun liveKeys(): Set<String> = holders.values.flatMap { h -> h.entries.filter { it.step != Step.RETIRED }.map { it.key } }.toSet()

    fun has(id: Int): Boolean = holders.containsKey(id)

    fun currentKey(id: Int): String? = holders[id]?.entries?.firstOrNull { it.step == Step.CURRENT }?.key

    private fun holder(id: Int): Holder =
        holders.getOrPut(id) {
            val e = Entity().named("specimen:$id")
            root.addChild(e)
            Holder(id, e)
        }

    /** Binds the panel of [key] (specimen [id]) once the framework has composed it. Returns true when it is bound. */
    fun bind(attachments: SpatialViewAttachments, id: Int, key: String, nowMs: Long): Boolean {
        val h = holder(id)
        if (h.entries.any { it.key == key }) return true
        val panel = attachments.entity(key) ?: return false
        val sub = Entity().named("sub:$key")
        h.entity.addChild(sub)
        sub.setUniformScale(PARK_SCALE)
        sub.addChild(panel)
        runCatching { panel.setName("panel:$key") }
        h.entries += Entry(key, sub, panel, frame, nowMs)
        PfLog.i("PANEL bound key=$key")
        return true
    }

    /** One frame: advances every swap. */
    fun onFrame(nowMs: Long) {
        frame++
        for (h in holders.values) {
            val cur = h.entries.firstOrNull { it.step == Step.CURRENT }
            for (e in h.entries.toList()) {
                when (e.step) {
                    Step.PARKED ->
                        if (frame - e.frame >= SETTLE_FRAMES && nowMs - e.ms >= SETTLE_MS) {
                            e.sub.setUniformScale(1f)
                            if (cur == null) {
                                e.sub.setLocal(position = Vec3.ZERO)
                                e.step = Step.CURRENT
                            } else {
                                e.sub.setLocal(position = Vec3(0f, 0f, -BEHIND_M))
                                e.step = Step.BEHIND
                            }
                            e.stepFrame = frame
                        }
                    Step.BEHIND ->
                        if (frame - e.stepFrame >= SHOW_FRAMES + SWAP_FRAMES) {
                            cur?.let { retire(it) }
                            e.sub.setLocal(position = Vec3.ZERO)
                            e.step = Step.CURRENT
                            e.stepFrame = frame
                            PfLog.i("PANEL swapped id=${h.id} key=${e.key}")
                        }
                    Step.CURRENT, Step.RETIRED -> Unit
                }
                val shown = (e.step == Step.BEHIND || e.step == Step.CURRENT) && frame - e.stepFrame >= SHOW_FRAMES || (e.step == Step.CURRENT && e.reported)
                if (shown != e.reported) {
                    e.reported = shown
                    onShown(e.key, shown)
                }
            }
            h.entries.removeAll { it.step == Step.RETIRED && frame - it.stepFrame > RETIRE_FRAMES && destroy(it) }
        }
    }

    private fun retire(e: Entry) {
        e.step = Step.RETIRED
        e.stepFrame = frame
        e.sub.enabled = false
        onShown(e.key, false)
    }

    private fun destroy(e: Entry): Boolean {
        runCatching { e.sub.removeAllChildren() }
        runCatching { e.sub.destroy(true) }
        return true
    }

    /** Pose of specimen [id]: centre, rotation, panel scale; [visible] false stows it. */
    fun place(id: Int, center: Vec3, rotation: Quat4, scale: Float, visible: Boolean, stow: Vec3) {
        val h = holders[id] ?: return
        if (!visible) {
            h.entity.setLocal(position = stow)
            if (h.enabled) {
                h.entity.enabled = false
                h.enabled = false
            }
            return
        }
        h.entity.setLocal(position = center, rotation = rotation, scale = Vec3(scale, scale, scale))
        if (!h.enabled) {
            h.entity.enabled = true
            h.enabled = true
        }
    }

    /**
     * S14 / S19: 30 % and no hits. Opacity through the opacity component, removed again at 1 (factory lesson: the
     * component makes everything blend even at 1.0). The pointer would be taken away by removing the panel's
     * interactable / collision component, but on 6.1.9 (emulator) an attachment panel entity has neither: the faded
     * specimen still takes the ray (logged `HIT off ... interactable=false collision=false`; S19 uses [把它挪开]).
     */
    fun fade(id: Int, faded: Boolean) {
        val h = holders[id] ?: return
        val want = if (faded) FADED else 1f
        if (h.opacity != want) {
            h.opacity = want
            if (want >= 1f) h.entity.clearOpacity() else h.entity.setOpacity(want)
            for (e in h.entries) if (want >= 1f) e.panel.clearOpacity() else e.panel.setOpacity(want)
        }
        if (h.hittable == !faded) return
        h.hittable = !faded
        for (e in h.entries) {
            if (faded) {
                // the ray hits the attachment panel's collision shape; its interactable (if any) takes the press
                val i = e.panel.components[InteractableComponent::class.java]
                val c = e.panel.components[CollisionComponent::class.java]
                e.hitRemoved = listOfNotNull(i, c)
                if (i != null) runCatching { e.panel.components.remove(InteractableComponent::class.java) }
                if (c != null) runCatching { e.panel.components.remove(CollisionComponent::class.java) }
                PfLog.i("HIT off key=${e.key} interactable=${i != null} collision=${c != null}")
            } else {
                for (c in e.hitRemoved) runCatching { e.panel.components.set(c) }
                e.hitRemoved = emptyList()
                PfLog.i("HIT on key=${e.key}")
            }
        }
    }

    /** Specimen [id] is gone: its panels are let go (the Stage stops declaring them) and the holder is destroyed. */
    fun remove(id: Int) {
        val h = holders.remove(id) ?: return
        for (e in h.entries) {
            onShown(e.key, false)
            runCatching { e.sub.removeAllChildren() }
        }
        runCatching { h.entity.destroy(true) }
        PfLog.i("PANEL removed id=$id")
    }

    fun ids(): Set<Int> = holders.keys.toSet()

    /** Entity of specimen [id]'s current panel (world bounds for PF-METRIC). */
    fun holderEntity(id: Int): Entity? = holders[id]?.entity

    fun release() {
        for (h in holders.values) {
            for (e in h.entries) runCatching { e.sub.removeAllChildren() }
            runCatching { h.entity.destroy(true) }
        }
        holders.clear()
    }

    companion object {
        private const val PARK_SCALE = 0.001f
        private const val SETTLE_FRAMES = 6L
        private const val SETTLE_MS = 600L
        private const val SHOW_FRAMES = 2L
        private const val SWAP_FRAMES = 4L
        private const val RETIRE_FRAMES = 4L
        private const val BEHIND_M = 0.002f
        const val FADED = 0.3f

        fun key(id: Int, w: Int, h: Int): String = "spec:$id:${w}x$h"
    }
}
