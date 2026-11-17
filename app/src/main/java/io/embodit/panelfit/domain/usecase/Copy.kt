package io.embodit.panelfit.domain.usecase

import io.embodit.panelfit.domain.model.Rating
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** One piece of copy in both languages (design `content.tone`; Chinese copied from the design package and sample). */
data class L(val zh: String, val en: String) {
    operator fun invoke(english: Boolean): String = if (english) en else zh

    fun format(english: Boolean, vararg args: Any): String = String.format(Locale.US, invoke(english), *args)
}

/** All copy of the app. Plain data, so the domain can build sentences and the tests can read them. */
object Copy {
    val appName = L("面板试衣间", "Panel Fit")

    // pages (TabBar)
    val tabLibrary = L("面板库", "Library")
    val tabAdjust = L("调节", "Adjust")
    val tabRate = L("看得清吗", "Legibility")
    val tabCard = L("规格卡", "Spec")
    val tabSettings = L("设置", "Settings")
    val pageList = L("面板列表", "Panel list")
    val pageImport = L("导入截图", "Import image")
    val back = L("返回", "Back")
    val sound = L("声音", "Sound")

    // S00 / S01
    val preparing = L("准备中…", "Getting ready…")
    val preparingSub = L("在找你的头在哪，最多 2 秒", "Finding your head, 2 s at most")
    val preparingNote = L("准备好就摆上你上次的布局", "Your last layout comes back when ready")
    val welcomeTitle = L("做面板之前，先戴上试试大小。", "Try a panel on before you build it.")
    val welcomeBody = L(
        "把要做的面板 1:1 摆在你面前，推远拉近看字清不清、手够不够，最后拿一张规格卡给研发。",
        "Put the panel in front of you at 1:1. Push it away, pull it close, check the text and your reach, then take a spec card to the devs.",
    )
    val welcomeStep1 = L("① 挑一块面板", "① Pick a panel")
    val welcomeStep2 = L("② 摇杆推远拉近，看字", "② Push and pull with the stick, read the text")
    val welcomeStep3 = L("③ 点「看得清 / 勉强 / 看不清」，拿规格卡", "③ Rate it Clear / Barely / Can't read, take the card")
    val tryOne = L("试一块", "Try one")
    val welcomeOnce = L("只出现一次，设置里能再看", "Shown once. Settings can show it again")

    // S02
    val libraryHint = L("点一张卡，它就摆到你面前 1 米", "Tap a card. It lands 1 m in front of you")
    val libraryFull = L("最多 6 块，先在面板列表删一块", "6 at most. Delete one in the panel list first")
    val listButton = L("面板列表 · %d 块", "Panel list · %d")
    val customSub = L("宽高、缩放自己定", "Set size and scale yourself")
    val importSub = L("从 files/import/ 里挑", "Pick from files/import/")
    val sizeLine = L("%d × %d dp · 缩放 %s", "%d × %d dp · scale %s")

    // S04
    val done = L("完成", "Done")
    val rowDist = L("距离", "Distance")
    val rowHeight = L("高度", "Height")
    val rowSide = L("左右", "Left/right")
    val rowFacing = L("朝向", "Facing")
    val rowTilt = L("倾角", "Tilt")
    val tiltToEye = L("朝向眼睛", "Face my eyes")
    val rowWidth = L("宽", "Width")
    val rowHeightDp = L("高", "Height")
    val rowScale = L("缩放", "Scale")
    val rowSurface = L("底", "Surface")
    val glass = L("玻璃", "Glass")
    val solid = L("实色", "Solid")
    val overlayReach = L("可达区", "Reach")
    val overlayNeck = L("舒适区", "Comfort")
    val overlayRuler = L("对照尺", "Ruler")
    val resizeNote = L("改宽高要换一块面板，停 0.5 秒才换", "A new size swaps the panel after a 0.5 s pause")
    val page = L("第 %d / 2 页", "Page %d / 2")
    val noneTitle = L("先点一块样张，", "Tap a specimen first,")
    val noneSub = L("或者去面板库加一块。", "or add one from the library.")
    val toLibrary = L("去面板库", "Go to library")
    val noneStick = L("摇杆这会儿不动任何东西", "The stick moves nothing right now")
    val belowEyeCm = L("眼下 %d cm", "%d cm below eye")
    val aboveEyeCm = L("眼上 %d cm", "%d cm above eye")
    val eyeLevel = L("眼平", "Eye level")
    val ahead0 = L("正前 0°", "Ahead 0°")
    val rightDeg = L("右 %d°", "Right %d°")
    val leftDeg = L("左 %d°", "Left %d°")
    val squareDeg = L("正对 0°", "Square 0°")
    val turnRight = L("右转 %d°", "Turned right %d°")
    val turnLeft = L("左转 %d°", "Turned left %d°")

    // S07
    val groupNames = listOf(L("大字", "Display"), L("标题", "Headline"), L("小标题", "Title"), L("正文", "Body"), L("标签", "Label"))
    val rateOk = L("看得清", "Clear")
    val rateMeh = L("勉强", "Barely")
    val rateNo = L("看不清", "Can't read")
    val refBig = L("够大", "big enough")
    val refSmallish = L("偏小", "smallish")
    val refTooSmall = L("太小", "too small")
    val refLine = L("参考：%s", "Ref: %s")
    val arcLine = L("%s′ · 约 %d px", "%s′ · about %d px")
    val overlaysHidden = L("叠加层先藏起来了，专心看字", "Overlays are hidden while you read")
    val wholeRow = L("整体看得清吗", "Can you read it overall?")

    // S08
    val cardDist = L("距离 · 位置", "Distance · place")
    val cardMm = L("物理尺寸", "Physical size")
    val cardAngle = L("角尺寸", "Angular size")
    val cardEq = L("等效 dp", "Equivalent dp")
    val cardReach = L("手够得着吗", "Within reach?")
    val cardNeck = L("脖子", "Neck")
    val cardOcclusion = L("遮挡", "Overlap")
    val fovPart = L("占单眼视场约 %s%%", "about %s%% of one eye's view")
    val reachYes = L("手能直接碰", "You can touch it")
    val reachNo = L("够不着，用射线 / 眼手点", "Out of reach, use the ray or eye + pinch")
    val neckOk = L("不用抬头、不用大低头", "No looking up, no big nod down")
    val neckMeh = L("能接受，久了会累", "Okay, tiring after a while")
    val neckBad = L("费脖子", "Hard on the neck")
    val noOverlap = L("没有", "None")
    val smallPrint = L(
        "换算 %s mm/dp（经验值，可在设置改）· 参考线 %s′ / %s′ 来自平板显示人因标准 · Stage 附着面板口径，Shared Space 窗口未校",
        "%s mm/dp (rule of thumb, change it in Settings) · ref lines %s′ / %s′ from flat-display ergonomics · Stage attachment panels; Shared Space windows not calibrated",
    )
    val save = L("存成文件", "Save as file")
    val lastSaved = L("上次存到：%s", "Last saved to: %s")
    val notSavedYet = L("存好会在这里告诉你路径", "The path shows here after saving")
    val turnPage = L("翻页看字号表", "Next page: type table")
    val typeTableTitle = L("字号表 · %s @ %s m", "Type table · %s @ %s m")
    val colRole = L("字号", "Style")
    val colArc = L("角分", "Arcmin")
    val colPx = L("像素", "Pixels")
    val colRef = L("参考", "Ref")
    val colSelf = L("你的自评", "Your rating")
    val notRated = L("还没自评", "Not rated")
    val facingLine = L("朝向：%s · 底：%s", "Facing: %s · Surface: %s")
    val typeNote = L("字号从 PicoTheme 运行时读，乘系统字体缩放；像素按 %s 像素 / 度估（未确认）", "Sizes are read from PicoTheme at run time times the system font scale; pixels at %s px per degree (unconfirmed)")
    val renderedNote = L("主题值 ≠ 实际渲染", "theme size ≠ rendered")

    // S09 / hints
    val savedPublic = L("存好了：%s", "Saved: %s")
    val savedPrivate = L("公共文件夹写不进，存到了应用自己的文件夹。电脑上 adb pull %s", "Public folder blocked; saved to the app folder. adb pull %s")
    val savedNone = L("两处都存不了。卡还在这里，截个图也行", "Couldn't save anywhere. The card is still here, a screenshot works too")
    val hintStick = L("摇杆左右转 · 前后推远拉近", "Stick left/right turns · forward/back pushes and pulls")
    val hintDrag = L("拖到虚线框附近会吸进去", "Drop it near a dashed frame and it snaps in")
    val hintHead = L("头部追踪不可用，角度先不算（面板照常能用）", "Head tracking is off, so no angles for now (the panel still works)")
    val hintDefault = L("摇杆左右转、前后推远拉近；也能捏住样张下面的标签拖", "Stick turns and pushes; you can also pinch the label under a specimen and drag")
    val hintLookAhead = L("看向你想放的正前方，头停一下就摆好", "Look where you want the front to be and hold still")
    val hintRestored = L("布局已恢复", "Layout restored")
    val hintBlocksWindow = L("这块挡住工具面板了，先让它变淡。", "This one covers the tool panel, so it's faded.")
    val moveItAway = L("把它挪开", "Move it aside")
    val hintImportBad = L("这张图打不开，换一张试试", "Can't open that image. Try another one")

    // S12
    val moveToFront = L("移到面前", "Bring here")
    val hide = L("隐藏", "Hide")
    val show = L("显示", "Show")
    val delete = L("删除", "Delete")
    val deleteAsk = L("删掉？", "Delete?")
    val cancel = L("取消", "Cancel")
    val listNote = L("最多 6 块。点删除会先问一句。", "6 at most. Delete asks first.")
    val listEmpty = L("还没有样张，去面板库加一块", "No specimens yet. Add one from the library")
    val hiddenTag = L("已隐藏", "hidden")

    // S15
    val importEmpty = L("把截图 adb push 到这个文件夹", "adb push your screenshot into this folder")
    val importCmd = L("放图：adb push shot.png /sdcard/Android/data/io.embodit.panelfit/files/import/", "adb push shot.png /sdcard/Android/data/io.embodit.panelfit/files/import/")
    val importNote = L("高超过 720 dp 的图会等比缩小，卡上会写", "Images taller than 720 dp are scaled down, and the card says so")
    val importLine = L("%d × %d px → %d × %d dp", "%d × %d px → %d × %d dp")
    val importScaled = L("（高超 720，等比缩小）", " (over 720 high, scaled down)")

    // S13
    val calib = L("校准", "Calibrate")
    val general = L("通用", "General")
    val setK = L("换算系数", "dp factor")
    val setHigh = L("够大线", "Big enough at")
    val setLow = L("太小线", "Too small below")
    val setMine = L("用我的阈值", "Use my threshold")
    val setPpd = L("像素密度", "Pixel density")
    val setArm = L("臂长", "Arm length")
    val setPosture = L("站着 / 坐着", "Standing / sitting")
    val stand = L("站", "Stand")
    val sit = L("坐", "Sit")
    val on = L("开", "On")
    val off = L("关", "Off")
    val howCalib = L(
        "怎么校：拿真尺子在透视里量「常用控制面板」有多宽（毫米），除以 300 就是换算系数。只改卡上的数，面板本身不变。",
        "How: hold a real ruler to the Control panel in passthrough, measure its width in mm and divide by 300. Only the card's numbers change, not the panel.",
    )
    val howCalibShort = L("改换算系数只改卡上的毫米数", "The dp factor only changes the card's millimetres")
    val mineNone = L("自评够 5 档后才有", "Needs 5 ratings first")
    val setFrames = L("推荐位虚线框", "Suggested frames")
    val setVolume = L("音量", "Volume")
    val low = L("低", "Low")
    val mid = L("中", "Mid")
    val setLanguage = L("语言", "Language")
    val setRecall = L("叫回工具面板", "Recall tool panel")
    val recall = L("叫回", "Recall")
    val setAllFront = L("全部样张回到面前", "All specimens to front")
    val allFront = L("回到面前", "To front")
    val setClear = L("清空全部样张", "Clear all specimens")
    val clear = L("清空", "Clear")
    val setAbout = L("关于", "About")
    val setWelcome = L("欢迎页", "Welcome")
    val showWelcome = L("再看一次", "Show again")
    val quitRow = L("退出应用", "Quit app")
    val quit = L("退出", "Quit")
    val quitAsk = L("退出面板试衣间？", "Quit Panel Fit?")
    val quitNote = L("布局会记住，下次打开还在", "Your layout is kept for next time")
    val clearAsk = L("把 %d 块样张都清掉？", "Clear all %d specimens?")

    // window chip on the Stage
    val openPanel = L("打开面板", "Open panel")

    // specimen content
    val sampleBody = L("这里是两行正文，用 bodyMedium。看看在这个距离读起来累不累。", "Two lines of body text in bodyMedium. See if it is tiring to read from here.")
    val option1 = L("选项一", "One")
    val option2 = L("选项二", "Two")
    val option3 = L("选项三", "Three")
    val ok = L("确定", "OK")
    val dialogTitle = L("要保存这次的改动吗？", "Save these changes?")
    val dialogBody = L("不保存的话，刚才调的位置和尺寸会丢掉。", "If you don't, the place and size you just set are lost.")
    val legibleWord = L("看得清", "Legible")
    val notCalibrated = L("Shared Space 窗口未校", "Shared Space windows not calibrated")
    val switchLabel = L("开关", "Switch")
    val iconButtonLabel = L("图标按钮", "Icon button")
    val buttonLabel = L("按钮", "Button")

    // size label
    val labelLine1 = L("%s m · %s · %s", "%s m · %s · %s")
    val labelLine2 = L("%s° × %s° · 等效 %d dp", "%s° × %s° · eq. %d dp")
    val labelResizing = L("换尺寸中…", "Resizing…")
    val labelBlocksWindow = L("挡住工具面板了", "Covers the tool panel")
    val labelCovers = L("挡住了%s那块的%s", "Covers the %s one's %s")
    val labelNoAngles = L("— · 头部追踪不可用", "— · no head tracking")
    val belowDeg = L("眼下 %d°", "%d° below eye")
    val aboveDeg = L("眼上 %d°", "%d° above eye")
    val facingSquare = L("正对", "square")
    val facingSlanted = L("有点斜", "a bit slanted")
    val facingTooSlanted = L("太斜，字会变形", "too slanted, text warps")
    val sideLeft = L("左边", "left")
    val sideRight = L("右边", "right")
    val sideBehind = L("后面", "back")
    val parts = mapOf(
        Part.WHOLE to L("整块", "whole panel"),
        Part.TOP to L("上半", "top half"),
        Part.BOTTOM to L("下半", "bottom half"),
        Part.LEFT to L("左半", "left half"),
        Part.RIGHT to L("右半", "right half"),
        Part.TOP_LEFT to L("左上角", "top left corner"),
        Part.TOP_RIGHT to L("右上角", "top right corner"),
        Part.BOTTOM_LEFT to L("左下角", "bottom left corner"),
        Part.BOTTOM_RIGHT to L("右下角", "bottom right corner"),
        Part.MIDDLE to L("中间", "middle"),
    )
    val coveredBy = L("挡住了：%s那块的%s", "Covers: the %s one's %s")
    val isCovered = L("%s被%s挡住了", "Its %s is covered by %s")
    val sideFront = L("前面", "front")

    // sentence
    val sentence = L("这块%s放 %s m：%s° × %s°，等效 %d dp。%s；%s。", "This %s at %s m: %s° × %s°, eq. %d dp. %s; %s.")
    val sentenceRole = L("%d sp 正文 %s′，%s", "%d sp body text is %s′, %s")
    val sentenceWhole = L("整体%s", "overall %s")
    val youSaid = L("你点了「%s」", "you said \"%s\"")
    val reachShortYes = L("手能直接碰", "you can touch it")
    val reachShortNo = L("手够不着，用射线点", "out of reach, use the ray")
    val noHead = L("头部追踪不可用，角度先不算", "Head tracking is off, so no angles for now")

    fun rating(r: Rating?): L? =
        when (r) {
            Rating.OK -> rateOk
            Rating.MEH -> rateMeh
            Rating.NO -> rateNo
            null -> null
        }

    fun ref(w: RefWord): L =
        when (w) {
            RefWord.BIG -> refBig
            RefWord.SMALLISH -> refSmallish
            RefWord.TOO_SMALL -> refTooSmall
        }

    fun facing(f: Facing): L =
        when (f) {
            Facing.SQUARE -> facingSquare
            Facing.SLANTED -> facingSlanted
            Facing.TOO_SLANTED -> facingTooSlanted
        }

    fun neck(n: Neck): L =
        when (n) {
            Neck.COMFORTABLE -> neckOk
            Neck.ACCEPTABLE -> neckMeh
            Neck.STRAIN -> neckBad
        }

    fun side(s: Side): L =
        when (s) {
            Side.LEFT -> sideLeft
            Side.RIGHT -> sideRight
            Side.BEHIND -> sideBehind
        }

    /** "正前" / "右 40°" / "左 15°" (bearing right of body forward). */
    fun place(english: Boolean, bearing: Float): String {
        val d = bearing.roundToInt()
        return when {
            abs(d) < 3 -> if (english) "ahead" else "正前"
            d > 0 -> if (english) "right $d°" else "右 $d°"
            else -> if (english) "left ${-d}°" else "左 ${-d}°"
        }
    }

    fun elev(english: Boolean, elevDeg: Float): String {
        val d = elevDeg.roundToInt()
        return when {
            d == 0 -> eyeLevel(english)
            d < 0 -> belowDeg.format(english, -d)
            else -> aboveDeg.format(english, d)
        }
    }

    fun f1(v: Float): String = String.format(Locale.US, "%.1f", v)

    fun f2(v: Float): String = String.format(Locale.US, "%.2f", v)
}
