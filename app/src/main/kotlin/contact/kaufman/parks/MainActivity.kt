package contact.kaufman.parks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import contact.kaufman.parks.ui.dashboard.DashboardScreen
import contact.kaufman.parks.ui.theme.ParksTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ParksTheme {
                DashboardScreen(onParkClick = { /* park detail lands next */ })
            }
        }
    }
}
