package com.rork.cipher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.rork.cipher.CipherApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Answers a message without opening Cipher.
 *
 * The words typed in the shade are handed straight to the repository, which
 * seals them on this phone exactly as the conversation would — nothing typed
 * here takes a shortcut around the encryption. Android keeps a broadcast alive
 * only briefly, so the send is waited on before the receiver lets go.
 */
class ReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repository = (context.applicationContext as? CipherApplication)?.repository ?: return
        val threadId = intent.getStringExtra(EXTRA_THREAD)?.takeIf { it.isNotEmpty() } ?: return
        val title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotEmpty() } ?: "@$threadId"

        when (intent.action) {
            ACTION_READ -> repository.readFromNotification(threadId)

            ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_REPLY)
                    ?.toString()
                    ?.trim()
                    .orEmpty()
                if (text.isEmpty()) return
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    val sent = runCatching {
                        withTimeoutOrNull(WORK_WINDOW_MS) {
                            repository.replyFromNotification(threadId, text)
                        }
                    }.onFailure { Log.w(TAG, "reply could not be sent") }
                        .getOrNull() ?: false
                    repository.reportReply(threadId, title, text, sent)
                    runCatching { pending.finish() }
                }
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "com.rork.cipher.action.REPLY"
        const val ACTION_READ = "com.rork.cipher.action.MARK_READ"
        const val EXTRA_THREAD = "thread"
        const val EXTRA_TITLE = "title"

        /** Where the typed reply arrives in the broadcast. */
        const val KEY_REPLY = "cipher.reply"

        private const val TAG = "ReplyReceiver"

        /** Comfortably inside the window Android gives a background broadcast. */
        private const val WORK_WINDOW_MS = 10_000L
    }
}
