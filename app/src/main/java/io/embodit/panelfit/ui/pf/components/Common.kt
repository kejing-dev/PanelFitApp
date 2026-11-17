package io.embodit.panelfit.ui.pf.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentControlDefaults
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Text
import io.embodit.panelfit.domain.usecase.L
import io.embodit.panelfit.platform.PfLog
import io.embodit.panelfit.ui.pf.debug.LocalProbePanel
import io.embodit.panelfit.ui.pf.debug.probe
import io.embodit.panelfit.ui.theme.PfColors

/** The language chosen in the app (in-app switch, S17). */
val LocalEnglish = compositionLocalOf { false }

/** Copy in the chosen language. */
@Composable
@ReadOnlyComposable
fun tr(l: L): String = l(LocalEnglish.current)

@Composable
@ReadOnlyComposable
fun tr(l: L, vararg args: Any): String = l.format(LocalEnglish.current, *args)

/** Type roles of the tool UI: PicoTheme's scale, tabular figures where numbers change. */
internal object PfType {
    val title: TextStyle
        @Composable get() = PicoTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)

    val titleMedium: TextStyle
        @Composable get() = PicoTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum, lnum")

    val body: TextStyle
        @Composable get() = PicoTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum, lnum")

    val bodyMedium: TextStyle
        @Composable get() = PicoTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum, lnum")

    val label: TextStyle
        @Composable get() = PicoTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum, lnum")

    val small: TextStyle
        @Composable get() = PicoTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum, lnum")
}

/**
 * Text that reports clipping (profile D13): when its layout overflows, `OVERFLOW <panel>/<name>` goes to the log.
 * Single-line by default; [lines] > 1 wraps.
 */
@Composable
internal fun PfText(
    text: String,
    name: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = PicoTheme.colorScheme.labelPrimary,
    lines: Int = 1,
    align: TextAlign? = null,
) {
    val panel = LocalProbePanel.current
    // a one-line text that ends within 4 dp of its box counts too: on the panel it can still be clipped by the next control
    // (QA q1-1, "Too small below" against the − key, laid out without overflow)
    val tightPx = with(LocalDensity.current) { TIGHT_DP.dp.toPx() }
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style,
        maxLines = lines,
        softWrap = lines > 1,
        overflow = TextOverflow.Clip,
        textAlign = align ?: TextAlign.Unspecified,
        onTextLayout = { r ->
            val widest = (0 until r.lineCount).maxOfOrNull { r.getLineRight(it) - r.getLineLeft(it) } ?: 0f
            if (r.hasVisualOverflow) PfLog.w("OVERFLOW $panel/$name text=\"$text\"")
            else if (lines == 1 && fixed(r) && widest > r.size.width - tightPx) PfLog.w("OVERFLOW $panel/$name tight text=\"$text\"")
        },
    )
}

/** An icon key: at least 48 x 48 dp (taste B19), tertiary fill, primary icon. */
@Composable
internal fun IconKey(name: String, iconRes: Int, description: String, enabled: Boolean = true, size: Dp = 48.dp, onClick: () -> Unit) {
    val content = if (enabled) PicoTheme.colorScheme.labelPrimary else PicoTheme.colorScheme.labelTertiary
    IconButton(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.probe(name, onClick),
        colors = IconButtonDefaults.iconButtonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = content),
        size = IconButtonDefaults.iconButtonSize(width = size, height = size),
        enabled = enabled,
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = description, modifier = Modifier.size(22.dp), tint = content)
    }
}

/**
 * A worded button, capsule, 48 dp high: [primary] = the light fill with ink text (the main action), otherwise the
 * tertiary fill with primary text. Default button colours are not used: a tertiary fill on the system glass is too
 * faint (factory MainWindowTemplate note).
 */
@Composable
internal fun WordButton(
    name: String,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    minWidth: Dp = 96.dp,
    height: Dp = 48.dp,
    iconRes: Int? = null,
    danger: Boolean = false,
    style: TextStyle? = null,
) {
    val container =
        when {
            danger -> PicoTheme.colorScheme.error
            primary -> PicoTheme.colorScheme.labelPrimaryLight
            else -> PicoTheme.colorScheme.fillTertiary
        }
    val content =
        when {
            !enabled -> PicoTheme.colorScheme.labelTertiary
            primary || danger -> PfColors.Ink
            else -> PicoTheme.colorScheme.labelPrimary
        }
    Button(
        onClick = { if (enabled) onClick() },
        modifier = modifier.alpha(if (enabled) 1f else 0.6f).probe(name, onClick),
        size = ButtonDefaults.Regular.copy(height = height, minWidth = minWidth),
        shape = CircleShape,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        if (iconRes != null) {
            Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(20.dp), tint = content)
            Spacer(modifier = Modifier.width(6.dp))
        }
        PfText(text = text, name = "$name.text", color = content, style = style ?: PfType.label)
    }
}

/** A SpatialUI segment control for options within one page; selected = interaction at 26 %. */
@Composable
internal fun Segments(name: String, items: List<String>, selected: Int, onSelect: (Int) -> Unit, width: Dp, height: Dp = 48.dp, style: TextStyle? = null) {
    val itemWidth = width / items.size
    SegmentControl(modifier = Modifier.width(width).height(height), backgroundColor = PicoTheme.colorScheme.fillTertiary) {
        items.forEachIndexed { i, text ->
            SegmentItem(
                selected = selected == i,
                onClick = { onSelect(i) },
                modifier = Modifier.width(itemWidth).height(height).probe("$name.$i") { onSelect(i) },
                textStyle = style ?: PfType.label,
                colors =
                    SegmentControlDefaults.colors(
                        itemBackgroundColor = Color.Transparent,
                        itemContentColor = PicoTheme.colorScheme.labelPrimary,
                        selectedItemBackgroundColor = PicoTheme.colorScheme.interaction.copy(alpha = 0.26f),
                        selectedItemContentColor = PicoTheme.colorScheme.labelPrimary,
                    ),
                title = {
                    // design-style: inherited-content-color SegmentItem
                    PfText(text = text, name = "$name.$i.text", style = style ?: PfType.label)
                },
            )
        }
    }
}

/**
 * A stepper row (design `rules.placement.stepper`: IconButton + Text, not `Stepper`, whose default - / + fall under
 * 3:1 on light glass): label on the left, [-] value [+] on the right, 48 dp high.
 */
@Composable
internal fun StepperRow(name: String, label: String, value: String, enabled: Boolean = true, labelWidth: Dp = 112.dp, onStep: (Int) -> Unit, extra: (@Composable () -> Unit)? = null) {
    Row(modifier = Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        // an 8 dp gap before the − key: a label that just fits its width was still clipped by the key (QA q1-1)
        PfText(text = label, name = "$name.label", style = PfType.body, modifier = Modifier.width(labelWidth))
        Spacer(Modifier.width(8.dp))
        IconKey("$name.minus", io.embodit.panelfit.R.drawable.ic_remove, "-", enabled = enabled) { onStep(-1) }
        PfText(text = value, name = "$name.value", style = PfType.body, modifier = Modifier.width(132.dp), align = TextAlign.Center)
        IconKey("$name.plus", io.embodit.panelfit.R.drawable.ic_add, "+", enabled = enabled) { onStep(1) }
        if (extra != null) {
            Spacer(Modifier.width(8.dp))
            extra()
        }
    }
}

/** A toggle with an icon and a word (overlay switches): selected = interaction fill with black content (5.2:1; ink was 4.1:1). */
@Composable
internal fun ToggleChip(name: String, text: String, iconRes: Int, on: Boolean, width: Dp, onClick: () -> Unit) {
    val container = if (on) PicoTheme.colorScheme.interaction else PicoTheme.colorScheme.fillTertiary
    val content = if (on) PfColors.Black else PicoTheme.colorScheme.labelPrimary
    Button(
        onClick = onClick,
        modifier = Modifier.width(width).probe(name, onClick),
        size = ButtonDefaults.Regular.copy(height = 48.dp, minWidth = width),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(18.dp), tint = content)
        Spacer(modifier = Modifier.width(6.dp))
        PfText(text = text, name = "$name.text", color = content, style = PfType.label)
    }
}

/** A status dot on an ink chip (status colours sit on ink, not on the glass: D13 contrast). */
@Composable
internal fun Dot(color: Color) {
    Box(modifier = Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(PfColors.Ink), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
    }
}

/** Warning text on an ink chip: the alert colour on the light system glass is 1.5:1, on ink 9:1 (D13, emulator table). */
@Composable
internal fun AlertText(text: String, name: String, style: TextStyle, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(6.dp)).background(PfColors.Ink).padding(horizontal = 6.dp)) {
        PfText(text, name, style, color = PfColors.Alert)
    }
}

/** A thin divider line between settings rows. */
@Composable
internal fun RowDivider(width: Dp) {
    Box(modifier = Modifier.width(width).height(1.dp).background(PicoTheme.colorScheme.dividerLine))
}

private const val TIGHT_DP = 4f

/** A text given its width (fixed, weight, fill): only there can the next control clip a line that just fits. */
private fun fixed(r: androidx.compose.ui.text.TextLayoutResult): Boolean {
    val c = r.layoutInput.constraints
    return r.size.width > 0 && c.hasBoundedWidth && c.minWidth == c.maxWidth
}
