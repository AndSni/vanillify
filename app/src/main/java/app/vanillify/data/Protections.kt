package app.vanillify.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Blocks that Android forgets at every boot: microphone blocks (the permission
 * controller re-syncs app-ops) and firewall blocks (the chain is in memory only).
 * Vanillify keeps what you chose here and puts it back once Shizuku is running.
 */
data class ProtectionState(
    /** Package → its microphone mode before the block, to put back on unblock. */
    val mic: Map<String, String> = emptyMap(),
    val net: Set<String> = emptySet(),
) {
    val isEmpty get() = mic.isEmpty() && net.isEmpty()
    val count get() = mic.size + net.size
}

class Protections(context: Context) {

    private val file = File(context.filesDir, "protections.json")
    private val _state = MutableStateFlow(load())
    val state: StateFlow<ProtectionState> = _state

    @Synchronized
    fun update(transform: (ProtectionState) -> ProtectionState) {
        _state.value = transform(_state.value)
        save()
    }

    private fun load(): ProtectionState = runCatching {
        val o = JSONObject(file.readText())
        val mic = o.getJSONObject("mic")
        val net = o.getJSONArray("net")
        ProtectionState(
            mic = mic.keys().asSequence().associateWith { mic.getString(it) },
            net = (0 until net.length()).map { net.getString(it) }.toSet(),
        )
    }.getOrDefault(ProtectionState())

    private fun save() {
        val s = _state.value
        val o = JSONObject()
            .put("mic", JSONObject(s.mic as Map<*, *>))
            .put("net", JSONArray(s.net.toList()))
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(file)
    }
}
