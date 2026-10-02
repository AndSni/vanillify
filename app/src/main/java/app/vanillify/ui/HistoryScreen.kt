package app.vanillify.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.vanillify.data.Change
import app.vanillify.shell.ShizukuState

private val Change.undoable get() = ok && !undone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(vm: MainViewModel, modifier: Modifier) {
    val changes by vm.journal.collectAsState()
    val shizuku by vm.shizuku.collectAsState()
    val busy by vm.busy.collectAsState()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    var clearing by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }
    // Ids of selected changes; only ones that can still be undone. Dropped ones fall out on their own.
    var selected by rememberSaveable { mutableStateOf(setOf<Long>()) }
    val undoable = changes.filter { it.undoable }
    val chosen = undoable.filter { it.id in selected }
    val canAct = shizuku == ShizukuState.READY && !busy

    if (selected.isNotEmpty() && chosen.isEmpty()) selected = emptySet() // all of them got undone
    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    fun toggle(c: Change) {
        if (!c.undoable) return
        selected = if (c.id in selected) selected - c.id else selected + c.id
    }

    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            if (chosen.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${chosen.size} selected") },
                    navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Clear selection") } },
                    actions = {
                        IconButton(onClick = { selected = undoable.map { it.id }.toSet() }) { Icon(Icons.Outlined.SelectAll, "Select all") }
                        IconButton(onClick = { confirmUndo = true }, enabled = canAct) { Icon(Icons.AutoMirrored.Outlined.Undo, "Undo") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                )
            } else {
                LargeTopAppBar(
                    title = { Text("History") },
                    actions = {
                        if (changes.isNotEmpty()) IconButton(onClick = { clearing = true }) { Icon(Icons.Outlined.DeleteSweep, "Clear history") }
                    },
                    scrollBehavior = scroll,
                )
            }
        },
    ) { padding ->
        if (changes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Changes you make show up here, each with a way to undo it.", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        val byDay = changes.groupBy { DateUtils.formatDateTime(context, it.time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            if (selected.isEmpty() && undoable.size > 1) item {
                Text(
                    "Long-press a change to select several and undo them together.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            byDay.forEach { (day, list) ->
                item(key = "day:$day") {
                    val dayUndoable = list.filter { it.undoable }
                    SectionHeaderWithAction(
                        day,
                        action = if (selected.isNotEmpty() && dayUndoable.isNotEmpty()) {
                            val all = dayUndoable.all { it.id in selected }
                            (if (all) "Clear" else "Select day") to {
                                val ids = dayUndoable.map { it.id }.toSet()
                                selected = if (all) selected - ids else selected + ids
                            }
                        } else null,
                    )
                }
                items(list, key = { it.id }) { c ->
                    ListItem(
                        headlineContent = {
                            Text(c.title, textDecoration = if (c.undone) TextDecoration.LineThrough else null)
                        },
                        supportingContent = {
                            Column {
                                Text(
                                    DateFormat.getTimeFormat(context).format(c.time) + when {
                                        c.undone -> " · Undone"
                                        !c.ok -> " · Not changed"
                                        else -> ""
                                    },
                                )
                                c.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            }
                        },
                        trailingContent = when {
                            selected.isNotEmpty() && c.undoable -> {
                                { Checkbox(checked = c.id in selected, onCheckedChange = { toggle(c) }) }
                            }
                            selected.isEmpty() && c.undoable -> {
                                { TextButton(onClick = { vm.undo(c) }, enabled = canAct) { Text("Undo") } }
                            }
                            else -> null
                        },
                        modifier = Modifier.combinedClickable(
                            onClick = { if (selected.isNotEmpty()) toggle(c) },
                            onLongClick = { toggle(c) },
                        ),
                    )
                }
            }
        }
    }

    if (confirmUndo) {
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            title = { Text(if (chosen.size == 1) "Undo this change?" else "Undo ${chosen.size} changes?") },
            text = { Text("They're undone newest first, so anything changed more than once goes back to how it was before Vanillify.") },
            confirmButton = {
                TextButton(onClick = { vm.undo(chosen); selected = emptySet(); confirmUndo = false }) { Text("Undo") }
            },
            dismissButton = { TextButton(onClick = { confirmUndo = false }) { Text("Cancel") } },
        )
    }

    if (clearing) {
        AlertDialog(
            onDismissRequest = { clearing = false },
            title = { Text("Clear history?") },
            text = { Text("Your changes stay in place, but you won't be able to undo them from here. Settings you turn off later go back to Android's defaults.") },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); clearing = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { clearing = false }) { Text("Cancel") } },
        )
    }
}
