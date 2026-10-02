package app.vanillify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.vanillify.ui.MainScreen
import app.vanillify.ui.VanillifyTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            VanillifyTheme {
                MainScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from Shizuku or Android's settings: look again.
        graph.bridge.refresh()
    }
}
