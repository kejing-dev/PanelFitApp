package io.embodit.panelfit.ui.pf.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Switch
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import io.embodit.panelfit.R
import io.embodit.panelfit.domain.model.Layout
import io.embodit.panelfit.domain.model.Specimen
import io.embodit.panelfit.domain.model.Surface
import io.embodit.panelfit.domain.usecase.Copy
import io.embodit.panelfit.platform.PfGraph
import io.embodit.panelfit.ui.theme.PfColors
import java.util.Locale

/**
 * A specimen: a real SpatialUI attachment panel of the chosen size with PicoTheme type and real components, so what
 * you see is what an app would show (design `container.notes`). Its controls give their own press feedback but do
 * nothing. Text here is the object under test: no overflow logging, it may clip at small custom sizes.
 *
 * [onRendered] reports the measured height of each type role's line (PF-METRIC, design `rules.math.text`).
 */
@Composable
fun SpecimenPanel(sp: Specimen, selected: Boolean, inView: Boolean, faded: Boolean = false, modifier: Modifier = Modifier, onRendered: (role: String, sp: Float) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val base = Modifier.size(sp.wDp.dp, sp.hDp.dp).clip(shape)
    val surface =
        if (sp.surface == Surface.GLASS) base.backgroundMaterial(enable = true, style = Material.Regular)
        // the solid surface takes the side of the text colour: dark under light text, light under dark text (D13)
        else base.background(if (PicoTheme.colorScheme.labelPrimary.luminance() > 0.5f) PfColors.SolidSurface else PfColors.SolidSurfaceLight)
    val frame = if (selected) surface.border(2.dp, PfColors.Interaction, shape) else surface
    // S14 / S19: 30 % (design); the entity's opacity component alone did not show on the emulator
    Box(modifier = modifier.alpha(if (faded) FADED_ALPHA else 1f).then(frame)) {
        if (!inView) return@Box
        val en = LocalEnglish.current
        when (sp.template.layout) {
            Layout.GENERIC -> Generic(sp, en)
            Layout.DIALOG -> Dialog(en)
            Layout.BIG_TYPE -> TypeRows(listOf("display", "headline"), en, onRendered)
            Layout.BODY_TYPE -> TypeRows(listOf("title", "body", "label"), en, onRendered)
            Layout.COMPONENTS -> Components(en)
            Layout.IMAGE -> Picture(sp)
        }
    }
}

@Composable
private fun Generic(sp: Specimen, en: Boolean) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(sp.name(en), style = PicoTheme.typography.titleMedium, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1)
        Text(Copy.sampleBody(en), style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelPrimary, maxLines = 2)
        SampleSegments(en)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SampleButton(Copy.cancel(en), primary = false)
            SampleButton(Copy.ok(en), primary = true)
        }
        if (sp.template.id == "T4") Text(Copy.notCalibrated(en), style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.labelSecondary, maxLines = 1)
    }
}

@Composable
private fun Dialog(en: Boolean) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(Copy.dialogTitle(en), style = PicoTheme.typography.titleMedium, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1)
        Text(Copy.dialogBody(en), style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelPrimary, maxLines = 2)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SampleButton(Copy.cancel(en), primary = false, modifier = Modifier.weight(1f))
            SampleButton(Copy.ok(en), primary = true, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SampleButton(text: String, primary: Boolean, modifier: Modifier = Modifier) {
    val container = if (primary) PicoTheme.colorScheme.labelPrimaryLight else PicoTheme.colorScheme.fillTertiary
    val content = if (primary) PfColors.Ink else PicoTheme.colorScheme.labelPrimary
    Button(
        onClick = {},
        modifier = modifier,
        size = ButtonDefaults.Regular.copy(height = 48.dp, minWidth = 96.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        Text(text, color = content, style = PicoTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun SampleSegments(en: Boolean) {
    var sel by remember { mutableIntStateOf(0) }
    SegmentControl(modifier = Modifier.height(40.dp), backgroundColor = PicoTheme.colorScheme.fillTertiary) {
        listOf(Copy.option1(en), Copy.option2(en), Copy.option3(en)).forEachIndexed { i, t ->
            SegmentItem(selected = sel == i, onClick = { sel = i }, modifier = Modifier.width(96.dp).height(40.dp), title = { Text(t, maxLines = 1) })
        }
    }
}

/** Type rows: every role of [groups] in L / M / S, "<role> <sp> sp 看得清 Ag" in that role's style. */
@Composable
private fun TypeRows(groups: List<String>, en: Boolean, onRendered: (String, Float) -> Unit) {
    val density = LocalDensity.current
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (g in groups) for (size in listOf("Large", "Medium", "Small")) {
            val role = g + size
            val style = styleOf(role)
            val sp = style.fontSize.value
            Text(
                text = String.format(Locale.US, "%s %d sp %s Ag", role, sp.toInt(), Copy.legibleWord(en)),
                style = style,
                color = PicoTheme.colorScheme.labelPrimary,
                maxLines = 1,
                onTextLayout = { r ->
                    if (r.lineCount > 0) {
                        val px = r.getLineBottom(0) - r.getLineTop(0)
                        val lh = style.lineHeight
                        val ratio = if (lh.type == TextUnitType.Sp && sp > 0f) lh.value / sp else 1.2f
                        val measuredSp = px / density.density / density.fontScale / ratio
                        onRendered(role, measuredSp)
                    }
                },
            )
        }
    }
}

/** PicoTheme style of a role name ("bodyMedium"). */
@Composable
fun styleOf(role: String): TextStyle {
    val t = PicoTheme.typography
    return when (role) {
        "displayLarge" -> t.displayLarge
        "displayMedium" -> t.displayMedium
        "displaySmall" -> t.displaySmall
        "headlineLarge" -> t.headlineLarge
        "headlineMedium" -> t.headlineMedium
        "headlineSmall" -> t.headlineSmall
        "titleLarge" -> t.titleLarge
        "titleMedium" -> t.titleMedium
        "titleSmall" -> t.titleSmall
        "bodyLarge" -> t.bodyLarge
        "bodyMedium" -> t.bodyMedium
        "bodySmall" -> t.bodySmall
        "labelLarge" -> t.labelLarge
        "labelMedium" -> t.labelMedium
        else -> t.labelSmall
    }
}

@Composable
private fun Components(en: Boolean) {
    var on by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (en) "Components" else "组件", style = PicoTheme.typography.titleMedium, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SampleButton(Copy.buttonLabel(en), primary = true)
            IconButton(
                onClick = {},
                colors = IconButtonDefaults.iconButtonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelPrimary),
                size = IconButtonDefaults.iconButtonSize(width = 48.dp, height = 48.dp),
            ) {
                Icon(painter = painterResource(R.drawable.ic_star), contentDescription = Copy.iconButtonLabel(en), modifier = Modifier.size(22.dp), tint = PicoTheme.colorScheme.labelPrimary)
            }
            Text(Copy.iconButtonLabel(en), style = PicoTheme.typography.labelLarge, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SampleSegments(en)
            Switch(checked = on, onCheckedChange = { on = it })
            Text(Copy.switchLabel(en), style = PicoTheme.typography.labelLarge, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1)
        }
        Text("titleMedium · " + Copy.legibleWord(en), style = PicoTheme.typography.titleMedium, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1)
        Text("bodyMedium · " + Copy.legibleWord(en), style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1)
        Text("labelMedium · " + Copy.legibleWord(en), style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1)
    }
}

@Composable
private fun Picture(sp: Specimen) {
    val name = sp.image ?: return
    val bmp = remember(name) { runCatching { PfGraph.files.decode(name, 1600) }.getOrNull() }
    if (bmp != null) Image(bitmap = bmp.asImageBitmap(), contentDescription = name, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(painter = painterResource(R.drawable.ic_broken_image), contentDescription = null, modifier = Modifier.size(48.dp), tint = PicoTheme.colorScheme.labelPrimary)
    }
}

/** The size label under a specimen, 320 x 64 dp: two lines, the second in alert colour on an ink chip when it warns. */
@Composable
fun SizeLabel(line1: String, line2: String, alert: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.size(320.dp, 64.dp).clip(RoundedCornerShape(18.dp)).backgroundMaterial(enable = true, style = Material.Regular).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PfText(line1, "label.l1", PfType.label, align = TextAlign.Center)
        if (alert) AlertText(line2, "label.l2", PfType.label) else PfText(line2, "label.l2", PfType.label, align = TextAlign.Center)
    }
}

/** The Stage's way back to the tool window after its × (native-window-move §3.3). */
@Composable
fun OpenPanelChip(text: String, onClick: () -> Unit) {
    Box(Modifier.size(220.dp, 64.dp).clip(RoundedCornerShape(32.dp)).backgroundMaterial(enable = true, style = Material.Regular), contentAlignment = Alignment.Center) {
        WordButton("openPanel", text, onClick, primary = true, iconRes = R.drawable.ic_open_in_new, minWidth = 200.dp)
    }
}

private const val FADED_ALPHA = 0.3f
