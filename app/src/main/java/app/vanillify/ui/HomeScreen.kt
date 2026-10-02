package app.vanillify.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.vanillify.catalog.Privacy
import app.vanillify.catalog.Tier
import app.vanillify.device.AppState
import app.vanillify.shell.ShizukuState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, modifier: Modifier, onVanillify: () -> Unit, onOpen: (Tab) -> Unit) {
    val shizuku by vm.shizuku.collectAsState()
    val apps by vm.apps.collectAsState()
    val privacy by vm.privacy.collectAsState()
    val protections by vm.protections.collectAsState()
    val reapplied by vm.lastReapply.collectAsState()
    val journal by vm.journal.collectAsState()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val removable = apps.orEmpty().count { it.tier == Tier.RECOMMENDED && it.app.state != AppState.REMOVED && !it.stuck }
    val removed = apps.orEmpty().count { it.app.state == AppState.REMOVED }
    val ready = shizuku == ShizukuState.READY

    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("Vanillify") }, scrollBehavior = scroll) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item { ShizukuCard(shizuku, vm) }
            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Make this phone vanilla", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Remove the apps your phone came with that you never asked for, stop the data sharing " +
                                "and background listening, and keep it that way. You review every change first, " +
                                "and everything can be undone.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onVanillify, enabled = ready && apps != null) { Text("Vanillify") }
                    }
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(if (apps == null) "Checking apps…" else "$removable apps safe to remove") },
                    supportingContent = { Text(if (removed > 0) "$removed already removed" else "Preinstalled apps the community list rates safe to remove") },
                    leadingContent = { Icon(Icons.Outlined.Apps, null) },
                    modifier = Modifier.clickable { onOpen(Tab.APPS) },
                )
            }
            item {
                val privateCount = privacy.tweaks.count { it.value }
                ListItem(
                    headlineContent = {
                        Text(if (privacy.loaded) "$privateCount of ${Privacy.tweaks.size} privacy settings set" else "Privacy settings")
                    },
                    supportingContent = {
                        Text(
                            when {
                                !ready -> "Connect Shizuku to check them"
                                !privacy.loaded -> "Checking…"
                                privacy.dns == "off" -> "Private DNS is off"
                                else -> "Private DNS is on"
                            },
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.Shield, null) },
                    modifier = Modifier.clickable { onOpen(Tab.PRIVACY) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("${protections.mic.size} microphone and ${protections.net.size} network blocks") },
                    supportingContent = {
                        Text(
                            when {
                                protections.isEmpty -> "None set"
                                !ready -> "Waiting for Shizuku: Android resets these at every restart"
                                reapplied != null && reapplied!! > 0 -> "Active. Put back $reapplied after the last restart"
                                else -> "Active"
                            },
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.Mic, null) },
                    modifier = Modifier.clickable { onOpen(Tab.PRIVACY) },
                )
            }
            item {
                val count = journal.count { it.ok && !it.undone }
                ListItem(
                    headlineContent = { Text("History") },
                    supportingContent = { Text(if (count == 0) "No changes yet" else "$count changes, each can be undone") },
                    leadingContent = { Icon(Icons.Outlined.History, null) },
                    modifier = Modifier.clickable { onOpen(Tab.HISTORY) },
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(16.dp))
                    Text(
                        "Vanillify has no internet permission: it can't send anything anywhere. App ratings come " +
                            "from the Universal Android Debloater community list (GPL-3.0), with Vanillify's own " +
                            "findings on top.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                val uri = LocalUriHandler.current
                // Opens the browser: Vanillify itself still has no network access.
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { runCatching { uri.openUri(SUPPORT_URL) } }
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Outlined.VolunteerActivism, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(16.dp))
                    Text(
                        buildAnnotatedString {
                            append("Vanillify is free, open source and has no ads. If it made your phone better, you can support its development. ")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("Support Vanillify") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * A forwarder on the project's GitHub Pages (docs/donate/), so the donation service can
 * change without an app update. Play builds may need a plain project page instead.
 */
const val SUPPORT_URL = "https://andsni.github.io/vanillify/donate/"

/** The one-tap plan: every change listed with a checkbox, nothing applied until you confirm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(vm: MainViewModel, onClose: () -> Unit) {
    val sections = remember { vm.plan() }
    val checked = remember { mutableStateMapOf<String, Boolean>().apply { sections.flatMap { it.items }.forEach { put(it.key, it.checked) } } }
    val progress by vm.progress.collectAsState()
    val chosen = sections.flatMap { it.items }.filter { checked[it.key] == true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review changes") },
                navigationIcon = {
                    IconButton(onClick = { vm.dismissProgress(); onClose() }, enabled = progress?.finished != false) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                    val p = progress
                    when {
                        p == null -> Button(onClick = { vm.runPlan(chosen) }, enabled = chosen.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text("Apply ${chosen.size} changes")
                        }
                        !p.finished -> Column {
                            Text("Applying ${p.done} of ${p.total}…")
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { p.done / p.total.toFloat() }, modifier = Modifier.fillMaxWidth())
                        }
                        else -> Button(onClick = { vm.dismissProgress(); onClose() }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                    }
                }
            }
        },
    ) { padding ->
        val p = progress
        if (p != null && p.finished) {
            LazyColumn(contentPadding = padding) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text("${p.total - p.failures.size} of ${p.total} changes made", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Everything is in History, where each change can be undone.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (p.failures.isNotEmpty()) {
                    item { SectionHeader("Not changed") }
                    items(p.failures) { ListItem(headlineContent = { Text(it) }) }
                }
            }
            return@Scaffold
        }
        if (sections.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Nothing left to change. This phone is already vanilla.", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        LazyColumn(contentPadding = padding) {
            sections.forEach { section ->
                item(key = "h:${section.title}") {
                    val all = section.items.all { checked[it.key] == true }
                    SectionHeaderWithAction(
                        section.title,
                        action = if (progress == null) (if (all) "Clear" else "Select all") to { section.items.forEach { checked[it.key] = !all } } else null,
                    )
                }
                items(section.items, key = { it.key }) { item ->
                    ListItem(
                        headlineContent = { Text(item.title) },
                        supportingContent = { Text(item.detail) },
                        leadingContent = if (item.key.startsWith("app:")) {
                            { AppIcon(item.key.removePrefix("app:")) }
                        } else null,
                        trailingContent = {
                            Checkbox(checked = checked[item.key] == true, onCheckedChange = { checked[item.key] = it }, enabled = progress == null)
                        },
                        modifier = Modifier.clickable(enabled = progress == null) { checked[item.key] = checked[item.key] != true },
                    )
                }
            }
        }
    }
}
