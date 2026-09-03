package com.xycz.simple_live

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
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
        val displayId: Int,
        val width: Int,
        val height: Int,
    )

    private data class DisplayCandidate(
        val physical: PhysicalDisplay,
        val width: Int,
        val height: Int,
        val areaGap: Long,
        val dimensionGap: Int,
        val aspectGap: Double,
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
     * Only a built-in display mode is a trusted physical-panel reference.
     * Android car launchers can wrap the right-hand pane in TYPE_VIRTUAL (or
     * overlay/Wi-Fi displays), whose mode is merely the pane size. Those modes
     * must never authorize immersive system UI.
     */
    private fun isBuiltInDisplay(display: Display): Boolean {
        // Some Android SDK stubs used by Flutter do not expose Display.type or
        // TYPE_BUILT_IN even though the runtime API does. Resolve the public
        // getter/constant without a compile-time dependency. If reflection is
        // unavailable, only the platform default display is safe to accept.
        val builtInType = runCatching {
            Display::class.java.getField("TYPE_BUILT_IN").getInt(null)
        }.getOrNull()
        val displayType = runCatching {
            Display::class.java.getMethod("getType").invoke(display) as? Int
        }.getOrNull()
        return if (builtInType != null && displayType != null) {
            displayType == builtInType
        } else {
            display.displayId == Display.DEFAULT_DISPLAY
        }
    }

    @Suppress("DEPRECATION")
    private fun physicalDisplay(display: Display): PhysicalDisplay? {
        if (!isBuiltInDisplay(display) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 &&
                !display.isValid)
        ) {
            return null
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val mode = runCatching { display.mode }.getOrNull() ?: return null
        if (mode.physicalWidth <= 0 || mode.physicalHeight <= 0) return null
        return PhysicalDisplay(
            displayId = display.displayId,
            width = mode.physicalWidth,
            height = mode.physicalHeight,
        )
    }

    private fun orientedCandidates(
        physical: PhysicalDisplay,
        currentWidth: Int,
        currentHeight: Int,
    ): List<DisplayCandidate> {
        if (currentWidth <= 0 || currentHeight <= 0) return emptyList()
        val edgeTolerance = max(12, max(physical.width, physical.height) / 100)
        val currentArea = currentWidth.toLong() * currentHeight.toLong()
        val orientations = if (physical.width != physical.height) {
            listOf(
                physical.width to physical.height,
                physical.height to physical.width,
            )
        } else {
            listOf(physical.width to physical.height)
        }
        return orientations.mapNotNull { (panelWidth, panelHeight) ->
            val widthRatio = currentWidth.toDouble() / panelWidth
            val heightRatio = currentHeight.toDouble() / panelHeight
            if (currentWidth > panelWidth + edgeTolerance ||
                currentHeight > panelHeight + edgeTolerance ||
                widthRatio < 0.40 ||
                heightRatio < 0.40
            ) {
                return@mapNotNull null
            }
            val panelArea = panelWidth.toLong() * panelHeight.toLong()
            val areaGap = (panelArea - currentArea).coerceAtLeast(0)
            val dimensionGap =
                (panelWidth - currentWidth).coerceAtLeast(0) +
                    (panelHeight - currentHeight).coerceAtLeast(0)
            val currentAspect = currentWidth.toDouble() / currentHeight
            val panelAspect = panelWidth.toDouble() / panelHeight
            DisplayCandidate(
                physical = physical,
                width = panelWidth,
                height = panelHeight,
                areaGap = areaGap,
                dimensionGap = dimensionGap,
                aspectGap = abs(currentAspect - panelAspect),
            )
        }
    }

    /**
     * Enumerate displays rather than trusting the Activity's current Display.
     * If the Activity is virtual, choose a single smallest built-in panel that
     * can contain the current window. Ties across physical displays are
     * ambiguous and intentionally return null.
     */
    @Suppress("DEPRECATION")
    private fun readPhysicalDisplay(
        targetDisplay: Display?,
        currentWidth: Int,
        currentHeight: Int,
    ): PhysicalDisplay? {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE)
            as? DisplayManager ?: return null
        val visibleDisplays = try {
            displayManager.getDisplays().toList()
        } catch (_: RuntimeException) {
            return null
        }
        val trustedDisplays = visibleDisplays.mapNotNull(::physicalDisplay)

        // A built-in current display is already an unambiguous physical
        // reference, but still prefer the enumerated instance when available.
        val targetPhysical = targetDisplay?.let(::physicalDisplay)
        if (targetPhysical != null) {
            return trustedDisplays.firstOrNull {
                it.displayId == targetPhysical.displayId
            } ?: targetPhysical
        }

        if (trustedDisplays.isEmpty()) return null
        val candidates = trustedDisplays.flatMap {
            orientedCandidates(it, currentWidth, currentHeight)
        }
        val best = candidates.minWithOrNull(
            compareBy<DisplayCandidate>(
                { it.areaGap },
                { it.dimensionGap },
                { it.aspectGap },
            ),
        ) ?: return null
        val tiedDisplayIds = candidates.filter {
            it.areaGap == best.areaGap &&
                it.dimensionGap == best.dimensionGap &&
                abs(it.aspectGap - best.aspectGap) <= 0.01
        }.map { it.physical.displayId }.toSet()
        if (tiedDisplayIds.size > 1) return null
        return best.physical.copy(width = best.width, height = best.height)
    }

    @Suppress("DEPRECATION")
    private fun readWindowState(): Map<String, Any> {
        val density = resources.displayMetrics.density.toDouble().coerceAtLeast(1.0)
        val targetDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            windowManager.defaultDisplay
        }
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
        val physical = readPhysicalDisplay(targetDisplay, currentWidth, currentHeight)
        val physicalWidth = physical?.width ?: 0
        val physicalHeight = physical?.height ?: 0

        // A full host window must fill the reliable panel in both dimensions
        // and start at the display origin. The origin check matters on car
        // launchers where a right-hand pane is exposed through a virtual
        // display; virtual display modes are never trusted as the panel.
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
        val fillsReliablePanel = physical != null &&
            widthRatio >= FULL_SIZE_THRESHOLD &&
            heightRatio >= FULL_SIZE_THRESHOLD &&
            widthRatio <= MAX_FULL_SIZE_RATIO &&
            heightRatio <= MAX_FULL_SIZE_RATIO &&
            startsAtDisplayOrigin
        val hasReliableConstrainedBounds = physical != null &&
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
            "physicalDisplayReliable" to (physical != null),
            "insetLeft" to insetLeft / density,
            "insetTop" to insetTop / density,
            "insetRight" to insetRight / density,
            "insetBottom" to insetBottom / density,
        )
    }
}
