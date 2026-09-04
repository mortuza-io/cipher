package com.rork.cipher.data

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.rork.cipher.CipherApplication

/**
 * Keeps Cipher's connection to the hub alive while the app is not on screen.
 *
 * Cipher has no push server standing between the two devices — that is the
 * point of it — so the only way a message can arrive while the app is closed
 * is for the encrypted socket itself to stay open. Android grants that to a
 * foreground service, which is why running it costs a permanent notification.
 *
 * The service holds no state of its own: the socket, the vault and the keys
 * all live in the single repository owned by the application object, so this is
 * purely a request to keep that process alive and connected.
 */
class DeliveryService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? CipherApplication
        if (app == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                app.repository.deliveryNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
            true
        }.getOrElse {
            Log.w(TAG, "could not go foreground: ${it.message}")
            false
        }
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        // A restarted process reconnects immediately rather than waiting out a
        // backoff it no longer remembers.
        app.repository.wake("service")
        return START_STICKY
    }

    /** Swiping Cipher away closes the window, not the connection. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        runCatching { (application as? CipherApplication)?.repository?.wake("task-removed") }
    }

    companion object {
        const val NOTIFICATION_ID = 4_711
        private const val TAG = "DeliveryService"
    }
}
