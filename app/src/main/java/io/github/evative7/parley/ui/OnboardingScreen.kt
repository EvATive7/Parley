package io.github.evative7.parley.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.evative7.parley.R
import kotlinx.coroutines.delay

/** Shown whenever RECORD_AUDIO is missing. Asks only when the user taps the button. */
@Composable
fun OnboardingScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme

    var success by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            success = true
        } else {
            // After a denial the rationale flag flips to false only once the system stops asking.
            permanentlyDenied = context.findActivity()
                ?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == false
        }
    }
    // Coming back from system settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) success = true
    }
    LaunchedEffect(success) {
        if (success) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            delay(900) // let the illustration finish morphing into the check
            onGranted()
        }
    }

    val morpher = rememberShapeMorpher(
        if (success) MaterialShapes.Circle else MaterialShapes.Cookie12Sided,
        motion.slowSpatialSpec(),
    )
    val spin = rememberSpin(
        spinning = !success,
        symmetry = 12, // Cookie12Sided
        settleSpec = motion.slowSpatialSpec(),
        periodMs = 20_000,
    )
    val fill by animateColorAsState(
        if (success) colors.primary else colors.primaryContainer, motion.slowEffectsSpec(), label = "fill",
    )
    val tint by animateColorAsState(
        if (success) colors.onPrimary else colors.onPrimaryContainer, motion.slowEffectsSpec(), label = "tint",
    )

    Surface(color = colors.surface) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                MorphCanvas(morpher, color = { fill }, rotation = { spin.value }, modifier = Modifier.fillMaxSize())
                AnimatedContent(
                    targetState = success,
                    transitionSpec = {
                        (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), 0.4f)) togetherWith
                            (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), 0.4f))
                    },
                    label = "illustration",
                ) { done ->
                    Icon(
                        painterResource(if (done) R.drawable.ic_check else R.drawable.ic_mic),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = tint,
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
            Text(
                stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(if (permanentlyDenied) R.string.onboarding_denied_hint else R.string.onboarding_body),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = {
                    if (permanentlyDenied) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", context.packageName, null)),
                        )
                    } else {
                        launcher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                enabled = !success,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                contentPadding = ButtonDefaults.contentPaddingFor(56.dp),
            ) {
                Text(
                    stringResource(if (permanentlyDenied) R.string.onboarding_open_settings else R.string.onboarding_grant),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
