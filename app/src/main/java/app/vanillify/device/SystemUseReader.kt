package app.vanillify.device

import app.vanillify.shell.ShizukuBridge

/** Reads [SystemUse] through Shizuku: roles, active services and overlays aren't visible to normal apps. */
class SystemUseReader(private val bridge: ShizukuBridge) {

    suspend fun read(): SystemUse? {
        if (!bridge.ready) return null
        val roles = HashMap<String, MutableSet<SystemRole>>()
        fun hold(pkgs: List<String>, role: SystemRole) = pkgs.forEach { roles.getOrPut(it) { mutableSetOf() } += role }

        for ((name, role) in ROLES) hold(Commands.packages(bridge.exec(Commands.roleHolders(name))), role)
        suspend fun setting(key: String) = Commands.settingValue(bridge.exec(Commands.settingsGet("secure", key)))
        hold(Commands.componentPackages(setting("default_input_method")), SystemRole.KEYBOARD)
        hold(Commands.componentPackages(setting("enabled_accessibility_services")), SystemRole.ACCESSIBILITY)
        hold(Commands.componentPackages(setting("enabled_notification_listeners")), SystemRole.NOTIFICATIONS)
        hold(Commands.deviceAdmins(bridge.exec(Commands.DEVICE_ADMINS)), SystemRole.DEVICE_ADMIN)

        return SystemUse(roles, Commands.overlayTargets(bridge.exec(Commands.OVERLAYS)))
    }

    private companion object {
        val ROLES = listOf(
            "android.app.role.HOME" to SystemRole.HOME,
            "android.app.role.DIALER" to SystemRole.DIALER,
            "android.app.role.SMS" to SystemRole.SMS,
            "android.app.role.BROWSER" to SystemRole.BROWSER,
            "android.app.role.ASSISTANT" to SystemRole.ASSISTANT,
            "android.app.role.WALLET" to SystemRole.WALLET,
        )
    }
}
