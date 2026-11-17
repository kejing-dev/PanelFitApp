package io.embodit.panelfit.domain

import io.embodit.panelfit.data.PfCodec
import io.embodit.panelfit.data.Saved
import io.embodit.panelfit.domain.model.BodyAnchor
import io.embodit.panelfit.domain.model.Overlays
import io.embodit.panelfit.domain.model.PfSettings
import io.embodit.panelfit.domain.model.PlaceMode
import io.embodit.panelfit.domain.model.PolarPose
import io.embodit.panelfit.domain.model.Quat4
import io.embodit.panelfit.domain.model.Rating
import io.embodit.panelfit.domain.model.RatingEntry
import io.embodit.panelfit.domain.model.Specimen
import io.embodit.panelfit.domain.model.Surface
import io.embodit.panelfit.domain.model.Template
import io.embodit.panelfit.domain.model.Vec3
import io.embodit.panelfit.domain.usecase.Facing
import io.embodit.panelfit.domain.usecase.Field
import io.embodit.panelfit.domain.usecase.FitMath
import io.embodit.panelfit.domain.usecase.Neck
import io.embodit.panelfit.domain.usecase.Occlusion
import io.embodit.panelfit.domain.usecase.Part
import io.embodit.panelfit.domain.usecase.PfReducer
import io.embodit.panelfit.domain.usecase.PfState
import io.embodit.panelfit.domain.usecase.Placement
import io.embodit.panelfit.domain.usecase.Reach
import io.embodit.panelfit.domain.usecase.Rect3
import io.embodit.panelfit.domain.usecase.RefWord
import io.embodit.panelfit.domain.usecase.Report
import io.embodit.panelfit.domain.usecase.Side
import io.embodit.panelfit.domain.usecase.SpecText
import io.embodit.panelfit.domain.usecase.StickAxis
import io.embodit.panelfit.domain.usecase.StickMap
import io.embodit.panelfit.domain.usecase.TypeRole
import io.embodit.panelfit.domain.usecase.TypeRoles
import io.embodit.panelfit.domain.usecase.Verdicts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class PanelFitDomainTest {
    private val eye = Vec3(0f, 1.6f, 0f)
    private val anchor = BodyAnchor(eye, 0f)
    private val bodyRole = TypeRole("bodyMedium", 3, 14f)

    private fun deg(r: Float) = r * 180f / PI.toFloat()

    private fun specimen(t: Template, dist: Float, id: Int = 1, scale: Float = t.scale) =
        Specimen(id, t, t.wDp, t.hDp, scale, Surface.GLASS, PolarPose(dist, 0f, 0f))

    private fun readyState(windowYaw: Float? = null): PfState {
        val s = PfReducer.ready(PfState(settings = PfSettings(welcomed = true)), eye, Vec3.AHEAD, headOk = true, restored = false, nowMs = 0L)
        return s.copy(windowYawDeg = windowYaw, headYawDeg = 0f)
    }

    // -- rules.math examples (unit-test benchmarks, 0.1 precision) -------------------------------------------------

    @Test
    fun t1At09m() {
        val r = Report.readings(specimen(Template.T1, 0.9f), anchor, eye, PfSettings(), listOf(bodyRole), 1f)
        assertEquals(15.2f, FitMath.r1(r.angW), 1e-4f)
        assertEquals(9.6f, FitMath.r1(r.angH), 1e-4f)
        assertEquals(333f, Math.round(r.eqW).toFloat(), 1e-4f)
        assertEquals(22.5f, FitMath.r1(r.styles.single().arcmin), 1e-4f)
        assertEquals(240f, r.mmW, 0.01f)
        assertEquals(151.2f, r.mmH, 0.01f)
    }

    @Test
    fun t2At12m() {
        val r = Report.readings(specimen(Template.T2, 1.2f), anchor, eye, PfSettings(), emptyList(), 1f)
        assertEquals(14.8f, FitMath.r1(r.angW), 1e-4f)
        assertEquals(8.8f, FitMath.r1(r.angH), 1e-4f)
        assertEquals(325f, Math.round(r.eqW).toFloat(), 1e-4f)
    }

    @Test
    fun t5bAt2m() {
        val r = Report.readings(specimen(Template.T5B, 2.0f), anchor, eye, PfSettings(), listOf(bodyRole), 1f)
        assertEquals(13.5f, FitMath.r1(r.styles.single().arcmin), 1e-4f)
    }

    @Test
    fun equivalentDpIgnoresK() {
        val a = Report.readings(specimen(Template.T1, 0.9f), anchor, eye, PfSettings(k = 0.8f), emptyList(), 1f)
        val b = Report.readings(specimen(Template.T1, 0.9f), anchor, eye, PfSettings(k = 1.1f), emptyList(), 1f)
        assertEquals(a.eqW, b.eqW, 1e-4f)
        assertTrue(b.mmW > a.mmW)
    }

    @Test
    fun renderedSizeWinsWhenTenPercentOff() {
        assertEquals(14f, TypeRole("bodyMedium", 3, 14f, 14.9f).usedSp, 1e-4f)
        assertEquals(16f, TypeRole("headlineSmall", 1, 24f, 16f).usedSp, 1e-4f)
    }

    // -- verdicts ------------------------------------------------------------------------------------------------

    @Test
    fun referenceWords() {
        assertEquals(RefWord.BIG, Verdicts.ref(24f, 24f, 16f))
        assertEquals(RefWord.SMALLISH, Verdicts.ref(22.5f, 24f, 16f))
        assertEquals(RefWord.TOO_SMALL, Verdicts.ref(13.5f, 24f, 16f))
    }

    @Test
    fun reachNeckFacing() {
        assertTrue(Verdicts.reachable(0.5f, 0.3f, 0.62f))
        assertFalse(Verdicts.reachable(0.6f, 0.3f, 0.62f)) // 0.95 x 0.62 = 0.589
        assertFalse(Verdicts.reachable(0.5f, 0.05f, 0.62f))
        assertEquals(Neck.COMFORTABLE, Verdicts.neck(-10f, 0f))
        assertEquals(Neck.ACCEPTABLE, Verdicts.neck(-45f, 0f))
        assertEquals(Neck.ACCEPTABLE, Verdicts.neck(0f, 40f))
        assertEquals(Neck.STRAIN, Verdicts.neck(30f, 0f))
        assertEquals(Facing.SQUARE, Verdicts.facing(10f))
        assertEquals(Facing.SLANTED, Verdicts.facing(20f))
        assertEquals(Facing.TOO_SLANTED, Verdicts.facing(31f))
    }

    @Test
    fun t1At09mIsOutOfReach() {
        val r = Report.readings(specimen(Template.T1, 0.9f).copy(pose = Placement.ahead(0.9f)), anchor, eye, PfSettings(), emptyList(), 1f)
        assertEquals(Reach.RAY_ONLY, r.reach)
        assertEquals(Neck.COMFORTABLE, r.neck)
        val near = Report.readings(specimen(Template.T6, 0.5f).copy(pose = Placement.ahead(0.5f, 0f, 35f)), anchor, eye, PfSettings(), emptyList(), 1f)
        assertEquals(Reach.DIRECT, near.reach)
    }

    @Test
    fun myThresholdNeedsFiveRatings() {
        val four = listOf(30f to Rating.OK, 25f to Rating.OK, 20f to Rating.MEH, 14f to Rating.NO)
        assertNull(Verdicts.myThreshold(four))
        val five = four + (22f to Rating.OK)
        assertEquals((22f + 20f) / 2f, Verdicts.myThreshold(five)!!, 1e-4f)
    }

    // -- stick ---------------------------------------------------------------------------------------------------

    @Test
    fun stickDeadZoneRatesAndLock() {
        assertEquals(StickAxis.NONE, StickMap.map(0.1f, -0.14f, StickAxis.NONE).axis)
        val right = StickMap.map(1f, 0f, StickAxis.NONE)
        assertEquals(StickAxis.TURN, right.axis)
        assertEquals(60f, right.turnDegPerS, 1e-4f)
        val fwd = StickMap.map(0f, 1f, StickAxis.NONE)
        assertEquals(0.5f, fwd.pushMps, 1e-4f)
        // locked to TURN: a stronger Y does nothing until both axes rest
        val locked = StickMap.map(0.3f, 0.9f, StickAxis.TURN)
        assertEquals(StickAxis.TURN, locked.axis)
        assertEquals(0f, locked.pushMps, 1e-4f)
    }

    @Test
    fun stickRightTurnsClockwiseSeenFromAbove() {
        val p0 = Placement.ahead(1f, 0f, 0f)
        val p1 = PfReducer.stickStep(p0, StickMap.map(1f, 0f, StickAxis.NONE), 0.5f, 0.3f)
        assertEquals(30f, p1.facingDeg, 1e-3f)
        val n0 = Placement.world(anchor, p0).facing.normal
        val n1 = Placement.world(anchor, p1).facing.normal
        // clockwise from above: the normal (towards the viewer, +Z) swings towards -X
        assertTrue(n0.cross(n1).y < 0f)
    }

    @Test
    fun stickPushClampsAndReleaseSnaps() {
        var p = Placement.ahead(1f, 0f, 10f)
        repeat(200) { p = PfReducer.stickStep(p, StickMap.map(0f, 1f, StickAxis.NONE), 0.1f, 0.3f) }
        assertEquals(3f, p.dist, 1e-4f)
        repeat(200) { p = PfReducer.stickStep(p, StickMap.map(0f, -1f, StickAxis.NONE), 0.1f, 1.02f) }
        assertEquals(0.8f, p.dist, 1e-4f) // wide panel: 0.8 m closest
        assertEquals(1.5f, Placement.snapDist(1.48f), 1e-4f)
        assertEquals(1.45f, Placement.snapDist(1.45f), 1e-4f)
        assertEquals(0f, Placement.snapFacing(8f), 1e-4f)
        assertEquals(14f, Placement.snapFacing(14f), 1e-4f)
    }

    // -- placeUpright / placement --------------------------------------------------------------------------------

    private fun headLooking(yawDeg: Float, pitchDeg: Float, rollDeg: Float): Quat4 =
        Quat4.axisAngle(Vec3.UP, yawDeg * PI.toFloat() / 180f) * Quat4.axisAngle(Vec3.RIGHT, pitchDeg * PI.toFloat() / 180f) *
            Quat4.axisAngle(Vec3(0f, 0f, 1f), rollDeg * PI.toFloat() / 180f)

    @Test
    fun placeUprightIgnoresPitchAndRoll() {
        for ((pitch, roll) in listOf(-30f to 0f, 30f to 0f, 0f to 20f, -30f to 20f)) {
            val rot = headLooking(25f, pitch, roll)
            val a = Placement.placeUpright(eye, rot.rotate(Vec3.AHEAD))
            val w = Placement.world(a, Placement.ahead())
            val up = w.facing.up
            assertTrue("up off by ${deg(acos(up.dot(Vec3.UP)))}", deg(acos(up.dot(Vec3.UP).coerceIn(-1f, 1f))) < 1f)
            val rel = w.center - eye
            val below = -deg(atan2(rel.y, hypot(rel.x, rel.z)))
            assertTrue("below $below", below in 10f..20f)
            assertTrue(rel.length in 0.8f..1.2f)
            // same yaw as the head: content straight ahead of the head's heading
            val bearing = deg(atan2(-rel.x, -rel.z))
            assertEquals(25f, bearing, 0.5f)
        }
    }

    @Test
    fun fourDirectionsFaceTheUser() {
        val mark = Vec3(2f, 1.6f, -3f)
        for (yaw in listOf(0f, 90f, 180f, 270f)) {
            // the user stands 1.5 m from the mark on each side and looks at it
            val y = yaw * PI.toFloat() / 180f
            val user = mark + Vec3(sin(y), 0f, cos(y)) * 1.5f
            val forward = (mark - user).normalized()
            val a = Placement.placeUpright(user, forward)
            val w = Placement.world(a, Placement.ahead())
            val toUser = (user - w.center).let { Vec3(it.x, 0f, it.z) }.normalized()
            val n = w.facing.normal.let { Vec3(it.x, 0f, it.z) }.normalized()
            assertTrue("yaw $yaw off ${deg(acos(n.dot(toUser)))}", deg(acos(n.dot(toUser).coerceIn(-1f, 1f))) < 10f)
            val k = 0.75f * w.center.distanceTo(user) // label scale
            assertTrue(k > 0f)
        }
    }

    /**
     * D29: every button that places content sits in the tool window, which the user looks at while pressing. Pressed
     * with the head at the button's yaw (window straight ahead, dragged 30 left, 30 / 60 right), the content lands where
     * it lands with the head straight ahead: offset < 2 degrees.
     */
    @Test
    fun placementButtonsIgnoreThePressYaw() {
        for (windowYaw in listOf<Float?>(0f, -30f, null)) {
            val straight = PfReducer.add(readyState(windowYaw).copy(headYawDeg = 0f), Template.T1).specimens.single().pose
            for (buttonYaw in listOf(-30f, 0f, 30f, 60f)) {
                val s = PfReducer.add(readyState(windowYaw).copy(headYawDeg = buttonYaw), Template.T1)
                assertTrue("add window $windowYaw button $buttonYaw", abs(s.specimens.single().pose.yawDeg - straight.yawDeg) < 2f)
                val moved = PfReducer.moveToFront(s.copy(headYawDeg = buttonYaw), s.specimens.single().id).specimens.single().pose
                assertTrue("front window $windowYaw button $buttonYaw", abs(moved.yawDeg - straight.yawDeg) < 2f)
            }
        }
    }

    /** "回到面前": pressed while looking at the button, it waits; the settled head (looking ahead again) places it. */
    @Test
    fun backToFrontSamplesTheSettledHead() {
        for (buttonYaw in listOf(-30f, 30f)) {
            var s = PfReducer.add(PfReducer.add(readyState(), Template.T1), Template.T2)
            val before = s.specimens.map { it.pose }
            val pressAnchor = s.anchor
            s = PfReducer.requestFront(s.copy(headYawDeg = buttonYaw), 1000L)
            assertEquals(pressAnchor, s.anchor) // nothing moves at the press
            assertNotNull(s.pendingFrontMs)
            // the user turns 90 degrees left and looks ahead; the head settles there
            val settledEye = Vec3(1f, 1.6f, 1f)
            val settledFwd = Quat4.axisAngle(Vec3.UP, PI.toFloat() / 2f).rotate(Vec3.AHEAD)
            s = PfReducer.allToFront(s, settledEye, settledFwd)
            assertNull(s.pendingFrontMs)
            assertEquals(before, s.specimens.map { it.pose })
            for (sp in s.specimens) {
                val rel = Placement.world(s.anchor!!, sp.pose).center - settledEye
                // world yaw of the specimen = settled heading (90 left) minus its bearing right of body forward
                val worldBearing = deg(atan2(-rel.x, -rel.z))
                assertEquals("button $buttonYaw", 90f - sp.pose.yawDeg, worldBearing, 2f)
            }
        }
    }

    @Test
    fun arcSpreadsPanelsAndSkipsTheWindow() {
        var s = readyState(windowYaw = 0f)
        repeat(4) { s = PfReducer.add(s, Template.T1) }
        val spans = s.specimens.map { sp -> val h = Placement.halfAngle(sp.widthM, sp.pose.dist); (sp.pose.yawDeg - h) to (sp.pose.yawDeg + h) }
        val window = Placement.windowSpan(0f)!!
        for ((a, b) in spans) assertTrue("in window: $a..$b", b <= window.from + 1e-3f || a >= window.to - 1e-3f)
        for (i in spans.indices) for (j in i + 1 until spans.size) {
            val (a0, a1) = spans[i]
            val (b0, b1) = spans[j]
            assertTrue("overlap $i $j", a1 + 3f <= b0 + 1e-3f || b1 + 3f <= a0 + 1e-3f)
        }
        // first one right of the window, the next ones right then left
        assertTrue(s.specimens[0].pose.yawDeg > 0f)
        assertTrue(s.specimens.any { it.pose.yawDeg < 0f })
        assertTrue(s.specimens.all { it.pose.dist >= 1.0f })
    }

    @Test
    fun noWindowFirstOneStraightAhead() {
        val s = PfReducer.add(readyState(windowYaw = null), Template.T1)
        val p = s.specimens.single().pose
        assertEquals(0f, p.yawDeg, 1e-4f)
        assertEquals(1.0f, p.dist, 1e-4f)
        assertEquals(-10f, deg(kotlin.math.asin(p.heightM / p.dist)), 0.01f)
    }

    @Test
    fun framesStayOutOfTheWindowAndSnap() {
        val frames = Placement.frames(0f)
        val window = Placement.windowSpan(0f)!!
        for (f in frames) {
            val h = Placement.halfAngle(f.wM, f.pose.dist)
            assertTrue(f.id, f.pose.yawDeg + h <= window.from + 1e-3f || f.pose.yawDeg - h >= window.to - 1e-3f)
        }
        val side = frames.first { it.id == "side" }
        val center = Placement.world(anchor, side.pose).center + Vec3(0.1f, 0f, 0f)
        assertEquals("side", Placement.snapFrame(anchor, center, frames)?.id)
        assertNull(Placement.snapFrame(anchor, center + Vec3(0.2f, 0f, 0f), frames))
        // without a window the main frame is straight ahead as designed
        assertEquals(0f, Placement.frames(null).first { it.id == "main" }.pose.yawDeg, 1e-4f)
        assertEquals(40f, Placement.frames(null).first { it.id == "side" }.pose.yawDeg, 1e-4f)
    }

    // -- placement state machine (profile D1) -------------------------------------------------------------------

    @Test
    fun placingConfirmLockedMove() {
        var s = PfReducer.add(readyState(), Template.T1)
        val id = s.specimens.single().id
        assertEquals(PlaceMode.PLACING, s.specimens.single().mode)
        s = PfReducer.step(s, Field.DIST, 1)
        assertEquals(1.05f, s.specimens.single().pose.dist, 1e-4f)
        s = PfReducer.done(s)
        assertEquals(PlaceMode.LOCKED, s.specimens.single().mode)
        assertNull(s.selected)
        val locked = s.specimens.single().pose
        // locked: stepper, stick, live drag and release do not move it
        s = PfReducer.step(s, Field.DIST, 1)
        s = PfReducer.livePose(s, id, locked.copy(dist = 2f))
        s = PfReducer.stickReleased(s, id, locked.copy(dist = 2f))
        s = PfReducer.dragReleased(s, id, Vec3(1f, 1f, -2f))
        assertEquals(locked, s.specimens.single().pose)
        // select again = move
        s = PfReducer.select(s, id)
        assertEquals(PlaceMode.PLACING, s.specimens.single().mode)
        s = PfReducer.stickReleased(s, id, locked.copy(dist = 2.01f))
        assertEquals(2.0f, s.specimens.single().pose.dist, 1e-4f)
    }

    @Test
    fun sixAtMostAndDeleteClearsSelection() {
        var s = readyState()
        repeat(8) { s = PfReducer.add(s, Template.T3) }
        assertEquals(6, s.specimens.size)
        assertTrue(s.full)
        val id = s.selected!!
        s = PfReducer.delete(s, id)
        assertNull(s.selected)
        assertEquals(5, s.specimens.size)
    }

    @Test
    fun resizeWaitsForTheSwap() {
        var s = PfReducer.add(readyState(), Template.T7)
        val id = s.selected!!
        s = PfReducer.step(s, Field.WIDTH, 1)
        assertEquals(408, s.specimens.single().wDp)
        assertTrue(id in s.resizing)
        s = PfReducer.step(s, Field.SCALE, 1)
        assertEquals(0.8f, s.specimens.single().scale, 1e-4f)
        s = PfReducer.resized(s, id)
        assertFalse(id in s.resizing)
        repeat(100) { s = PfReducer.step(s, Field.HEIGHT_DP, 1) }
        assertEquals(720, s.specimens.single().hDp)
    }

    @Test
    fun clearanceOnlyForUserPushes() {
        var s = PfReducer.add(readyState(), Template.T4) // 1.02 m wide
        repeat(20) { s = PfReducer.step(s, Field.DIST, -1) }
        assertEquals(0.8f, s.specimens.single().pose.dist, 1e-4f)
        var t = PfReducer.add(readyState(), Template.T1)
        repeat(20) { t = PfReducer.step(t, Field.DIST, -1) }
        assertEquals(0.5f, t.specimens.single().pose.dist, 1e-4f)
    }

    @Test
    fun workingDoesNotChangeSettings() {
        var s = readyState()
        val settings = s.settings
        s = PfReducer.add(s, Template.T5B)
        s = PfReducer.step(s, Field.DIST, 1)
        s = PfReducer.overlay(s, "reach", true)
        s = PfReducer.derive(s.copy(roles = listOf(bodyRole)), eye, 0f)
        s = PfReducer.rate(s, "bodyMedium", Rating.MEH)
        s = PfReducer.done(s)
        assertEquals(settings, s.settings)
    }

    // -- occlusion, inside, window --------------------------------------------------------------------------------

    @Test
    fun nearerPanelCoversTheLowerPartOfTheFarther() {
        val front = Placement.world(anchor, Placement.ahead(1.0f, 22f, 8f))
        val back = Placement.world(anchor, Placement.ahead(1.6f, 24f, 4f))
        val rects = listOf(Rect3.of(1, front, 0.24f, 0.15f), Rect3.of(2, back, 0.312f, 0.185f))
        val o = Occlusion.all(eye, rects).single()
        assertEquals(1, o.front)
        assertEquals(2, o.back)
        assertTrue(o.part == Part.BOTTOM || o.part == Part.BOTTOM_LEFT || o.part == Part.BOTTOM_RIGHT)
        assertTrue(o.side == Side.RIGHT || o.side == Side.BEHIND)
        val apart = listOf(Rect3.of(1, Placement.world(anchor, Placement.ahead(1f, -20f)), 0.24f, 0.15f), Rect3.of(2, Placement.world(anchor, Placement.ahead(1f, 20f)), 0.24f, 0.15f))
        assertTrue(Occlusion.all(eye, apart).isEmpty())
    }

    @Test
    fun partNames() {
        assertEquals(Part.WHOLE, Occlusion.part(-1f, 1f, -1f, 1f, 1f, 1f))
        assertEquals(Part.BOTTOM, Occlusion.part(-1f, 1f, -1f, 0f, 1f, 1f))
        assertEquals(Part.TOP_RIGHT, Occlusion.part(0.2f, 1f, 0.2f, 1f, 1f, 1f))
    }

    @Test
    fun insideAndBlockingTheWindow() {
        val w = Placement.world(anchor, Placement.ahead(0.6f, 0f, 0f))
        val r = Rect3.of(1, w, 0.3f, 0.2f)
        assertTrue(Occlusion.inside(w.center + w.facing.normal * 0.05f, r))
        assertFalse(Occlusion.inside(eye, r))
        val ahead = Rect3.of(1, Placement.world(anchor, Placement.ahead(1.2f, 0f, 10f)), 0.3f, 0.2f)
        assertTrue(Occlusion.blocksWindow(ahead, eye, 0f, 0f))
        assertFalse(Occlusion.blocksWindow(ahead, eye, 0f, null))
        val aside = Rect3.of(1, Placement.world(anchor, Placement.ahead(1.2f, 40f, 10f)), 0.3f, 0.2f)
        assertFalse(Occlusion.blocksWindow(aside, eye, 0f, 0f))
    }

    // -- text and export -----------------------------------------------------------------------------------------

    @Test
    fun sentenceAsInTheSample() {
        val sp = specimen(Template.T1, 0.9f).copy(pose = Placement.ahead(0.9f), ratings = mapOf("bodyMedium" to RatingEntry(Rating.MEH, 22.5f)))
        val r = Report.readings(sp, anchor, eye, PfSettings(), listOf(bodyRole), 1f)
        val zh = SpecText.sentence(false, sp, r)
        assertEquals("这块常用控制面板放 0.90 m：15.2° × 9.6°，等效 333 dp。14 sp 正文 22.5′，你点了「勉强」；手够不着，用射线点。", zh)
        val en = SpecText.sentence(true, sp, r)
        assertTrue(en, en.startsWith("This Control panel at 0.90 m"))
        assertEquals("0.90 m · 10° below eye · square", SpecText.labelLine1(true, r))
        assertEquals("15.2° × 9.6° · 等效 333 dp", SpecText.labelLine2(false, r))
    }

    @Test
    fun exportNameAndSchema() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 31, 14, 20) }
        val sp = specimen(Template.T5B, 1f)
        assertEquals("panelfit-T5b-20261031-1420", SpecText.fileBase(sp, cal.time))
        assertEquals("panelfit-T5b-20261031-1420-2", SpecText.fileBase(sp, cal.time, 2))
        val r = Report.readings(sp, anchor, eye, PfSettings(), listOf(bodyRole), 1f)
        val j = SpecText.json(sp, r, PfSettings(), null, emptyList(), "s", "6.1.9", "PICO", 1f)
        for (key in listOf("schemaVersion", "sdkVersion", "device", "k", "ppd", "thresholds", "panel", "pose", "angles", "eqDp", "fovShare", "styles", "reach", "neck", "occlusion", "verdictSentence", "note")) {
            assertTrue(key, j.has(key))
        }
        assertEquals("Stage 附着面板口径，Shared Space 窗口未校", j.getString("note"))
        val md = SpecText.markdown(sp, r, PfSettings(), emptyList(), "s")
        assertTrue(md.contains("Shared Space 窗口未校"))
    }

    @Test
    fun layoutRoundTrips() {
        val s = PfReducer.add(PfReducer.add(readyState(), Template.T1), Template.T6)
        val saved = Saved(s.specimens.map { it.copy(ratings = mapOf("bodySmall" to RatingEntry(Rating.NO, 12f))) }, s.nextId, PfSettings(k = 0.83f, welcomed = true), Overlays(reach = true), "Download/PanelFit/x.json")
        val back = PfCodec.decode(PfCodec.encode(saved))
        assertNotNull(back)
        assertEquals(saved.specimens.map { it.copy(mode = PlaceMode.LOCKED) }, back!!.specimens)
        assertEquals(saved.settings, back.settings)
        assertEquals(saved.overlays, back.overlays)
        assertEquals(saved.lastSaved, back.lastSaved)
    }

    @Test
    fun allFifteenRoles() {
        assertEquals(15, TypeRoles.names().size)
        assertTrue("bodyMedium" in TypeRoles.names())
    }

    @Test
    fun coveredRowNamesThePanelThatCovers() {
        val o = io.embodit.panelfit.domain.usecase.Overlap(front = 1, back = 2, u0 = -0.1f, u1 = 0.1f, v0 = -0.1f, v1 = 0f, part = Part.BOTTOM, side = Side.RIGHT)
        // front (1) sees the back one on its right; the back one (2) is covered by the one on its left
        assertEquals("挡住了：右边那块的下半", SpecText.occlusionRow(false, 1, listOf(o)))
        assertEquals("下半被左边那块挡住了", SpecText.occlusionRow(false, 2, listOf(o)))
        assertEquals("Its bottom half is covered by the left one", SpecText.occlusionRow(true, 2, listOf(o)))
        val behind = o.copy(side = Side.BEHIND)
        assertEquals("下半被前面那块挡住了", SpecText.occlusionRow(false, 2, listOf(behind)))
        assertEquals("Its bottom half is covered by the front one", SpecText.occlusionRow(true, 2, listOf(behind)))
    }

    @Test
    fun aFrameASpecimenSitsOnIsTaken() {
        val f = Placement.frames(0f).first()
        val w = 400 * Specimen.DP_M * 0.75f
        val h = 252 * Specimen.DP_M * 0.75f
        assertTrue(Placement.overlapsSeen(f.pose, f.wM, f.hM, f.pose.copy(yawDeg = f.pose.yawDeg + 3f), w, h))
        assertFalse(Placement.overlapsSeen(f.pose, f.wM, f.hM, f.pose.copy(yawDeg = f.pose.yawDeg + 40f), w, h))
    }

    @Test
    fun dragLineTurnsBackForAHandThatReturns() {
        // a specimen 1.5 m ahead, 20 degrees right: the hand goes 6 cm right then back; the specimen ends where it began
        val right = Vec3.RIGHT
        val up = Vec3.UP
        val fwd = Vec3.AHEAD
        val start = (fwd + right * 0.364f).normalized()
        val depth = io.embodit.panelfit.domain.usecase.DragLine.equivalentDepth(1.5f, 0f)
        var dir = start
        repeat(6) { dir = io.embodit.panelfit.domain.usecase.DragLine.turn(dir, right * 0.01f, depth, right, up, fwd) }
        assertTrue("moved right", dir.x > start.x + 0.02f)
        repeat(6) { dir = io.embodit.panelfit.domain.usecase.DragLine.turn(dir, right * -0.01f, depth, right, up, fwd) }
        assertTrue("back where it began", dir.distanceTo(start) < 1e-4f)
    }

    @Test
    fun debugDragHandMovesTheSpecimenByTheAskedShift() {
        // the debug `drag x` turns metres of specimen travel into hand travel; fed through turn() it lands that far
        val d = 1.2f
        val depth = io.embodit.panelfit.domain.usecase.DragLine.equivalentDepth(d, 0f)
        val hand = io.embodit.panelfit.domain.usecase.DragLine.handFor(Vec3(-0.12f, 0f, 0f), d, depth)
        val dir = io.embodit.panelfit.domain.usecase.DragLine.turn(Vec3.AHEAD, hand, depth, Vec3.RIGHT, Vec3.UP, Vec3.AHEAD)
        val at = dir * d
        assertEquals(-0.12f, at.x, 0.005f)
        assertEquals(d, at.length, 1e-4f)
    }
}
