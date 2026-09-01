package com.xycz.simple_live

import android.content.pm.PackageManager
import android.os.Build
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

    private fun readWindowState(): Map<String, Any> {
        val density = resources.displayMetrics.density.toDouble().coerceAtLeast(1.0)
        val isMultiWindow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            isInMultiWindowMode
        val isAutomotive = packageManager.hasSystemFeature(
            PackageManager.FEATURE_AUTOMOTIVE,
        )

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val width = resources.displayMetrics.widthPixels / density
            val height = resources.displayMetrics.heightPixels / density
            return mapOf(
                "isAutomotive" to isAutomotive,
                "isInMultiWindowMode" to isMultiWindow,
                // Android 10 and below cannot reliably compare current and
                // maximum window bounds. Prefer keeping OEM bars visible over
                // accidentally promoting a vendor split window.
                "isHostFullScreen" to false,
                "width" to width,
                "height" to height,
                "maximumWidth" to width,
                "maximumHeight" to height,
                "insetLeft" to 0.0,
                "insetTop" to 0.0,
                "insetRight" to 0.0,
                "insetBottom" to 0.0,
            )
        }

        val currentMetrics = windowManager.currentWindowMetrics
        val maximumMetrics = windowManager.maximumWindowMetrics
        val currentBounds = currentMetrics.bounds
        val maximumBounds = maximumMetrics.bounds
        val widthRatio = if (maximumBounds.width() > 0) {
            currentBounds.width().toDouble() / maximumBounds.width()
        } else {
            0.0
        }

        // Some car launchers implement split screen without reporting Android's
        // multi-window flag. Comparing the current and maximum bounds keeps an
        // in-app fullscreen action from promoting a 2/3 window to the full display.
        val isHostFullScreen = !isMultiWindow && widthRatio >= 0.90
        val insetTypes = WindowInsets.Type.statusBars() or
            WindowInsets.Type.navigationBars() or
            WindowInsets.Type.displayCutout() or
            WindowInsets.Type.tappableElement() or
            WindowInsets.Type.mandatorySystemGestures()
        val insets = currentMetrics.windowInsets.getInsetsIgnoringVisibility(insetTypes)

        return mapOf(
            "isAutomotive" to isAutomotive,
            "isInMultiWindowMode" to isMultiWindow,
            "isHostFullScreen" to isHostFullScreen,
            "width" to currentBounds.width() / density,
            "height" to currentBounds.height() / density,
            "maximumWidth" to maximumBounds.width() / density,
            "maximumHeight" to maximumBounds.height() / density,
            "insetLeft" to insets.left / density,
            "insetTop" to insets.top / density,
            "insetRight" to insets.right / density,
            "insetBottom" to insets.bottom / density,
        )
    }
}
