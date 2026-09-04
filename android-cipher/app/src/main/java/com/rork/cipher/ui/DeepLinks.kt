package com.rork.cipher.ui

import android.net.Uri
import com.rork.cipher.data.Invite
import com.rork.cipher.data.Invites
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the invite the app was opened with — a personal link, a room link or a
 * scanned QR code — until the navigation graph is ready to act on it.
 */
object DeepLinks {

    private val _pending = MutableStateFlow<Invite?>(null)
    val pending: StateFlow<Invite?> = _pending.asStateFlow()

    fun offer(uri: Uri?) {
        _pending.value = Invites.parse(uri) ?: return
    }

    /** Accepts a raw string, as produced by the QR scanner. */
    fun offer(raw: String) {
        _pending.value = Invites.parse(raw) ?: return
    }

    fun consume() {
        _pending.value = null
    }
}
