package app.vanillify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

/**
 * After a reboot Android has dropped the microphone and network blocks. If Shizuku is
 * already running (e.g. Sui, or Shizuku's own start on boot), [Graph] re-applies them as
 * soon as it connects; otherwise this posts a notification asking to start Shizuku.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val graph = context.graph
        val pending = graph.protections.state.value.count
        if (pending == 0) return
        val result = goAsync()
        graph.scope.launch {
            try {
                if (graph.bridge.awaitReady(WAIT_MS)) graph.reapply() else Notifier.showPaused(context, pending)
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        /** Well inside the ~10 s a receiver may run for. */
        const val WAIT_MS = 8_000L
    }
}
