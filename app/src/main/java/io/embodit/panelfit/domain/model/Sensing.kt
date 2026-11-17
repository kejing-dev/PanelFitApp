package io.embodit.panelfit.domain.model

/** A pointing ray in Stage metres. */
data class PointerRay(val origin: Vec3, val direction: Vec3)

/** Why head tracking cannot be used (S16). */
enum class BlockReason { NOT_IN_FULL_SPACE, WITHOUT_PERMISSION, DEVICE_NOT_SUPPORTED, LOST }
