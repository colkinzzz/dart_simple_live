package com.xycz.simple_live

import android.content.pm.PackageManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowInsets
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The vehicle launcher owns the Activity window. This channel only reports
 * its current bounds; it never requests a different window or orientation.
 */
class MainActivity : FlutterActivity() {
    companion object {
        private const val CAR_WINDOW_CHANNEL = "com.xycz.simple_live/car_window"
        private const val FULL_SIZE_THRESHOLD = 0.90
        private const val SPLIT_SIZE_THRESHOLD = 0.86
        private const val MAX_FULL_SIZE_RATIO = 1.10
    }

    private data class PhysicalDisplay(
        val width: Int,
        val height: Int,
        val reliable: Boolean,
    )

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CAR_WINDOW_CHANNEL,
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "getWindowState" -> result.success(readWindowState())
                else -> result.notImplemented()
            }
        }
    }

    /**
     * Display.Mode is the only modern API here that describes the panel rather
     * than the current task. getRealMetrics is retained only as an
     * informational/legacy fallback and is deliberately not trusted for the
     * full-screen decision on Android M and newer.
     */
    @Suppress("DEPRECATION")
    private fun readPhysicalDisplay(targetDisplay: Display?): PhysicalDisplay? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val mode = targetDisplay?.mode
            if (mode != null && mode.physicalWidth > 0 && mode.physicalHeight > 0) {
                return PhysicalDisplay(
                    width = mode.physicalWidth,
                    height = mode.physicalHeight,
                    reliable = true,
                )
            }
        }

        val metrics = DisplayMetrics()
        targetDisplay?.getRealMetrics(metrics)
        return if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
            PhysicalDisplay(
                width = metrics.widthPixels,
                height = metrics.heightPixels,
                reliable = false,
            )
        } else {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun readWindowState(): Map<String, Any> {
        val density = resources.displayMetrics.density.toDouble().coerceAtLeast(1.0)
        val targetDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            windowManager.defaultDisplay
        }
        val physical = readPhysicalDisplay(targetDisplay)
        val physicalWidth = physical?.width ?: 0
        val physicalHeight = physical?.height ?: 0
        val isMultiWindow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            isInMultiWindowMode
        val isAutomotive = packageManager.hasSystemFeature(
            PackageManager.FEATURE_AUTOMOTIVE,
        )

        val currentWidth: Int
        val currentHeight: Int
        var currentBoundsLeft = 0
        var currentBoundsTop = 0
        var currentBoundsRight = 0
        var currentBoundsBottom = 0
        var insetLeft = 0
        var insetTop = 0
        var insetRight = 0
        var insetBottom = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val currentMetrics = windowManager.currentWindowMetrics
            val bounds = currentMetrics.bounds
            currentWidth = bounds.width()
            currentHeight = bounds.height()
            currentBoundsLeft = bounds.left
            currentBoundsTop = bounds.top
            currentBoundsRight = bounds.right
            currentBoundsBottom = bounds.bottom
            val insetTypes = WindowInsets.Type.statusBars() or
                WindowInsets.Type.navigationBars() or
                WindowInsets.Type.displayCutout() or
                WindowInsets.Type.tappableElement() or
                WindowInsets.Type.mandatorySystemGestures()
            val insets = currentMetrics.windowInsets.getInsetsIgnoringVisibility(insetTypes)
            insetLeft = insets.left
            insetTop = insets.top
            insetRight = insets.right
            insetBottom = insets.bottom
        } else {
            currentWidth = window.decorView.width.takeIf { it > 0 }
                ?: resources.displayMetrics.widthPixels
            currentHeight = window.decorView.height.takeIf { it > 0 }
                ?: resources.displayMetrics.heightPixels
            currentBoundsRight = currentWidth
            currentBoundsBottom = currentHeight
        }

        // A full host window must fill the reliable panel in both dimensions
        // and start at the display origin. The origin check matters on car
        // launchers where a right-hand pane can look like a full-width virtual
        // display to getRealMetrics().
        val directWidthRatio = if (physicalWidth > 0) {
            currentWidth.toDouble() / physicalWidth
        } else {
            0.0
        }
        val directHeightRatio = if (physicalHeight > 0) {
            currentHeight.toDouble() / physicalHeight
        } else {
            0.0
        }
        val swappedWidthRatio = if (physicalHeight > 0) {
            currentWidth.toDouble() / physicalHeight
        } else {
            0.0
        }
        val swappedHeightRatio = if (physicalWidth > 0) {
            currentHeight.toDouble() / physicalWidth
        } else {
            0.0
        }
        val useSwappedPanel = min(swappedWidthRatio, swappedHeightRatio) >
            min(directWidthRatio, directHeightRatio)
        val panelWidth = if (useSwappedPanel) physicalHeight else physicalWidth
        val panelHeight = if (useSwappedPanel) physicalWidth else physicalHeight
        val widthRatio = if (panelWidth > 0) {
            currentWidth.toDouble() / panelWidth
        } else {
            0.0
        }
        val heightRatio = if (panelHeight > 0) {
            currentHeight.toDouble() / panelHeight
        } else {
            0.0
        }
        val edgeTolerance = max(12, max(panelWidth, panelHeight) / 100)
        val startsAtDisplayOrigin =
            abs(currentBoundsLeft) <= edgeTolerance &&
                abs(currentBoundsTop) <= edgeTolerance
        val fillsReliablePanel = physical?.reliable == true &&
            widthRatio >= FULL_SIZE_THRESHOLD &&
            heightRatio >= FULL_SIZE_THRESHOLD &&
            widthRatio <= MAX_FULL_SIZE_RATIO &&
            heightRatio <= MAX_FULL_SIZE_RATIO &&
            startsAtDisplayOrigin
        val hasReliableConstrainedBounds = physical?.reliable == true &&
            (widthRatio < SPLIT_SIZE_THRESHOLD ||
                heightRatio < SPLIT_SIZE_THRESHOLD ||
                !startsAtDisplayOrigin)
        val hostWindowState = when {
            isMultiWindow -> "split"
            fillsReliablePanel -> "full"
            hasReliableConstrainedBounds -> "split"
            else -> "unknown"
        }

        return mapOf(
            "isAutomotive" to isAutomotive,
            "isInMultiWindowMode" to isMultiWindow,
            "hostWindowState" to hostWindowState,
            "width" to currentWidth / density,
            "height" to currentHeight / density,
            "maximumWidth" to if (panelWidth > 0) panelWidth / density else 0,
            "maximumHeight" to if (panelHeight > 0) panelHeight / density else 0,
            "widthRatio" to widthRatio,
            "heightRatio" to heightRatio,
            "currentWidthPx" to currentWidth,
            "currentHeightPx" to currentHeight,
            "currentBoundsLeftPx" to currentBoundsLeft,
            "currentBoundsTopPx" to currentBoundsTop,
            "currentBoundsRightPx" to currentBoundsRight,
            "currentBoundsBottomPx" to currentBoundsBottom,
            "physicalWidthPx" to physicalWidth,
            "physicalHeightPx" to physicalHeight,
            "physicalDisplayReliable" to (physical?.reliable == true),
            "insetLeft" to insetLeft / density,
            "insetTop" to insetTop / density,
            "insetRight" to insetRight / density,
            "insetBottom" to insetBottom / density,
        )
    }
}
