package io.embodit.panelfit.domain.usecase

import kotlin.math.abs
import kotlin.math.sign

/** Which thumbstick axis drives the selected specimen (design `rules.placement.joystick`: axis lock). */
enum class StickAxis { NONE, TURN, PUSH }

/** One frame of stick output: [turnDegPerS] about the specimen's own vertical (+ = clockwise from above), [pushMps] away from the eye. */
data class StickOut(val axis: StickAxis, val turnDegPerS: Float, val pushMps: Float)

/**
 * Thumbstick -> motion, a pure function used by the real controller and by the debug `stick` command alike.
 * Dead zone 0.15; beyond it the rate grows linearly to 60 degrees/s (X) and 0.5 m/s (Y, stick forward = farther).
 * The first axis past the dead zone keeps the stick until both axes are back inside it.
 */
object StickMap {
    const val DEAD = 0.15f
    const val TURN_DEG_PER_S = 60f
    const val PUSH_MPS = 0.5f

    fun shape(v: Float): Float = if (abs(v) <= DEAD) 0f else sign(v) * ((abs(v) - DEAD) / (1f - DEAD)).coerceAtMost(1f)

    fun map(x: Float, y: Float, locked: StickAxis): StickOut {
        val sx = shape(x)
        val sy = shape(y)
        val axis =
            when {
                sx == 0f && sy == 0f -> StickAxis.NONE
                // locked: the other axis does nothing, even when the locked one is back in its dead zone
                locked != StickAxis.NONE -> locked
                abs(sx) >= abs(sy) -> StickAxis.TURN
                else -> StickAxis.PUSH
            }
        return when (axis) {
            StickAxis.NONE -> StickOut(StickAxis.NONE, 0f, 0f)
            StickAxis.TURN -> StickOut(StickAxis.TURN, sx * TURN_DEG_PER_S, 0f)
            StickAxis.PUSH -> StickOut(StickAxis.PUSH, 0f, sy * PUSH_MPS)
        }
    }
}
