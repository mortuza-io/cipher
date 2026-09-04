package com.rork.cipher.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.rork.cipher.CipherApplication
import com.rork.cipher.data.CipherRepository
import com.rork.cipher.data.ClaimResult
import com.rork.cipher.data.ConnectionState
import com.rork.cipher.data.DirectoryUser
import com.rork.cipher.data.FileRef
import com.rork.cipher.data.Invite
import com.rork.cipher.data.Message
import com.rork.cipher.data.PhotoDraft
import com.rork.cipher.data.PhotoEdit
import com.rork.cipher.data.PhotoRef
import com.rork.cipher.data.PhotoSaveResult
import com.rork.cipher.data.Recording
import com.rork.cipher.data.ScheduledMessage
import com.rork.cipher.data.SessionState
import com.rork.cipher.data.Settings
import com.rork.cipher.data.Thread
import com.rork.cipher.data.UnlockResult
import com.rork.cipher.data.Verification
import com.rork.cipher.data.VoicePlayback
import com.rork.cipher.data.VoiceRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ChatFilter(val label: String) {
    ALL("All"), UNREAD("Unread"), PINNED("Pinned")
}

/** Shared view model over the live Cipher session. */
class CipherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CipherRepository =
        (application as CipherApplication).repository

    val session: StateFlow<SessionState> = repository.session
    val threads: StateFlow<List<Thread>> = repository.threads
    val typingPeers: StateFlow<Set<String>> = repository.typingPeers
    val onlinePeers: StateFlow<Set<String>> = repository.onlinePeers
    val connection: StateFlow<ConnectionState> = repository.connection
    val hasNetwork: StateFlow<Boolean> = repository.hasNetwork
    val settings: StateFlow<Settings> = repository.settings
    val blocked: StateFlow<Set<String>> = repository.blocked
    val scheduled: StateFlow<List<ScheduledMessage>> = repository.scheduled
    val lastError: StateFlow<String?> = repository.lastError

    /** True when the hub can wake this phone, so nothing has to run in the background. */
    val pushReady: StateFlow<Boolean> = repository.pushReady

    /** The single voice note that is open right now, wherever it lives. */
    val voicePlayback: StateFlow<VoicePlayback?> = repository.voicePlayback

    private val _chatFilter = MutableStateFlow(ChatFilter.ALL)
    val chatFilter: StateFlow<ChatFilter> = _chatFilter.asStateFlow()

    fun setChatFilter(value: ChatFilter) {
        _chatFilter.value = value
    }

    fun clearError() = repository.clearError()

    /** Probes or rebuilds the hub connection, e.g. when a screen comes back. */
    fun wake(reason: String) = repository.wake(reason)

    // ------------------------------------------------------------- discovery

    suspend fun search(query: String): List<DirectoryUser> = repository.search(query)

    suspend fun lookup(username: String): DirectoryUser? = repository.lookup(username)

    fun knowsKey(peer: String): Boolean = repository.knowsKey(peer)

    // --------------------------------------------------------------- session

    suspend fun createAccount(username: String): ClaimResult = repository.createAccount(username)

    suspend fun unlock(key: String): UnlockResult = repository.unlock(key)

    fun lockVault() = repository.lockVault()

    fun signOut() = repository.signOut()

    fun deleteAccount() = repository.deleteAccount()

    fun accountKey(): String? = repository.currentKey()

    // -------------------------------------------------------------- app lock

    fun setPin(pin: String): Boolean = repository.setPin(pin)

    fun verifyPin(pin: String): Boolean = repository.verifyPin(pin)

    fun unlockWithPin(pin: String): Boolean = repository.unlockWithPin(pin)

    fun unlockWithBiometric(): Boolean = repository.unlockWithBiometric()

    fun clearLock() = repository.clearLock()

    fun enableBiometricUnlock(): Boolean = repository.enableBiometricUnlock()

    fun disableBiometricUnlock() = repository.disableBiometricUnlock()

    fun username(): String? = repository.account()?.username

    fun updateSettings(transform: (Settings) -> Settings) = repository.updateSettings(transform)

    // -------------------------------------------------------------- messaging

    fun openThread(peer: String) = repository.openThread(peer)

    fun closeThread(peer: String) = repository.closeThread(peer)

    fun markRead(peer: String) = repository.markRead(peer)

    /**
     * @param secret hides the message behind noise until the reader taps it.
     * @param burnMinutes a self-destruct clock for this one message.
     */
    fun send(
        peer: String,
        text: String,
        replyTo: Message? = null,
        secret: Boolean = false,
        burnMinutes: Int? = null
    ) = repository.sendMessage(peer, text, replyTo, secret, burnMinutes)

    /** Queues a message to leave later; it waits encrypted on this device. */
    fun scheduleMessage(
        peer: String,
        text: String,
        at: Long,
        secret: Boolean = false,
        burnMinutes: Int? = null,
        replyTo: Message? = null
    ): Boolean = repository.scheduleMessage(peer, text, at, secret, burnMinutes, replyTo)

    fun cancelScheduled(id: String) = repository.cancelScheduled(id)

    fun sendScheduledNow(id: String) = repository.sendScheduledNow(id)

    /** Encrypts a picked image and sends it as an attachment. */
    fun sendPhoto(peer: String, uri: Uri, caption: String = "", replyTo: Message? = null) =
        repository.sendPhoto(peer, uri, caption, replyTo)

    /** Sends a batch picked and edited in the send preview, in the chosen order. */
    fun sendPhotos(
        peer: String,
        drafts: List<PhotoDraft>,
        caption: String = "",
        replyTo: Message? = null
    ) = repository.sendPhotos(peer, drafts, caption, replyTo)

    /** Downloads and decrypts an attachment; cached after the first read. */
    suspend fun photoBytes(ref: PhotoRef): ByteArray? = repository.photoBytes(ref)

    /** Renders a picked image the way it will be sent, for the send preview. */
    suspend fun photoPreview(uri: Uri, edit: PhotoEdit, maxEdge: Int = 1280): Bitmap? =
        repository.previewPhoto(uri, edit, maxEdge)

    /** Copies an open photo into the gallery. Refused for locked photos. */
    suspend fun savePhoto(ref: PhotoRef): PhotoSaveResult = repository.savePhotoToGallery(ref)

    /** Pull-to-refresh on Home: reconnect if needed, then ask for what was missed. */
    suspend fun resync(): Boolean = repository.resync()

    /** Encrypts a picked document and sends it as a file. */
    fun sendFile(peer: String, uri: Uri, replyTo: Message? = null) =
        repository.sendFile(peer, uri, replyTo)

    /** True when a file is already on this phone and opens without a download. */
    fun fileReady(ref: FileRef): Boolean = repository.fileReady(ref)

    /** Downloads, decrypts and hands a file to another app. */
    suspend fun openFile(ref: FileRef): Uri? = repository.openFile(ref)

    /** Downloads, decrypts and copies a file into Downloads. */
    suspend fun saveFile(ref: FileRef): Boolean = repository.saveFile(ref)

    /** Seals a finished recording and sends it as a voice message. */
    fun sendVoice(peer: String, recording: Recording, replyTo: Message? = null) =
        repository.sendVoice(peer, recording, replyTo)

    /** Plays, pauses or resumes a voice note, fetching it first if needed. */
    fun toggleVoice(ref: VoiceRef) = repository.toggleVoice(ref)

    /** @param fraction where in the open note to jump to, 0..1. */
    fun seekVoice(fraction: Float) = repository.seekVoice(fraction)

    fun stopVoice() = repository.stopVoice()

    /** Re-sends a message that never reached the hub. */
    fun resend(peer: String, messageId: String) = repository.resend(peer, messageId)

    fun react(peer: String, messageId: String, emoji: String) =
        repository.react(peer, messageId, emoji)

    fun unsend(peer: String, messageId: String) = repository.unsend(peer, messageId)

    fun deleteMessage(peer: String, messageId: String) =
        repository.deleteMessageLocally(peer, messageId)

    /** Puts a self-destruct clock on one message, mirrored to the other device. */
    fun burnMessage(peer: String, messageId: String, minutes: Int) =
        repository.burnMessage(peer, messageId, minutes)

    /** Arms a message to burn once the other side has actually opened it. */
    fun burnOnRead(peer: String, messageId: String, afterMs: Long) =
        repository.burnMessageOnRead(peer, messageId, afterMs)

    /** Sends a copy of a message into another thread. */
    fun forwardMessage(peer: String, messageId: String, target: String) =
        repository.forwardMessage(peer, messageId, target)

    /** Keeps one message at the top of the thread on this device, or clears it. */
    fun pinMessage(peer: String, messageId: String?) =
        repository.setPinnedMessage(peer, messageId)

    /** Rewrites one of my own messages on both sides, keeping the old version. */
    fun editMessage(peer: String, messageId: String, text: String) =
        repository.editMessage(peer, messageId, text)

    // ----------------------------------------------------------------- rooms

    fun thread(peer: String): Thread? = repository.thread(peer)

    /** @return the new room's thread id. */
    fun createGroup(name: String, members: List<String>): String? =
        repository.createGroup(name, members)

    fun addMembers(peer: String, users: List<String>) = repository.addMembers(peer, users)

    fun renameGroup(peer: String, name: String) = repository.renameGroup(peer, name)

    fun leaveGroup(peer: String) = repository.leaveGroup(peer)

    fun groupLink(peer: String): String? = repository.groupLink(peer)

    /** Joins a room from a scanned or tapped invite link. */
    fun joinGroup(invite: Invite.Room): String? = repository.joinGroup(invite)

    fun setDraft(peer: String, text: String) = repository.setDraft(peer, text)

    fun setTyping(peer: String, on: Boolean) = repository.setTyping(peer, on)

    fun togglePin(peer: String) = repository.togglePin(peer)

    fun toggleMute(peer: String) = repository.toggleMute(peer)

    fun setBurn(peer: String, minutes: Int?) = repository.setBurn(peer, minutes)

    fun clearMessages(peer: String) = repository.clearMessages(peer)

    fun deleteThread(peer: String) = repository.deleteThread(peer)

    // ----------------------------------------------------------- verification

    /** Peers whose key has been checked in person and has not changed since. */
    val verified: StateFlow<Set<String>> = repository.verified

    /** Peers whose key changed underneath us and who have not been looked at. */
    val keyAlarms: StateFlow<Set<String>> = repository.keyAlarms

    fun verification(peer: String): Verification = repository.verification(peer)

    /** The code both phones must show, in groups of four. */
    fun safetyNumber(peer: String): List<String>? = repository.safetyNumber(peer)

    /** The same code as one string, which is what a QR carries. */
    fun safetyCode(peer: String): String? = repository.safetyCode(peer)

    fun keyFingerprint(peer: String): String? = repository.keyFingerprint(peer)

    fun myKeyFingerprint(): String? = repository.myKeyFingerprint()

    /** Fetches the peer's key if this device has never seen it. */
    suspend fun ensureKey(peer: String): Boolean = repository.ensureKey(peer)

    fun markVerified(peer: String) = repository.markVerified(peer)

    fun clearVerified(peer: String) = repository.clearVerified(peer)

    fun dismissKeyAlarm(peer: String) = repository.dismissKeyAlarm(peer)

    // ---------------------------------------------------------------- notes

    /** True when this thread is the notebook addressed to yourself. */
    fun isSelf(peer: String): Boolean = repository.isSelf(peer)

    /** Opens (creating if needed) the conversation with your own account. */
    fun openNotes(): String? = repository.openNotes()

    // ----------------------------------------------------------------- blocks

    fun isBlocked(peer: String): Boolean = repository.isBlocked(peer)

    fun setBlocked(peer: String, on: Boolean) = repository.setBlocked(peer, on)
}
