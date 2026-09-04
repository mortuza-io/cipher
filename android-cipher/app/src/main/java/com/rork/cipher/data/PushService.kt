package com.rork.cipher.data

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rork.cipher.CipherApplication
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Receives the hub's wake-ups.
 *
 * The payload is deliberately empty of meaning: it carries the word "wake" and
 * nothing else — no sender, no thread, no text. Android starts this service,
 * Cipher opens its own encrypted connection, pulls whatever is waiting and
 * decrypts it on the phone. Only then does a notification get written, by the
 * app, from plaintext that never left the device.
 */
class PushService : FirebaseMessagingService() {

    private val repository: CipherRepository?
        get() = (application as? CipherApplication)?.repository

    override fun onNewToken(token: String) {
        // Tokens rotate on their own. A stale one at the hub means a silent
        // phone, so the new one is handed over the moment Android issues it.
        repository?.onPushToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val reason = message.data["r"] ?: "message"
        val repository = repository ?: return
        Log.d(TAG, "woken for $reason")
        // Android keeps a woken process alive only while this call is running.
        // Handing the work to a coroutine and returning would freeze the app
        // mid-reconnect, so the catch-up is waited on here instead — this
        // already runs on Firebase's own background thread.
        runBlocking {
            withTimeoutOrNull(WORK_WINDOW_MS) { repository.onPushWake() }
        }
    }

    private companion object {
        const val TAG = "PushService"

        /** Comfortably inside the window Android allows a high-priority wake-up. */
        const val WORK_WINDOW_MS = 15_000L
    }
}
