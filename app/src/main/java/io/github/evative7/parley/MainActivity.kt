package io.github.evative7.parley

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.evative7.parley.ui.OnboardingScreen
import io.github.evative7.parley.ui.TranslateScreen
import io.github.evative7.parley.ui.theme.ParleyTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ParleyTheme {
                // Checked silently on start; no dialog unless the user asks for one on the onboarding page.
                // Revoking the permission in system settings kills the process, so this re-runs then.
                var granted by remember { mutableStateOf(hasMicPermission()) }
                AnimatedContent(
                    targetState = granted,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "root",
                ) { ok ->
                    if (ok) TranslateScreen() else OnboardingScreen(onGranted = { granted = true })
                }
            }
        }
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
