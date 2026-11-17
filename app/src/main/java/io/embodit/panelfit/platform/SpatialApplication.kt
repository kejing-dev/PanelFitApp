package io.embodit.panelfit.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import io.embodit.panelfit.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PfGraph.init(applicationContext)
        launch(::mainApp)
    }
}
