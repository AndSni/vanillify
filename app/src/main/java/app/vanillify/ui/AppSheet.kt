package app.vanillify.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.vanillify.device.AppLinks
import app.vanillify.device.AppState
import app.vanillify.device.Link
import app.vanillify.device.LinkKind

/** Details of one app: what it is, what it's linked to, and what you can do with it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    row: AppRow,
    links: AppLinks?,
    systemUseKnown: Boolean,
    ready: Boolean,
    vm: MainViewModel,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onDisable: () -> Unit,
    onRestore: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canGoBack) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                        Spacer(Modifier.size(4.dp))
                    }
                    AppIcon(row.pkg, Modifier.size(48.dp))
                    Spacer(Modifier.size(16.dp))
                    Column {
                        Text(row.label, style = MaterialTheme.typography.titleLarge)
                        Text(row.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "${row.source.label} · ${row.tier.label} · ${stateLabel(row.app.state)}",
                    style = MaterialTheme.typography.labelLarge,
                )

                links?.roles?.takeIf { it.isNotEmpty() }?.let { roles ->
                    Spacer(Modifier.height(16.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            roles.forEach { role ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Outlined.WarningAmber, null, Modifier.padding(top = 2.dp).size(20.dp))
                                    Spacer(Modifier.size(12.dp))
                                    Column {
                                        Text(role.label, style = MaterialTheme.typography.titleSmall)
                                        Text(role.warning, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

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

            if (links == null) return@Column
            LinkSection(
                "Depends on",
                "Uses code or protected features from these. Without them, parts of this app can stop working.",
                links.needs, vm, onOpen, brokenIfOff = true,
            )
            LinkSection(
                "Depended on by",
                "These use code or protected features from this app. Removing it can break parts of them.",
                links.neededBy, vm, onOpen,
            )
            links.overlayFor?.let { target ->
                LinkSection("Add-on for", "This is a resource add-on (overlay) that changes another app.", listOf(Link(target, LinkKind.LIBRARY, emptySet())), vm, onOpen, showVia = false)
            }
            if (links.overlays.isNotEmpty()) LinkSection(
                "Add-ons",
                "Resource add-ons (overlays) that change this app. They do nothing without it.",
                links.overlays.map { Link(it, LinkKind.LIBRARY, emptySet()) }, vm, onOpen, showVia = false,
            )
            if (links.sharesIdWith.isNotEmpty()) LinkSection(
                "Shares its identity with",
                "These run as the same app: same permissions and data.",
                links.sharesIdWith.map { Link(it, LinkKind.LIBRARY, emptySet()) }, vm, onOpen, showVia = false,
            )
            LinkSection(
                "Optional features from",
                "Works without these, minus some features.",
                links.uses, vm, onOpen, collapsed = true,
            )
            LinkSection(
                "Provides optional features to",
                "These keep working without it, minus some features.",
                links.usedBy, vm, onOpen, collapsed = true,
            )
            if (links.isEmpty) {
                Text(
                    "No other apps depend on this one, and it doesn't depend on any.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
            if (!systemUseKnown) {
                Text(
                    "Connect Shizuku to also see whether the system uses this app (home screen, keyboard, default apps) and its add-ons.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private fun stateLabel(state: AppState) = when (state) {
    AppState.ENABLED -> "Active"
    AppState.DISABLED -> "Disabled"
    AppState.REMOVED -> "Removed"
}

/**
 * One group of linked apps. Long groups show the first few with "Show all"; optional
 * ones start folded. [brokenIfOff] marks needed apps that are already removed or disabled.
 */
@Composable
private fun LinkSection(
    title: String,
    explanation: String,
    links: List<Link>,
    vm: MainViewModel,
    onOpen: (String) -> Unit,
    collapsed: Boolean = false,
    brokenIfOff: Boolean = false,
    showVia: Boolean = true,
) {
    if (links.isEmpty()) return
    var expanded by remember { mutableStateOf(!collapsed) }
    var all by remember { mutableStateOf(false) }
    SectionHeaderWithAction(
        "$title (${links.size})",
        action = if (collapsed) (if (expanded) "Hide" else "Show") to { expanded = !expanded } else null,
    )
    if (!expanded) return
    Text(
        explanation,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    val shown = if (all) links else links.take(PREVIEW)
    shown.forEach { link ->
        val other = vm.rowFor(link.pkg)
        val state = other?.app?.state
        val broken = brokenIfOff && state != null && state != AppState.ENABLED
        ListItem(
            headlineContent = { Text(other?.label ?: link.pkg) },
            supportingContent = {
                val parts = listOfNotNull(
                    if (broken) "${stateLabel(state!!)}: parts of this app may not work" else state?.takeIf { it != AppState.ENABLED }?.let(::stateLabel),
                    if (showVia) link.kind.label + if (link.via.isNotEmpty()) ": " + link.via.take(3).joinToString() else "" else null,
                )
                if (parts.isNotEmpty()) Text(
                    parts.joinToString(" · "),
                    color = if (broken) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            },
            leadingContent = { AppIcon(link.pkg, Modifier.size(32.dp)) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth().clickable(enabled = other != null) { onOpen(link.pkg) },
        )
    }
    if (links.size > PREVIEW && !all) {
        TextButton(onClick = { all = true }, modifier = Modifier.padding(start = 8.dp)) { Text("Show all ${links.size}") }
    }
}

private const val PREVIEW = 5
