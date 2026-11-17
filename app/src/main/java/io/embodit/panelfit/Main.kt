package io.embodit.panelfit

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import io.embodit.panelfit.ui.pf.PfScreen
import io.embodit.panelfit.ui.pf.PfShared
import io.embodit.panelfit.ui.pf.window.PfWindow
import io.embodit.panelfit.ui.theme.PanelFitTheme

/**
 * App entry. The default container is one Mixed Stage (AndroidManifest: `pico.spatial.stage.style=1`) with the
 * specimens; the tool panel is a system Planar window opened from the Stage (factory native-window rule 2026-11-02,
 * deviation from the design's attachment tool panel), fixed at the design's 560 x 680 dp.
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PanelFitTheme { PfScreen() }
        }
        WindowContainer(
            PfShared.WINDOW_ID,
            form = Form.Planar,
            defaultSize = WindowContainerSize(width = 560.dp, height = 680.dp),
        ) {
            PfWindow(Modifier.windowConstraints(minWidth = 560.dp, minHeight = 680.dp, maxWidth = 560.dp, maxHeight = 680.dp))
        }
    }
