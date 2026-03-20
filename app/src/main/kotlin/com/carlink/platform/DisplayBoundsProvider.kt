package com.carlink.platform

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager
import com.carlink.ui.settings.DisplayMode

data class EdgeInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    companion object {
        val ZERO = EdgeInsets()
    }
}

data class ProjectionLayout(
    val nativeWidth: Int,
    val nativeHeight: Int,
    val videoWidth: Int,
    val videoHeight: Int,
    val systemBarInsets: EdgeInsets,
    val cutoutInsets: EdgeInsets,
    val safeInsets: EdgeInsets,
)

/**
 * Provides display bounds/inset information across modern AAOS and legacy Android 9 hosts.
 *
 * API 30+ uses WindowMetrics. API 28 falls back to real-vs-app display metrics plus any
 * available display-cutout info from the current decor view.
 */
object DisplayBoundsProvider {
    fun projectionLayout(
        activity: Activity,
        displayMode: DisplayMode,
    ): ProjectionLayout {
        val snapshot = snapshot(activity, activity.windowManager, activity.window.decorView)
        return snapshot.toProjectionLayout(displayMode)
    }

    fun usableEvenSize(
        activity: Activity,
        displayMode: DisplayMode,
    ): Pair<Int, Int> {
        val layout = projectionLayout(activity, displayMode)
        return Pair(layout.videoWidth and 1.inv(), layout.videoHeight and 1.inv())
    }

    fun nativeDisplaySize(context: Context): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (windowManager == null) {
            val metrics = context.resources.displayMetrics
            return Pair(metrics.widthPixels, metrics.heightPixels)
        }

        val snapshot = snapshot(context, windowManager, null)
        return Pair(snapshot.nativeWidth, snapshot.nativeHeight)
    }

    fun refreshRate(activity: Activity): Int =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display?.refreshRate?.toInt() ?: 60
            } else {
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay?.refreshRate?.toInt() ?: 60
            }
        } catch (_: Exception) {
            60
        }

    private data class DisplaySnapshot(
        val nativeWidth: Int,
        val nativeHeight: Int,
        val systemBars: EdgeInsets,
        val cutout: EdgeInsets,
    ) {
        fun toProjectionLayout(displayMode: DisplayMode): ProjectionLayout {
            val layout =
                when (displayMode) {
                    DisplayMode.SYSTEM_UI_VISIBLE -> {
                        ProjectionLayout(
                            nativeWidth = nativeWidth,
                            nativeHeight = nativeHeight,
                            videoWidth =
                                (nativeWidth - systemBars.left - systemBars.right - cutout.left - cutout.right)
                                    .coerceAtLeast(0),
                            videoHeight =
                                (nativeHeight - systemBars.top - systemBars.bottom - cutout.top - cutout.bottom)
                                    .coerceAtLeast(0),
                            systemBarInsets = systemBars,
                            cutoutInsets = cutout,
                            safeInsets = EdgeInsets.ZERO,
                        )
                    }

                    DisplayMode.STATUS_BAR_HIDDEN -> {
                        ProjectionLayout(
                            nativeWidth = nativeWidth,
                            nativeHeight = nativeHeight,
                            videoWidth = (nativeWidth - systemBars.left - systemBars.right).coerceAtLeast(0),
                            videoHeight = (nativeHeight - systemBars.bottom).coerceAtLeast(0),
                            systemBarInsets = systemBars,
                            cutoutInsets = cutout,
                            safeInsets =
                                EdgeInsets(
                                    left = cutout.left,
                                    top = cutout.top,
                                    right = cutout.right,
                                ),
                        )
                    }

                    DisplayMode.NAV_BAR_HIDDEN -> {
                        ProjectionLayout(
                            nativeWidth = nativeWidth,
                            nativeHeight = nativeHeight,
                            videoWidth = nativeWidth.coerceAtLeast(0),
                            videoHeight = (nativeHeight - systemBars.top).coerceAtLeast(0),
                            systemBarInsets = systemBars,
                            cutoutInsets = cutout,
                            safeInsets =
                                EdgeInsets(
                                    left = cutout.left,
                                    right = cutout.right,
                                    bottom = cutout.bottom,
                                ),
                        )
                    }

                    DisplayMode.FULLSCREEN_IMMERSIVE -> {
                        ProjectionLayout(
                            nativeWidth = nativeWidth,
                            nativeHeight = nativeHeight,
                            videoWidth = nativeWidth.coerceAtLeast(0),
                            videoHeight = nativeHeight.coerceAtLeast(0),
                            systemBarInsets = systemBars,
                            cutoutInsets = cutout,
                            safeInsets = cutout,
                        )
                    }
                }

            return layout.copy(
                videoWidth = layout.videoWidth.coerceAtLeast(0),
                videoHeight = layout.videoHeight.coerceAtLeast(0),
            )
        }
    }

    private fun snapshot(
        context: Context,
        windowManager: WindowManager,
        decorView: View?,
    ): DisplaySnapshot =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val windowInsets = metrics.windowInsets
            val systemInsets =
                windowInsets.getInsetsIgnoringVisibility(
                    android.view.WindowInsets.Type.systemBars(),
                )
            val cutoutInsets =
                windowInsets.getInsetsIgnoringVisibility(
                    android.view.WindowInsets.Type.displayCutout(),
                )

            DisplaySnapshot(
                nativeWidth = bounds.width(),
                nativeHeight = bounds.height(),
                systemBars =
                    EdgeInsets(
                        left = systemInsets.left,
                        top = systemInsets.top,
                        right = systemInsets.right,
                        bottom = systemInsets.bottom,
                    ),
                cutout =
                    EdgeInsets(
                        left = cutoutInsets.left,
                        top = cutoutInsets.top,
                        right = cutoutInsets.right,
                        bottom = cutoutInsets.bottom,
                    ),
            )
        } else {
            snapshotLegacy(context, windowManager, decorView)
        }

    @Suppress("DEPRECATION")
    private fun snapshotLegacy(
        context: Context,
        windowManager: WindowManager,
        decorView: View?,
    ): DisplaySnapshot {
        val display = windowManager.defaultDisplay
        if (display == null) {
            val metrics = context.resources.displayMetrics
            return DisplaySnapshot(
                nativeWidth = metrics.widthPixels,
                nativeHeight = metrics.heightPixels,
                systemBars = EdgeInsets.ZERO,
                cutout = EdgeInsets.ZERO,
            )
        }

        val realMetrics = DisplayMetrics()
        val appMetrics = DisplayMetrics()
        display.getRealMetrics(realMetrics)
        display.getMetrics(appMetrics)

        val nativeWidth = maxOf(realMetrics.widthPixels, appMetrics.widthPixels)
        val nativeHeight = maxOf(realMetrics.heightPixels, appMetrics.heightPixels)

        val horizontalInset = (nativeWidth - appMetrics.widthPixels).coerceAtLeast(0)
        val verticalInset = (nativeHeight - appMetrics.heightPixels).coerceAtLeast(0)
        val statusBarHeight = resolveDimensionPixelSize(context, "status_bar_height").coerceAtMost(verticalInset)

        val systemBars =
            when {
                horizontalInset > 0 && verticalInset == 0 -> {
                    EdgeInsets(right = horizontalInset)
                }

                verticalInset > 0 -> {
                    EdgeInsets(
                        top = statusBarHeight,
                        right = horizontalInset,
                        bottom = (verticalInset - statusBarHeight).coerceAtLeast(0),
                    )
                }

                else -> {
                    EdgeInsets.ZERO
                }
            }

        val cutout =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val displayCutout = decorView?.rootWindowInsets?.displayCutout
                if (displayCutout != null) {
                    EdgeInsets(
                        left = displayCutout.safeInsetLeft,
                        top = displayCutout.safeInsetTop,
                        right = displayCutout.safeInsetRight,
                        bottom = displayCutout.safeInsetBottom,
                    )
                } else {
                    EdgeInsets.ZERO
                }
            } else {
                EdgeInsets.ZERO
            }

        return DisplaySnapshot(
            nativeWidth = nativeWidth,
            nativeHeight = nativeHeight,
            systemBars = systemBars,
            cutout = cutout,
        )
    }

    private fun resolveDimensionPixelSize(
        context: Context,
        resourceName: String,
    ): Int {
        val resourceId = context.resources.getIdentifier(resourceName, "dimen", "android")
        return if (resourceId != 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }
}
