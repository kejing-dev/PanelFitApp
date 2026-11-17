package io.embodit.panelfit.domain.usecase

import io.embodit.panelfit.domain.model.Rating
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.roundToInt

/**
 * The card's numbers (design `rules.math`). Pure functions: given dp, scale, k and distance they return millimetres,
 * degrees, equivalent dp, arc minutes and headset pixels. k changes only what is shown; the equivalent dp does not
 * depend on it.
 */
object FitMath {
    /** Latin cap height per em (design: 0.70) and the height of a CJK glyph per em (0.90). */
    const val CAP_EM = 0.70f
    const val CJK_EM = 0.90f

    /** Default field of view of one eye, degrees (by Build.MODEL; unconfirmed, a reference number only). */
    const val FOV_H = 104f
    const val FOV_V = 100f

    fun mm(dp: Float, scale: Float, k: Float): Float = dp * scale * k

    /** Angular size, degrees, of a length [mm] seen from [distM] (eye to the panel centre). */
    fun angleDeg(mm: Float, distM: Float): Float = (2.0 * atan(mm / 1000.0 / 2.0 / distM) * 180.0 / PI).toFloat()

    /** dp needed at scale 1 and 1 m for the same angular size; independent of k. */
    fun eqDp(dp: Float, scale: Float, distM: Float): Float = dp * scale / distM

    /** Height of a glyph in mm: sp x font scale x em share x panel scale x k. */
    fun glyphMm(sp: Float, fontScale: Float, scale: Float, k: Float, em: Float = CAP_EM): Float = sp * fontScale * em * scale * k

    /** Arc minutes of a glyph [glyphMm] high at [distM]. */
    fun arcmin(glyphMm: Float, distM: Float): Float = (atan(glyphMm / 1000.0 / distM) * 180.0 / PI * 60.0).toFloat()

    /** Headset pixels for [arcmin] at [ppd] pixels per degree. */
    fun px(arcmin: Float, ppd: Float): Float = arcmin / 60f * ppd

    /** Share of one eye's field of view, 0..1. */
    fun fovShare(angW: Float, angH: Float, fovH: Float = FOV_H, fovV: Float = FOV_V): Float = angW * angH / (fovH * fovV)

    /** One decimal, half up (the unit-test benchmarks are given to 0.1). */
    fun r1(v: Float): Float = (v * 10f).roundToInt() / 10f
}

/** Reference words for a glyph's arc minutes (design `rules.verdicts.textRef`); never the verdict. */
enum class RefWord { BIG, SMALLISH, TOO_SMALL }

/** Hand reach (design `rules.verdicts.reach`). */
enum class Reach { DIRECT, RAY_ONLY }

/** Neck (design `rules.verdicts.neck`). */
enum class Neck { COMFORTABLE, ACCEPTABLE, STRAIN }

/** Facing (design `rules.verdicts.facing`). */
enum class Facing { SQUARE, SLANTED, TOO_SLANTED }

object Verdicts {
    fun ref(arcmin: Float, high: Float, low: Float): RefWord =
        when {
            arcmin >= high -> RefWord.BIG
            arcmin >= low -> RefWord.SMALLISH
            else -> RefWord.TOO_SMALL
        }

    /**
     * Directly reachable: horizontal distance from the eye between 0.35 m and 0.95 x arm length and between 0.10 m
     * and 0.50 m below the eye.
     */
    fun reachable(horizontalM: Float, belowEyeM: Float, armM: Float): Boolean =
        horizontalM in 0.35f..(0.95f * armM) && belowEyeM in 0.10f..0.50f

    /** Neck verdict for a panel centre [vertDeg] above eye level and [horizDeg] off body forward. */
    fun neck(vertDeg: Float, horizDeg: Float): Neck {
        val h = kotlin.math.abs(horizDeg)
        return when {
            vertDeg in -40f..10f && h <= 30f -> Neck.COMFORTABLE
            vertDeg in -50f..20f && h <= 55f -> Neck.ACCEPTABLE
            else -> Neck.STRAIN
        }
    }

    /** [offDeg]: angle between the panel's normal and the direction from the panel to the eye. */
    fun facing(offDeg: Float): Facing =
        when {
            offDeg <= 15f -> Facing.SQUARE
            offDeg <= 30f -> Facing.SLANTED
            else -> Facing.TOO_SLANTED
        }

    /**
     * "Your" smallest legible arc minutes from self-ratings ([arcmin] to [Rating]); null until at least five ratings
     * exist. The line sits halfway between the smallest glyph rated legible and the largest one rated not legible (or
     * "barely") below it; with no such pair, at the smallest legible one.
     */
    fun myThreshold(ratings: List<Pair<Float, Rating>>): Float? {
        if (ratings.size < MIN_RATINGS) return null
        val ok = ratings.filter { it.second == Rating.OK }.map { it.first }
        if (ok.isEmpty()) return ratings.maxOf { it.first }
        val minOk = ok.min()
        val notBelow = ratings.filter { it.second != Rating.OK && it.first < minOk }.map { it.first }
        return if (notBelow.isEmpty()) minOk else (minOk + notBelow.max()) / 2f
    }

    const val MIN_RATINGS = 5
}
