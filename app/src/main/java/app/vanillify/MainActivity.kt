package app.vanillify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.vanillify.ui.MainScreen
import app.vanillify.ui.StartAt
import app.vanillify.ui.Tab
import app.vanillify.ui.VanillifyTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Debug builds only: open a screen straight from adb, for store screenshots and checks
        // without tapping through the UI, e.g.
        // adb shell am start -n com.vanillify.app/app.vanillify.MainActivity --es tab apps --es sheet com.example
        val start = if (BuildConfig.DEBUG) StartAt(
            tab = intent.getStringExtra("tab")?.let { name -> Tab.entries.firstOrNull { it.name.equals(name, true) } },
            review = intent.getBooleanExtra("review", false),
            sheet = intent.getStringExtra("sheet"),
        ) else StartAt()
        setContent {
            VanillifyTheme {
                MainScreen(start = start)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from Shizuku or Android's settings: look again.
        graph.bridge.refresh()
    }
}
