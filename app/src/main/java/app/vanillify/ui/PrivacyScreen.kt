package app.vanillify.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.vanillify.catalog.Privacy
import app.vanillify.device.InstalledApp
import app.vanillify.shell.ShizukuState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(vm: MainViewModel, modifier: Modifier) {
    val shizuku by vm.shizuku.collectAsState()
    val p by vm.privacy.collectAsState()
    val busy by vm.busy.collectAsState()
    val apps by vm.apps.collectAsState()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val ready = shizuku == ShizukuState.READY
    var dnsDialog by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Blocks need re-applying after a restart; the notification is how Vanillify asks for Shizuku then.
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun beforeBlocking() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun note(pkg: String) = apps.orEmpty().firstOrNull { it.pkg == pkg }?.entry?.note

    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("Privacy") }, scrollBehavior = scroll) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item { ShizukuCard(shizuku, vm) }
            if (ready && !p.loaded) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                return@LazyColumn
            }
            if (!ready) {
                item {
                    Text(
                        "Vanillify reads and changes these settings through Shizuku.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                return@LazyColumn
            }

            item { SectionHeader("Private DNS") }
            item {
                val current = p.dns?.let { id -> Privacy.dnsProviders.firstOrNull { it.id == id }?.name ?: id.removePrefix("custom:") }
                ListItem(
                    headlineContent = { Text("Private DNS") },
                    supportingContent = { Text(if (current == "Off") "Off: lookups go unencrypted to your network's DNS" else current.orEmpty()) },
                    leadingContent = { Icon(Icons.Outlined.Dns, null) },
                    modifier = Modifier.clickable(enabled = !busy) { dnsDialog = true },
                )
            }

            item { SectionHeader("System settings") }
            items(Privacy.tweaks, key = { it.id }) { t ->
                val on = p.tweaks[t.id] == true
                ListItem(
                    headlineContent = { Text(t.title) },
                    supportingContent = {
                        Column {
                            Text(t.summary)
                            t.tradeoff?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                        }
                    },
                    trailingContent = { Switch(checked = on, onCheckedChange = { vm.setTweak(t, it) }, enabled = !busy) },
                    modifier = Modifier.clickable(enabled = !busy) { vm.setTweak(t, !on) },
                )
            }

            item { SectionHeader("Microphone") }
            item {
                Text(
                    "These apps run all the time, so Android's \"only while using the app\" doesn't hold them back. " +
                        "Calls, voice messages and voice typing keep working.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(Privacy.micTargets.filter { it.pkg in p.mic }, key = { "mic:${it.pkg}" }) { t ->
                val blocked = p.mic[t.pkg] == true
                ListItem(
                    headlineContent = { Text("Block: ${vm.labelOf(t.pkg)}") },
                    supportingContent = {
                        Column {
                            Text(t.why)
                            Text(t.tradeoff, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    leadingContent = { AppIcon(t.pkg) },
                    trailingContent = {
                        Switch(checked = blocked, enabled = !busy, onCheckedChange = { if (it) beforeBlocking(); vm.setMic(t.pkg, it) })
                    },
                )
            }

            item { SectionHeader("Network") }
            if (!p.firewall) {
                item {
                    Text(
                        "Blocking an app's network access needs Android 14 or later.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            } else {
                item {
                    Text(
                        "Blocked apps keep working, just without internet. Uses Android's own firewall: no VPN needed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                items(p.net.keys.toList(), key = { "net:$it" }) { pkg ->
                    val blocked = p.net[pkg] == true
                    ListItem(
                        headlineContent = { Text(vm.labelOf(pkg)) },
                        supportingContent = { Text(note(pkg) ?: pkg, maxLines = 3) },
                        leadingContent = { AppIcon(pkg) },
                        trailingContent = {
                            Switch(checked = blocked, enabled = !busy, onCheckedChange = { if (it) beforeBlocking(); vm.setNet(pkg, it) })
                        },
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Block another app") },
                        leadingContent = { Icon(Icons.Outlined.Add, null) },
                        modifier = Modifier.clickable(enabled = !busy) { addDialog = true },
                    )
                }
            }
            item {
                Text(
                    "Android resets microphone and network blocks when the phone restarts. Vanillify puts them back " +
                        "as soon as Shizuku is running again, and reminds you if it isn't.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (dnsDialog) {
        AlertDialog(
            onDismissRequest = { dnsDialog = false },
            title = { Text("Private DNS") },
            text = {
                Column {
                    Privacy.dnsProviders.forEach { provider ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(selected = p.dns == provider.id, role = Role.RadioButton) { vm.setDns(provider); dnsDialog = false }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            RadioButton(selected = p.dns == provider.id, onClick = null)
                            Column(Modifier.padding(start = 16.dp)) {
                                Text(provider.name, style = MaterialTheme.typography.bodyLarge)
                                Text(provider.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dnsDialog = false }) { Text("Cancel") } },
        )
    }

    if (addDialog) {
        AddAppDialog(
            vm = vm,
            exclude = p.net.keys,
            onPick = { beforeBlocking(); vm.setNet(it.pkg, true); addDialog = false },
            onDismiss = { addDialog = false },
        )
    }
}

@Composable
private fun AddAppDialog(vm: MainViewModel, exclude: Set<String>, onPick: (InstalledApp) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val choices by produceState<List<InstalledApp>?>(null) {
        value = withContext(Dispatchers.IO) { vm.firewallChoices() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block network access") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val list = choices
                if (list == null) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(
                            list.filter { it.pkg !in exclude && (query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true)) },
                            key = { it.pkg },
                        ) { app ->
                            ListItem(
                                headlineContent = { Text(app.label) },
                                supportingContent = { Text(app.pkg, maxLines = 1) },
                                leadingContent = { AppIcon(app.pkg) },
                                modifier = Modifier.clickable { onPick(app) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
