package com.rork.cipher.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat

/**
 * Local notifications for messages that arrive while another screen is open.
 *
 * Bodies are decrypted on this phone and never travel through any push service,
 * so an alert may print what was written. The lock screen still gets a redacted
 * version, and previews can be switched off entirely in Settings.
 */
class Notifier(context: Context) {

    private val app = context.applicationContext
    private val manager = NotificationManagerCompat.from(app)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New encrypted messages"
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            manager.createNotificationChannel(channel)

            // Silent and low: the connection notice is a permanent fixture, so
            // it must never make a sound or push a message alert out of view.
            val connection = NotificationChannel(
                CONNECTION_CHANNEL_ID,
                "Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Cipher listening for messages"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            manager.createNotificationChannel(connection)
        }
    }

    /**
     * The ongoing notice Android requires in exchange for staying connected.
     *
     * It says what it is doing and nothing about any conversation.
     */
    fun connectionNotification(): Notification = NotificationCompat.Builder(
        app,
        CONNECTION_CHANNEL_ID
    )
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentTitle("Cipher is listening")
        .setContentText("Encrypted messages arrive while the app is closed")
        .setOngoing(true)
        .setSilent(true)
        .setShowWhen(false)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setVisibility(NotificationCompat.VISIBILITY_SECRET)
        .setContentIntent(openApp("connection"))
        .build()

    private fun openApp(tag: String): android.app.PendingIntent {
        val launch = Intent().apply {
            setClassName(app, "com.rork.cipher.MainActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return android.app.PendingIntent.getActivity(
            app,
            tag.hashCode(),
            launch,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun allowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * @param title who or which room wrote, already formatted for display.
     * @param threadId the thread this alert belongs to, so it can be cleared.
     * @param count how many arrived at once, for a backlog collected in one go.
     * @param lines what was written, newest last, already redacted for hidden
     *   messages and attachments. Empty when previews are switched off.
     * @param mention somebody wrote your name in a room, which is why this one
     *   is here even if the room is muted.
     * @param canReply true while the vault is open, which is the only state in
     *   which a reply typed here could actually be encrypted.
     */
    fun notifyMessage(
        title: String,
        threadId: String,
        count: Int = 1,
        lines: List<String> = emptyList(),
        mention: Boolean = false,
        canReply: Boolean = false
    ) {
        if (!allowed()) return
        val pending = openApp(threadId)
        val summary = when {
            lines.isNotEmpty() -> lines.last()
            count > 1 -> "$count encrypted messages · open Cipher to read"
            else -> "Encrypted message · open Cipher to read"
        }
        val builder = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(summary)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(redacted(count))
            .setContentIntent(pending)
        if (count > 1) builder.setNumber(count)
        // Says why a muted room is speaking up, before the words are read.
        if (mention) builder.setSubText("Mentioned you")
        when {
            // A pile-up reads as a conversation, not as one line with the rest
            // silently dropped.
            lines.size > 1 -> {
                val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
                lines.forEach { style.addLine(it) }
                if (count > lines.size) style.setSummaryText("+${count - lines.size} more")
                builder.setStyle(style)
            }
            lines.size == 1 -> builder.setStyle(
                NotificationCompat.BigTextStyle().bigText(lines.first())
            )
        }
        if (canReply) {
            builder.addAction(replyAction(title, threadId))
            builder.addAction(readAction(threadId))
        }
        runCatching { manager.notify(threadId.hashCode(), builder.build()) }
    }

    /**
     * The inline reply box.
     *
     * Android's generated "smart replies" are refused: they would hand the
     * decrypted words to a system model to be read, which is the one thing this
     * app exists to prevent.
     */
    private fun replyAction(
        title: String,
        threadId: String
    ): NotificationCompat.Action {
        val input = RemoteInput.Builder(ReplyReceiver.KEY_REPLY)
            .setLabel("Reply to $title")
            .build()
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            broadcast(ReplyReceiver.ACTION_REPLY, threadId, title, mutable = true)
        )
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
    }

    private fun readAction(threadId: String): NotificationCompat.Action =
        NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            "Mark as read",
            broadcast(ReplyReceiver.ACTION_READ, threadId, null, mutable = false)
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

    private fun broadcast(
        action: String,
        threadId: String,
        title: String?,
        mutable: Boolean
    ): PendingIntent {
        val intent = Intent(app, ReplyReceiver::class.java).apply {
            this.action = action
            setPackage(app.packageName)
            putExtra(ReplyReceiver.EXTRA_THREAD, threadId)
            title?.let { putExtra(ReplyReceiver.EXTRA_TITLE, it) }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(app, "$action:$threadId".hashCode(), intent, flags)
    }

    /**
     * Confirms a reply that was typed in the shade and actually left the phone.
     *
     * It replaces the alert it answered, stays silent and clears itself after a
     * few seconds: a sent message should not leave a second thing to dismiss.
     */
    fun notifyReplySent(threadId: String, title: String, text: String, preview: Boolean) {
        if (!allowed()) return
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(if (preview) "Sent · $text" else "Reply sent")
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(REPLY_NOTE_MS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(redacted(1))
            .setContentIntent(openApp(threadId))
            .build()
        runCatching { manager.notify(threadId.hashCode(), notification) }
    }

    /** A reply was typed while the vault was sealed, so nothing could be encrypted. */
    fun notifyReplyStuck(threadId: String, title: String, sealed: Boolean) {
        if (!allowed()) return
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(
                if (sealed) "Unlock Cipher to send that reply"
                else "That reply is still waiting to go out · open Cipher"
            )
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(redacted(1))
            .setContentIntent(openApp(threadId))
            .build()
        runCatching { manager.notify(threadId.hashCode(), notification) }
    }

    /**
     * The peer's key is not the one it was.
     *
     * Every other alert here is about words arriving; this one is about the
     * encryption itself, so it is loud, it is not silenceable by muting a chat,
     * and it never prints anything that was written.
     */
    fun notifyKeyChange(peer: String) {
        if (!allowed()) return
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("@$peer's security code changed")
            .setContentText("Verify it again before you send anything private")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Their key is not the one this phone had. That happens when " +
                        "somebody reinstalls Cipher — and it is also what reading " +
                        "your conversation would look like. Verify before you write."
                )
            )
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openApp("key:$peer"))
            .build()
        runCatching { manager.notify("key:$peer".hashCode(), notification) }
    }

    /** What a locked phone shows in place of the real alert. */
    private fun redacted(count: Int): Notification = NotificationCompat.Builder(app, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_chat)
        .setContentTitle("Cipher")
        .setContentText(
            if (count > 1) "$count new encrypted messages" else "New encrypted message"
        )
        .setAutoCancel(true)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .build()

    /**
     * A wake-up arrived while the vault was sealed.
     *
     * Nothing can be decrypted in this state, so the notice says only that
     * something is waiting — not who wrote, and certainly not what.
     */
    fun notifySealed() {
        if (!allowed()) return
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("New encrypted message")
            .setContentText("Unlock Cipher to read it")
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openApp("sealed"))
            .build()
        runCatching { manager.notify(SEALED_ID, notification) }
    }

    fun clear(peer: String) {
        runCatching { manager.cancel(peer.hashCode()) }
    }

    fun clearAll() {
        runCatching { manager.cancelAll() }
    }

    private companion object {
        const val CHANNEL_ID = "cipher.messages"
        const val CONNECTION_CHANNEL_ID = "cipher.connection"
        /** One slot for every sealed-vault alert, so a backlog is one line, not ten. */
        const val SEALED_ID = 9_001

        /** How long the "sent" confirmation stays in the shade. */
        const val REPLY_NOTE_MS = 6_000L
    }
}
