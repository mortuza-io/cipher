package com.rork.cipher.data

import kotlinx.serialization.Serializable

@Serializable
data class Account(
    val username: String,
    val authDigest: String,
    val publicKey: String,
    val sealedPrivateKey: String,
    val createdAt: Long
)

/** A registered account returned by the live directory. */
data class DirectoryUser(
    val username: String,
    val publicKey: String,
    val online: Boolean,
    val createdAt: Long
) {
    /** Fingerprint of the account's real public key, not of its name. */
    val fingerprint: String get() = CryptoBox.fingerprint(publicKey)
}

/**
 * How far this device trusts the key it is encrypting to.
 *
 * `UNKNOWN` means the key has not been fetched yet, `CHANGED` that the peer's
 * key is not the one that was verified — the only state that raises an alarm.
 */
enum class Verification { UNKNOWN, UNVERIFIED, VERIFIED, CHANGED }

/**
 * Telegram-style receipt ladder. `PENDING` means the message has not reached the
 * hub yet (queued while offline), `SENT` that the hub stored it, `DELIVERED`
 * that the peer's device has it and `READ` that the peer opened the chat.
 */
enum class DeliveryState { PENDING, SENT, DELIVERED, READ }

enum class ConnectionState { OFFLINE, CONNECTING, ONLINE }

/** The whole room, written as `@everyone` (or `@all`). */
const val MENTION_ALL = "everyone"

/** Where one `@name` sits inside a message, and who it resolved to. */
data class MentionSpan(val start: Int, val end: Int, val name: String)

private val MENTION_TOKEN = Regex("@([A-Za-z0-9._]{2,21})")

/**
 * Where a room message calls people out.
 *
 * A token only counts when it names somebody actually in the roster, so an
 * email address or a lone "@" stays plain text. The match is trimmed from the
 * right until it resolves, because "@ada." ending a sentence still means ada,
 * and it is ignored when glued to a preceding word.
 */
fun mentionSpans(text: String, members: Collection<String>): List<MentionSpan> {
    if (members.isEmpty() || !text.contains('@')) return emptyList()
    val roster = members.mapTo(mutableSetOf()) { it.lowercase() }
    return MENTION_TOKEN.findAll(text).mapNotNull { match ->
        val at = match.range.first
        if (at > 0 && (text[at - 1].isLetterOrDigit() || text[at - 1] == '@')) {
            return@mapNotNull null
        }
        var token = match.groupValues[1].lowercase()
        while (token.length >= 2) {
            val resolved = when {
                token == MENTION_ALL || token == "all" -> MENTION_ALL
                roster.contains(token) -> token
                else -> null
            }
            if (resolved != null) {
                return@mapNotNull MentionSpan(at, at + 1 + token.length, resolved)
            }
            token = token.dropLast(1)
        }
        null
    }.toList()
}

/** True when this text calls out [me] by name, or calls the whole room. */
fun mentionsMe(text: String, members: Collection<String>, me: String): Boolean {
    val mine = me.lowercase()
    return mentionSpans(text, members).any { it.name == mine || it.name == MENTION_ALL }
}

/** How this device gates access to the vault when Cipher is reopened. */
@Serializable
enum class LockMode { NONE, PIN }

/**
 * An encrypted room shared by several accounts.
 *
 * The hub has no notion of a group: a group is a roster its members agree on,
 * and every message is fanned out over the existing pairwise encrypted
 * channels. `token` is the secret carried in the group's invite link and is what
 * a member checks before admitting a newcomer; `version` rises on every roster
 * change so the newest roster always wins.
 */
@Serializable
data class GroupInfo(
    val id: String,
    val name: String,
    val members: List<String>,
    val admin: String,
    val token: String,
    val version: Long = 1L,
    val createdAt: Long = 0L
) {
    fun others(me: String): List<String> = members.filterNot { it == me }
}

/** One superseded version of a message, kept so an edit can be audited. */
@Serializable
data class MessageEdit(val text: String, val at: Long)

/**
 * Everything needed to fetch and open one encrypted photo.
 *
 * `mediaKey` is a one-off AES key minted per photo and carried inside the
 * (already encrypted) message envelope, so the hub holds the ciphertext and
 * never the key. `thumb` is a tiny JPEG preview that rides along in the
 * envelope itself, so a bubble can render before the full image is downloaded.
 */
@Serializable
data class PhotoRef(
    val blob: String,
    val mediaKey: String,
    val width: Int,
    val height: Int,
    val thumb: String,
    /**
     * Sent locked: the photo may be looked at but not kept. It is never saved
     * to the gallery, never forwarded, and the window showing it is marked
     * secure so Android itself refuses the screenshot and the recent-apps
     * thumbnail.
     */
    val locked: Boolean = false
) {
    val aspect: Float get() = if (width > 0 && height > 0) width.toFloat() / height else 1f
}

/**
 * Everything needed to fetch and play one encrypted voice note.
 *
 * The same shape of secret as a photo: the audio is sealed under a one-off
 * media key that only ever travels inside the encrypted envelope. [levels] is
 * the waveform sampled while recording, so a bubble draws the shape of the
 * recording before a single byte of audio has been downloaded.
 */
@Serializable
data class VoiceRef(
    val blob: String,
    val mediaKey: String,
    val durationMs: Long,
    /** One bar per sample, 0..15. */
    val levels: List<Int> = emptyList()
) {
    val clock: String get() = formatDuration(durationMs)
}

/**
 * Everything needed to fetch and open one encrypted file.
 *
 * The same secret as a photo or a voice note: the bytes are sealed under a
 * one-off media key that only ever travels inside the encrypted envelope. The
 * name and type are part of that envelope too, so the hub stores an object it
 * cannot open and cannot even name.
 */
@Serializable
data class FileRef(
    val blob: String,
    val mediaKey: String,
    val name: String,
    val size: Long,
    val mime: String = ""
) {
    /** Uppercase extension used as the sheet on the file's glyph, e.g. `PDF`. */
    val kind: String
        get() = name.substringAfterLast('.', "").take(4).uppercase().ifEmpty { "FILE" }

    val readableSize: String get() = formatBytes(size)
}

/** Formats a byte count the way a file row prints it: `2.4 MB`. */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes.toDouble() / 1024.0
    var unit = 0
    while (value >= 1024.0 && unit < units.size - 1) {
        value /= 1024.0
        unit++
    }
    return if (value >= 10.0) "${value.toInt()} ${units[unit]}"
    else "${(Math.round(value * 10.0) / 10.0)} ${units[unit]}"
}

/** Formats a length of audio the way a voice note prints it: `m:ss`. */
fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000L).coerceAtLeast(0L)
    val tail = (seconds % 60).toString().padStart(2, '0')
    return "${seconds / 60}:$tail"
}

/**
 * Packs waveform levels one hex character per bar.
 *
 * The envelope is encrypted, base64'd and sent once per recipient, so a
 * compact waveform is worth the small loss of resolution.
 */
fun packLevels(levels: List<Int>): String =
    levels.joinToString("") { it.coerceIn(0, 15).toString(16) }

fun unpackLevels(packed: String): List<Int> =
    packed.mapNotNull { it.digitToIntOrNull(16) }

/**
 * The plaintext shape of a message body. It is serialised, encrypted and only
 * then handed to the hub, so quoted replies, photo keys, group rosters and
 * edits stay unreadable server-side too.
 *
 * Envelopes whose `kind` is a control kind ("edit", "react", "roster", "join",
 * "leave", "note") mutate an existing thread instead of adding a bubble.
 */
@Serializable
data class MessageEnvelope(
    val v: Int = 4,
    val text: String,
    val qid: String? = null,
    val q: String? = null,
    /** "photo" for an attachment, or one of the control kinds. */
    val kind: String? = null,
    val blob: String? = null,
    val mk: String? = null,
    val w: Int = 0,
    val h: Int = 0,
    val thumb: String? = null,
    /** Photo sent locked: look, but no saving, forwarding or screenshots. */
    val pl: Boolean = false,
    /** True when the sender marked this message as hidden until tapped. */
    val s: Boolean = false,
    /** Group id, when this payload belongs to a room rather than a pair. */
    val g: String? = null,
    /** Group name, roster, admin, token and roster version. */
    val gn: String? = null,
    val gm: List<String> = emptyList(),
    val ga: String? = null,
    val gt: String? = null,
    val gv: Long = 0L,
    /** Canonical id of a group message, shared by every fanned-out copy. */
    val mid: String? = null,
    /** Id of the message a control payload acts on. */
    val eid: String? = null,
    val eat: Long = 0L,
    /** Milliseconds a message survives once it has been read, or 0. */
    val bor: Long = 0L,
    /** Human-readable line for a room event, e.g. "ada joined". */
    val sys: String? = null,
    /** Voice note: length in milliseconds, and its packed waveform. */
    val vd: Long = 0L,
    val vl: String? = null,
    /** File: original name, byte count and media type. */
    val fn: String? = null,
    val fs: Long = 0L,
    val fm: String? = null
)

data class Message(
    val id: String,
    val text: String,
    val outgoing: Boolean,
    val at: Long,
    val state: DeliveryState = DeliveryState.SENT,
    /** Id of the message this one replies to. */
    val replyTo: String? = null,
    /** Snapshot of the quoted text, so the quote survives even if the original burns. */
    val replyText: String? = null,
    /** Wall-clock time this message self-destructs, or 0 when it never does. */
    val expiresAt: Long = 0L,
    /**
     * Burn after reading: how long this message survives once it has actually
     * been opened. The clock does not start until then, so an unread message
     * waits indefinitely and a read one is gone within [burnOnReadMs].
     */
    val burnOnReadMs: Long = 0L,
    /** Emoji per username. */
    val reactions: Map<String, String> = emptyMap(),
    /** Encrypted attachment, when this message is a photo. */
    val photo: PhotoRef? = null,
    /** Encrypted attachment, when this message is a voice note. */
    val voice: VoiceRef? = null,
    /** Encrypted attachment, when this message is a shared file. */
    val file: FileRef? = null,
    /** True while the attachment is still being encrypted and uploaded. */
    val uploading: Boolean = false,
    /** When the hub handed this message to the peer's device, or 0. */
    val deliveredAt: Long = 0L,
    /** When the peer opened the chat and read it, or 0. */
    val readAt: Long = 0L,
    /** Hidden message: scrambled on both sides until it is tapped. */
    val secret: Boolean = false,
    /** Who wrote it, in a group. Null in one-to-one threads. */
    val from: String? = null,
    /** Superseded versions of the text, oldest first. */
    val edits: List<MessageEdit> = emptyList(),
    /** When the text was last rewritten, or 0 when it never was. */
    val editedAt: Long = 0L,
    /** A roster change or other room event, rendered as a centred note. */
    val system: Boolean = false
) {
    val ephemeral: Boolean get() = expiresAt > 0L

    /** Armed to burn, but the clock has not started because nobody has read it. */
    val burnsOnRead: Boolean get() = burnOnReadMs > 0L && expiresAt <= 0L

    val edited: Boolean get() = editedAt > 0L

    /** True when the body is plain text that its author is allowed to rewrite. */
    val editable: Boolean
        get() = outgoing && !system && photo == null && voice == null && file == null && !uploading

    /** One-line summary for chat lists, quotes and notifications. */
    val preview: String
        get() = when {
            system -> text
            secret -> "Hidden message"
            voice != null -> "Voice message · ${voice.clock}"
            file != null -> "${file.kind} · ${file.name}"
            photo == null -> text
            text.isNotBlank() -> "${if (photo.locked) "Locked photo" else "Photo"} · $text"
            photo.locked -> "Locked photo"
            else -> "Photo"
        }
}

data class Thread(
    val peer: String,
    val messages: List<Message>,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val burnMinutes: Int? = null,
    val unread: Int = 0,
    /**
     * Somebody wrote your name in this room and you have not read it yet. Kept
     * apart from [unread] because a mention outranks a busy room: it survives
     * mute and is called out on its own in the chat list.
     */
    val mentioned: Boolean = false,
    val draft: String = "",
    /** Id of the message kept at the top of this thread, or null. */
    val pinnedId: String? = null,
    /** Set when this thread is a room instead of a pair. */
    val group: GroupInfo? = null
) {
    val lastMessage: Message? get() = messages.lastOrNull()
    val lastActivity: Long get() = lastMessage?.at ?: 0L
    val fingerprint: String get() = CryptoBox.fingerprint(peer)
    val photos: List<Message> get() = messages.filter { it.photo != null }

    /** The pinned message, if it is still around. */
    val pinnedMessage: Message? get() = pinnedId?.let { id -> messages.firstOrNull { it.id == id } }
    val isGroup: Boolean get() = group != null

    /** What a header or chat row prints for this thread. */
    val title: String get() = group?.name ?: "@$peer"
}

/**
 * The name a thread carries on screen. Identical to [Thread.title] except for
 * the thread addressed to yourself, which is a notebook rather than a chat.
 */
fun Thread.titleFor(me: String?): String = when {
    group != null -> group.name
    me != null && peer == me -> "Note to self"
    else -> "@$peer"
}

/**
 * User preferences. `receipts`, `typing`, `presence` and `strangers` are mirrored
 * to the hub because the server has to enforce them; the rest are device-local.
 */
@Serializable
data class Settings(
    val receipts: Boolean = true,
    val typing: Boolean = true,
    val presence: Boolean = true,
    val strangers: Boolean = true,
    val notifications: Boolean = true,
    /**
     * Prints what was written in the alert itself. Decryption happens on this
     * phone, so the text never leaves it — but a notification is readable by
     * anyone holding the handset, which is why it can be switched off.
     */
    val notificationPreview: Boolean = true,
    /**
     * Explicit opt-in to background delivery. The service that holds the
     * socket open costs a permanent notification, so it never runs unless the
     * user has asked for it here.
     */
    val keepActive: Boolean = false,
    val blockScreenshots: Boolean = false,
    val requireKeyOnOpen: Boolean = false,
    val defaultBurnMinutes: Int? = null,
    /** Screen lock for the app itself: a PIN, optionally opened by biometrics. */
    val lockMode: LockMode = LockMode.NONE,
    val biometricUnlock: Boolean = false,
    /** Paints the whole interface dark; off is Cipher in daylight. */
    val darkTheme: Boolean = true,
    /** Hands the choice to Android's own light/dark setting. */
    val themeFollowsSystem: Boolean = false
)

/**
 * A message written now and sent later.
 *
 * It never leaves this device until [at] passes: the hub is told nothing about
 * a message that has not been sent yet, so a queued message is invisible to
 * everyone, including the person it is addressed to.
 */
data class ScheduledMessage(
    val id: String,
    val peer: String,
    val text: String,
    /** Wall-clock time this message should leave. */
    val at: Long,
    val secret: Boolean = false,
    val burnMinutes: Int? = null,
    val replyTo: String? = null,
    val replyText: String? = null
)

/** Vault-encrypted form of a queued message: the text rests encrypted too. */
@Serializable
data class StoredScheduled(
    val id: String,
    val peer: String,
    val cipher: String,
    val at: Long,
    val secret: Boolean = false,
    val burnMinutes: Int? = null,
    val replyTo: String? = null,
    val replyCipher: String? = null
)

@Serializable
data class StoredMessage(
    val id: String,
    val cipher: String,
    val outgoing: Boolean,
    val at: Long,
    val state: String = DeliveryState.SENT.name,
    val replyTo: String? = null,
    val replyCipher: String? = null,
    val expiresAt: Long = 0L,
    val burnOnReadMs: Long = 0L,
    val reactionsCipher: String? = null,
    /** Vault-encrypted `PhotoRef`, so even the media key rests encrypted. */
    val photoCipher: String? = null,
    /** Vault-encrypted `VoiceRef`, for the same reason. */
    val voiceCipher: String? = null,
    /** Vault-encrypted `FileRef`, for the same reason. */
    val fileCipher: String? = null,
    val deliveredAt: Long = 0L,
    val readAt: Long = 0L,
    val secret: Boolean = false,
    val from: String? = null,
    /** Vault-encrypted list of superseded versions. */
    val editsCipher: String? = null,
    val editedAt: Long = 0L,
    val system: Boolean = false
)

@Serializable
data class StoredThread(
    val peer: String,
    val messages: List<StoredMessage> = emptyList(),
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val burnMinutes: Int? = null,
    val unread: Int = 0,
    val mentioned: Boolean = false,
    val draftCipher: String? = null,
    val pinnedId: String? = null,
    val group: GroupInfo? = null
)

@Serializable
data class Vault(
    val account: Account? = null,
    val threads: List<StoredThread> = emptyList(),
    val settings: Settings = Settings(),
    val lastSeq: Long = 0L,
    val peerKeys: Map<String, String> = emptyMap(),
    val blocked: List<String> = emptyList(),
    val scheduled: List<StoredScheduled> = emptyList(),
    /** Peer to the exact public key that was verified face to face. */
    val verifiedKeys: Map<String, String> = emptyMap(),
    /** Peers whose key changed and who have not been looked at since. */
    val keyAlarms: List<String> = emptyList()
)

/** Result of claiming a username on the hub. */
sealed interface ClaimResult {
    data class Success(val accountKey: String) : ClaimResult
    data object Taken : ClaimResult
    data class Failed(val message: String) : ClaimResult
}

/** Result of signing in with an account key. */
sealed interface UnlockResult {
    data object Success : UnlockResult
    data object WrongKey : UnlockResult
    data class Failed(val message: String) : UnlockResult
}
