package app.vanillify.device

/**
 * The shell commands Vanillify runs and how their output is read. Pure functions,
 * so the parsing is unit-tested against real output captured from phones.
 * Everything targets user 0, the phone's owner.
 */
object Commands {

    /** Single-quotes [s] for `sh -c`; package names and setting values are passed through this. */
    fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    // --- Packages -------------------------------------------------------------------------

    /** Removes the app for the user but keeps it on the system partition, so it can come back. */
    fun uninstall(pkg: String) = "pm uninstall -k --user 0 ${quote(pkg)}"
    fun disable(pkg: String) = "pm disable-user --user 0 ${quote(pkg)}"
    fun enable(pkg: String) = "pm enable --user 0 ${quote(pkg)}"
    fun installExisting(pkg: String) = "cmd package install-existing --user 0 ${quote(pkg)}"

    fun uninstalled(out: String?) = out?.trimStart()?.startsWith("Success") == true
    fun disabled(out: String?) = out?.contains("new state: disabled-user") == true
    fun enabled(out: String?) = out?.contains("new state: enabled") == true

    /** "Package x installed for user: 0"; an app that is already installed also counts. */
    fun reinstalled(out: String?) = out != null && (out.contains("installed for user") || out.contains("already installed"))

    // --- Settings -------------------------------------------------------------------------

    fun settingsGet(ns: String, key: String) = "settings get $ns ${quote(key)}"

    /** An empty value has to reach `settings` as an explicit empty argument. */
    fun settingsPut(ns: String, key: String, value: String) = "settings put $ns ${quote(key)} ${quote(value)}"
    fun settingsDelete(ns: String, key: String) = "settings delete $ns ${quote(key)}"

    /** `settings get` prints "null" for a setting that doesn't exist. */
    fun settingValue(out: String?): String? = out?.trimEnd('\n', '\r')?.takeUnless { it == "null" }

    // --- App-ops (microphone) -------------------------------------------------------------

    fun appopsGet(pkg: String, op: String) = "appops get ${quote(pkg)} $op"

    /**
     * Permission-backed ops like RECORD_AUDIO live per app ID ("uid mode"), which wins
     * over the package mode, so blocks are set with --uid.
     */
    fun appopsSetUid(pkg: String, op: String, mode: String) = "appops set --uid ${quote(pkg)} $op $mode"

    /** The uid mode from `appops get`: "Uid mode: RECORD_AUDIO: foreground". "default" when none is set. */
    fun uidMode(out: String?, op: String): String? {
        if (out == null || out.contains("No UID")) return null
        val line = out.lineSequence().firstOrNull { it.trimStart().startsWith("Uid mode: $op:") }
            ?: return "default"
        return line.substringAfter("$op:").trim().substringBefore(';').trim().ifEmpty { "default" }
    }

    // --- Per-app firewall (Android's FIREWALL_CHAIN_OEM_DENY_3) ---------------------------

    const val CHAIN_GET = "cmd connectivity get-chain3-enabled"
    fun chainSet(on: Boolean) = "cmd connectivity set-chain3-enabled $on"
    fun netGet(pkg: String) = "cmd connectivity get-package-networking-enabled ${quote(pkg)}"
    fun netSet(pkg: String, allowed: Boolean) = "cmd connectivity set-package-networking-enabled $allowed ${quote(pkg)}"

    /** "chain:enabled" / "chain:disabled"; null when this Android has no such command. */
    fun chainState(out: String?): Boolean? = when (out?.trim()) {
        "chain:enabled" -> true
        "chain:disabled" -> false
        else -> null
    }

    /** "com.example:allow" / "com.example:deny"; null when the answer isn't one of those. */
    fun netAllowed(out: String?): Boolean? = when (out?.trim()?.substringAfterLast(':')) {
        "allow" -> true
        "deny" -> false
        else -> null
    }

    // --- Errors ---------------------------------------------------------------------------

    /** The line of a pm/cmd error worth showing, without the Java stack trace. */
    fun brief(out: String?): String {
        if (out.isNullOrBlank()) return "no answer from the shell"
        val lines = out.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return lines.firstOrNull { it.startsWith("Failure") || it.startsWith("Error") || it.startsWith("java.") }
            ?.removePrefix("java.lang.")
            ?: lines.first { !it.startsWith("at ") && !it.startsWith("Exception occurred") }
    }
}
