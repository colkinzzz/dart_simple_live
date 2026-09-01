package com.xycz.simple_live

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.ViewConfiguration
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
                "getDiagnostics" -> result.success(readWindowState())
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

    private fun addInsets(
        values: MutableMap<String, Any>,
        prefix: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        density: Double,
    ) {
        values["${prefix}Left"] = left / density
        values["${prefix}Top"] = top / density
        values["${prefix}Right"] = right / density
        values["${prefix}Bottom"] = bottom / density
        values["${prefix}LeftPx"] = left
        values["${prefix}TopPx"] = top
        values["${prefix}RightPx"] = right
        values["${prefix}BottomPx"] = bottom
    }

    private fun orientationName(orientation: Int): String = when (orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> "landscape"
        Configuration.ORIENTATION_PORTRAIT -> "portrait"
        Configuration.ORIENTATION_SQUARE -> "square"
        else -> "undefined"
    }

    private fun rotationName(rotation: Int): String = when (rotation) {
        Display.ROTATION_90 -> "90"
        Display.ROTATION_180 -> "180"
        Display.ROTATION_270 -> "270"
        else -> "0"
    }

    private fun uiModeTypeName(uiModeType: Int): String = when (uiModeType) {
        Configuration.UI_MODE_TYPE_CAR -> "car"
        Configuration.UI_MODE_TYPE_DESK -> "desk"
        Configuration.UI_MODE_TYPE_TELEVISION -> "television"
        Configuration.UI_MODE_TYPE_WATCH -> "watch"
        Configuration.UI_MODE_TYPE_VR_HEADSET -> "vr_headset"
        Configuration.UI_MODE_TYPE_APPLIANCE -> "appliance"
        Configuration.UI_MODE_TYPE_NORMAL -> "normal"
        else -> "undefined"
    }

    private fun colorHex(color: Int): String =
        String.format("#%08X", color)

    @Suppress("DEPRECATION")
    private fun readWindowState(): Map<String, Any> {
        val resourceMetrics = resources.displayMetrics
        val density = resourceMetrics.density.toDouble().coerceAtLeast(1.0)
        val configuration = resources.configuration
        val isMultiWindow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            isInMultiWindowMode
        val isAutomotive = packageManager.hasSystemFeature(
            PackageManager.FEATURE_AUTOMOTIVE,
        )
        val physicalMetrics = readPhysicalDisplayMetrics()
        val physicalWidth = physicalMetrics.widthPixels.coerceAtLeast(1)
        val physicalHeight = physicalMetrics.heightPixels.coerceAtLeast(1)
        val targetDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
        val displayMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            targetDisplay?.mode
        } else {
            null
        }

        val currentWidth: Int
        val currentHeight: Int
        var currentBoundsLeft = 0
        var currentBoundsTop = 0
        var currentBoundsRight = 0
        var currentBoundsBottom = 0
        var maximumWindowWidth = physicalWidth
        var maximumWindowHeight = physicalHeight
        var insetLeft = 0
        var insetTop = 0
        var insetRight = 0
        var insetBottom = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val currentMetrics = windowManager.currentWindowMetrics
            val currentBounds = currentMetrics.bounds
            currentWidth = currentBounds.width()
            currentHeight = currentBounds.height()
            currentBoundsLeft = currentBounds.left
            currentBoundsTop = currentBounds.top
            currentBoundsRight = currentBounds.right
            currentBoundsBottom = currentBounds.bottom
            val maximumBounds = windowManager.maximumWindowMetrics.bounds
            maximumWindowWidth = maximumBounds.width()
            maximumWindowHeight = maximumBounds.height()
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

        // This OEM's 1/3 + 2/3 layout does not set isInMultiWindowMode, and
        // maximumWindowMetrics may describe only the current task. Comparing
        // against the physical panel reliably separates ~0.67 split from 1.0.
        val widthRatio = currentWidth.toDouble() / physicalWidth
        val isHostFullScreen = widthRatio >= 0.90

        val uiModeType = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        val nightMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val viewConfiguration = ViewConfiguration.get(this)
        val decorLocationOnScreen = IntArray(2)
        val decorLocationInWindow = IntArray(2)
        window.decorView.getLocationOnScreen(decorLocationOnScreen)
        window.decorView.getLocationInWindow(decorLocationInWindow)
        val visibleDisplayFrame = Rect()
        window.decorView.getWindowVisibleDisplayFrame(visibleDisplayFrame)

        val values = mutableMapOf<String, Any>(
            "diagnosticSchema" to 1,
            "sdkInt" to Build.VERSION.SDK_INT,
            "targetSdk" to applicationInfo.targetSdkVersion,
            "isAutomotive" to isAutomotive,
            "hasTouchscreen" to packageManager.hasSystemFeature(
                PackageManager.FEATURE_TOUCHSCREEN,
            ),
            "hasLeanback" to packageManager.hasSystemFeature(
                PackageManager.FEATURE_LEANBACK,
            ),
            "isInMultiWindowMode" to isMultiWindow,
            "isInPictureInPictureMode" to (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    isInPictureInPictureMode
                ),
            "isHostFullScreen" to isHostFullScreen,
            "width" to currentWidth / density,
            "height" to currentHeight / density,
            "maximumWidth" to physicalWidth / density,
            "maximumHeight" to physicalHeight / density,
            "widthRatio" to widthRatio,
            "currentWidthPx" to currentWidth,
            "currentHeightPx" to currentHeight,
            "currentBoundsLeftPx" to currentBoundsLeft,
            "currentBoundsTopPx" to currentBoundsTop,
            "currentBoundsRightPx" to currentBoundsRight,
            "currentBoundsBottomPx" to currentBoundsBottom,
            "physicalWidthPx" to physicalWidth,
            "physicalHeightPx" to physicalHeight,
            "maximumWindowWidthPx" to maximumWindowWidth,
            "maximumWindowHeightPx" to maximumWindowHeight,
            "decorWidthPx" to window.decorView.width,
            "decorHeightPx" to window.decorView.height,
            "decorLocationOnScreenXPx" to decorLocationOnScreen[0],
            "decorLocationOnScreenYPx" to decorLocationOnScreen[1],
            "decorLocationInWindowXPx" to decorLocationInWindow[0],
            "decorLocationInWindowYPx" to decorLocationInWindow[1],
            "visibleDisplayFrameLeftPx" to visibleDisplayFrame.left,
            "visibleDisplayFrameTopPx" to visibleDisplayFrame.top,
            "visibleDisplayFrameRightPx" to visibleDisplayFrame.right,
            "visibleDisplayFrameBottomPx" to visibleDisplayFrame.bottom,
            "insetLeft" to insetLeft / density,
            "insetTop" to insetTop / density,
            "insetRight" to insetRight / density,
            "insetBottom" to insetBottom / density,
            "density" to resourceMetrics.density.toDouble(),
            "densityDpi" to resourceMetrics.densityDpi,
            "scaledDensity" to resourceMetrics.scaledDensity.toDouble(),
            "fontScale" to configuration.fontScale.toDouble(),
            "xdpi" to physicalMetrics.xdpi.toDouble(),
            "ydpi" to physicalMetrics.ydpi.toDouble(),
            "screenWidthDp" to configuration.screenWidthDp,
            "screenHeightDp" to configuration.screenHeightDp,
            "smallestScreenWidthDp" to configuration.smallestScreenWidthDp,
            "orientation" to orientationName(configuration.orientation),
            "rotation" to rotationName(targetDisplay?.rotation ?: 0),
            "displayName" to (targetDisplay?.name ?: "unknown"),
            "refreshRate" to (targetDisplay?.refreshRate?.toDouble() ?: 0.0),
            "displayModeWidthPx" to (displayMode?.physicalWidth ?: 0),
            "displayModeHeightPx" to (displayMode?.physicalHeight ?: 0),
            "displayModeRefreshRate" to (
                displayMode?.refreshRate?.toDouble() ?: 0.0
                ),
            "uiModeType" to uiModeTypeName(uiModeType),
            "nightMode" to when (nightMode) {
                Configuration.UI_MODE_NIGHT_YES -> "night"
                Configuration.UI_MODE_NIGHT_NO -> "day"
                else -> "undefined"
            },
            "requestedOrientation" to requestedOrientation,
            "windowFlags" to "0x${Integer.toHexString(window.attributes.flags)}",
            "softInputMode" to "0x${Integer.toHexString(window.attributes.softInputMode)}",
            "systemUiVisibility" to "0x${Integer.toHexString(window.decorView.systemUiVisibility)}",
            "statusBarColor" to colorHex(window.statusBarColor),
            "navigationBarColor" to colorHex(window.navigationBarColor),
            "memoryClassMb" to activityManager.memoryClass,
            "largeMemoryClassMb" to activityManager.largeMemoryClass,
            "availableMemoryMb" to memoryInfo.availMem / 1024 / 1024,
            "totalMemoryMb" to memoryInfo.totalMem / 1024 / 1024,
            "isLowMemory" to memoryInfo.lowMemory,
            "isLowRamDevice" to activityManager.isLowRamDevice,
            "availableProcessors" to Runtime.getRuntime().availableProcessors(),
            "touchSlopPx" to viewConfiguration.scaledTouchSlop,
            "doubleTapSlopPx" to viewConfiguration.scaledDoubleTapSlop,
            "edgeSlopPx" to viewConfiguration.scaledEdgeSlop,
            "minimumFlingVelocityPx" to viewConfiguration.scaledMinimumFlingVelocity,
            "maximumFlingVelocityPx" to viewConfiguration.scaledMaximumFlingVelocity,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            values["layoutInDisplayCutoutMode"] =
                window.attributes.layoutInDisplayCutoutMode
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowInsets = windowManager.currentWindowMetrics.windowInsets
            val statusBarInsets =
                windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
            addInsets(
                values,
                "statusBarInset",
                statusBarInsets.left,
                statusBarInsets.top,
                statusBarInsets.right,
                statusBarInsets.bottom,
                density,
            )
            val navigationBarInsets =
                windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
            addInsets(
                values,
                "navigationBarInset",
                navigationBarInsets.left,
                navigationBarInsets.top,
                navigationBarInsets.right,
                navigationBarInsets.bottom,
                density,
            )
            val displayCutoutInsets =
                windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout())
            addInsets(
                values,
                "displayCutoutInset",
                displayCutoutInsets.left,
                displayCutoutInsets.top,
                displayCutoutInsets.right,
                displayCutoutInsets.bottom,
                density,
            )
            val tappableInsets =
                windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.tappableElement())
            addInsets(
                values,
                "tappableInset",
                tappableInsets.left,
                tappableInsets.top,
                tappableInsets.right,
                tappableInsets.bottom,
                density,
            )
            val systemGestureInsets =
                windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemGestures())
            addInsets(
                values,
                "systemGestureInset",
                systemGestureInsets.left,
                systemGestureInsets.top,
                systemGestureInsets.right,
                systemGestureInsets.bottom,
                density,
            )
            val mandatoryGestureInsets = windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.mandatorySystemGestures(),
            )
            addInsets(
                values,
                "mandatoryGestureInset",
                mandatoryGestureInsets.left,
                mandatoryGestureInsets.top,
                mandatoryGestureInsets.right,
                mandatoryGestureInsets.bottom,
                density,
            )
            val imeInsets = windowInsets.getInsets(WindowInsets.Type.ime())
            addInsets(
                values,
                "imeInset",
                imeInsets.left,
                imeInsets.top,
                imeInsets.right,
                imeInsets.bottom,
                density,
            )
            values["isStatusBarVisible"] =
                windowInsets.isVisible(WindowInsets.Type.statusBars())
            values["isNavigationBarVisible"] =
                windowInsets.isVisible(WindowInsets.Type.navigationBars())
            values["isImeVisible"] = windowInsets.isVisible(WindowInsets.Type.ime())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values["statusBarContrastEnforced"] = window.isStatusBarContrastEnforced
            values["navigationBarContrastEnforced"] =
                window.isNavigationBarContrastEnforced
        }

        return values
    }
}
