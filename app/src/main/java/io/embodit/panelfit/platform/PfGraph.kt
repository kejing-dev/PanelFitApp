package io.embodit.panelfit.platform

import android.annotation.SuppressLint
import android.content.Context
import io.embodit.panelfit.data.PfFiles
import io.embodit.panelfit.data.PfStore
import io.embodit.panelfit.platform.input.ControllerInput
import io.embodit.panelfit.platform.tracking.HeadTracker

/** Process-wide singletons: the Stage and the tool window are two compositions in one process. */
@SuppressLint("StaticFieldLeak")
object PfGraph {
    lateinit var appContext: Context
        private set

    val head = HeadTracker()
    val controller = ControllerInput()

    val store: PfStore by lazy { PfStore(appContext) }
    val files: PfFiles by lazy { PfFiles(appContext) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
