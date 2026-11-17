package io.embodit.panelfit.domain.model

/** How a specimen's content is laid out (design `content.templates[].content`). */
enum class Layout { GENERIC, DIALOG, BIG_TYPE, BODY_TYPE, COMPONENTS, IMAGE }

/**
 * The nine library cards (design `content.templates`). Sizes are dp at the template's scale; T7 starts from T1's
 * size and is changed on the adjust page, T8 takes the picture's size.
 */
enum class Template(val id: String, val zh: String, val en: String, val wDp: Int, val hDp: Int, val scale: Float, val layout: Layout) {
    T1("T1", "常用控制面板", "Control panel", 400, 252, 0.75f, Layout.GENERIC),
    T2("T2", "宽面板", "Wide panel", 520, 308, 0.75f, Layout.GENERIC),
    T3("T3", "对话框", "Dialog", 360, 200, 0.75f, Layout.DIALOG),
    T4("T4", "大窗口", "Large window", 1280, 720, 1.0f, Layout.GENERIC),
    T5("T5", "大字样张", "Big type", 600, 520, 1.0f, Layout.BIG_TYPE),
    T5B("T5b", "正文样张", "Body type", 600, 520, 1.0f, Layout.BODY_TYPE),
    T6("T6", "组件样张", "Component specimen", 560, 360, 1.0f, Layout.COMPONENTS),
    T7("T7", "自定义尺寸", "Custom size", 400, 252, 0.75f, Layout.GENERIC),
    T8("T8", "导入截图", "Imported image", 400, 252, 1.0f, Layout.IMAGE);

    fun name(english: Boolean): String = if (english) en else zh

    companion object {
        fun of(id: String): Template? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

enum class Surface { GLASS, SOLID }

enum class Language { ZH, EN }

enum class Posture { STAND, SIT }

/** Self-rating of one type role (design `rules.verdicts.textSelf`). */
enum class Rating { OK, MEH, NO }

data class RatingEntry(val rating: Rating, val arcmin: Float)

/**
 * A specimen's pose relative to the body anchor (eye position and body yaw when it was placed or the layout was
 * brought to the front): [dist] eye to centre, [heightM] centre above the eye (negative below), [yawDeg] right of body
 * forward, [facingDeg] turn about its own vertical away from facing the eye (right = clockwise seen from above),
 * [tiltDeg] turn about its own horizontal axis (positive: face tilted upwards). The layout is stored this way, so
 * "back to the front" re-places the whole layout from the current head and keeps it as it was (design `persistence`).
 */
data class PolarPose(val dist: Float, val heightM: Float, val yawDeg: Float, val facingDeg: Float = 0f, val tiltDeg: Float = 0f)

/** Body anchor: eye position and body yaw (radians, 0 = Stage -Z, positive turns left as about +Y). */
data class BodyAnchor(val eye: Vec3, val yaw: Float)

/** Placement state machine of a specimen (profile D1 "placement must have an end"). */
enum class PlaceMode { PLACING, LOCKED }

data class Specimen(
    val id: Int,
    val template: Template,
    val wDp: Int,
    val hDp: Int,
    val scale: Float,
    val surface: Surface,
    val pose: PolarPose,
    val hidden: Boolean = false,
    val mode: PlaceMode = PlaceMode.PLACING,
    /** T8: file name in files/import/, and whether the picture was scaled down to fit 720 dp. */
    val image: String? = null,
    val imageScaled: Boolean = false,
    /** Self-ratings by type role ("bodyMedium", ..., "whole" for a picture) with the arc minutes when rated. */
    val ratings: Map<String, RatingEntry> = emptyMap(),
) {
    fun name(english: Boolean): String = template.name(english)

    /** Width in metres at the SDK's real size (1 dp = 0.8 mm at scale 1, PanelRig.DP_M); not the display k. */
    val widthM: Float
        get() = wDp * DP_M * scale

    val heightPanelM: Float
        get() = hDp * DP_M * scale

    companion object {
        /** Metres per dp of a Stage attachment panel at scale 1 (Spatial Ruler finding). */
        const val DP_M = 0.0008f
    }
}

/** The app's settings (design `panels.settings`). */
data class PfSettings(
    val k: Float = 0.80f,
    val refHigh: Float = 24f,
    val refLow: Float = 16f,
    val useMine: Boolean = false,
    val ppd: Float = 20f,
    val armM: Float = 0.62f,
    val posture: Posture = Posture.STAND,
    val frames: Boolean = true,
    val sound: Boolean = true,
    val volumeHigh: Boolean = false,
    val language: Language = Language.ZH,
    val welcomed: Boolean = false,
)

/** Which overlays are on (S10). */
data class Overlays(val reach: Boolean = false, val neck: Boolean = false, val ruler: Boolean = false)
