package app.vanillify.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
fun AppsScreen(vm: MainViewModel, modifier: Modifier, openSheet: String? = null) {
    val apps by vm.apps.collectAsState()
    val shizuku by vm.shizuku.collectAsState()
    val busy by vm.busy.collectAsState()
    var tiers by rememberSaveable { mutableStateOf(setOf(Tier.RECOMMENDED)) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }
    var trail by remember { mutableStateOf(listOfNotNull(openSheet)) }
    val links by vm.links.collectAsState()
    val systemUseKnown by vm.systemUseKnown.collectAsState()
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val ready = shizuku == ShizukuState.READY

    val shown = apps.orEmpty()
        .filter { it.tier in tiers }
        .filter { q -> query.isBlank() || q.label.contains(query, true) || q.pkg.contains(query, true) }
        .sortedWith(compareBy({ it.app.state.ordinal }, { it.label.lowercase() }))
    val chosen = apps.orEmpty().filter { it.pkg in selected }

    /** Asks first when something is rated risky, is in use by the system, or other apps need it. */
    fun guarded(rows: List<AppRow>, verb: String, run: () -> Unit) {
        val risky = rows.filter { it.tier == Tier.EXPERT || it.tier == Tier.UNSAFE }
        val reasons = buildList {
            if (risky.isNotEmpty()) add(
                (if (rows.size == 1) "It's" else "${risky.size} of these are") +
                    " rated ${risky.first().tier.label.lowercase()}: ${verb.lowercase()} ${if (risky.size == 1) "it" else "them"} can break features or the phone itself.",
            )
            addAll(vm.removalWarnings(rows))
        }
        if (reasons.isEmpty()) run() else confirm = (reasons.joinToString("\n\n") + "\n\nYou can undo it in History.") to run
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
                            else trail = listOf(row.pkg)
                        },
                        onLongClick = { selected = selected + row.pkg },
                    ),
                )
            }
        }
    }

    // The sheet keeps a trail, so following a link to another app can come back.
    trail.lastOrNull()?.let(vm::rowFor)?.let { row ->
        AppSheet(
            row = row,
            links = links?.of(row.pkg),
            systemUseKnown = systemUseKnown,
            ready = ready && !busy,
            vm = vm,
            canGoBack = trail.size > 1,
            onBack = { trail = trail.dropLast(1) },
            onOpen = { pkg -> if (vm.rowFor(pkg) != null) trail = trail + pkg },
            onDismiss = { trail = emptyList() },
            onRemove = { guarded(listOf(row), "Removing") { vm.remove(listOf(row)) }; trail = emptyList() },
            onDisable = { guarded(listOf(row), "Disabling") { vm.disable(listOf(row)) }; trail = emptyList() },
            onRestore = { vm.restore(listOf(row)); trail = emptyList() },
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

