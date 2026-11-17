package io.embodit.panelfit.ui.pf

import io.embodit.panelfit.domain.model.Language
import io.embodit.panelfit.domain.model.PfSettings
import io.embodit.panelfit.domain.model.PolarPose
import io.embodit.panelfit.domain.model.Rating
import io.embodit.panelfit.domain.model.Surface
import io.embodit.panelfit.domain.model.Template
import io.embodit.panelfit.domain.model.Vec3
import io.embodit.panelfit.domain.usecase.Confirm
import io.embodit.panelfit.domain.usecase.Field
import io.embodit.panelfit.domain.usecase.Page
import io.embodit.panelfit.domain.usecase.Sub
import io.embodit.panelfit.domain.usecase.TypeRole

/** Everything that changes the app: user input from the window and the Stage, the scene's sensing, debug commands. */
sealed interface PfEvent {
    // window: library and navigation
    data class Add(val template: Template) : PfEvent

    data class ImportPick(val name: String) : PfEvent

    data object RefreshImports : PfEvent

    data class GoPage(val page: Page) : PfEvent

    data class GoSub(val sub: Sub) : PfEvent

    data class AdjustPage(val index: Int) : PfEvent

    data class CardPage(val index: Int) : PfEvent

    data class RateGroup(val index: Int) : PfEvent

    data class SettingsPage(val index: Int) : PfEvent

    data object TryOne : PfEvent

    // selection and adjusting
    data class Select(val id: Int?) : PfEvent

    data object Done : PfEvent

    data class Step(val field: Field, val dir: Int) : PfEvent

    data object TiltToEye : PfEvent

    data class SetSurface(val surface: Surface) : PfEvent

    data class Overlay(val key: String, val on: Boolean? = null) : PfEvent

    data class Rate(val role: String, val rating: Rating) : PfEvent

    data object Save : PfEvent

    // list
    data class MoveToFront(val id: Int) : PfEvent

    data class ToggleHidden(val id: Int) : PfEvent

    data class AskDelete(val id: Int?) : PfEvent

    data class Delete(val id: Int) : PfEvent

    data class MoveOutOfWindow(val id: Int) : PfEvent

    // settings
    data class Settings(val settings: PfSettings) : PfEvent

    data class Lang(val language: Language) : PfEvent

    data class Ask(val confirm: Confirm?) : PfEvent

    data object ClearAll : PfEvent

    data object QuitYes : PfEvent

    data object AllToFront : PfEvent

    /** The head came to rest after "回到面前" (scene, D29). */
    data class FrontSettled(val eye: Vec3, val forward: Vec3) : PfEvent

    data object RecallWindow : PfEvent

    data object ShowWelcome : PfEvent

    // scene -> model
    data class Ready(val eye: Vec3, val forward: Vec3, val headOk: Boolean) : PfEvent

    data class Head(val eye: Vec3?, val forward: Vec3?, val ok: Boolean) : PfEvent

    data class LivePose(val id: Int, val pose: PolarPose) : PfEvent

    data class StickActive(val id: Int) : PfEvent

    data class StickReleased(val id: Int, val pose: PolarPose) : PfEvent

    data class DragStart(val id: Int) : PfEvent

    data class DragReleased(val id: Int, val center: Vec3) : PfEvent

    data class Resized(val id: Int) : PfEvent

    data class Roles(val roles: List<TypeRole>, val fontScale: Float) : PfEvent

    data class Rendered(val role: String, val sp: Float) : PfEvent

    data class Window(val lifecycle: String) : PfEvent

    data object Background : PfEvent

    data object Foreground : PfEvent

    data class Tick(val nowMs: Long) : PfEvent
}

/** One-shot outputs the containers act on. */
sealed interface PfEffect {
    data object OpenWindow : PfEffect

    data object RecallWindow : PfEffect

    data object Exit : PfEffect

    data class Sound(val kind: SoundKind) : PfEffect
}

enum class SoundKind { TAP, SNAP, SAVED, FAIL }
