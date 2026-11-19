package io.embodit.panelfit.ui.pf.window

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.PicoTheme
import io.embodit.panelfit.BuildConfig
import io.embodit.panelfit.R
import io.embodit.panelfit.domain.model.Language
import io.embodit.panelfit.domain.model.Layout
import io.embodit.panelfit.domain.model.Posture
import io.embodit.panelfit.domain.model.Rating
import io.embodit.panelfit.domain.model.Surface
import io.embodit.panelfit.domain.model.Template
import io.embodit.panelfit.domain.usecase.Confirm
import io.embodit.panelfit.domain.usecase.Copy
import io.embodit.panelfit.domain.usecase.Field
import io.embodit.panelfit.domain.usecase.Page
import io.embodit.panelfit.domain.usecase.PfState
import io.embodit.panelfit.domain.usecase.RefWord
import io.embodit.panelfit.domain.usecase.SpecText
import io.embodit.panelfit.domain.usecase.Sub
import io.embodit.panelfit.domain.usecase.TypeRoles
import io.embodit.panelfit.platform.PfGraph
import io.embodit.panelfit.ui.pf.PfEvent
import io.embodit.panelfit.ui.pf.components.AlertText
import io.embodit.panelfit.ui.pf.components.Dot
import io.embodit.panelfit.ui.pf.components.IconKey
import io.embodit.panelfit.ui.pf.components.LocalEnglish
import io.embodit.panelfit.ui.pf.components.PfText
import io.embodit.panelfit.ui.pf.components.PfType
import io.embodit.panelfit.ui.pf.components.RowDivider
import io.embodit.panelfit.ui.pf.components.Segments
import io.embodit.panelfit.ui.pf.components.StepperRow
import io.embodit.panelfit.ui.pf.components.ToggleChip
import io.embodit.panelfit.ui.pf.components.WordButton
import io.embodit.panelfit.ui.pf.components.tr
import io.embodit.panelfit.ui.pf.debug.probe
import io.embodit.panelfit.ui.theme.PfColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Usable width of the window's content (560 - 2 x 24). */
private val W = 512.dp

/** Settings steppers: the longest English label ("Too small below") with room to spare (QA q1-1). */
private val SET_LABEL_W = 160.dp

// -- S00 / S01 ---------------------------------------------------------------------------------------------------

@Composable
internal fun PreparingView() {
    Column(Modifier.fillMaxWidth().padding(top = 96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painter = painterResource(R.drawable.ic_hourglass_empty), contentDescription = null, modifier = Modifier.size(40.dp), tint = PicoTheme.colorScheme.labelPrimary)
        PfText(tr(Copy.preparing), "prep.title", PfType.title)
        PfText(tr(Copy.preparingSub), "prep.sub", PfType.body)
        PfText(tr(Copy.preparingNote), "prep.note", PfType.small, color = PicoTheme.colorScheme.labelSecondary)
    }
}

@Composable
internal fun WelcomeView(onEvent: (PfEvent) -> Unit) {
    // gate:allow button-gap welcome lines stacked top to bottom, one key only ([try one]); no keys side by side
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PfText(tr(Copy.welcomeTitle), "welcome.title", PfType.title, lines = 2)
        PfText(tr(Copy.welcomeBody), "welcome.body", PfType.body, lines = 3)
        Spacer(Modifier.height(4.dp))
        for ((i, l) in listOf(Copy.welcomeStep1, Copy.welcomeStep2, Copy.welcomeStep3).withIndex()) {
            PfText(tr(l), "welcome.step$i", PfType.body, lines = 2)
        }
        Spacer(Modifier.height(8.dp))
        WordButton("tryOne", tr(Copy.tryOne), { onEvent(PfEvent.TryOne) }, primary = true, iconRes = R.drawable.ic_play_arrow, minWidth = 200.dp)
        PfText(tr(Copy.welcomeOnce), "welcome.once", PfType.small, color = PicoTheme.colorScheme.labelSecondary)
    }
}

// -- S02 library ---------------------------------------------------------------------------------------------------

@Composable
internal fun LibraryPage(state: PfState, onEvent: (PfEvent) -> Unit) {
    val en = LocalEnglish.current
    val cards = Template.entries
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        for (row in 0 until 5) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (col in 0 until 2) {
                    val i = row * 2 + col
                    if (i < cards.size) {
                        val t = cards[i]
                        val sub =
                            when (t) {
                                Template.T7 -> Copy.customSub(en)
                                Template.T8 -> Copy.importSub(en)
                                else -> Copy.sizeLine.format(en, t.wDp, t.hDp, scaleText(t.scale))
                            }
                        LibraryCard("card.${t.id}", t.name(en), sub, enabled = !state.full) {
                            if (t == Template.T8) onEvent(PfEvent.GoSub(Sub.IMPORT)) else onEvent(PfEvent.Add(t))
                        }
                    } else {
                        WordButton(
                            "listButton",
                            Copy.listButton.format(en, state.specimens.size),
                            { onEvent(PfEvent.GoSub(Sub.LIST)) },
                            modifier = Modifier.width(CARD_W),
                            minWidth = CARD_W,
                            height = 72.dp,
                            iconRes = R.drawable.ic_list,
                        )
                    }
                }
            }
        }
    }
}

private fun scaleText(v: Float): String = if (v == 1f) "1" else String.format(Locale.US, "%.2f", v).trimEnd('0')

/** Library card width: two per row with a 16 dp gap (B31) in the 512 dp column. */
private val CARD_W = 248.dp

/** A library card, 248 x 72: name and size line; greyed when six specimens are out (S02:full). */
@Composable
private fun LibraryCard(name: String, title: String, sub: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.width(CARD_W).alpha(if (enabled) 1f else 0.45f).probe(name, onClick),
        size = ButtonDefaults.Regular.copy(height = 72.dp, minWidth = CARD_W),
        shape = RoundedCornerShape(16.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelPrimary),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            PfText(title, "$name.title", PfType.titleMedium)
            PfText(sub, "$name.sub", PfType.small, color = PicoTheme.colorScheme.labelSecondary)
        }
        Icon(painter = painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(20.dp), tint = PicoTheme.colorScheme.labelPrimary)
    }
}

// -- S12 list ------------------------------------------------------------------------------------------------------

@Composable
internal fun ListPage(state: PfState, onEvent: (PfEvent) -> Unit) {
    val en = LocalEnglish.current
    // gate:allow button-gap list rows stacked top to bottom: rows 56 dp with 48 dp keys, so keys of two rows are 16 dp apart
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.specimens.isEmpty()) {
            PfText(Copy.listEmpty(en), "list.empty", PfType.body, lines = 2)
            WordButton("list.toLibrary", Copy.toLibrary(en), { onEvent(PfEvent.GoSub(Sub.NONE)) }, iconRes = R.drawable.ic_dashboard)
            return@Column
        }
        for (sp in state.specimens) {
            val r = state.readings[sp.id]
            val where = if (r != null && state.headOk) "${Copy.f2(r.dist)} m · ${Copy.place(en, r.bearingDeg)}" else "${Copy.f2(sp.pose.dist)} m"
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    // selected: a dot on ink, not interaction-coloured text (1.7:1 on the light glass)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.selected == sp.id) {
                            Dot(PfColors.Interaction)
                            Spacer(Modifier.width(6.dp))
                        }
                        PfText(sp.name(en), "list.${sp.id}.name", PfType.label)
                    }
                    PfText(where + if (sp.hidden) " · " + Copy.hiddenTag(en) else "", "list.${sp.id}.where", PfType.small, color = PicoTheme.colorScheme.labelSecondary)
                }
                if (state.deleteAsk == sp.id) {
                    PfText(Copy.deleteAsk(en), "list.${sp.id}.ask", PfType.label)
                    Spacer(Modifier.width(8.dp))
                    WordButton("list.${sp.id}.cancel", Copy.cancel(en), { onEvent(PfEvent.AskDelete(null)) }, minWidth = 72.dp)
                    Spacer(Modifier.width(6.dp))
                    WordButton("list.${sp.id}.delete", Copy.delete(en), { onEvent(PfEvent.Delete(sp.id)) }, danger = true, minWidth = 72.dp)
                } else {
                    WordButton("list.${sp.id}.front", Copy.moveToFront(en), { onEvent(PfEvent.MoveToFront(sp.id)) }, minWidth = 48.dp, iconRes = R.drawable.ic_my_location)
                    Spacer(Modifier.width(6.dp))
                    WordButton(
                        "list.${sp.id}.hide",
                        (if (sp.hidden) Copy.show else Copy.hide)(en),
                        { onEvent(PfEvent.ToggleHidden(sp.id)) },
                        minWidth = 48.dp,
                        iconRes = if (sp.hidden) R.drawable.ic_visibility else R.drawable.ic_visibility_off,
                    )
                    Spacer(Modifier.width(6.dp))
                    IconKey("list.${sp.id}.del", R.drawable.ic_delete, Copy.delete(en)) { onEvent(PfEvent.AskDelete(sp.id)) }
                }
            }
        }
        PfText(Copy.listNote(en), "list.note", PfType.small, color = PicoTheme.colorScheme.labelSecondary)
    }
}

// -- S15 import ----------------------------------------------------------------------------------------------------

@Composable
internal fun ImportPage(state: PfState, onEvent: (PfEvent) -> Unit) {
    val en = LocalEnglish.current
    // gate:allow button-gap full-width file rows stacked top to bottom (72 dp each), not keys side by side; six must fit the page
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.importFiles.isEmpty()) {
            Icon(painter = painterResource(R.drawable.ic_image), contentDescription = null, modifier = Modifier.size(32.dp), tint = PicoTheme.colorScheme.labelPrimary)
            PfText(Copy.importEmpty(en), "import.empty", PfType.body, lines = 2)
            PfText(Copy.importCmd(en), "import.cmd", PfType.small, lines = 3, color = PicoTheme.colorScheme.labelSecondary)
            PfText(Copy.importNote(en), "import.note", PfType.small, lines = 2, color = PicoTheme.colorScheme.labelSecondary)
            WordButton("import.refresh", Copy.back(en), { onEvent(PfEvent.GoSub(Sub.NONE)) }, iconRes = R.drawable.ic_arrow_back)
            return@Column
        }
        for (f in state.importFiles.take(6)) {
            val (wDp, hDp) = if (f.hPx > 720) (f.wPx * 720f / f.hPx).roundToInt() to 720 else f.wPx to f.hPx
            val line = Copy.importLine.format(en, f.wPx, f.hPx, wDp, hDp) + if (f.hPx > 720) Copy.importScaled(en) else ""
            Button(
                onClick = { onEvent(PfEvent.ImportPick(f.name)) },
                modifier = Modifier.width(W).probe("import.${f.name}") { onEvent(PfEvent.ImportPick(f.name)) },
                size = ButtonDefaults.Regular.copy(height = 72.dp, minWidth = W),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelPrimary),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Thumb(f.name)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    PfText(f.name, "import.${f.name}.name", PfType.label)
                    PfText(if (f.ok) line else Copy.hintImportBad(en), "import.${f.name}.line", PfType.small, color = PicoTheme.colorScheme.labelSecondary)
                }
            }
        }
        PfText(Copy.importNote(en), "import.note", PfType.small, lines = 2, color = PicoTheme.colorScheme.labelSecondary)
    }
}

@Composable
private fun Thumb(name: String) {
    val bmp = remember(name) { runCatching { PfGraph.files.decode(name, 96) }.getOrNull() }
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(PfColors.Ink), contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bitmap = bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp))
        else Icon(painter = painterResource(R.drawable.ic_broken_image), contentDescription = null, modifier = Modifier.size(24.dp), tint = PfColors.White)
    }
}

// -- S04 adjust ----------------------------------------------------------------------------------------------------

/** S04:none / S07:none / S08:none: one screen for all three pages. */
@Composable
internal fun NoneSelected(onEvent: (PfEvent) -> Unit) {
    // gate:allow button-gap icon, two lines and one key stacked top to bottom; no keys side by side
    Column(Modifier.fillMaxWidth().padding(top = 72.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(painter = painterResource(R.drawable.ic_touch_app), contentDescription = null, modifier = Modifier.size(40.dp), tint = PicoTheme.colorScheme.labelPrimary)
        PfText(tr(Copy.noneTitle), "none.title", PfType.title)
        PfText(tr(Copy.noneSub), "none.sub", PfType.body)
        Spacer(Modifier.height(8.dp))
        WordButton("none.library", tr(Copy.toLibrary), { onEvent(PfEvent.GoPage(Page.LIBRARY)) }, iconRes = R.drawable.ic_dashboard, minWidth = 180.dp)
    }
}

@Composable
private fun NameDoneRow(state: PfState, onEvent: (PfEvent) -> Unit) {
    val sp = state.selectedSpecimen ?: return
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        PfText(sp.name(LocalEnglish.current), "adjust.name", PfType.titleMedium, Modifier.weight(1f))
        WordButton("adjust.done", tr(Copy.done), { onEvent(PfEvent.Done) }, primary = true, iconRes = R.drawable.ic_check)
    }
}

@Composable
private fun PageSwitch(name: String, index: Int, onPage: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        IconKey("$name.prev", R.drawable.ic_chevron_left, "<", enabled = index > 0) { onPage(index - 1) }
        PfText(tr(Copy.page, index + 1), "$name.page", PfType.label, Modifier.width(120.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
        IconKey("$name.next", R.drawable.ic_chevron_right, ">", enabled = index < 1) { onPage(index + 1) }
    }
}

@Composable
internal fun AdjustPage(state: PfState, onEvent: (PfEvent) -> Unit) {
    val sp = state.selectedSpecimen
    if (sp == null) {
        NoneSelected(onEvent)
        return
    }
    val en = LocalEnglish.current
    val p = sp.pose
    // gate:allow button-gap stepper rows (48 dp) stacked top to bottom; keys side by side within a row are 132 dp apart (value between)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NameDoneRow(state, onEvent)
        if (state.adjustPage == 0) {
            StepperRow("dist", tr(Copy.rowDist), "${Copy.f2(p.dist)} m", onStep = { onEvent(PfEvent.Step(Field.DIST, it)) })
            val cm = (p.heightM * 100f).roundToInt()
            StepperRow("height", tr(Copy.rowHeight), if (cm < 0) Copy.belowEyeCm.format(en, -cm) else if (cm > 0) Copy.aboveEyeCm.format(en, cm) else Copy.eyeLevel(en), onStep = { onEvent(PfEvent.Step(Field.HEIGHT, it)) })
            val side = p.yawDeg.roundToInt()
            StepperRow("side", tr(Copy.rowSide), if (side == 0) Copy.ahead0(en) else if (side > 0) Copy.rightDeg.format(en, side) else Copy.leftDeg.format(en, -side), onStep = { onEvent(PfEvent.Step(Field.SIDE, it)) })
            val facing = p.facingDeg.roundToInt()
            StepperRow("facing", tr(Copy.rowFacing), if (facing == 0) Copy.squareDeg(en) else if (facing > 0) Copy.turnRight.format(en, facing) else Copy.turnLeft.format(en, -facing), onStep = { onEvent(PfEvent.Step(Field.FACING, it)) })
            StepperRow("tilt", tr(Copy.rowTilt), "${p.tiltDeg.roundToInt()}°", onStep = { onEvent(PfEvent.Step(Field.TILT, it)) }) {
                WordButton("tilt.toEye", tr(Copy.tiltToEye), { onEvent(PfEvent.TiltToEye) }, minWidth = 72.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val w = (512 - 32) / 3
                ToggleChip("overlay.reach", tr(Copy.overlayReach), R.drawable.ic_pan_tool, state.overlays.reach, w.dp) { onEvent(PfEvent.Overlay("reach")) }
                ToggleChip("overlay.neck", tr(Copy.overlayNeck), R.drawable.ic_accessibility_new, state.overlays.neck, w.dp) { onEvent(PfEvent.Overlay("neck")) }
                ToggleChip("overlay.ruler", tr(Copy.overlayRuler), R.drawable.ic_straighten, state.overlays.ruler, w.dp) { onEvent(PfEvent.Overlay("ruler")) }
            }
        } else {
            StepperRow("width", tr(Copy.rowWidth), "${sp.wDp} dp", onStep = { onEvent(PfEvent.Step(Field.WIDTH, it)) })
            StepperRow("heightDp", tr(Copy.rowHeightDp), "${sp.hDp} dp", onStep = { onEvent(PfEvent.Step(Field.HEIGHT_DP, it)) })
            StepperRow("scale", tr(Copy.rowScale), Copy.f2(sp.scale), onStep = { onEvent(PfEvent.Step(Field.SCALE, it)) })
            Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                PfText(tr(Copy.rowSurface), "surface.label", PfType.body, Modifier.width(112.dp))
                Segments("surface", listOf(tr(Copy.glass), tr(Copy.solid)), if (sp.surface == Surface.GLASS) 0 else 1, { onEvent(PfEvent.SetSurface(if (it == 0) Surface.GLASS else Surface.SOLID)) }, width = 228.dp)
            }
            PfText(tr(Copy.resizeNote), "adjust.resizeNote", PfType.small, lines = 2, color = PicoTheme.colorScheme.labelSecondary)
            Spacer(Modifier.height(52.dp))
        }
        PageSwitch("adjustPage", state.adjustPage) { onEvent(PfEvent.AdjustPage(it)) }
    }
}

// -- S07 legibility -------------------------------------------------------------------------------------------------

@Composable
internal fun RatePage(state: PfState, onEvent: (PfEvent) -> Unit) {
    val sp = state.selectedSpecimen
    if (sp == null) {
        NoneSelected(onEvent)
        return
    }
    val en = LocalEnglish.current
    val r = state.readings[sp.id]?.takeIf { state.headOk }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (sp.template.layout == Layout.IMAGE) {
            PfText(sp.name(en), "rate.name", PfType.titleMedium)
            RateRow("rate.whole", Copy.wholeRow(en), null, null, sp.ratings[TypeRoles.WHOLE]?.rating) { onEvent(PfEvent.Rate(TypeRoles.WHOLE, it)) }
            return@Column
        }
        Segments("rateGroup", Copy.groupNames.map { it(en) }, state.rateGroup, { onEvent(PfEvent.RateGroup(it)) }, width = W)
        val group = TypeRoles.GROUPS[state.rateGroup]
        for (size in TypeRoles.SIZES) {
            val role = group + size
            val row = r?.styles?.firstOrNull { it.role.role == role }
            val sp0 = state.roles.firstOrNull { it.role == role }
            val title = "$role ${sp0?.usedSp?.roundToInt() ?: "—"} sp"
            val arc = if (row != null) Copy.arcLine.format(en, Copy.f1(row.arcmin), row.px.roundToInt()) else "—"
            val ref = row?.ref
            RateRow("rate.$role", title, arc, ref, sp.ratings[role]?.rating) { onEvent(PfEvent.Rate(role, it)) }
        }
    }
}

@Composable
private fun RateRow(name: String, title: String, arc: String?, ref: RefWord?, self: Rating?, onRate: (Rating) -> Unit) {
    val en = LocalEnglish.current
    // gate:allow button-gap the title line sits above the three keys; the keys themselves are 16 dp apart (row below)
    Column(Modifier.fillMaxWidth().height(96.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            PfText(title, "$name.title", PfType.label, Modifier.width(196.dp))
            if (arc != null) PfText(arc, "$name.arc", PfType.small, Modifier.width(150.dp))
            if (ref != null) {
                Dot(refColor(ref))
                Spacer(Modifier.width(6.dp))
                PfText(Copy.refLine.format(en, Copy.ref(ref)(en)), "$name.ref", PfType.small)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for ((rating, l) in listOf(Rating.OK to Copy.rateOk, Rating.MEH to Copy.rateMeh, Rating.NO to Copy.rateNo)) {
                val on = self == rating
                WordButton(
                    "$name.${rating.name.lowercase(Locale.US)}",
                    l(en),
                    { onRate(rating) },
                    modifier = Modifier.width(160.dp),
                    minWidth = 160.dp,
                    primary = on,
                    iconRes = if (on) R.drawable.ic_check else null,
                )
            }
        }
    }
}

@Composable
private fun refColor(ref: RefWord) =
    when (ref) {
        RefWord.BIG -> PfColors.Good
        RefWord.SMALLISH -> PfColors.Alert
        RefWord.TOO_SMALL -> PfColors.Error
    }

// -- S08 spec card -------------------------------------------------------------------------------------------------

@Composable
internal fun CardPage(state: PfState, onEvent: (PfEvent) -> Unit) {
    val sp = state.selectedSpecimen
    if (sp == null) {
        NoneSelected(onEvent)
        return
    }
    val en = LocalEnglish.current
    val r = state.readings[sp.id]?.takeIf { state.headOk }
    // gate:allow button-gap sentence, table rows and the page keys stacked top to bottom; no keys side by side
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.cardPage == 0) {
            PfText(if (r != null) SpecText.sentence(en, sp, r) else Copy.noHead(en), "card.sentence", PfType.bodyMedium, Modifier.height(66.dp), lines = 3)
            Column {
                val dash = "—"
                TableRow("card.dist", Copy.cardDist(en), r?.let { SpecText.distRow(en, it) } ?: dash)
                TableRow("card.mm", Copy.cardMm(en), r?.let { SpecText.mmRow(it) } ?: "${(sp.wDp * sp.scale * state.settings.k).roundToInt()} × ${(sp.hDp * sp.scale * state.settings.k).roundToInt()} mm")
                TableRow("card.angle", Copy.cardAngle(en), r?.let { SpecText.angleRow(en, it) } ?: dash)
                TableRow("card.eq", Copy.cardEq(en), r?.let { SpecText.eqRow(it) } ?: dash)
                TableRow("card.reach", Copy.cardReach(en), r?.let { SpecText.reachRow(en, it) } ?: dash)
                TableRow("card.neck", Copy.cardNeck(en), r?.let { Copy.neck(it.neck)(en) } ?: dash)
                TableRow("card.occlusion", Copy.cardOcclusion(en), SpecText.occlusionRow(en, sp.id, state.overlaps), alert = state.overlaps.any { it.front == sp.id || it.back == sp.id })
            }
            PfText(SpecText.smallPrint(en, state.settings), "card.small", PfType.small, Modifier.height(36.dp), lines = 2, color = PicoTheme.colorScheme.labelSecondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                WordButton("card.save", tr(Copy.save), { onEvent(PfEvent.Save) }, primary = true, iconRes = R.drawable.ic_download, minWidth = 180.dp)
                Spacer(Modifier.weight(1f))
                WordButton("card.next", tr(Copy.turnPage), { onEvent(PfEvent.CardPage(1)) }, iconRes = R.drawable.ic_chevron_right)
            }
            PfText(state.lastSaved?.let { Copy.lastSaved.format(en, it) } ?: Copy.notSavedYet(en), "card.lastSaved", PfType.small, lines = 2, color = PicoTheme.colorScheme.labelSecondary)
        } else {
            val dist = r?.let { Copy.f2(it.dist) } ?: "—"
            PfText(Copy.typeTableTitle.format(en, sp.name(en), dist), "card2.title", PfType.titleMedium)
            Row(Modifier.height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                for ((l, w) in listOf(Copy.colRole to 150, Copy.colArc to 80, Copy.colPx to 64, Copy.colRef to 90, Copy.colSelf to 128)) {
                    PfText(l(en), "card2.col.${l.en}", PfType.small, Modifier.width(w.dp), color = PicoTheme.colorScheme.labelSecondary)
                }
            }
            val shown =
                r?.styles?.filter { it.self != null || it.role.role in setOf("bodyLarge", "bodyMedium", "bodySmall", "titleMedium", "labelLarge") }?.take(8) ?: emptyList()
            for (row in shown) {
                Row(Modifier.height(26.dp), verticalAlignment = Alignment.CenterVertically) {
                    PfText("${row.role.role} ${row.role.usedSp.roundToInt()}", "card2.${row.role.role}.role", PfType.small, Modifier.width(150.dp))
                    PfText("${Copy.f1(row.arcmin)}′", "card2.${row.role.role}.arc", PfType.small, Modifier.width(80.dp))
                    PfText("${row.px.roundToInt()}", "card2.${row.role.role}.px", PfType.small, Modifier.width(64.dp))
                    PfText(Copy.ref(row.ref)(en), "card2.${row.role.role}.ref", PfType.small, Modifier.width(90.dp))
                    PfText(Copy.rating(row.self?.rating)?.invoke(en) ?: Copy.notRated(en), "card2.${row.role.role}.self", PfType.small, Modifier.width(128.dp))
                }
            }
            val facing = r?.let { "${Copy.facing(it.facing)(en)} (${it.facingOff.roundToInt()}°)" } ?: "—"
            PfText(Copy.facingLine.format(en, facing, (if (sp.surface == Surface.GLASS) Copy.glass else Copy.solid)(en)), "card2.facing", PfType.small)
            val rendered = state.roles.any { it.usedSp != it.themeSp }
            PfText(Copy.typeNote.format(en, state.settings.ppd.roundToInt()) + if (rendered) " · " + Copy.renderedNote(en) else "", "card2.note", PfType.small, lines = 2, color = PicoTheme.colorScheme.labelSecondary)
            WordButton("card.prev", tr(Copy.back), { onEvent(PfEvent.CardPage(0)) }, iconRes = R.drawable.ic_chevron_left)
        }
    }
}

@Composable
private fun TableRow(name: String, label: String, value: String, alert: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
        PfText(label, "$name.label", PfType.small, Modifier.width(120.dp), color = PicoTheme.colorScheme.labelSecondary)
        Row(Modifier.weight(1f)) {
            if (alert) AlertText(value, "$name.value", PfType.label) else PfText(value, "$name.value", PfType.label)
        }
    }
}

// -- S13 settings --------------------------------------------------------------------------------------------------

@Composable
internal fun SettingsContent(state: PfState, onEvent: (PfEvent) -> Unit, onQuit: () -> Unit) {
    val en = LocalEnglish.current
    // D26 (2026-11-17 user on the headset: settings opened on Calibrate and [Quit app] sat in General, out of sight):
    // the segments only switch the middle; recall and quit sit in a footer below it, pinned to the bottom of the page
    Column(Modifier.fillMaxSize()) {
        // gate:allow tabs-segment options inside the one settings page (pages themselves switch on the native TabBar); quit and recall stay below it
        Segments("settingsPage", listOf(Copy.calib(en), Copy.general(en), Copy.howToTab(en)), state.settingsPage, { onEvent(PfEvent.SettingsPage(it)) }, width = W)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f)) { SettingsSegment(state, onEvent) }
        RowDivider(W)
        Spacer(Modifier.height(8.dp))
        SettingsFooter(state, onEvent, onQuit)
    }
}

/** The middle of the settings page: the open segment (calibrate / general / how to). */
@Composable
private fun SettingsSegment(state: PfState, onEvent: (PfEvent) -> Unit) {
    val en = LocalEnglish.current
    val st = state.settings
    fun set(next: io.embodit.panelfit.domain.model.PfSettings) = onEvent(PfEvent.Settings(next))
    // gate:allow exit-first-page [Quit app] is in SettingsFooter, outside this when and below every segment (SettingsContent)
    when (state.settingsPage) {
        0 -> {
            StepperRow("set.k", Copy.setK(en), "${Copy.f2(st.k)} mm/dp", labelWidth = SET_LABEL_W, onStep = { set(st.copy(k = round2(st.k + it * 0.01f).coerceIn(0.3f, 2f))) })
            StepperRow("set.high", Copy.setHigh(en), "${st.refHigh.roundToInt()}′", labelWidth = SET_LABEL_W, onStep = { set(st.copy(refHigh = (st.refHigh + it).coerceIn(st.refLow + 1f, 60f))) })
            StepperRow("set.low", Copy.setLow(en), "${st.refLow.roundToInt()}′", labelWidth = SET_LABEL_W, onStep = { set(st.copy(refLow = (st.refLow + it).coerceIn(4f, st.refHigh - 1f))) })
            Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                PfText(Copy.setMine(en), "set.mine.label", PfType.body, Modifier.width(168.dp))
                Segments("set.mine", listOf(Copy.on(en), Copy.off(en)), if (st.useMine) 0 else 1, { set(st.copy(useMine = it == 0)) }, width = 160.dp)
                Spacer(Modifier.width(12.dp))
                PfText(state.mine?.let { "${Copy.f1(it)}′" } ?: Copy.mineNone(en), "set.mine.value", PfType.small, Modifier.weight(1f), lines = 2)
            }
            StepperRow("set.ppd", Copy.setPpd(en), "${st.ppd.roundToInt()} /°", labelWidth = SET_LABEL_W, onStep = { set(st.copy(ppd = (st.ppd + it).coerceIn(5f, 60f))) })
            StepperRow("set.arm", Copy.setArm(en), "${Copy.f2(st.armM)} m", labelWidth = SET_LABEL_W, onStep = { set(st.copy(armM = round2(st.armM + it * 0.01f).coerceIn(0.4f, 0.9f))) })
            Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                PfText(Copy.setPosture(en), "set.posture.label", PfType.body, Modifier.width(168.dp))
                Segments("set.posture", listOf(Copy.stand(en), Copy.sit(en)), if (st.posture == Posture.STAND) 0 else 1, { set(st.copy(posture = if (it == 0) Posture.STAND else Posture.SIT)) }, width = 160.dp)
            }
            PfText(Copy.howCalib(en), "set.how", PfType.small, lines = 3, color = PicoTheme.colorScheme.labelSecondary)
        }
        2 -> HowToSegment(en)
        else -> {
            SwitchRow("set.frames", Copy.setFrames(en), st.frames) { set(st.copy(frames = it)) }
            SwitchRow("set.sound", Copy.sound(en), st.sound) { set(st.copy(sound = it)) }
            Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                PfText(Copy.setVolume(en), "set.volume.label", PfType.body, Modifier.weight(1f))
                Segments("set.volume", listOf(Copy.low(en), Copy.mid(en)), if (st.volumeHigh) 1 else 0, { set(st.copy(volumeHigh = it == 1)) }, width = 160.dp)
            }
            Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                PfText(Copy.setLanguage(en), "set.lang.label", PfType.body, Modifier.weight(1f))
                Segments("set.lang", listOf("中文", "EN"), if (st.language == Language.ZH) 0 else 1, { onEvent(PfEvent.Lang(if (it == 0) Language.ZH else Language.EN)) }, width = 160.dp)
            }
            ActionRow("set.front", Copy.setAllFront(en), Copy.allFront(en), R.drawable.ic_my_location) { onEvent(PfEvent.AllToFront) }
            if (state.confirm == Confirm.CLEAR) {
                Row(Modifier.fillMaxWidth().height(48.dp).probe("clear.confirm"), verticalAlignment = Alignment.CenterVertically) {
                    PfText(Copy.clearAsk.format(en, state.specimens.size), "clear.ask", PfType.body, Modifier.weight(1f))
                    WordButton("clear.cancel", Copy.cancel(en), { onEvent(PfEvent.Ask(null)) }, minWidth = 80.dp)
                    Spacer(Modifier.width(8.dp))
                    WordButton("clear.yes", Copy.clear(en), { onEvent(PfEvent.ClearAll) }, danger = true, minWidth = 80.dp)
                }
            } else {
                ActionRow("set.clear", Copy.setClear(en), Copy.clear(en), R.drawable.ic_delete) { onEvent(PfEvent.Ask(Confirm.CLEAR)) }
            }
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                PfText(Copy.setAbout(en) + " · v" + BuildConfig.VERSION_NAME, "set.about", PfType.body, Modifier.weight(1f))
                WordButton("set.welcome", Copy.showWelcome(en), { onEvent(PfEvent.ShowWelcome) }, minWidth = 96.dp)
            }
        }
    }
}

/** The settings footer, on screen whatever segment is open (D26 / D44): [recall tool panel] and [quit app]. */
@Composable
private fun SettingsFooter(state: PfState, onEvent: (PfEvent) -> Unit, onQuit: () -> Unit) {
    val en = LocalEnglish.current
    QuitRow(
        label = Copy.quitRow(en),
        ask = Copy.quitAsk(en),
        cancel = Copy.cancel(en),
        quit = Copy.quit(en),
        note = Copy.quitNote(en),
        confirming = state.confirm == Confirm.QUIT,
        onAsk = { onEvent(PfEvent.Ask(if (it) Confirm.QUIT else null)) },
        onQuit = {
            onEvent(PfEvent.QuitYes)
            onQuit()
        },
        recall = { WordButton("set.recall", Copy.setRecall(en), { onEvent(PfEvent.RecallWindow) }, iconRes = R.drawable.ic_open_in_new, minWidth = 200.dp, height = FOOTER_KEY_H) },
    )
}

/**
 * "怎么操作" (B19): one row per main action, an icon, what to do with the hand and with the controller. The words are
 * string resources (how_to_*), read in the app's own language (it can differ from the system's).
 */
@Composable
private fun HowToSegment(en: Boolean) {
    val res = localized(en)
    val rows =
        listOf(
            Triple(R.drawable.ic_dashboard, R.string.how_to_add, R.array.how_to_add_ways),
            Triple(R.drawable.ic_touch_app, R.string.how_to_select, R.array.how_to_select_ways),
            Triple(R.drawable.ic_open_with, R.string.how_to_move, R.array.how_to_move_ways),
            Triple(R.drawable.ic_tune, R.string.how_to_turn, R.array.how_to_turn_ways),
            Triple(R.drawable.ic_visibility, R.string.how_to_rate, R.array.how_to_rate_ways),
        )
    Column {
        for ((i, row) in rows.withIndex()) {
            val (icon, title, ways) = row
            val (hand, pad) = res.getStringArray(ways).let { it[0] to it[1] }
            Row(Modifier.fillMaxWidth().height(HOW_TO_ROW_H), verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp), tint = PicoTheme.colorScheme.labelPrimary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    PfText(res.getString(title), "howto.$i.title", PfType.label)
                    PfText(hand, "howto.$i.hand", PfType.small, color = PicoTheme.colorScheme.labelSecondary)
                    PfText(pad, "howto.$i.pad", PfType.small, color = PicoTheme.colorScheme.labelSecondary)
                }
            }
        }
    }
}

/** Resources in the app's language (settings), not the system's. */
@Composable
private fun localized(en: Boolean): android.content.res.Resources {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(context, en) {
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(if (en) Locale.ENGLISH else Locale.SIMPLIFIED_CHINESE)
        context.createConfigurationContext(config).resources
    }
}

private val HOW_TO_ROW_H = 68.dp

@Composable
private fun SwitchRow(name: String, label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val en = LocalEnglish.current
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        PfText(label, "$name.label", PfType.body, Modifier.weight(1f))
        Segments(name, listOf(Copy.on(en), Copy.off(en)), if (on) 0 else 1, { onChange(it == 0) }, width = 160.dp)
    }
}

@Composable
private fun ActionRow(name: String, label: String, action: String, icon: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        PfText(label, "$name.label", PfType.body, Modifier.weight(1f))
        WordButton(name, action, onClick, iconRes = icon, minWidth = 120.dp)
    }
}

private fun round2(v: Float): Float = (v * 100f).roundToInt() / 100f

@Suppress("unused")
private fun widthOf(d: Dp): Float = abs(d.value)
