package com.rork.cipher

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.cipher.data.CipherRepository
import com.rork.cipher.data.SessionState
import com.rork.cipher.ui.DeepLinks
import com.rork.cipher.ui.navigation.AppNavigation
import com.rork.cipher.ui.theme.AppTheme
import com.rork.cipher.ui.theme.DarkPalette
import com.rork.cipher.ui.theme.LightPalette

class MainActivity : FragmentActivity() {

    private lateinit var repository: CipherRepository

    /** When the app last left the screen, so the app lock knows how long it was away. */
    private var awaySince = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyBarStyle(dark = true)
        DeepLinks.offer(intent?.data)
        repository = (application as CipherApplication).repository
        setContent {
            val settings by repository.settings.collectAsStateWithLifecycle()
            val session by repository.session.collectAsStateWithLifecycle()
            LaunchedEffect(settings.blockScreenshots) {
                if (settings.blockScreenshots) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            val permission = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { }
            LaunchedEffect(session, settings.notifications) {
                val active = session is SessionState.Active
                if (active &&
                    settings.notifications &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            val dark = if (settings.themeFollowsSystem) isSystemInDarkTheme()
            else settings.darkTheme
            // The system bars follow the palette, so a light Cipher gets dark
            // status icons instead of leaving them invisible on paper.
            LaunchedEffect(dark) { applyBarStyle(dark) }

            AppTheme(dark = dark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (dark) DarkPalette.canvas else LightPalette.canvas
                ) {
                    AppNavigation()
                }
            }
        }
    }

    private fun applyBarStyle(dark: Boolean) {
        val scrim = (if (dark) DarkPalette.canvas else LightPalette.canvas).value.toInt()
        enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(scrim)
            else SystemBarStyle.light(scrim, scrim),
            navigationBarStyle = if (dark) SystemBarStyle.dark(scrim)
            else SystemBarStyle.light(scrim, scrim)
        )
    }

    /** Invite links tapped while Cipher is already running. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLinks.offer(intent.data)
    }

    override fun onStop() {
        super.onStop()
        awaySince = System.currentTimeMillis()
    }

    /** Re-seals the vault when the app has been off screen long enough. */
    override fun onStart() {
        super.onStart()
        val left = awaySince
        awaySince = 0L
        if (left > 0L && System.currentTimeMillis() - left > LOCK_AFTER_MS) {
            repository.lockForAppLock()
            return
        }
        // Coming back on screen never trusts the old socket: it is probed, and
        // rebuilt immediately if the hub stopped answering while we were away.
        repository.wake("foreground")
    }

    private companion object {
        const val LOCK_AFTER_MS = 30_000L
    }
}
