package app.vanillify.device

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
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
    /** Every permission the app asks for, including other apps' custom ones. */
    val requested: List<String> = emptyList(),
    /** Custom permissions this app defines → true when only same-signature apps may hold them. */
    val defines: Map<String, Boolean> = emptyMap(),
    /** Apps with the same id share one identity, permissions and data. */
    val sharedUserId: String? = null,
)

/** A code library that one app provides and others load. */
data class LibraryUse(val name: String, val provider: String, val users: List<String>)

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
                requested = requested.toList(),
                defines = info.permissions.orEmpty().associate { it.name to isSignature(it) },
                sharedUserId = @Suppress("DEPRECATION") info.sharedUserId,
            )
        }.sortedBy { it.label.lowercase() }
    }

    /** Libraries shipped inside apps (not the framework's own), with the apps that load them. */
    fun libraries(): List<LibraryUse> = runCatching {
        pm.getSharedLibraries(0).mapNotNull { lib ->
            val provider = lib.declaringPackage?.packageName?.takeIf { it != "android" } ?: return@mapNotNull null
            LibraryUse(lib.name, provider, lib.dependentPackages.map { it.packageName }.filter { it != provider }.distinct())
        }.filter { it.users.isNotEmpty() }
    }.getOrDefault(emptyList())

    private fun isSignature(p: PermissionInfo): Boolean {
        val base = if (Build.VERSION.SDK_INT >= 28) p.protection else @Suppress("DEPRECATION") (p.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE)
        @Suppress("DEPRECATION")
        return base == PermissionInfo.PROTECTION_SIGNATURE || base == PermissionInfo.PROTECTION_SIGNATURE_OR_SYSTEM
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
