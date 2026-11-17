package io.embodit.panelfit.domain.usecase

import io.embodit.panelfit.domain.model.BodyAnchor
import io.embodit.panelfit.domain.model.Layout
import io.embodit.panelfit.domain.model.PfSettings
import io.embodit.panelfit.domain.model.RatingEntry
import io.embodit.panelfit.domain.model.Specimen
import io.embodit.panelfit.domain.model.Vec3
import kotlin.math.abs
import kotlin.math.hypot

/** One PicoTheme type role: its theme size and, once measured on the panel, the size it actually renders at. */
data class TypeRole(val role: String, val group: Int, val themeSp: Float, val renderedSp: Float? = null) {
    /** The size the card uses: the measured one when it differs from the theme by more than 10 % (design `rules.math.text`). */
    val usedSp: Float
        get() = renderedSp?.takeIf { abs(it - themeSp) > themeSp * 0.10f } ?: themeSp

    val source: String
        get() = if (usedSp != themeSp) "rendered" else "theme"
}

/** One row of the type table. */
data class StyleRow(val role: TypeRole, val glyphMm: Float, val arcmin: Float, val px: Float, val ref: RefWord, val self: RatingEntry?)

/** Everything the card, the label and the export say about one specimen seen from one eye position. */
data class Readings(
    val id: Int,
    val dist: Float,
    val bearingDeg: Float,
    val elevDeg: Float,
    val mmW: Float,
    val mmH: Float,
    val angW: Float,
    val angH: Float,
    val eqW: Float,
    val eqH: Float,
    val fovShare: Float,
    val facingOff: Float,
    val facing: Facing,
    val reach: Reach,
    val neck: Neck,
    val styles: List<StyleRow>,
)

/** The 15 PicoTheme roles in the rate page's five groups (Display, Headline, Title, Body, Label; L / M / S). */
object TypeRoles {
    val GROUPS = listOf("display", "headline", "title", "body", "label")
    val SIZES = listOf("Large", "Medium", "Small")

    fun names(): List<String> = GROUPS.flatMap { g -> SIZES.map { g + it } }

    /** The role a sentence quotes: body text, the line people read most. */
    const val KEY_ROLE = "bodyMedium"

    /** A picture specimen is rated as a whole. */
    const val WHOLE = "whole"
}

object Report {
    /** Control centres of a specimen in its own metres (u right, v up from the centre), by layout. */
    fun controlPoints(s: Specimen): List<Pair<Float, Float>> {
        val hw = s.widthM / 2f
        val hh = s.heightPanelM / 2f
        return when (s.template.layout) {
            Layout.DIALOG -> listOf(-0.45f * hw to -0.72f * hh, 0.45f * hw to -0.72f * hh)
            Layout.COMPONENTS -> listOf(-0.6f * hw to 0.3f * hh, 0.2f * hw to 0.3f * hh, -0.4f * hw to -0.3f * hh, 0.6f * hw to -0.3f * hh)
            Layout.BIG_TYPE, Layout.BODY_TYPE, Layout.IMAGE -> listOf(0f to 0f)
            Layout.GENERIC -> listOf(-0.45f * hw to -0.75f * hh, 0.45f * hw to -0.75f * hh, 0f to -0.35f * hh)
        }
    }

    fun readings(s: Specimen, anchor: BodyAnchor, eye: Vec3, settings: PfSettings, roles: List<TypeRole>, fontScale: Float, mine: Float? = null): Readings {
        val pose = Placement.world(anchor, s.pose)
        val dist = pose.center.distanceTo(eye).coerceAtLeast(0.05f)
        val mmW = FitMath.mm(s.wDp.toFloat(), s.scale, settings.k)
        val mmH = FitMath.mm(s.hDp.toFloat(), s.scale, settings.k)
        val angW = FitMath.angleDeg(mmW, dist)
        val angH = FitMath.angleDeg(mmH, dist)
        val off = Placement.facingOff(pose, eye)
        val right = pose.facing.right
        val up = pose.facing.up
        val reachable =
            controlPoints(s).any { (u, v) ->
                val p = pose.center + right * u + up * v
                val rel = p - eye
                Verdicts.reachable(hypot(rel.x, rel.z), -rel.y, settings.armM)
            }
        val vert = Placement.elevation(eye, pose.center)
        val horiz = Placement.bearing(anchor, eye, pose.center)
        val styles =
            roles.map { r ->
                val g = FitMath.glyphMm(r.usedSp, fontScale, s.scale, settings.k)
                val a = FitMath.arcmin(g, dist)
                StyleRow(r, g, a, FitMath.px(a, settings.ppd), refWord(a, settings, s, mine), s.ratings[r.role])
            }
        return Readings(
            id = s.id,
            dist = dist,
            bearingDeg = horiz,
            elevDeg = vert,
            mmW = mmW,
            mmH = mmH,
            angW = angW,
            angH = angH,
            eqW = FitMath.eqDp(s.wDp.toFloat(), s.scale, dist),
            eqH = FitMath.eqDp(s.hDp.toFloat(), s.scale, dist),
            fovShare = FitMath.fovShare(angW, angH),
            facingOff = off,
            facing = Verdicts.facing(off),
            reach = if (reachable) Reach.DIRECT else Reach.RAY_ONLY,
            neck = Verdicts.neck(vert, horiz),
            styles = styles,
        )
    }

    /** Reference word with the user's own threshold as the "big enough" line when chosen and available. */
    fun refWord(arcmin: Float, settings: PfSettings, @Suppress("UNUSED_PARAMETER") s: Specimen? = null, mine: Float? = null): RefWord {
        val high = if (settings.useMine && mine != null) mine else settings.refHigh
        return Verdicts.ref(arcmin, high, settings.refLow.coerceAtMost(high))
    }
}
