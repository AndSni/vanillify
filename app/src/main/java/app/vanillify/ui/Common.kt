package app.vanillify.ui

import android.content.pm.PackageManager
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.vanillify.shell.ShizukuState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val iconCache = LruCache<String, ImageBitmap>(300)

/** An app's own icon, loaded off the main thread; works for apps removed for the user too. */
@Composable
fun AppIcon(pkg: String, modifier: Modifier = Modifier.size(40.dp)) {
    val context = LocalContext.current
    val icon by produceState(iconCache.get(pkg), pkg) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val pm = context.packageManager
                val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
                val info = if (Build.VERSION.SDK_INT >= 33) {
                    pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(flags.toLong()))
                } else {
                    @Suppress("DEPRECATION") pm.getApplicationInfo(pkg, flags)
                }
                info.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()?.also { iconCache.put(pkg, it) }
        }
    }
    Box(modifier.clip(CircleShape)) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) }
    }
}

/** Settings-style group title in the primary colour. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** [SectionHeader] with an optional text button on the right, e.g. "Select all". */
@Composable
fun SectionHeaderWithAction(text: String, action: Pair<String, () -> Unit>?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        SectionHeader(text)
        action?.let { (label, onClick) ->
            TextButton(onClick = onClick, modifier = Modifier.padding(end = 8.dp)) { Text(label) }
        }
    }
}

/** What Shizuku needs from the user right now, with the one button that helps. */
@Composable
fun ShizukuCard(state: ShizukuState, vm: MainViewModel, modifier: Modifier = Modifier) {
    if (state == ShizukuState.READY) return
    val (title, body) = when (state) {
        ShizukuState.NOT_INSTALLED -> "Install Shizuku" to
            "Vanillify changes system apps and settings through Shizuku, which gives it the same rights as a computer running adb. No root needed. Install Shizuku from F-Droid, Google Play or GitHub."
        ShizukuState.NOT_RUNNING -> "Start Shizuku" to
            "Shizuku is installed but not running. Open it and start it with Wireless debugging. It has to be started again after every restart."
        ShizukuState.UNSUPPORTED -> "Update Shizuku" to "This Shizuku is too old. Update it to version 11 or later."
        ShizukuState.NO_PERMISSION -> "Allow Vanillify in Shizuku" to "Shizuku is running. Allow Vanillify to use it."
        ShizukuState.CONNECTING -> "Connecting to Shizuku…" to "This takes a second."
        ShizukuState.FAILING -> "Shizuku isn't responding" to "Vanillify will keep trying. You can also retry now."
        ShizukuState.READY -> return
    }
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.size(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (state) {
                    ShizukuState.NOT_RUNNING -> Button(onClick = { vm.openShizuku() }) { Text("Open Shizuku") }
                    ShizukuState.NO_PERMISSION -> Button(onClick = { vm.requestShizukuPermission() }) { Text("Allow") }
                    ShizukuState.FAILING -> Button(onClick = { vm.retryShizuku() }) { Text("Retry") }
                    else -> {}
                }
                if (state != ShizukuState.CONNECTING) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { vm.checkShizuku() }) { Text("Check again") }
                }
            }
        }
    }
}
