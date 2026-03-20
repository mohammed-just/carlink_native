package com.carlink.cluster

import android.app.ActivityManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.carlink.MainActivity
import com.carlink.logging.Logger
import com.carlink.logging.logInfo
import com.carlink.logging.logWarn
import com.carlink.navigation.NavigationStateManager
import com.carlink.platform.PlatformCapabilities
import com.carlink.ui.settings.AdapterConfigPreference

/**
 * Isolates cluster startup behavior so the Android 9 T7 build can keep projection stable
 * even when modern Templates Host integration is unavailable.
 */
interface ClusterBridge {
    fun applyComponentState(activity: MainActivity)

    fun scheduleInitialLaunch(activity: MainActivity)

    fun onUsbAttached(activity: MainActivity)

    fun restart(activity: MainActivity)

    companion object {
        fun create(capabilities: PlatformCapabilities): ClusterBridge =
            when (capabilities.clusterEnvironment) {
                PlatformCapabilities.ClusterEnvironment.MODERN_TEMPLATES_HOST -> ModernTemplatesClusterBridge()
                PlatformCapabilities.ClusterEnvironment.LEGACY_PRIVILEGED -> LegacyPrivilegedClusterBridge(capabilities)
                PlatformCapabilities.ClusterEnvironment.LEGACY_CAR_ONLY,
                PlatformCapabilities.ClusterEnvironment.NONE,
                -> DisabledClusterBridge(capabilities)
            }
    }
}

private const val CAR_APP_ACTIVITY_CLASS = "androidx.car.app.activity.CarAppActivity"

private class ModernTemplatesClusterBridge : ClusterBridge {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun applyComponentState(activity: MainActivity) {
        AdapterConfigPreference.getInstance(activity).applyClusterComponentState(
            context = activity,
            componentAvailable = true,
        )
    }

    override fun scheduleInitialLaunch(activity: MainActivity) {
        if (!AdapterConfigPreference.getInstance(activity).getClusterNavigationSync()) return

        mainHandler.postDelayed({
            if (!activity.isDestroyed && !activity.isFinishing) {
                launchCarAppActivity(activity)
            }
        }, 4000)
    }

    override fun onUsbAttached(activity: MainActivity) {
        if (!AdapterConfigPreference.getInstance(activity).getClusterNavigationSync()) return

        logInfo(
            "[LIFECYCLE] onNewIntent: USB_DEVICE_ATTACHED - re-launching cluster binding",
            tag = "MAIN",
        )
        launchCarAppActivity(activity)
    }

    override fun restart(activity: MainActivity) {
        logWarn(
            "[CLUSTER] restartClusterBinding() - tearing down and re-establishing binding chain",
            tag = "MAIN",
        )
        NavigationStateManager.clear()

        val activityManager = activity.getSystemService(ActivityManager::class.java)
        for (appTask in activityManager.appTasks) {
            if (appTask.taskInfo.baseActivity?.className == CAR_APP_ACTIVITY_CLASS) {
                logInfo(
                    "[CLUSTER] Finishing CarAppActivity task (taskId=${appTask.taskInfo.taskId})",
                    tag = "MAIN",
                )
                appTask.finishAndRemoveTask()
                break
            }
        }

        mainHandler.postDelayed({
            if (!activity.isDestroyed && !activity.isFinishing) {
                logInfo("[CLUSTER] Re-launching CarAppActivity after teardown", tag = "MAIN")
                launchCarAppActivity(activity)
            }
        }, 2000)
    }

    private fun launchCarAppActivity(activity: MainActivity) {
        if (ClusterBindingState.sessionAlive) {
            logInfo("[CLUSTER] Cluster session still alive - will retry after teardown", tag = "MAIN")
            mainHandler.postDelayed({
                if (!activity.isDestroyed && !activity.isFinishing && !ClusterBindingState.sessionAlive) {
                    logInfo("[CLUSTER] Old session torn down - retrying launch", tag = "MAIN")
                    launchCarAppActivity(activity)
                }
            }, 4000)
            return
        }

        try {
            val intent =
                Intent().apply {
                    setClassName(activity, CAR_APP_ACTIVITY_CLASS)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
                }
            activity.startActivity(intent)
            logInfo("[CLUSTER] Launched CarAppActivity for Templates Host binding", tag = "MAIN")

            mainHandler.postDelayed({
                if (!activity.isDestroyed && !activity.isFinishing) {
                    val bringBack =
                        Intent(activity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION
                        }
                    activity.startActivity(bringBack)
                    logInfo("[CLUSTER] Brought MainActivity back to foreground", tag = "MAIN")
                }
            }, 1000)
        } catch (e: Exception) {
            logWarn("[CLUSTER] Failed to launch CarAppActivity: ${e.message}", tag = "MAIN")
        }
    }
}

private open class DisabledClusterBridge(
    private val capabilities: PlatformCapabilities,
) : ClusterBridge {
    override fun applyComponentState(activity: MainActivity) {
        AdapterConfigPreference.getInstance(activity).applyClusterComponentState(
            context = activity,
            componentAvailable = false,
        )
        logInfo("[CLUSTER] ${capabilities.clusterStatusMessage}", tag = Logger.Tags.CLUSTER)
    }

    override fun scheduleInitialLaunch(activity: MainActivity) {
        if (AdapterConfigPreference.getInstance(activity).getClusterNavigationSync()) {
            logWarn("[CLUSTER] Ignoring cluster start request: ${capabilities.clusterStatusMessage}", tag = Logger.Tags.CLUSTER)
        }
    }

    override fun onUsbAttached(activity: MainActivity) {
        if (AdapterConfigPreference.getInstance(activity).getClusterNavigationSync()) {
            logWarn("[CLUSTER] Cluster attach ignored: ${capabilities.clusterStatusMessage}", tag = Logger.Tags.CLUSTER)
        }
    }

    override fun restart(activity: MainActivity) {
        logWarn("[CLUSTER] Reset ignored: ${capabilities.clusterStatusMessage}", tag = Logger.Tags.CLUSTER)
    }
}

private class LegacyPrivilegedClusterBridge(
    capabilities: PlatformCapabilities,
) : DisabledClusterBridge(capabilities)
