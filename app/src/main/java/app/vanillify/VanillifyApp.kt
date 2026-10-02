package app.vanillify

import android.app.Application
import android.content.Context
import android.util.Log
import app.vanillify.catalog.Catalog
import app.vanillify.data.Journal
import app.vanillify.data.Protections
import app.vanillify.device.Apps
import app.vanillify.engine.Engine
import app.vanillify.shell.ShizukuBridge
import app.vanillify.shell.ShizukuState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VanillifyApp : Application() {

    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
        graph.start()
    }
}

val Context.graph: Graph get() = (applicationContext as VanillifyApp).graph

/** The app's long-lived parts, created once per process. */
class Graph(private val app: Application) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val bridge = ShizukuBridge(app)
    val apps = Apps(app)
    val journal = Journal(app)
    val protections = Protections(app)
    val engine = Engine(bridge, journal, protections, apps::state)

    /** ~5,400 entries; parsed once in the background. */
    val catalog: Deferred<Catalog> = scope.async(Dispatchers.IO) { Catalog.load(app) }

    private val _lastReapply = MutableStateFlow<Int?>(null)
    /** How many blocks the last re-apply had to put back; null until one ran in this process. */
    val lastReapply: StateFlow<Int?> = _lastReapply

    fun start() {
        bridge.start()
        // Whenever Shizuku (re)connects, e.g. after a reboot, put Android's forgotten blocks back.
        scope.launch {
            bridge.state.collect { state ->
                if (state == ShizukuState.READY) reapply()
            }
        }
    }

    suspend fun reapply() {
        if (protections.state.value.isEmpty) return
        val fixed = engine.reapply() ?: return
        _lastReapply.value = fixed
        Notifier.clearPaused(app)
        if (fixed > 0) Log.i("Vanillify", "re-applied $fixed protections")
    }
}
