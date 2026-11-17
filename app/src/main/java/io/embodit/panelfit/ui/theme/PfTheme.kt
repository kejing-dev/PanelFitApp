package io.embodit.panelfit.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.systemColorScheme

/**
 * The app theme: SpatialUI's system colour scheme and PicoTheme's own typography, unchanged, because the specimens
 * show what PicoTheme really looks like (design `container.notes`: the panels are real SpatialUI panels).
 */
@Composable
fun PanelFitTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    PicoTheme(colorScheme = systemColorScheme(context), content = content)
}

/**
 * The only colour literals of the app (design `entities3d` and `rules.overlays`): overlay lines and markers in the
 * room, verdict dots on ink chips, the selection frame. Panel chrome uses PicoTheme roles.
 */
object PfColors {
    val Reach = Color(0xFF7CF2A3) // design-style: fixed-figma-color reachGreen: reach arcs at 60 %
    val Interaction = Color(0xFFFFD24A) // design-style: fixed-figma-color interaction: selection frame, frames at 40 %
    val Error = Color(0xFFFF5A4E) // design-style: fixed-figma-color error: occlusion mask at 25 %
    val Alert = Color(0xFFFFB020) // design-style: fixed-figma-color alert: label line when covering
    val Ink = Color(0xFF1C1F22) // design-style: fixed-figma-color ink: chips behind verdict dots, text on light fills
    val White = Color(0xFFFFFFFF) // design-style: fixed-figma-color white: ruler bar
    val Black = Color(0xFF000000) // design-style: fixed-figma-color black: ruler ticks
    val SolidSurface = Color(0xFF2B2F36) // design-style: fixed-figma-color solidSurface: specimen "solid" surface under light text
    val SolidSurfaceLight = Color(0xFFECEEF2) // design-style: fixed-figma-color solidSurfaceLight: under dark text
    val Good = Color(0xFF5BD68A) // design-style: fixed-figma-color passable: reference "big enough" dot
    val Grey = Color(0xFF9AA0A6) // design-style: fixed-figma-color labelTertiary: not rated
}
