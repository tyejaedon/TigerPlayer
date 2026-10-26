@file:SuppressLint("NewApi")
package com.tigerplayer.ui.coverscreen

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlin.math.max
import kotlin.math.min

private const val TAG = "CoverScreenWindowState"

data class CoverScreenWindowState(
    val widthDp: Int,
    val heightDp: Int,
    val isCoverScreen: Boolean,
    val hasSeparatingHinge: Boolean,
    val displayId: Int = Display.DEFAULT_DISPLAY,
    val isSecondaryDisplay: Boolean = false,
    val isInMultiWindowMode: Boolean = false
)

/**
 * dp band tuned against known cover/outer display profiles across OEMs (approximate,
 * short-edge x long-edge dp at each device's reported density):
 * - Samsung Galaxy Z Flip4/5/6 cover: ~260x260 to ~306x316dp (roughly square).
 * - Motorola Razr (2022) cover: ~246x343dp, aspect ratio ~1.39.
 * - Motorola Razr+ / Razr 50 Ultra (2023/2024) cover: ~409x413dp, aspect ratio ~1.01 - this is
 *   the profile that motivated broadening the original 220..399 / <=450 / <=1.35f band (issue
 *   #125), which only covered the Z Flip family and missed this larger, near-square panel.
 *
 * This heuristic is intentionally permissive on its own; it is only ever consulted as a
 * fallback when [resolveIsCoverScreen] has already ruled out a genuine secondary display and a
 * user-resized multi-window state (split-screen/freeform/DeX - see issue #122), so a wider band
 * does not reopen the false-positive surface those checks close.
 */
fun isCoverScreenHeuristic(widthDp: Int, heightDp: Int): Boolean {
    val shortEdge = min(widthDp, heightDp)
    val longEdge = max(widthDp, heightDp)
    if (shortEdge <= 0 || longEdge <= 0) return false
    val aspectRatio = longEdge.toFloat() / shortEdge.toFloat()
    return shortEdge in 180..440 && longEdge <= 480 && aspectRatio <= 1.55f
}

/**
 * True when the window is hosted on a genuinely distinct physical [Display] (the Motorola-style
 * true-secondary-display model or Samsung FlexWindow secondary display) rather than merely being
 * resized on the default display. Any non-default display (displayId != DEFAULT_DISPLAY, e.g. 1)
 * is an authoritative secondary/cover display signal.
 */
fun isSecondaryDisplayIdentity(displayId: Int?): Boolean {
    if (displayId == null) return false
    return displayId != Display.DEFAULT_DISPLAY && displayId != Display.INVALID_DISPLAY
}

/**
 * Combines the displayId identity signal with the dp/hinge heuristic and rejects ordinary
 * user-resize states. Precedence:
 * 1. Force preview flag ([forcePreview]) allows previewing cover UI on any display.
 * 2. A true secondary display ([isSecondaryDisplay]) is authoritative — there is no ambiguity,
 *    so it wins even if the window happens to also report multi-window mode.
 * 3. Multi-window mode (split-screen, freeform/desktop-mode, Samsung DeX pop-up view) means the
 *    small size is a user resize choice on the *same* display, not a real device posture, so it
 *    is rejected outright.
 * 4. Otherwise, resize-model devices (no second display ever exists) fall back to the dp-size +
 *    hinge heuristic.
 */
internal fun resolveIsCoverScreen(
    widthDp: Int,
    heightDp: Int,
    hasSeparatingHinge: Boolean,
    isSecondaryDisplay: Boolean,
    isInMultiWindowMode: Boolean = false,
    imeBottomDp: Int = 0,
    forcePreview: Boolean = false
): Boolean {
    if (forcePreview) return true
    if (isSecondaryDisplay) return true
    if (isInMultiWindowMode) return false
    val effectiveHeight = heightDp + imeBottomDp
    return isCoverScreenHeuristic(widthDp, effectiveHeight) && !hasSeparatingHinge
}

/**
 * Computes base display/window dimensions in dp immune to soft keyboard (IME) resizing.
 * Un-insetted height is computed by adding the IME keyboard inset to the configuration height,
 * preserving exact window dimensions without inflating to the maximum unfolded display bounds.
 */
internal fun resolveBaseWindowDimensions(
    activity: Activity? = null,
    configurationWidthDp: Int,
    configurationHeightDp: Int,
    density: Float = 1f,
    imeBottomDp: Int = 0
): Pair<Int, Int> {
    val uninsettedHeight = configurationHeightDp + imeBottomDp
    return Pair(configurationWidthDp, uninsettedHeight)
}

/**
 * Resolves the display ID for the current composition across View, Context, and Activity.
 * On Android foldables/dual-screen devices, the outer cover panel is a secondary display
 * (displayId != Display.DEFAULT_DISPLAY, typically displayId = 1).
 */
internal fun resolveDisplayId(
    context: Context?,
    activity: Activity?,
    view: android.view.View?
): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val viewDisplayId = runCatching { view?.display?.displayId }.getOrNull()
        if (viewDisplayId != null && viewDisplayId != Display.INVALID_DISPLAY) {
            return viewDisplayId
        }
        val contextDisplayId = runCatching { context?.display?.displayId }.getOrNull()
        if (contextDisplayId != null && contextDisplayId != Display.INVALID_DISPLAY) {
            return contextDisplayId
        }
        val activityDisplayId = runCatching { activity?.display?.displayId }.getOrNull()
        if (activityDisplayId != null && activityDisplayId != Display.INVALID_DISPLAY) {
            return activityDisplayId
        }
    }
    @Suppress("DEPRECATION")
    return view?.display?.displayId
        ?: activity?.windowManager?.defaultDisplay?.displayId
        ?: Display.DEFAULT_DISPLAY
}

@Composable
fun rememberCoverScreenWindowState(
    forcePreview: Boolean = false
): CoverScreenWindowState {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val imeBottomDp = with(density) { imeBottom.toDp().value.toInt() }

    var displayChangeTick by remember { mutableStateOf(0) }

    DisposableEffect(activity, view) {
        val displayManager = (activity ?: context).getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val listener = if (displayManager != null) {
            object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) {
                    displayChangeTick++
                }

                override fun onDisplayRemoved(displayId: Int) {
                    displayChangeTick++
                }

                override fun onDisplayChanged(displayId: Int) {
                    displayChangeTick++
                }
            }
        } else {
            null
        }

        if (displayManager != null) {
            try {
                displayManager.registerDisplayListener(listener, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register DisplayManager.DisplayListener", e)
            }
        }

        onDispose {
            if (displayManager != null) {
                try {
                    displayManager.unregisterDisplayListener(listener)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to unregister DisplayManager.DisplayListener", e)
                }
            }
        }
    }

    val windowLayoutInfo by produceState<WindowLayoutInfo?>(initialValue = null, activity) {
        if (activity == null) {
            value = null
            return@produceState
        }
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .collect { value = it }
    }

    val hasSeparatingHinge = windowLayoutInfo
        ?.displayFeatures
        ?.any { it is FoldingFeature && it.isSeparating } == true

    val displayId = remember(activity, view, displayChangeTick) {
        resolveDisplayId(context = context, activity = activity, view = view)
    }
    val isSecondaryDisplay = isSecondaryDisplayIdentity(displayId)
    val isInMultiWindowMode = activity?.isInMultiWindowMode == true

    val (baseWidthDp, baseHeightDp) = remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        imeBottomDp,
        displayChangeTick
    ) {
        resolveBaseWindowDimensions(
            activity = activity,
            configurationWidthDp = configuration.screenWidthDp,
            configurationHeightDp = configuration.screenHeightDp,
            density = density.density,
            imeBottomDp = imeBottomDp
        )
    }

    val isCover = resolveIsCoverScreen(
        widthDp = baseWidthDp,
        heightDp = baseHeightDp,
        hasSeparatingHinge = hasSeparatingHinge,
        isSecondaryDisplay = isSecondaryDisplay,
        isInMultiWindowMode = isInMultiWindowMode,
        forcePreview = forcePreview
    )

    return remember(
        baseWidthDp,
        baseHeightDp,
        isCover,
        hasSeparatingHinge,
        displayId,
        isSecondaryDisplay,
        isInMultiWindowMode
    ) {
        CoverScreenWindowState(
            widthDp = baseWidthDp,
            heightDp = baseHeightDp,
            isCoverScreen = isCover,
            hasSeparatingHinge = hasSeparatingHinge,
            displayId = displayId,
            isSecondaryDisplay = isSecondaryDisplay,
            isInMultiWindowMode = isInMultiWindowMode
        )
    }
}

internal tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
