package io.github.evative7.parley.ui

import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import io.github.evative7.parley.model.Lang

/** App resources resolved for [lang] instead of the device language, for text meant for either party. */
@Composable
fun rememberLangResources(lang: Lang): Resources {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(lang, configuration) {
        val config = Configuration(configuration).apply { setLocale(lang.locale) }
        context.createConfigurationContext(config).resources
    }
}

/**
 * The one text transition used on this screen: the old text rises and fades out as the new one rises in.
 * Use with `contentAlignment = Alignment.Center`; the default top-start anchor drifts sideways while
 * the size animates, which turns the rise into a diagonal.
 */
fun <S> AnimatedContentTransitionScope<S>.riseAndFade(motion: MotionScheme): ContentTransform =
    (slideInVertically(motion.defaultEffectsSpec()) { it / 2 } + fadeIn(motion.defaultEffectsSpec())) togetherWith
        (slideOutVertically(motion.defaultEffectsSpec()) { -it / 2 } + fadeOut(motion.fastEffectsSpec())) using
        SizeTransform(clip = false)
