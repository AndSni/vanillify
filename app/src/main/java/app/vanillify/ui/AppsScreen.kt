package app.vanillify.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.vanillify.catalog.Tier
import app.vanillify.device.AppState
import app.vanillify.shell.ShizukuState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppsScreen(vm: MainViewModel, modifier: Modifier) {
    val apps by vm.apps.collectAsState()
    val shizuku by vm.shizuku.collectAsState()
    val busy by vm.busy.collectAsState()
    var tiers by rememberSaveable { mutableStateOf(setOf(Tier.RECOMMENDED)) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }
    var detail by remember { mutableStateOf<AppRow?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val ready = shizuku == ShizukuState.READY

    val shown = apps.orEmpty()
        .filter { it.tier in tiers }
        .filter { q -> query.isBlank() || q.label.contains(query, true) || q.pkg.contains(query, true) }
        .sortedWith(compareBy({ it.app.state.ordinal }, { it.label.lowercase() }))
    val chosen = apps.orEmpty().filter { it.pkg in selected }

    /** Removing something rated Expert or Keep asks first. */
    fun guarded(rows: List<AppRow>, verb: String, run: () -> Unit) {
        val risky = rows.filter { it.tier == Tier.EXPERT || it.tier == Tier.UNSAFE }
        if (risky.isEmpty()) run() else confirm = (
            "${risky.size} of these ${if (rows.size == 1) "is" else "are"} rated ${risky.first().tier.label.lowercase()}. " +
                "$verb ${if (risky.size == 1) "it" else "them"} can break features or the phone itself. You can undo it in History."
            ) to run
    }

    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            if (selected.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${selected.size} selected") },
                    navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Clear selection") } },
                    actions = {
                        IconButton(onClick = { guarded(chosen, "Removing") { vm.remove(chosen); selected = emptySet() } }, enabled = ready && !busy) {
                            Icon(Icons.Outlined.Delete, "Remove")
                        }
                        IconButton(onClick = { guarded(chosen, "Disabling") { vm.disable(chosen); selected = emptySet() } }, enabled = ready && !busy) {
                            Icon(Icons.Outlined.Block, "Disable")
                        }
                        IconButton(onClick = { vm.restore(chosen); selected = emptySet() }, enabled = ready && !busy) {
                            Icon(Icons.Outlined.Restore, "Restore")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                )
            } else {
                LargeTopAppBar(
                    title = { Text("Apps") },
                    actions = { IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Outlined.Search, "Search") } },
                    scrollBehavior = scroll,
                )
            }
        },
    ) { padding ->
        if (apps == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item { ShizukuCard(shizuku, vm) }
            if (searching) item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Tier.entries.forEach { t ->
                        FilterChip(
                            selected = t in tiers,
                            onClick = { tiers = if (t in tiers) tiers - t else tiers + t },
                            label = { Text("${t.label} (${apps.orEmpty().count { it.tier == t }})") },
                        )
                    }
                }
            }
            if (shown.isEmpty()) item {
                Text(
                    "No apps match. Try another filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp),
                )
            }
            items(shown, key = { it.pkg }) { row ->
                val off = row.app.state != AppState.ENABLED
                ListItem(
                    headlineContent = { Text(row.label) },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                row.source.label,
                                row.tier.label,
                                when (row.app.state) {
                                    AppState.DISABLED -> "Disabled"
                                    AppState.REMOVED -> "Removed"
                                    AppState.ENABLED -> null
                                },
                            ).joinToString(" · "),
                        )
                    },
                    leadingContent = { AppIcon(row.pkg, Modifier.size(40.dp).alpha(if (off) 0.38f else 1f)) },
                    trailingContent = if (selected.isNotEmpty()) {
                        { Checkbox(checked = row.pkg in selected, onCheckedChange = { selected = if (it) selected + row.pkg else selected - row.pkg }) }
                    } else null,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            if (selected.isNotEmpty()) selected = if (row.pkg in selected) selected - row.pkg else selected + row.pkg
                            else detail = row
                        },
                        onLongClick = { selected = selected + row.pkg },
                    ),
                )
            }
        }
    }

    detail?.let { row ->
        AppSheet(
            row = row,
            ready = ready && !busy,
            onDismiss = { detail = null },
            onRemove = { guarded(listOf(row), "Removing") { vm.remove(listOf(row)) }; detail = null },
            onDisable = { guarded(listOf(row), "Disabling") { vm.disable(listOf(row)) }; detail = null },
            onRestore = { vm.restore(listOf(row)); detail = null },
        )
    }

    confirm?.let { (text, run) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Are you sure?") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { run(); confirm = null }) { Text("Continue") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSheet(row: AppRow, ready: Boolean, onDismiss: () -> Unit, onRemove: () -> Unit, onDisable: () -> Unit, onRestore: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(row.pkg, Modifier.size(48.dp))
                Spacer(Modifier.size(16.dp))
                Column {
                    Text(row.label, style = MaterialTheme.typography.titleLarge)
                    Text(row.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "${row.source.label} · ${row.tier.label} · " + when (row.app.state) {
                    AppState.ENABLED -> "Active"
                    AppState.DISABLED -> "Disabled"
                    AppState.REMOVED -> "Removed"
                },
                style = MaterialTheme.typography.labelLarge,
            )
            val entry = row.entry
            if (entry != null && entry.fromVanillify) {
                Spacer(Modifier.height(16.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Vanillify found", style = MaterialTheme.typography.labelLarge)
                        entry.findings.forEach { Text("• ${it.label}", style = MaterialTheme.typography.bodyMedium) }
                        entry.note?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (!entry?.description.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(entry!!.description, style = MaterialTheme.typography.bodyMedium)
            }
            if (entry != null && entry.neededBy.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Needed by: ${entry.neededBy.joinToString()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (entry == null) {
                Spacer(Modifier.height(16.dp))
                Text("No one has rated this app yet. Only remove it if you know what it does.", style = MaterialTheme.typography.bodyMedium)
            }
            if (row.stuck) {
                Spacer(Modifier.height(12.dp))
                Text("It's already disabled, and the manufacturer blocks removing it further.", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (row.app.state) {
                    AppState.REMOVED -> Button(onClick = onRestore, enabled = ready) { Text("Restore") }
                    AppState.DISABLED -> {
                        if (!row.stuck) Button(onClick = onRemove, enabled = ready) { Text("Remove") }
                        OutlinedButton(onClick = onRestore, enabled = ready) { Text("Enable") }
                    }
                    AppState.ENABLED -> {
                        Button(onClick = onRemove, enabled = ready) { Text("Remove") }
                        OutlinedButton(onClick = onDisable, enabled = ready) { Text("Disable") }
                    }
                }
            }
            if (!ready) {
                Spacer(Modifier.height(8.dp))
                Text("Connect Shizuku to make changes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
