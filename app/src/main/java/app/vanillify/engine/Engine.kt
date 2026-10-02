package app.vanillify.engine

import app.vanillify.catalog.DnsProvider
import app.vanillify.catalog.Privacy
import app.vanillify.catalog.Tweak
import app.vanillify.data.Change
import app.vanillify.data.ChangeKind
import app.vanillify.data.Journal
import app.vanillify.data.Protections
import app.vanillify.device.AppState
import app.vanillify.device.Commands
import app.vanillify.shell.ShizukuBridge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Outcome of one action, as shown to the user. */
data class Outcome(val ok: Boolean, val message: String)

/**
 * Every change Vanillify makes goes through here: it reads the current state first,
 * makes the change through Shizuku, checks it took, and journals what was there
 * before so it can be undone. One change at a time.
 */
class Engine(
    private val bridge: ShizukuBridge,
    private val journal: Journal,
    private val protections: Protections,
    private val stateOf: (String) -> AppState?,
) {
    private val lock = Mutex()

    private suspend fun sh(command: String, timeoutMs: Long = 15_000) = bridge.exec(command, timeoutMs)

    private fun notConnected(title: String, kind: ChangeKind, target: String) =
        Outcome(false, "Shizuku isn't connected").also {
            journal.add(change(kind, target, title, null, null, false, "Shizuku isn't connected"))
        }

    private fun change(kind: ChangeKind, target: String, title: String, before: String?, after: String?, ok: Boolean, error: String? = null) =
        Change(System.nanoTime(), System.currentTimeMillis(), kind, target, title, before, after, ok, error)

    // --- Apps -----------------------------------------------------------------------------

    /** Removes [pkg] for the user; falls back to disabling when the phone refuses removal. */
    suspend fun remove(pkg: String, label: String): Outcome = lock.withLock {
        if (!bridge.ready) return notConnected("Remove $label", ChangeKind.PACKAGE, pkg)
        val before = stateOf(pkg) ?: return Outcome(false, "$label isn't on this phone")
        if (before == AppState.REMOVED) return Outcome(true, "$label was already removed")
        val out = sh(Commands.uninstall(pkg))
        if (Commands.uninstalled(out)) {
            journal.add(change(ChangeKind.PACKAGE, pkg, "Removed $label", before.name, AppState.REMOVED.name, true))
            return Outcome(true, "Removed $label")
        }
        val why = Commands.brief(out)
        if (before == AppState.DISABLED) {
            // Already off, and the manufacturer won't let it go further: nothing more to do without root.
            journal.add(change(ChangeKind.PACKAGE, pkg, "Couldn't remove $label (already disabled, locked by the manufacturer)", before.name, before.name, false, why))
            return Outcome(false, "$label is already disabled; the manufacturer blocks removing it")
        }
        val off = sh(Commands.disable(pkg))
        if (Commands.disabled(off)) {
            journal.add(change(ChangeKind.PACKAGE, pkg, "Disabled $label (removal refused)", before.name, AppState.DISABLED.name, true, why))
            return Outcome(true, "Disabled $label: the phone refused to remove it")
        }
        val error = "$why; disable: ${Commands.brief(off)}"
        journal.add(change(ChangeKind.PACKAGE, pkg, "Couldn't remove $label", before.name, before.name, false, error))
        Outcome(false, "Couldn't remove $label: $why")
    }

    suspend fun disable(pkg: String, label: String): Outcome = lock.withLock {
        if (!bridge.ready) return notConnected("Disable $label", ChangeKind.PACKAGE, pkg)
        val before = stateOf(pkg) ?: return Outcome(false, "$label isn't on this phone")
        if (before != AppState.ENABLED) return Outcome(true, "$label is already off")
        val out = sh(Commands.disable(pkg))
        val ok = Commands.disabled(out)
        journal.add(change(ChangeKind.PACKAGE, pkg, if (ok) "Disabled $label" else "Couldn't disable $label", before.name, if (ok) AppState.DISABLED.name else before.name, ok, if (ok) null else Commands.brief(out)))
        Outcome(ok, if (ok) "Disabled $label" else "Couldn't disable $label: ${Commands.brief(out)}")
    }

    /** Brings [pkg] back and switches it on. */
    suspend fun restore(pkg: String, label: String): Outcome = lock.withLock {
        if (!bridge.ready) return notConnected("Restore $label", ChangeKind.PACKAGE, pkg)
        val before = stateOf(pkg) ?: return Outcome(false, "$label isn't on this phone")
        val ok = toState(pkg, AppState.ENABLED)
        journal.add(change(ChangeKind.PACKAGE, pkg, if (ok) "Restored $label" else "Couldn't restore $label", before.name, if (ok) AppState.ENABLED.name else before.name, ok))
        Outcome(ok, if (ok) "Restored $label" else "Couldn't restore $label")
    }

    /** Moves [pkg] to [target] from whatever state it is in now. */
    private suspend fun toState(pkg: String, target: AppState): Boolean {
        val now = stateOf(pkg) ?: return false
        if (now == target) return true
        return when (target) {
            AppState.REMOVED -> Commands.uninstalled(sh(Commands.uninstall(pkg)))
            AppState.ENABLED -> {
                if (now == AppState.REMOVED && !Commands.reinstalled(sh(Commands.installExisting(pkg)))) return false
                Commands.enabled(sh(Commands.enable(pkg))) || stateOf(pkg) == AppState.ENABLED
            }
            AppState.DISABLED -> {
                if (now == AppState.REMOVED && !Commands.reinstalled(sh(Commands.installExisting(pkg)))) return false
                Commands.disabled(sh(Commands.disable(pkg)))
            }
        }
    }

    // --- Settings -------------------------------------------------------------------------

    suspend fun readSetting(namespace: String, key: String): String? =
        Commands.settingValue(sh(Commands.settingsGet(namespace, key)))

    /** Sets or clears one tweak. Clearing puts back what was there before Vanillify changed it. */
    suspend fun setTweak(tweak: Tweak, private: Boolean): Outcome = lock.withLock {
        val target = "${tweak.namespace}/${tweak.key}"
        if (!bridge.ready) return notConnected(tweak.title, ChangeKind.SETTING, target)
        val before = readSetting(tweak.namespace, tweak.key)
        val wanted = if (private) tweak.privateValue else originalSetting(target)
        val ok = writeSetting(tweak.namespace, tweak.key, wanted)
        val title = if (private) tweak.title else "Undid: ${tweak.title}"
        journal.add(change(ChangeKind.SETTING, target, title, before, wanted, ok))
        Outcome(ok, if (ok) title else "Couldn't change the setting")
    }

    /** The value a setting had before Vanillify first changed it; null means "didn't exist". */
    private fun originalSetting(target: String): String? =
        journal.changes.value.lastOrNull { it.kind == ChangeKind.SETTING && it.target == target && it.ok }?.before

    private suspend fun writeSetting(namespace: String, key: String, value: String?): Boolean {
        if (value == null) sh(Commands.settingsDelete(namespace, key)) else sh(Commands.settingsPut(namespace, key, value))
        return readSetting(namespace, key) == value
    }

    /** "mode|hostname", e.g. "hostname|base.dns.mullvad.net" or "off|". */
    suspend fun readDns(): String =
        "${readSetting("global", DNS_MODE) ?: "off"}|${readSetting("global", DNS_HOST).orEmpty()}"

    suspend fun setDns(provider: DnsProvider): Outcome = lock.withLock {
        if (!bridge.ready) return notConnected("Private DNS: ${provider.name}", ChangeKind.SETTING, "global/$DNS_MODE")
        val before = readDns()
        val ok = applyDns(if (provider.hostname == null) "off|" else "hostname|${provider.hostname}")
        journal.add(change(ChangeKind.SETTING, "global/$DNS_MODE", "Private DNS: ${provider.name}", before, readDns(), ok))
        Outcome(ok, if (ok) "Private DNS: ${provider.name}" else "Couldn't change Private DNS")
    }

    private suspend fun applyDns(value: String): Boolean {
        val mode = value.substringBefore('|')
        val host = value.substringAfter('|')
        if (host.isNotEmpty()) writeSetting("global", DNS_HOST, host)
        return writeSetting("global", DNS_MODE, mode)
    }

    // --- Microphone -----------------------------------------------------------------------

    /** The app's microphone mode, or null when it isn't installed. */
    suspend fun micMode(pkg: String): String? = Commands.uidMode(sh(Commands.appopsGet(pkg, Privacy.MIC_OP)), Privacy.MIC_OP)

    suspend fun setMicBlocked(pkg: String, label: String, blocked: Boolean): Outcome = lock.withLock {
        if (!bridge.ready) return notConnected("Microphone: $label", ChangeKind.MICROPHONE, pkg)
        val before = micMode(pkg) ?: return Outcome(false, "$label isn't on this phone")
        val wanted = if (blocked) BLOCKED else protections.state.value.mic[pkg]?.takeIf { it != BLOCKED } ?: "foreground"
        sh(Commands.appopsSetUid(pkg, Privacy.MIC_OP, wanted))
        val ok = micMode(pkg) == wanted
        if (ok) protections.update { s ->
            if (blocked) s.copy(mic = s.mic + (pkg to (s.mic[pkg] ?: before))) else s.copy(mic = s.mic - pkg)
        }
        val title = if (blocked) "Blocked the microphone for $label" else "Gave $label the microphone back"
        journal.add(change(ChangeKind.MICROPHONE, pkg, title, before, wanted, ok))
        Outcome(ok, if (ok) title else "Couldn't change microphone access for $label")
    }

    // --- Firewall -------------------------------------------------------------------------

    /** Whether this Android has the per-app firewall that Vanillify uses (Android 14 and later). */
    suspend fun firewallSupported(): Boolean = Commands.chainState(sh(Commands.CHAIN_GET)) != null

    /** True when [pkg] is blocked right now. */
    suspend fun netBlocked(pkg: String): Boolean =
        Commands.chainState(sh(Commands.CHAIN_GET)) == true && Commands.netAllowed(sh(Commands.netGet(pkg))) == false

    suspend fun setNetBlocked(pkg: String, label: String, blocked: Boolean): Outcome = lock.withLock {
        if (!bridge.ready) return notConnected("Network: $label", ChangeKind.NETWORK, pkg)
        val ok = applyNet(pkg, blocked)
        if (ok) protections.update { s -> s.copy(net = if (blocked) s.net + pkg else s.net - pkg) }
        val title = if (blocked) "Blocked network access for $label" else "Gave $label network access back"
        journal.add(change(ChangeKind.NETWORK, pkg, title, if (blocked) "allow" else "deny", if (blocked) "deny" else "allow", ok))
        Outcome(ok, if (ok) title else "Couldn't change network access for $label")
    }

    private suspend fun applyNet(pkg: String, blocked: Boolean): Boolean {
        if (blocked && Commands.chainState(sh(Commands.CHAIN_GET)) != true) sh(Commands.chainSet(true))
        sh(Commands.netSet(pkg, allowed = !blocked))
        return Commands.netAllowed(sh(Commands.netGet(pkg))) == !blocked
    }

    // --- After a reboot -------------------------------------------------------------------

    /**
     * Puts back the microphone and network blocks Android dropped at boot.
     * Returns how many had to be re-applied, or null when Shizuku isn't connected.
     */
    suspend fun reapply(): Int? = lock.withLock {
        if (!bridge.ready) return null
        val s = protections.state.value
        var fixed = 0
        for (pkg in s.mic.keys) {
            val mode = micMode(pkg) ?: continue // uninstalled since
            if (mode != BLOCKED) {
                sh(Commands.appopsSetUid(pkg, Privacy.MIC_OP, BLOCKED))
                fixed++
            }
        }
        if (s.net.isNotEmpty()) {
            if (Commands.chainState(sh(Commands.CHAIN_GET)) != true) sh(Commands.chainSet(true))
            for (pkg in s.net) {
                if (Commands.netAllowed(sh(Commands.netGet(pkg))) != false) {
                    sh(Commands.netSet(pkg, allowed = false))
                    fixed++
                }
            }
        }
        fixed
    }

    // --- Undo -----------------------------------------------------------------------------

    suspend fun undo(c: Change): Outcome {
        if (!c.ok || c.undone) return Outcome(false, "Nothing to undo")
        val result = when (c.kind) {
            ChangeKind.PACKAGE -> lock.withLock {
                if (!bridge.ready) return Outcome(false, "Shizuku isn't connected")
                val target = c.before?.let(AppState::valueOf) ?: return Outcome(false, "Unknown earlier state")
                Outcome(toState(c.target, target), "Undid: ${c.title}")
            }
            ChangeKind.SETTING -> lock.withLock {
                if (!bridge.ready) return Outcome(false, "Shizuku isn't connected")
                val ok = if (c.target == "global/$DNS_MODE") applyDns(c.before ?: "off|")
                else writeSetting(c.target.substringBefore('/'), c.target.substringAfter('/'), c.before)
                Outcome(ok, "Undid: ${c.title}")
            }
            ChangeKind.MICROPHONE -> setMicBlocked(c.target, c.target, blocked = c.after != BLOCKED)
            ChangeKind.NETWORK -> setNetBlocked(c.target, c.target, blocked = c.after != "deny")
        }
        if (result.ok) journal.markUndone(c.id)
        return result
    }

    companion object {
        const val BLOCKED = "ignore"
        const val DNS_MODE = "private_dns_mode"
        const val DNS_HOST = "private_dns_specifier"
    }
}
