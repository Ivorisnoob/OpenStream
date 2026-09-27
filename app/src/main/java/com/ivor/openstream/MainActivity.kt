package com.ivor.openstream

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ivor.openstream.presentation.navigation.AppNavigation
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.data.settings.ThemeMode
import com.ivor.openstream.presentation.shortcuts.AppShortcut
import com.ivor.openstream.presentation.shortcuts.ShortcutRequest
import com.ivor.openstream.ui.theme.OpenStreamTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appSettings: AppSettingsStore

    private var shortcutRequest by mutableStateOf<ShortcutRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request notification permission for Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                0
            )
        }

        AppShortcut.publish(this)
        // Only a fresh launch; a recreated activity already acted on its shortcut.
        if (savedInstanceState == null) {
            shortcutRequest = AppShortcut.from(intent)?.let(::ShortcutRequest)
        }

        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val settings by appSettings.settings.collectAsState()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            OpenStreamTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                // Most screens draw their own background without a Scaffold, so this root
                // Surface is what gives un-styled Text the theme's onBackground color.
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation(
                        windowSizeClass = windowSizeClass.widthSizeClass,
                        shortcutRequest = shortcutRequest,
                        onShortcutHandled = { shortcutRequest = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AppShortcut.from(intent)?.let { shortcutRequest = ShortcutRequest(it) }
    }
}
