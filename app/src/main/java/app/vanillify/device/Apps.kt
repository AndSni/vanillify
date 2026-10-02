package app.vanillify.device

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

/** What an app looks like to the phone's owner (user 0). */
enum class AppState { ENABLED, DISABLED, REMOVED }

data class InstalledApp(
    val pkg: String,
    val label: String,
    val uid: Int,
    /** Came with the phone (system or updated system app). */
    val system: Boolean,
    val state: AppState,
    val usesInternet: Boolean,
    val usesMicrophone: Boolean,
)

/**
 * Reads the app inventory straight from Android's package manager: no Shizuku needed,
 * so the lists work (read-only) before Shizuku is set up. Apps removed for the user
 * still show up, because their code stays on the system partition.
 */
class Apps(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    fun all(): List<InstalledApp> {
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS or
            PackageManager.GET_PERMISSIONS
        val packages = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION") pm.getInstalledPackages(flags)
        }
        return packages.mapNotNull { info ->
            val app = info.applicationInfo ?: return@mapNotNull null
            val requested = info.requestedPermissions.orEmpty()
            InstalledApp(
                pkg = info.packageName,
                label = runCatching { app.loadLabel(pm).toString() }.getOrDefault(info.packageName),
                uid = app.uid,
                system = app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                state = stateOf(app),
                usesInternet = Manifest.permission.INTERNET in requested,
                usesMicrophone = Manifest.permission.RECORD_AUDIO in requested,
            )
        }.sortedBy { it.label.lowercase() }
    }

    /** The current state of one app, or null when the phone doesn't have it at all. */
    fun state(pkg: String): AppState? {
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
        val app = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION") pm.getApplicationInfo(pkg, flags)
            }
        }.getOrNull() ?: return null
        return stateOf(app)
    }

    private fun stateOf(app: ApplicationInfo): AppState {
        if (app.flags and ApplicationInfo.FLAG_INSTALLED == 0) return AppState.REMOVED
        val setting = runCatching { pm.getApplicationEnabledSetting(app.packageName) }
            .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        return when (setting) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> AppState.DISABLED
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> if (app.enabled) AppState.ENABLED else AppState.DISABLED
            else -> AppState.ENABLED
        }
    }
}
