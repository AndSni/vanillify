package app.vanillify.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.vanillify.catalog.CatalogEntry
import app.vanillify.catalog.DnsProvider
import app.vanillify.catalog.Finding
import app.vanillify.catalog.Privacy
import app.vanillify.catalog.Source
import app.vanillify.catalog.Tier
import app.vanillify.catalog.Tweak
import app.vanillify.data.Change
import app.vanillify.device.AppState
import app.vanillify.device.InstalledApp
import app.vanillify.device.LibraryUse
import app.vanillify.device.LinkIndex
import app.vanillify.device.SystemUse
import app.vanillify.device.SystemUseReader
import app.vanillify.engine.Engine
import app.vanillify.engine.Outcome
import app.vanillify.graph
import app.vanillify.shell.ShizukuState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One app as the lists show it: what's installed plus what the catalog knows. */
data class AppRow(val app: InstalledApp, val entry: CatalogEntry?) {
    val pkg get() = app.pkg
    val label get() = app.label
    val tier get() = entry?.tier ?: Tier.UNKNOWN
    val source get() = entry?.source ?: Source.UNKNOWN
    val findings get() = entry?.findings.orEmpty()
    /** Already off, and the manufacturer refuses removal: shown, but not offered again. */
    val stuck get() = Finding.LOCKED in findings && app.state == AppState.DISABLED
}

/** Current values of everything on the Privacy screen; null fields aren't known yet. */
data class PrivacyState(
    val loaded: Boolean = false,
    /** Tweak id → its private value is set. */
    val tweaks: Map<String, Boolean> = emptyMap(),
    /** Selected DNS provider id, or "custom:host". */
    val dns: String? = null,
    /** Package → microphone blocked. Only installed packages. */
    val mic: Map<String, Boolean> = emptyMap(),
    val firewall: Boolean = false,
    /** Package → network blocked, for suggested and already-blocked apps. */
    val net: Map<String, Boolean> = emptyMap(),
)

/** One line of the one-tap review. */
data class PlanItem(val key: String, val title: String, val detail: String, val checked: Boolean, val run: suspend (Engine) -> Outcome)

data class PlanSection(val title: String, val items: List<PlanItem>)

data class Progress(val done: Int, val total: Int, val failures: List<String>, val finished: Boolean)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = app.graph
    val shizuku: StateFlow<ShizukuState> = graph.bridge.state
    val journal: StateFlow<List<Change>> = graph.journal.changes
    val protections = graph.protections.state
    val lastReapply = graph.lastReapply

    private val _apps = MutableStateFlow<List<AppRow>?>(null)
    val apps: StateFlow<List<AppRow>?> = _apps

    private val _privacy = MutableStateFlow(PrivacyState())
    val privacy: StateFlow<PrivacyState> = _privacy

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _progress = MutableStateFlow<Progress?>(null)
    val progress: StateFlow<Progress?> = _progress

    private val messages = Channel<String>(Channel.BUFFERED)
    val message = messages.receiveAsFlow()

    init {
        refreshApps()
        viewModelScope.launch {
            shizuku.collect {
                if (it == ShizukuState.READY) {
                    refreshPrivacy()
                    refreshSystemUse()
                }
            }
        }
    }

    fun requestShizukuPermission() = graph.bridge.requestPermission()
    fun openShizuku(): Boolean = graph.bridge.openApp()
    fun retryShizuku() = graph.bridge.restartHelper()
    fun checkShizuku() = graph.bridge.refresh()

    /** Every app on the phone, including ones you installed, for links and names. */
    @Volatile private var everything: Map<String, InstalledApp> = emptyMap()
    @Volatile private var libraries: List<LibraryUse> = emptyList()
    @Volatile private var systemUse = SystemUse()

    private val _links = MutableStateFlow<LinkIndex?>(null)
    /** How apps depend on each other; null while loading. */
    val links: StateFlow<LinkIndex?> = _links

    /** False until roles, services and overlays have been read through Shizuku. */
    private val _systemUseKnown = MutableStateFlow(false)
    val systemUseKnown: StateFlow<Boolean> = _systemUseKnown

    fun refreshApps() {
        viewModelScope.launch {
            val catalog = graph.catalog.await()
            val (all, libs) = withContext(Dispatchers.IO) { graph.apps.all() to graph.apps.libraries() }
            everything = all.associateBy { it.pkg }
            libraries = libs
            _apps.value = all.map { AppRow(it, catalog[it.pkg]) }
                // System apps, plus anything the catalog flags as pushed adware.
                .filter { it.app.system || Finding.ADWARE in it.findings }
            rebuildLinks()
        }
    }

    private fun refreshSystemUse() {
        viewModelScope.launch {
            val use = SystemUseReader(graph.bridge).read() ?: return@launch
            systemUse = use
            _systemUseKnown.value = true
            rebuildLinks()
        }
    }

    private suspend fun rebuildLinks() {
        val catalog = graph.catalog.await()
        val apps = everything.values.toList()
        if (apps.isEmpty()) return
        _links.value = withContext(Dispatchers.Default) {
            LinkIndex(apps, libraries, { pkg -> catalog[pkg]?.let { it.dependencies to it.neededBy } }, systemUse)
        }
    }

    /** The row for any app on the phone, including ones the Apps list doesn't show. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun rowFor(pkg: String): AppRow? {
        _apps.value?.firstOrNull { it.pkg == pkg }?.let { return it }
        val app = everything[pkg] ?: return null
        val catalog = if (graph.catalog.isCompleted) graph.catalog.getCompleted() else null
        return AppRow(app, catalog?.get(pkg))
    }

    /**
     * What breaks if [rows] go: roles they hold, and other active apps that need them
     * (apps being removed together don't count). Empty when nothing is in the way.
     */
    fun removalWarnings(rows: List<AppRow>): List<String> {
        val index = _links.value ?: return emptyList()
        val going = rows.map { it.pkg }.toSet()
        return rows.flatMap { row ->
            val l = index.of(row.pkg)
            val roles = l.roles.map { "${row.label}: ${it.label.replaceFirstChar(Char::lowercase)}. ${it.warning}" }
            val needers = l.neededBy.filter { it.pkg !in going && everything[it.pkg]?.state == AppState.ENABLED }
            val needed = if (needers.isEmpty()) emptyList() else listOf(
                "${needers.size} active app${if (needers.size == 1) " depends" else "s depend"} on ${row.label}: " +
                    needers.take(4).joinToString { labelOf(it.pkg) } + if (needers.size > 4) ", …" else "",
            )
            roles + needed
        }
    }

    fun refreshPrivacy() {
        viewModelScope.launch {
            val engine = graph.engine
            if (!graph.bridge.ready) return@launch
            apps.filterNotNull().first() // the network list is built from the app list
            val tweaks = Privacy.tweaks.associate { it.id to (engine.readSetting(it.namespace, it.key) == it.privateValue) }
            val dns = engine.readDns().let { v ->
                val host = v.substringAfter('|')
                when {
                    v.substringBefore('|') != "hostname" -> "off"
                    else -> Privacy.dnsProviders.firstOrNull { it.hostname == host }?.id ?: "custom:$host"
                }
            }
            val mic = Privacy.micTargets.mapNotNull { t -> engine.micMode(t.pkg)?.let { t.pkg to (it == Engine.BLOCKED) } }.toMap()
            val firewall = engine.firewallSupported()
            val net = if (!firewall) emptyMap() else netCandidates().associateWith { engine.netBlocked(it) }
            _privacy.value = PrivacyState(true, tweaks, dns, mic, firewall, net)
        }
    }

    /** Apps the firewall section lists: ones the catalog saw phoning home, plus any already blocked. */
    private fun netCandidates(): List<String> {
        val installed = _apps.value.orEmpty().filter { it.app.state != AppState.REMOVED }
        val suggested = installed.filter { Finding.PHONES_HOME in it.findings && it.app.usesInternet }.map { it.pkg }
        return (suggested + graph.protections.state.value.net).distinct()
    }

    /** Every app that may use the network, for "Block another app". */
    fun firewallChoices(): List<InstalledApp> = graph.apps.all().filter { it.usesInternet && it.state != AppState.REMOVED }

    fun labelOf(pkg: String): String = everything[pkg]?.label
        ?: runCatching { getApplication<Application>().packageManager.let { pm -> pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() } }.getOrDefault(pkg)

    // --- Actions --------------------------------------------------------------------------

    private fun act(refreshApps: Boolean = false, refreshPrivacy: Boolean = false, block: suspend (Engine) -> List<Outcome>) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val results = block(graph.engine)
                val failed = results.filterNot { it.ok }
                messages.send(
                    when {
                        results.size == 1 -> results.single().message
                        failed.isEmpty() -> "Done: ${results.size} changes"
                        else -> "${results.size - failed.size} done, ${failed.size} couldn't be changed"
                    },
                )
            } finally {
                _busy.value = false
                if (refreshApps) refreshApps()
                if (refreshPrivacy) refreshPrivacy()
            }
        }
    }

    fun remove(rows: List<AppRow>) = act(refreshApps = true) { e -> rows.map { e.remove(it.pkg, it.label) } }
    fun disable(rows: List<AppRow>) = act(refreshApps = true) { e -> rows.map { e.disable(it.pkg, it.label) } }
    fun restore(rows: List<AppRow>) = act(refreshApps = true) { e -> rows.map { e.restore(it.pkg, it.label) } }

    fun setTweak(tweak: Tweak, on: Boolean) = act(refreshPrivacy = true) { e -> listOf(e.setTweak(tweak, on)) }
    fun setDns(provider: DnsProvider) = act(refreshPrivacy = true) { e -> listOf(e.setDns(provider)) }
    fun setMic(pkg: String, blocked: Boolean) = act(refreshPrivacy = true) { e -> listOf(e.setMicBlocked(pkg, labelOf(pkg), blocked)) }
    fun setNet(pkg: String, blocked: Boolean) = act(refreshPrivacy = true) { e -> listOf(e.setNetBlocked(pkg, labelOf(pkg), blocked)) }
    fun undo(change: Change) = undo(listOf(change))

    /**
     * Undoes newest first, so several changes to the same app or setting unwind back to
     * where it started. [changes] may come in any order.
     */
    fun undo(changes: List<Change>) = act(refreshApps = true, refreshPrivacy = true) { e ->
        val order = journal.value.withIndex().associate { (i, c) -> c.id to i } // the journal is newest first
        changes.sortedBy { order[it.id] ?: Int.MAX_VALUE }.map { e.undo(it) }
    }
    fun clearHistory() = graph.journal.clear()

    // --- One tap --------------------------------------------------------------------------

    /** What "Vanillify" would do on this phone right now, safest choices pre-checked. */
    fun plan(): List<PlanSection> {
        val rows = _apps.value.orEmpty()
        val p = _privacy.value
        val sections = mutableListOf<PlanSection>()

        val removable = rows.filter { it.tier == Tier.RECOMMENDED && it.app.state != AppState.REMOVED && !it.stuck }
            .sortedWith(compareBy({ it.source.ordinal }, { it.label.lowercase() }))
        if (removable.isNotEmpty()) sections += PlanSection(
            "Remove preinstalled apps",
            removable.map { r ->
                // Manufacturer, carrier and ad apps are pre-checked; Google and Android apps are your call.
                val checked = r.source !in setOf(Source.GOOGLE, Source.AOSP) || r.findings.isNotEmpty()
                PlanItem("app:${r.pkg}", r.label, "${r.source.label} · ${r.pkg}", checked) { it.remove(r.pkg, r.label) }
            },
        )

        if (p.loaded) {
            val tweaks = Privacy.tweaks.filter { p.tweaks[it.id] == false }
            if (tweaks.isNotEmpty()) sections += PlanSection(
                "Privacy settings",
                tweaks.map { t -> PlanItem("tweak:${t.id}", t.title, t.tradeoff ?: "Nothing noticeable changes", t.recommended) { it.setTweak(t, true) } },
            )
            if (p.dns == "off") {
                val mullvad = Privacy.dnsProviders.first { it.id == "mullvad" }
                sections += PlanSection(
                    "Private DNS",
                    listOf(PlanItem("dns", "Use ${mullvad.name} Private DNS", mullvad.summary, false) { it.setDns(mullvad) }),
                )
            }
            val mic = Privacy.micTargets.filter { p.mic[it.pkg] == false }
            if (mic.isNotEmpty()) sections += PlanSection(
                "Microphone",
                mic.map { t -> PlanItem("mic:${t.pkg}", "Block: ${labelOf(t.pkg)}", t.tradeoff, t.recommended) { it.setMicBlocked(t.pkg, labelOf(t.pkg), true) } },
            )
            val net = p.net.filterValues { !it }.keys
            if (p.firewall && net.isNotEmpty()) sections += PlanSection(
                "Network",
                net.map { pkg -> PlanItem("net:$pkg", "Block: ${labelOf(pkg)}", "Keeps working offline; stops connecting home", true) { it.setNetBlocked(pkg, labelOf(pkg), true) } },
            )
        }
        return sections
    }

    fun runPlan(items: List<PlanItem>) {
        viewModelScope.launch {
            _busy.value = true
            val failures = mutableListOf<String>()
            _progress.value = Progress(0, items.size, failures, false)
            items.forEachIndexed { i, item ->
                val r = item.run(graph.engine)
                if (!r.ok) failures += r.message
                _progress.value = Progress(i + 1, items.size, failures.toList(), false)
            }
            _progress.value = _progress.value?.copy(finished = true)
            _busy.value = false
            refreshApps()
            refreshPrivacy()
        }
    }

    fun dismissProgress() {
        _progress.value = null
    }
}
