package io.embodit.panelfit.ui.pf

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.embodit.panelfit.platform.PfGraph
import io.embodit.panelfit.platform.PfLog

/**
 * What the Stage and the tool window share (factory snippet native-window-move §3.4): two compositions in one
 * process, so the model lives here and not in either container's `viewModel()`.
 */
object PfShared {
    /** Tool window container id and its fixed tag (one instance, reused). */
    const val WINDOW_ID = "PanelFitTools"
    const val WINDOW_TAG = "main"

    @Volatile private var vm: PfViewModel? = null

    val viewModel: PfViewModel
        get() = vm ?: synchronized(this) { vm ?: PfViewModel(PfGraph.store, PfGraph.files).also { vm = it } }

    /** Last lifecycle event the window reported ("never" until it first opens). */
    var window by mutableStateOf("never")
        private set

    fun windowEvent(name: String) {
        if (window != name) PfLog.i("WINDOW $name")
        window = name
        viewModel.onEvent(PfEvent.Window(name))
    }

    /** The model as one JSON line for the debug `dump` command. */
    fun dump(): String {
        val s = viewModel.state.value
        val o = org.json.JSONObject()
        o.put("state", PfViewModel.stateName(s))
        o.put("page", s.page.name).put("sub", s.sub.name).put("selected", s.selected ?: org.json.JSONObject.NULL)
        o.put("headOk", s.headOk).put("windowYawDeg", s.windowYawDeg?.toDouble() ?: org.json.JSONObject.NULL).put("window", window)
        o.put("anchor", s.anchor?.let { "${it.eye} yaw=${it.yaw}" } ?: org.json.JSONObject.NULL)
        val arr = org.json.JSONArray()
        for (sp in s.specimens) {
            val r = s.readings[sp.id]
            arr.put(
                org.json.JSONObject().put("id", sp.id).put("t", sp.template.id).put("dp", "${sp.wDp}x${sp.hDp}").put("scale", sp.scale.toDouble())
                    .put("mode", sp.mode.name).put("hidden", sp.hidden).put("pose", sp.pose.toString())
                    .put("dist", r?.dist?.toDouble() ?: org.json.JSONObject.NULL).put("ang", r?.let { "${it.angW}x${it.angH}" } ?: org.json.JSONObject.NULL)
                    .put("eq", r?.let { "${it.eqW}x${it.eqH}" } ?: org.json.JSONObject.NULL),
            )
        }
        o.put("specimens", arr)
        o.put("overlaps", org.json.JSONArray(s.overlaps.map { "${it.front}>${it.back}:${it.part}" }))
        o.put("blocksWindow", org.json.JSONArray(s.blocksWindow.toList())).put("inside", org.json.JSONArray(s.inside.toList())).put("resizing", org.json.JSONArray(s.resizing.toList()))
        o.put("toast", s.toast?.kind?.name ?: org.json.JSONObject.NULL).put("lastSaved", s.lastSaved ?: org.json.JSONObject.NULL)
        return o.toString()
    }

    /** The window is on screen (not closed with its ×). */
    val windowOpen: Boolean
        get() = window == "ON_CREATE" || window == "ON_START" || window == "ON_RESUME" || window == "ON_PAUSE"
}
