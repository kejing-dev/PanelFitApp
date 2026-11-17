package io.embodit.panelfit.ui.pf.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.PicoTheme
import io.embodit.panelfit.R
import io.embodit.panelfit.domain.usecase.Copy
import io.embodit.panelfit.domain.usecase.Page
import io.embodit.panelfit.domain.usecase.PfState
import io.embodit.panelfit.domain.usecase.Phase
import io.embodit.panelfit.domain.usecase.Sub
import io.embodit.panelfit.domain.usecase.Toast
import io.embodit.panelfit.ui.pf.PfEvent
import io.embodit.panelfit.ui.pf.PfShared
import io.embodit.panelfit.ui.pf.components.IconKey
import io.embodit.panelfit.ui.pf.components.LocalEnglish
import io.embodit.panelfit.ui.pf.components.PfText
import io.embodit.panelfit.ui.pf.components.PfType
import io.embodit.panelfit.ui.pf.components.WordButton
import io.embodit.panelfit.ui.pf.components.tr
import io.embodit.panelfit.ui.pf.debug.LocalProbePanel
import io.embodit.panelfit.ui.pf.debug.probe
import io.embodit.panelfit.ui.theme.PanelFitTheme

/**
 * Content of the tool window (Planar WindowContainer, `Main.kt`): the design's tool panel moved into a system window
 * (native-window rule 2026-11-02): four TabBar pages (library, adjust, legibility, spec) and settings. Moving and
 * closing belong to the system window.
 */
@Composable
fun PfWindow(modifier: Modifier = Modifier) {
    val viewModel = PfShared.viewModel
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onEvent = viewModel::onEvent

    // the Stage shows its "open panel" chip while this window is closed, and recalls it by close + reopen
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> PfShared.windowEvent(e.name) }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            PfShared.windowEvent("ON_DESTROY")
        }
    }

    val en = state.english
    PanelFitTheme {
        CompositionLocalProvider(LocalEnglish provides en, LocalProbePanel provides "window") {
            MainWindow(
                modifier = modifier,
                pages =
                    listOf(
                        MainWindowPage(Page.LIBRARY.id, tr(Copy.tabLibrary), R.drawable.ic_dashboard) { LibraryTab(state, onEvent) },
                        MainWindowPage(Page.ADJUST.id, tr(Copy.tabAdjust), R.drawable.ic_tune) { Gate(state, onEvent) { AdjustPage(state, onEvent) } },
                        MainWindowPage(Page.RATE.id, tr(Copy.tabRate), R.drawable.ic_visibility) { Gate(state, onEvent) { RatePage(state, onEvent) } },
                        MainWindowPage(Page.CARD.id, tr(Copy.tabCard), R.drawable.ic_description) { Gate(state, onEvent) { CardPage(state, onEvent) } },
                    ),
                current = if (state.page == Page.SETTINGS) SETTINGS else state.page.id,
                onSelect = { id -> onEvent(PfEvent.GoPage(Page.entries.firstOrNull { it.id == id } ?: Page.SETTINGS)) },
                settingsLabel = tr(Copy.tabSettings),
                settingsIcon = R.drawable.ic_settings,
                header = { Header(state, onEvent) },
                hint = { HintRow(state, onEvent) },
                // quitting runs on the Stage (PfScreen: release, close window and Stage, remove tasks, end process)
                settings = { SettingsContent(state, onEvent) {} },
            )
        }
    }
}

/** TabBar id of a page. */
val Page.id: String
    get() = name.lowercase()

/** Preparing / welcome cover every page but settings (S00 / S01). */
@Composable
private fun Gate(state: PfState, onEvent: (PfEvent) -> Unit, content: @Composable () -> Unit) {
    when (state.phase) {
        Phase.PREPARING -> PreparingView()
        Phase.WELCOME -> WelcomeView(onEvent)
        Phase.READY -> content()
    }
}

@Composable
private fun LibraryTab(state: PfState, onEvent: (PfEvent) -> Unit) {
    Gate(state, onEvent) {
        when (state.sub) {
            Sub.NONE -> LibraryPage(state, onEvent)
            Sub.LIST -> ListPage(state, onEvent)
            Sub.IMPORT -> ImportPage(state, onEvent)
        }
    }
}

/** Header row, 48 dp: page title (or "← 返回 · 页名" on the pages reached from the library) and the sound key (D15). */
@Composable
private fun Header(state: PfState, onEvent: (PfEvent) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        val sub = if (state.page == Page.LIBRARY && state.phase == Phase.READY) state.sub else Sub.NONE
        if (sub != Sub.NONE) {
            IconKey("back", R.drawable.ic_arrow_back, tr(Copy.back)) { onEvent(PfEvent.GoSub(Sub.NONE)) }
            Spacer(Modifier.width(12.dp))
            PfText(tr(if (sub == Sub.LIST) Copy.pageList else Copy.pageImport), "header.title", PfType.title, Modifier.weight(1f))
        } else {
            val title =
                when (state.page) {
                    Page.LIBRARY -> Copy.tabLibrary
                    Page.ADJUST -> Copy.tabAdjust
                    Page.RATE -> Copy.tabRate
                    Page.CARD -> Copy.tabCard
                    Page.SETTINGS -> Copy.tabSettings
                }
            PfText(tr(Copy.appName) + " · " + tr(title), "header.title", PfType.title, Modifier.weight(1f))
        }
        val on = state.settings.sound
        IconKey("sound", if (on) R.drawable.ic_volume_up else R.drawable.ic_volume_off, tr(Copy.sound)) {
            onEvent(PfEvent.Settings(state.settings.copy(sound = !on)))
        }
    }
}

/**
 * The hint row (design `panels.toast`): save result > stick / drag hint > import error, layout restored, covering the
 * tool panel > head tracking unavailable > the page's default line. Two lines at most, never a dialog.
 */
@Composable
private fun HintRow(state: PfState, onEvent: (PfEvent) -> Unit) {
    val en = LocalEnglish.current
    val t = state.toast
    data class Hint(val icon: Int, val text: String, val strong: Boolean = false, val action: (@Composable () -> Unit)? = null)
    val blocking = state.blocksWindow.firstOrNull()
    val hint =
        when {
            t != null && t.isSave ->
                when (t.kind) {
                    Toast.Kind.SAVED_PUBLIC -> Hint(R.drawable.ic_check_circle, Copy.savedPublic.format(en, t.arg))
                    Toast.Kind.SAVED_PRIVATE -> Hint(R.drawable.ic_folder, Copy.savedPrivate.format(en, t.arg))
                    else -> Hint(R.drawable.ic_error, Copy.savedNone(en))
                }
            state.pendingFrontMs != null -> Hint(R.drawable.ic_my_location, Copy.hintLookAhead(en), strong = true)
            state.stickActive -> Hint(R.drawable.ic_open_with, Copy.hintStick(en), strong = true)
            state.dragging != null -> Hint(R.drawable.ic_open_with, Copy.hintDrag(en), strong = true)
            !state.headOk -> Hint(R.drawable.ic_gps_off, Copy.hintHead(en)) // S16 stays (design: above the rest)
            t?.kind == Toast.Kind.IMPORT_BAD -> Hint(R.drawable.ic_broken_image, Copy.hintImportBad(en))
            t?.kind == Toast.Kind.RESTORED -> Hint(R.drawable.ic_replay, Copy.hintRestored(en))
            blocking != null ->
                Hint(R.drawable.ic_block, Copy.hintBlocksWindow(en)) {
                    WordButton("moveAside", Copy.moveItAway(en), { onEvent(PfEvent.MoveOutOfWindow(blocking)) }, minWidth = 96.dp)
                }
            state.page == Page.LIBRARY && state.full -> Hint(R.drawable.ic_info, Copy.libraryFull(en))
            state.page == Page.LIBRARY -> Hint(R.drawable.ic_info, Copy.libraryHint(en))
            state.page == Page.RATE -> Hint(R.drawable.ic_info, Copy.overlaysHidden(en))
            state.page == Page.SETTINGS && state.settingsPage == 0 -> Hint(R.drawable.ic_info, Copy.howCalibShort(en))
            state.selected == null && state.page != Page.SETTINGS -> Hint(R.drawable.ic_info, Copy.noneStick(en))
            else -> Hint(R.drawable.ic_info, Copy.hintDefault(en))
        }
    val bg = if (hint.strong) PicoTheme.colorScheme.interaction else PicoTheme.colorScheme.fillTertiary
    val fg = if (hint.strong) io.embodit.panelfit.ui.theme.PfColors.Black else PicoTheme.colorScheme.labelPrimary // 5.2:1 on interaction
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 12.dp).probe("hint"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(hint.icon), contentDescription = null, modifier = Modifier.size(20.dp), tint = fg)
        Spacer(Modifier.width(8.dp))
        PfText(hint.text, "hint.text", PfType.small, Modifier.weight(1f), color = fg, lines = 2)
        hint.action?.invoke()
    }
}
