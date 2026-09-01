package com.xycz.simple_live

import android.content.pm.PackageManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowInsets
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    companion object {
        private const val CAR_WINDOW_CHANNEL = "com.xycz.simple_live/car_window"
    }

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

    @Suppress("DEPRECATION")
    private fun readPhysicalDisplayMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        val targetDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            windowManager.defaultDisplay
        }
        targetDisplay?.getRealMetrics(metrics)
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) {
            windowManager.defaultDisplay.getRealMetrics(metrics)
        }
        return metrics
    }

    private fun readWindowState(): Map<String, Any> {
        val density = resources.displayMetrics.density.toDouble().coerceAtLeast(1.0)
        val isMultiWindow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            isInMultiWindowMode
        val isAutomotive = packageManager.hasSystemFeature(
            PackageManager.FEATURE_AUTOMOTIVE,
        )
        val physicalMetrics = readPhysicalDisplayMetrics()
        val physicalWidth = physicalMetrics.widthPixels.coerceAtLeast(1)
        val physicalHeight = physicalMetrics.heightPixels.coerceAtLeast(1)

        val currentWidth: Int
        val currentHeight: Int
        var insetLeft = 0
        var insetTop = 0
        var insetRight = 0
        var insetBottom = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val currentMetrics = windowManager.currentWindowMetrics
            val currentBounds = currentMetrics.bounds
            currentWidth = currentBounds.width()
            currentHeight = currentBounds.height()
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
        }

        // This OEM's 1/3 + 2/3 layout does not set isInMultiWindowMode, and
        // maximumWindowMetrics may describe only the current task. Comparing
        // against the physical panel reliably separates ~0.67 split from 1.0.
        val widthRatio = currentWidth.toDouble() / physicalWidth
        val isHostFullScreen = widthRatio >= 0.90

        return mapOf(
            "isAutomotive" to isAutomotive,
            "isInMultiWindowMode" to isMultiWindow,
            "isHostFullScreen" to isHostFullScreen,
            "width" to currentWidth / density,
            "height" to currentHeight / density,
            "maximumWidth" to physicalWidth / density,
            "maximumHeight" to physicalHeight / density,
            "widthRatio" to widthRatio,
            "insetLeft" to insetLeft / density,
            "insetTop" to insetTop / density,
            "insetRight" to insetRight / density,
            "insetBottom" to insetBottom / density,
        )
    }
}
