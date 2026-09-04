package com.rork.cipher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.cipher.data.Message
import com.rork.cipher.data.Thread
import com.rork.cipher.data.Verification
import com.rork.cipher.ui.CipherViewModel
import com.rork.cipher.ui.burnLabel
import com.rork.cipher.ui.components.IdenticonAvatar
import com.rork.cipher.ui.components.MonoKeyText
import com.rork.cipher.ui.components.PhotoThumbnail
import com.rork.cipher.ui.components.PhotoViewer
import com.rork.cipher.ui.relativeStamp
import com.rork.cipher.ui.theme.Canvas
import com.rork.cipher.ui.theme.Hairline
import com.rork.cipher.ui.theme.SignalGreen
import com.rork.cipher.ui.theme.SoftMint
import com.rork.cipher.ui.theme.SurfaceElevated
import com.rork.cipher.ui.theme.TextPrimary
import com.rork.cipher.ui.theme.TextSecondary
import com.rork.cipher.ui.theme.WarningRed

private val BURN_CHOICES = listOf<Int?>(null, 5, 60, 60 * 24, 60 * 24 * 7)

/**
 * The person behind a conversation: their key, what you have shared, and every
 * switch that governs the thread — reached by tapping the chat header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerProfileScreen(
    peer: String,
    viewModel: CipherViewModel,
    onBack: () -> Unit,
    onLeaveChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val threads by viewModel.threads.collectAsStateWithLifecycle()
    val onlinePeers by viewModel.onlinePeers.collectAsStateWithLifecycle()
    val blocked by viewModel.blocked.collectAsStateWithLifecycle()
    val verifiedPeers by viewModel.verified.collectAsStateWithLifecycle()
    val keyAlarms by viewModel.keyAlarms.collectAsStateWithLifecycle()

    val thread = remember(threads, peer) {
        threads.firstOrNull { it.peer == peer } ?: Thread(peer = peer, messages = emptyList())
    }
    val isBlocked = blocked.contains(peer)
    val isOnline = onlinePeers.contains(peer) && !isBlocked
    val photos = remember(thread.messages) { thread.photos.reversed() }

    var showBurn by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Message?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var showVerify by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Canvas,
                    titleContentColor = TextPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                title = { Text("Contact", style = MaterialTheme.typography.titleMedium) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box {
                    IdenticonAvatar(username = peer, size = 96.dp)
                    if (isOnline) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Canvas)
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(SignalGreen)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "@$peer",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        isBlocked -> "blocked"
                        isOnline -> "online now"
                        thread.lastActivity > 0L -> "last message ${relativeStamp(thread.lastActivity)}"
                        else -> "no messages yet"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        isBlocked -> WarningRed
                        isOnline -> SignalGreen
                        else -> TextSecondary
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
            KeyCard(
                fingerprint = viewModel.keyFingerprint(peer),
                state = when {
                    keyAlarms.contains(peer) -> Verification.CHANGED
                    !viewModel.knowsKey(peer) -> Verification.UNKNOWN
                    verifiedPeers.contains(peer) -> Verification.VERIFIED
                    else -> Verification.UNVERIFIED
                },
                onClick = { showVerify = true }
            )

            Spacer(Modifier.height(24.dp))
            GroupHeading("SHARED PHOTOS")
            Spacer(Modifier.height(12.dp))
            if (photos.isEmpty()) {
                Text(
                    text = "Nothing shared yet. Photos sent here are encrypted before they leave the phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(photos, key = { it.id }) { message ->
                        val ref = message.photo
                        if (ref != null) {
                            PhotoThumbnail(
                                ref = ref,
                                load = viewModel::photoBytes,
                                onClick = { viewing = message }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                MonoKeyText(text = "${photos.size} sealed · key never leaves this chat")
            }

            Spacer(Modifier.height(26.dp))
            GroupHeading("THIS CONVERSATION")
            Spacer(Modifier.height(12.dp))
            OptionCard {
                OptionRow(
                    icon = Icons.Outlined.PushPin,
                    label = if (thread.pinned) "Unpin from top" else "Pin to top",
                    trailing = if (thread.pinned) "on" else null,
                    onClick = { viewModel.togglePin(peer) }
                )
                RowDivider()
                OptionRow(
                    icon = Icons.Outlined.NotificationsOff,
                    label = if (thread.muted) "Unmute notifications" else "Mute notifications",
                    trailing = if (thread.muted) "muted" else null,
                    onClick = { viewModel.toggleMute(peer) }
                )
                RowDivider()
                OptionRow(
                    icon = Icons.Outlined.Timer,
                    label = "Disappearing messages",
                    trailing = thread.burnMinutes?.let(::burnLabel) ?: "off",
                    onClick = { showBurn = true }
                )
            }

            Spacer(Modifier.height(20.dp))
            GroupHeading("SAFETY")
            Spacer(Modifier.height(12.dp))
            OptionCard {
                OptionRow(
                    icon = if (isBlocked) Icons.Outlined.LockOpen else Icons.Outlined.Block,
                    label = if (isBlocked) "Unblock @$peer" else "Block @$peer",
                    tint = if (isBlocked) TextPrimary else WarningRed,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.setBlocked(peer, !isBlocked)
                    }
                )
                RowDivider()
                OptionRow(
                    icon = Icons.Outlined.Delete,
                    label = "Clear messages",
                    tint = WarningRed,
                    onClick = { confirmClear = true }
                )
                RowDivider()
                OptionRow(
                    icon = Icons.Outlined.DeleteForever,
                    label = "Delete this chat",
                    tint = WarningRed,
                    onClick = {
                        viewModel.deleteThread(peer)
                        onLeaveChat()
                    }
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Blocking is enforced by the hub: no messages, no typing, no online dot — in either direction.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showVerify) {
        VerifySheet(peer = peer, viewModel = viewModel, onDismiss = { showVerify = false })
    }

    val viewed = viewing
    val viewedPhoto = viewed?.photo
    if (viewed != null && viewedPhoto != null) {
        PhotoViewer(
            ref = viewedPhoto,
            caption = viewed.text,
            load = viewModel::photoBytes,
            onSave = { viewModel.savePhoto(viewedPhoto) },
            onDismiss = { viewing = null }
        )
    }

    if (confirmClear) {
        ModalBottomSheet(
            onDismissRequest = { confirmClear = false },
            containerColor = SurfaceElevated
        ) {
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 40.dp)) {
                Text(
                    text = "Clear every message with @$peer?",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "They disappear from this phone only. Anything already delivered stays on their device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Clear on this device",
                    style = MaterialTheme.typography.titleSmall,
                    color = WarningRed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, WarningRed, RoundedCornerShape(14.dp))
                        .clickable {
                            viewModel.clearMessages(peer)
                            confirmClear = false
                        }
                        .padding(vertical = 14.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    if (showBurn) {
        ModalBottomSheet(
            onDismissRequest = { showBurn = false },
            containerColor = SurfaceElevated
        ) {
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 40.dp)) {
                Text(
                    text = "Disappearing messages",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Spacer(Modifier.height(16.dp))
                BURN_CHOICES.forEach { option ->
                    val selected = thread.burnMinutes == option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.setBurn(peer, option)
                                showBurn = false
                            }
                            .padding(vertical = 14.dp, horizontal = 8.dp)
                    ) {
                        Text(
                            text = option?.let { "After ${burnLabel(it)}" } ?: "Off",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) SignalGreen else TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(
                                Icons.Outlined.DoneAll,
                                contentDescription = null,
                                tint = SignalGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The peer's real key, and whether anybody has ever checked it. */
@Composable
private fun KeyCard(
    fingerprint: String?,
    state: Verification,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when (state) {
        Verification.CHANGED -> WarningRed
        Verification.VERIFIED -> SignalGreen
        else -> TextSecondary
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceElevated)
            .border(
                width = 1.dp,
                color = if (state == Verification.CHANGED) WarningRed.copy(alpha = 0.5f)
                else Hairline,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (state == Verification.VERIFIED) SignalGreen.copy(alpha = 0.16f)
                    else Canvas
                )
        ) {
            Icon(
                imageVector = when (state) {
                    Verification.CHANGED -> Icons.Outlined.WarningAmber
                    Verification.VERIFIED -> Icons.Outlined.Verified
                    else -> Icons.Outlined.Shield
                },
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (state) {
                    Verification.CHANGED -> "Security code changed"
                    Verification.VERIFIED -> "Verified in person"
                    Verification.UNKNOWN -> "Key not fetched yet"
                    Verification.UNVERIFIED -> "Key exchanged, not verified"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (state == Verification.CHANGED) WarningRed else TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (state) {
                    Verification.CHANGED ->
                        "Their key is not the one this phone had. Check it again before writing."

                    Verification.VERIFIED ->
                        "This key was compared in person and has not changed since."

                    else -> "Tap to compare codes in person, or scan theirs."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = fingerprint ?: "—",
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                color = SoftMint
            )
        }
    }
}

@Composable
private fun GroupHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
        letterSpacing = 1.4.sp,
        modifier = modifier
    )
}

@Composable
private fun OptionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceElevated)
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
    ) {
        content()
    }
}

@Composable
private fun RowDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Hairline)
    )
}

@Composable
private fun OptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    tint: Color = TextPrimary
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (tint == TextPrimary) SignalGreen else tint,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Canvas)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = trailing,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = if (trailing == "off") TextSecondary else SoftMint
                )
            }
        }
    }
}
