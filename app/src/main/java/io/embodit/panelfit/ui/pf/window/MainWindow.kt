package io.embodit.panelfit.ui.pf.window

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.TabBar
import com.pico.spatial.ui.platform.ViewPoint
import com.pico.spatial.ui.platform.containers.SpatialNavigator
import io.embodit.panelfit.platform.PfLog
import io.embodit.panelfit.ui.pf.components.PfText
import io.embodit.panelfit.ui.pf.components.PfType
import io.embodit.panelfit.ui.pf.components.WordButton
import io.embodit.panelfit.ui.pf.debug.probe
import kotlin.system.exitProcess

/*
 * Main window, from the factory template (prompts/snippets/MainWindowTemplate.kt, taste D8 / D21 / D26): all 2D content
 * of the app in one system window, pages switched by the native TabBar, settings last with "quit" at its bottom and an
 * inline confirmation. Changed from the template: the current page and the quit confirmation are hoisted into the
 * app's model (debug `state` reaches them through the same events as a press), the copy is bilingual, and every page
 * gets the same header (title + sound key) and hint row (design `panels.toolPanel`).
 */

private const val TAG_PAGE = "PAGE"

/** One page of the main window. [icon]: a Material icon (taste D23). */
class MainWindowPage(
    val id: String,
    val label: String,
    @DrawableRes val icon: Int,
    val content: @Composable () -> Unit,
)

/**
 * The single main window. [pages] are the app's pages (at most 6; the TabBar holds 7 and the last is Settings).
 * [header] is drawn above every page, [hint] below; [settings] is the settings page content including its quit row
 * ([QuitRow]).
 */
@Composable
fun MainWindow(
    pages: List<MainWindowPage>,
    modifier: Modifier = Modifier,
    current: String,
    onSelect: (String) -> Unit,
    settingsLabel: String,
    @DrawableRes settingsIcon: Int,
    header: @Composable () -> Unit,
    hint: @Composable () -> Unit,
    settings: @Composable ColumnScope.() -> Unit,
) {
    require(pages.size in 1..6) { "TabBar holds at most 7 items: 6 pages + Settings" }
    val page = pages.firstOrNull { it.id == current }
    Column(modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp)) {
        header()
        Box(Modifier.fillMaxWidth().height(CONTENT_H.dp).padding(top = 8.dp)) {
            if (page != null) page.content() else Column(verticalArrangement = Arrangement.spacedBy(0.dp)) { settings() }
        }
        Box(Modifier.fillMaxWidth().padding(top = 8.dp)) { hint() }
    }

    // system TabBar: its own small window outside this one (emulator 6.1.0, seen from the front: centred above it)
    TabBar(followViewpoints = setOf(ViewPoint.Front, ViewPoint.Left)) {
        pages.forEach { p ->
            item(
                selected = current == p.id,
                onClick = {
                    PfLog.i("$TAG_PAGE ${p.id}")
                    onSelect(p.id)
                },
                mainContent = { Icon(painter = painterResource(p.icon), contentDescription = p.label) },
                supportContent = { Text(p.label) },
            )
        }
        item(
            selected = current == SETTINGS,
            onClick = {
                PfLog.i("$TAG_PAGE $SETTINGS")
                onSelect(SETTINGS)
            },
            mainContent = { Icon(painter = painterResource(settingsIcon), contentDescription = settingsLabel) },
            supportContent = { Text(settingsLabel) },
        )
    }
}

const val SETTINGS = "settings"

/** Content area of every page, dp (design: 484). */
const val CONTENT_H = 492

/**
 * D26: the last row of the settings page. The confirmation replaces the row in place (no second panel):
 * "退出面板试衣间？[取消] [退出]".
 */
@Composable
fun QuitRow(label: String, ask: String, cancel: String, quit: String, note: String, confirming: Boolean, onAsk: (Boolean) -> Unit, onQuit: () -> Unit) {
    if (!confirming) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            PfText(label, "quit.label", PfType.body, Modifier.weight(1f))
            WordButton("quit", quit, { onAsk(true) }, iconRes = io.embodit.panelfit.R.drawable.ic_logout)
        }
    } else {
        Row(Modifier.fillMaxWidth().height(48.dp).probe("quit.confirm"), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PfText(ask, "quit.ask", PfType.body)
                PfText(note, "quit.note", PfType.small)
            }
            WordButton("quit.cancel", cancel, { onAsk(false) }, minWidth = 80.dp)
            Box(Modifier.width(8.dp))
            WordButton("quit.yes", quit, onQuit, danger = true, minWidth = 80.dp)
        }
    }
}

/** D26 exit: release, close the window and the Stage, remove every task, then end the process. */
suspend fun exitApp(context: Context, navigator: SpatialNavigator, windowId: String, windowTag: String, onExit: () -> Unit) {
    PfLog.i("EXIT release + closeWindow + closeStage + finishAndRemoveTask + exitProcess")
    runCatching(onExit).onFailure { PfLog.e("EXIT release failed", it) }
    runCatching { navigator.closeWindowContainer(windowId, windowTag) }.onFailure { PfLog.w("EXIT closeWindow: $it") }
    runCatching { navigator.closeStage() }.onFailure { PfLog.w("EXIT closeStage: $it") }
    runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.appTasks.forEach { runCatching { it.finishAndRemoveTask() } }
    }.onFailure { PfLog.e("EXIT finishAndRemoveTask failed", it) }
    Handler(Looper.getMainLooper()).postDelayed({ exitProcess(0) }, 400)
}
