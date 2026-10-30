package io.github.evative7.parley

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.github.evative7.parley.debug.DebugModeStore
import javax.inject.Inject

@HiltAndroidApp
class ParleyApp : Application() {
    // Injected at startup so the saved debug mode applies before the first screen.
    @Inject lateinit var debugMode: DebugModeStore
}
