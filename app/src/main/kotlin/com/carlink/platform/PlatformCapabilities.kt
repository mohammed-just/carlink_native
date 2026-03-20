package com.carlink.platform

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.carlink.BuildConfig

data class PlatformCapabilities(
    val platformInfo: PlatformDetector.PlatformInfo,
    val buildPlatform: String,
    val modernClusterStackEnabled: Boolean,
    val experimentalLegacyClusterEnabled: Boolean,
    val templatesHostFeatureAvailable: Boolean,
    val legacyCarFrameworkAvailable: Boolean,
    val privilegedClusterPermissionsGranted: Boolean,
    val clusterEnvironment: ClusterEnvironment,
) {
    enum class ClusterEnvironment {
        MODERN_TEMPLATES_HOST,
        LEGACY_PRIVILEGED,
        LEGACY_CAR_ONLY,
        NONE,
    }

    val isModernAaosBuild: Boolean get() = buildPlatform == "modernAaos"
    val isT7Api28Build: Boolean get() = buildPlatform == "t7Api28"
    val canUseModernClusterStack: Boolean get() = clusterEnvironment == ClusterEnvironment.MODERN_TEMPLATES_HOST
    val canToggleClusterSetting: Boolean get() = canUseModernClusterStack
    val canResetCluster: Boolean get() = canUseModernClusterStack
    val showsExperimentalClusterPath: Boolean =
        clusterEnvironment == ClusterEnvironment.LEGACY_PRIVILEGED ||
            (isT7Api28Build && clusterEnvironment == ClusterEnvironment.LEGACY_CAR_ONLY)

    val clusterStatusMessage: String
        get() =
            when (clusterEnvironment) {
                ClusterEnvironment.MODERN_TEMPLATES_HOST -> {
                    "Modern Templates Host cluster path is available on this build."
                }

                ClusterEnvironment.LEGACY_PRIVILEGED -> {
                    "Legacy android.car cluster permissions are granted, but the privileged T7 bridge is still experimental and disabled in the normal APK."
                }

                ClusterEnvironment.LEGACY_CAR_ONLY -> {
                    "Legacy android.car was detected, but cluster permissions are not granted. Cluster stays disabled so projection remains stable."
                }

                ClusterEnvironment.NONE -> {
                    "No compatible cluster host was detected. Cluster stays disabled on this build."
                }
            }

    companion object {
        private const val TAG = "CARLINK_CAPS"
        private const val TEMPLATES_HOST_FEATURE = "android.software.car.templates_host"
        private const val TEMPLATES_HOST_PROVIDER =
            "com.google.android.apps.automotive.templates.host.ClusterIconContentProvider"

        private val LEGACY_CLUSTER_PERMISSIONS =
            arrayOf(
                "android.car.permission.CAR_NAVIGATION_MANAGER",
                "android.car.permission.CAR_DISPLAY_IN_CLUSTER",
            )

        fun detect(context: Context): PlatformCapabilities {
            val platformInfo = PlatformDetector.detect(context)
            val packageManager = context.packageManager

            val templatesHostFeatureAvailable =
                BuildConfig.MODERN_CLUSTER_STACK_ENABLED &&
                    (
                        packageManager.hasSystemFeature(TEMPLATES_HOST_FEATURE) ||
                            resolveTemplatesHostProvider(packageManager)
                    )

            val legacyCarFrameworkAvailable = hasLegacyCarFramework()
            val privilegedClusterPermissionsGranted =
                BuildConfig.EXPERIMENTAL_LEGACY_CLUSTER_ENABLED &&
                    LEGACY_CLUSTER_PERMISSIONS.all { permission ->
                        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                    }

            val clusterEnvironment =
                when {
                    BuildConfig.MODERN_CLUSTER_STACK_ENABLED && templatesHostFeatureAvailable -> {
                        ClusterEnvironment.MODERN_TEMPLATES_HOST
                    }

                    legacyCarFrameworkAvailable && privilegedClusterPermissionsGranted -> {
                        ClusterEnvironment.LEGACY_PRIVILEGED
                    }

                    legacyCarFrameworkAvailable -> {
                        ClusterEnvironment.LEGACY_CAR_ONLY
                    }

                    else -> {
                        ClusterEnvironment.NONE
                    }
                }

            return PlatformCapabilities(
                platformInfo = platformInfo,
                buildPlatform = BuildConfig.PLATFORM_FLAVOR,
                modernClusterStackEnabled = BuildConfig.MODERN_CLUSTER_STACK_ENABLED,
                experimentalLegacyClusterEnabled = BuildConfig.EXPERIMENTAL_LEGACY_CLUSTER_ENABLED,
                templatesHostFeatureAvailable = templatesHostFeatureAvailable,
                legacyCarFrameworkAvailable = legacyCarFrameworkAvailable,
                privilegedClusterPermissionsGranted = privilegedClusterPermissionsGranted,
                clusterEnvironment = clusterEnvironment,
            ).also { capabilities ->
                if (BuildConfig.DEBUG) {
                    Log.i(
                        TAG,
                        "[CAPS] flavor=${capabilities.buildPlatform}, env=${capabilities.clusterEnvironment}, " +
                            "templatesHost=${capabilities.templatesHostFeatureAvailable}, " +
                            "legacyCar=${capabilities.legacyCarFrameworkAvailable}, " +
                            "legacyPriv=${capabilities.privilegedClusterPermissionsGranted}, " +
                            "platform=${capabilities.platformInfo}",
                    )
                }
            }
        }

        private fun resolveTemplatesHostProvider(packageManager: PackageManager): Boolean =
            try {
                @Suppress("DEPRECATION")
                packageManager.resolveContentProvider(TEMPLATES_HOST_PROVIDER, 0) != null
            } catch (_: Exception) {
                false
            }

        private fun hasLegacyCarFramework(): Boolean =
            try {
                Class.forName("android.car.Car")
                true
            } catch (_: Throwable) {
                false
            }
    }
}
