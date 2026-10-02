package app.vanillify.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class ChangeKind { PACKAGE, SETTING, MICROPHONE, NETWORK }

/** One change Vanillify made, with what was there before so it can be undone. */
data class Change(
    val id: Long,
    val time: Long,
    val kind: ChangeKind,
    /** Package name, or "namespace/key" for a setting. */
    val target: String,
    /** What a person reads: "Removed Facebook App Installer". */
    val title: String,
    val before: String?,
    val after: String?,
    val ok: Boolean,
    val error: String? = null,
    val undone: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("time", time).put("kind", kind.name).put("target", target).put("title", title)
        .put("before", before ?: JSONObject.NULL).put("after", after ?: JSONObject.NULL)
        .put("ok", ok).put("error", error ?: JSONObject.NULL).put("undone", undone)

    companion object {
        fun fromJson(o: JSONObject) = Change(
            id = o.getLong("id"),
            time = o.getLong("time"),
            kind = ChangeKind.valueOf(o.getString("kind")),
            target = o.getString("target"),
            title = o.getString("title"),
            before = o.optStringOrNull("before"),
            after = o.optStringOrNull("after"),
            ok = o.getBoolean("ok"),
            error = o.optStringOrNull("error"),
            undone = o.optBoolean("undone"),
        )
    }
}

internal fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key)

/** Every change, newest first, kept in a small JSON file in the app's private storage. */
class Journal(context: Context) {

    private val file = File(context.filesDir, "journal.json")
    private val _changes = MutableStateFlow(load())
    val changes: StateFlow<List<Change>> = _changes

    @Synchronized
    fun add(change: Change) {
        _changes.value = (listOf(change) + _changes.value).take(MAX)
        save()
    }

    @Synchronized
    fun markUndone(id: Long) {
        _changes.value = _changes.value.map { if (it.id == id) it.copy(undone = true) else it }
        save()
    }

    @Synchronized
    fun clear() {
        _changes.value = emptyList()
        save()
    }

    private fun load(): List<Change> = runCatching {
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { Change.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun save() {
        val arr = JSONArray()
        _changes.value.forEach { arr.put(it.toJson()) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }

    private companion object {
        const val MAX = 2_000
    }
}
