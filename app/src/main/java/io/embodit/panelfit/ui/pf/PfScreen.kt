package io.embodit.panelfit.ui.pf

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.dsl.registerSystem
import com.pico.spatial.ui.foundation.dsl.unregisterSystem
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectTapGestures
import com.pico.spatial.ui.platform.LocalAudioEffectPlayer
import com.pico.spatial.ui.platform.SpatialSoundEffect
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import io.embodit.panelfit.domain.model.Vec3
import io.embodit.panelfit.domain.usecase.Copy
import io.embodit.panelfit.domain.usecase.PfState
import io.embodit.panelfit.domain.usecase.SpecText
import io.embodit.panelfit.domain.usecase.TypeRole
import io.embodit.panelfit.domain.usecase.TypeRoles
import io.embodit.panelfit.platform.PfGraph
import io.embodit.panelfit.platform.PfLog
import io.embodit.panelfit.ui.pf.components.LocalEnglish
import io.embodit.panelfit.ui.pf.components.OpenPanelChip
import io.embodit.panelfit.ui.pf.components.SizeLabel
import io.embodit.panelfit.ui.pf.components.SpecimenPanel
import io.embodit.panelfit.ui.pf.components.styleOf
import io.embodit.panelfit.ui.pf.components.tr
import io.embodit.panelfit.ui.pf.debug.LocalProbePanel
import io.embodit.panelfit.ui.pf.debug.PfProbe
import io.embodit.panelfit.ui.pf.scene.PfFrameSystem
import io.embodit.panelfit.ui.pf.scene.PfScene
import io.embodit.panelfit.ui.pf.window.exitApp
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Stage (Mixed, passthrough): specimens, their labels, the overlays and the "open panel" chip. The tool window is
 * a system window opened from here (`Main.kt`, native-window-move §3.3) and recalled by close, wait for ON_DESTROY,
 * open (§3.5).
 */
@Composable
fun PfScreen() {
    val context = LocalContext.current
    val viewModel = PfShared.viewModel
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalSpatialNavigator.current
    remember(context) {
        PfProbe.enabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        true
    }
    val scene = remember { PfScene(viewModel::onEvent) }

    DisposableEffect(scene) {
        registerSystem<PfFrameSystem>()
        PfProbe.host =
            object : PfProbe.Host {
                override fun dispatch(event: PfEvent) = viewModel.onEvent(event)

                override fun uiState(): PfState = viewModel.state.value

                override fun dumpJson(): String = PfShared.dump()

                override fun scene(): PfProbe.SceneProbe = scene

                override fun pressControl(name: String): Boolean = PfProbe.click(name)
            }
        PfLog.i("STAGE enter")
        onDispose {
            PfProbe.host = null
            unregisterSystem<PfFrameSystem>()
            scene.detach()
            PfLog.i("STAGE disposed")
        }
    }

    // the tool window opens with the Stage; "recall" = close, wait for ON_DESTROY, open (native-window-move §3.5)
    var recallPending by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        PfLog.i("WINDOW open request")
        runCatching { navigator.openWindowContainer(PfShared.WINDOW_ID, PfShared.WINDOW_TAG, null) }.onFailure { PfLog.e("WINDOW open failed: $it", it) }
    }
    LaunchedEffect(PfShared.window) {
        if (recallPending && PfShared.window == "ON_DESTROY") {
            recallPending = false
            PfLog.i("WINDOW recall: reopen")
            runCatching { navigator.openWindowContainer(PfShared.WINDOW_ID, PfShared.WINDOW_TAG, null) }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        scene.onBackground()
                        viewModel.onEvent(PfEvent.Background)
                    }
                    Lifecycle.Event.ON_START -> {
                        scene.onForeground()
                        viewModel.onEvent(PfEvent.Foreground)
                    }
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(scene, state) { scene.render(state) }

    // PicoTheme sizes are read at run time (design `rules.math.text`), with the system font scale
    val density = LocalDensity.current
    val roles = TypeRoles.names().map { r -> r to styleOf(r).fontSize.value }
    LaunchedEffect(roles, density.fontScale) {
        viewModel.onEvent(PfEvent.Roles(roles.mapIndexed { i, (r, sp) -> TypeRole(r, i / 3, sp) }, density.fontScale))
    }

    // PF-COLORS: the resolved theme roles, for the contrast table (tools/contrast.py, ui-audit.md)
    val scheme = com.pico.spatial.ui.design.PicoTheme.colorScheme
    LaunchedEffect(scheme) {
        fun hex(c: androidx.compose.ui.graphics.Color) = String.format(java.util.Locale.US, "#%08X", c.toArgb())
        PfLog.i(
            "PF-COLORS labelPrimary=${hex(scheme.labelPrimary)} labelSecondary=${hex(scheme.labelSecondary)} labelTertiary=${hex(scheme.labelTertiary)} " +
                "labelPrimaryLight=${hex(scheme.labelPrimaryLight)} fillTertiary=${hex(scheme.fillTertiary)} interaction=${hex(scheme.interaction)} " +
                "error=${hex(scheme.error)} alert=${hex(scheme.alert)} passable=${hex(scheme.passable)} dividerLine=${hex(scheme.dividerLine)}",
        )
    }

    val audio = LocalAudioEffectPlayer.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                PfEffect.OpenWindow -> runCatching { navigator.openWindowContainer(PfShared.WINDOW_ID, PfShared.WINDOW_TAG, null) }
                PfEffect.RecallWindow ->
                    if (PfShared.window == "ON_DESTROY" || PfShared.window == "never") {
                        runCatching { navigator.openWindowContainer(PfShared.WINDOW_ID, PfShared.WINDOW_TAG, null) }
                    } else {
                        recallPending = true
                        PfLog.i("WINDOW recall: close")
                        runCatching { navigator.closeWindowContainer(PfShared.WINDOW_ID, PfShared.WINDOW_TAG) }
                    }
                PfEffect.Exit -> launch { exitApp(context, navigator, PfShared.WINDOW_ID, PfShared.WINDOW_TAG) { PfGraph.head.stop(); PfGraph.controller.stop() } }
                is PfEffect.Sound ->
                    runCatching {
                        when (effect.kind) {
                            SoundKind.TAP -> audio.playSystem(SpatialSoundEffect.OpClick)
                            SoundKind.SNAP -> audio.playSystem(SpatialSoundEffect.OpDragEnd)
                            SoundKind.SAVED -> audio.playSystem(SpatialSoundEffect.StateSelected)
                            SoundKind.FAIL -> audio.playSystem(SpatialSoundEffect.StateFailure)
                        }
                    }.onFailure { PfLog.w("SOUND failed: $it") }
            }
        }
    }

    CompositionLocalProvider(LocalEnglish provides state.english) {
        PfStage(state, scene, viewModel::onEvent)
    }
}

/**
 * One SpatialView carrying the scene and the attachment panels the scene asks for ([PfScene.declared]: at most two
 * new ones per frame). Specimen panels are keyed by size: a new size is a new panel.
 */
@Composable
private fun PfStage(state: PfState, scene: PfScene, onEvent: (PfEvent) -> Unit) {
    val bindTick = scene.bindTick.intValue
    val keys = scene.declared.toList()
    val context = LocalContext.current
    SpatialView(
        // I5: the label colliders take the system pointer; the recognizers sit on this view, which never moves, so a
        // drag's increments are the hand's (a gesture on a moving attachment panel got none from a real pinch, QA q1-2)
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        context = context,
                        targetedToEntity = TargetEntity.any { e -> scene.acceptLabelHit(e) },
                        onTap = { scene.onLabelTap() },
                    )
                }
                .pointerInput(Unit) {
                    detectSpatialDragGesture(
                        context = context,
                        targetedToEntity = TargetEntity.any { e -> scene.acceptLabelHit(e) },
                        onDragStart = { _, _ -> scene.onLabelGesture("start", Vec3.ZERO) },
                        onDragEnd = { scene.onLabelGesture("end", Vec3.ZERO) },
                        onDragCancel = { scene.onLabelGesture("cancel", Vec3.ZERO) },
                    ) { value ->
                        val a = value.dragAmount
                        scene.onLabelGesture("move", Vec3(a.x, a.y, a.z))
                    }
                },
        attachments = {
            for (k in keys) {
                when {
                    k == PfScene.CHIP ->
                        AttachmentPanel(k) {
                            Pooled(k, scene, state.english) { inView -> if (inView) OpenPanelChip(tr(Copy.openPanel)) { onEvent(PfEvent.RecallWindow) } }
                        }
                    k.startsWith(PfScene.LABEL) ->
                        AttachmentPanel(k) {
                            Pooled(k, scene, state.english) { inView -> if (inView) LabelFor(k, state, scene, onEvent) }
                        }
                    k.startsWith(PfScene.SPEC) -> {
                        val id = k.substringAfter(':').substringBefore(':').toIntOrNull()
                        val sp = state.specimens.firstOrNull { it.id == id }
                        if (sp != null) {
                            AttachmentPanel(k) {
                                // the panel keeps the size its key names, also while a new size waits for its swap
                                val (w, h) = k.substringAfterLast(':').split('x').let { it[0].toInt() to it[1].toInt() }
                                Pooled(k, scene, state.english) { inView ->
                                    SpecimenPanel(
                                        sp.copy(wDp = w, hDp = h),
                                        selected = state.selected == sp.id,
                                        inView = inView,
                                        faded = sp.id in state.inside || sp.id in state.blocksWindow,
                                        modifier = Modifier.observePress { onEvent(PfEvent.Select(sp.id)) },
                                        onRendered = { role, rendered -> reportRendered(state, role, rendered, onEvent) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        update = { _, attachments ->
            @Suppress("UNUSED_EXPRESSION") bindTick
            scene.bindPanels(attachments)
        },
        initial = { content, attachments -> scene.attach(content, attachments) },
    )
}

private val reported = HashMap<String, Float>()

/** PF-METRIC theme vs rendered size, once per role (and again when it changes by more than 0.5 sp). */
private fun reportRendered(state: PfState, role: String, rendered: Float, onEvent: (PfEvent) -> Unit) {
    val last = reported[role]
    if (last != null && abs(last - rendered) < 0.5f) return
    reported[role] = rendered
    val theme = state.roles.firstOrNull { it.role == role }?.themeSp
    PfLog.i("PF-METRIC type role=$role themeSp=$theme renderedSp=${PfLog.f(rendered, 1)}")
    onEvent(PfEvent.Rendered(role, rendered))
}

/** Observes a press on the panel without consuming it (design I2: the specimen's own components still react). */
private fun Modifier.observePress(onPress: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                if (e.type == PointerEventType.Press) onPress()
            }
        }
    }

/** A size label: which specimen it belongs to comes from the scene's label slots; its collider takes the drag (I5). */
@Composable
private fun LabelFor(key: String, state: PfState, scene: PfScene, onEvent: (PfEvent) -> Unit) {
    val index = key.removePrefix(PfScene.LABEL).toIntOrNull() ?: return
    val sp = state.specimens.filter { !it.hidden }.firstOrNull { scene.labelIndexOf(it.id) == index } ?: return
    val en = state.english
    val r = state.readings[sp.id]
    val cover = state.overlaps.firstOrNull { it.front == sp.id }
    val (line1, line2, alert) =
        when {
            !state.headOk || r == null -> Triple(Copy.f2(sp.pose.dist) + " m", Copy.labelNoAngles(en), false)
            sp.id in state.resizing -> Triple(SpecText.labelLine1(en, r), Copy.labelResizing(en), false)
            sp.id in state.blocksWindow -> Triple(SpecText.labelLine1(en, r), Copy.labelBlocksWindow(en), true)
            cover != null -> Triple(SpecText.labelLine1(en, r), SpecText.covers(en, cover), true)
            else -> Triple(SpecText.labelLine1(en, r), SpecText.labelLine2(en, r), false)
        }
    // a press that lands on the label panel rather than its collider still selects
    SizeLabel(line1, line2, alert, modifier = Modifier.observePress { onEvent(PfEvent.Select(sp.id)) })
}

/**
 * Root of a pooled panel: [content] gets `inView` (moved into view two frames ago). On the idle emulator a panel can
 * keep a half-recorded first frame: the content is composed afresh 1.5, 4 and 8 s after it came into view (factory
 * finding, Bianzhong).
 */
@Composable
private fun Pooled(key: String, scene: PfScene, english: Boolean, content: @Composable (inView: Boolean) -> Unit) {
    val inView = scene.panelShown[key] == true
    var gen by remember { mutableIntStateOf(0) }
    LaunchedEffect(inView) {
        if (!inView) return@LaunchedEffect
        for (wait in REFRESH_STEPS_MS) {
            delay(wait)
            gen++
        }
    }
    CompositionLocalProvider(LocalProbePanel provides key, LocalEnglish provides english) { key(gen) { content(inView) } }
}

private val REFRESH_STEPS_MS = longArrayOf(1_500L, 2_500L, 4_000L)
