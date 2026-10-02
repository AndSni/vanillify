package app.vanillify.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.vanillify.shell.ShizukuState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: MainViewModel, modifier: Modifier) {
    val changes by vm.journal.collectAsState()
    val shizuku by vm.shizuku.collectAsState()
    val busy by vm.busy.collectAsState()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    var clearing by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("History") },
                actions = {
                    if (changes.isNotEmpty()) IconButton(onClick = { clearing = true }) { Icon(Icons.Outlined.DeleteSweep, "Clear history") }
                },
                scrollBehavior = scroll,
            )
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
            byDay.forEach { (day, list) ->
                item(key = "day:$day") { SectionHeader(day) }
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
                        trailingContent = if (c.ok && !c.undone) {
                            {
                                TextButton(onClick = { vm.undo(c) }, enabled = shizuku == ShizukuState.READY && !busy) { Text("Undo") }
                            }
                        } else null,
                    )
                }
            }
        }
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
