package com.rork.cipher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.cipher.data.Invites
import com.rork.cipher.ui.CipherViewModel
import com.rork.cipher.ui.components.IdenticonAvatar
import com.rork.cipher.ui.components.MonoKeyText
import com.rork.cipher.ui.components.OnlineDot
import com.rork.cipher.ui.theme.Canvas
import com.rork.cipher.ui.theme.Hairline
import com.rork.cipher.ui.theme.OnSignal
import com.rork.cipher.ui.theme.SignalGreen
import com.rork.cipher.ui.theme.SoftMint
import com.rork.cipher.ui.theme.SurfaceElevated
import com.rork.cipher.ui.theme.TextPrimary
import com.rork.cipher.ui.theme.TextSecondary
import com.rork.cipher.ui.theme.WarningRed

/** Room details: who is in it, how to invite the next person, and the way out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupProfileScreen(
    peer: String,
    viewModel: CipherViewModel,
    onBack: () -> Unit,
    onLeft: () -> Unit,
    modifier: Modifier = Modifier
) {
    val threads by viewModel.threads.collectAsStateWithLifecycle()
    val onlinePeers by viewModel.onlinePeers.collectAsStateWithLifecycle()
    val thread = remember(threads, peer) { threads.firstOrNull { it.peer == peer } }
    val group = thread?.group
    val me = remember { viewModel.username().orEmpty() }

    var renaming by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                title = { Text("Room", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showQr = true }) {
                        Icon(
                            Icons.Outlined.QrCode2,
                            contentDescription = "Room invite code",
                            tint = SignalGreen
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas)
            )
        }
    ) { padding ->
        if (group == null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                MonoKeyText(text = "this room is gone", color = TextSecondary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevated)
                        .border(1.dp, SignalGreen.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        Icons.Outlined.Groups,
                        contentDescription = null,
                        tint = SignalGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    MonoKeyText(
                        text = "${group.members.size} members \u00b7 room ${group.id.take(6)}"
                    )
                }
                Text(
                    text = "Rename",
                    style = MaterialTheme.typography.labelLarge,
                    color = SignalGreen,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { renaming = true }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, Hairline, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = SignalGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Every message is sealed once per member, over the same " +
                        "end-to-end channels as a private chat. The hub never learns that " +
                        "this room exists.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MEMBERS",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { adding = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Outlined.PersonAdd,
                        contentDescription = null,
                        tint = SignalGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Add people",
                        style = MaterialTheme.typography.labelLarge,
                        color = SignalGreen
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            ) {
                group.members.sorted().forEachIndexed { index, member ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Hairline)
                        )
                    }
                    MemberRow(
                        member = member,
                        isMe = member == me,
                        isAdmin = member == group.admin,
                        online = onlinePeers.contains(member),
                        fingerprint = if (member == me) viewModel.myKeyFingerprint()
                        else viewModel.keyFingerprint(member)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            OutlinedButton(
                onClick = { confirmLeave = true },
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Leave room")
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showQr && group != null) {
        InviteQrSheet(
            title = group.name,
            caption = "Anyone who scans this joins the room. The code carries a room " +
                "token, never a key \u2014 a member's device still has to let them in.",
            link = viewModel.groupLink(peer) ?: Invites.roomLink(
                group.id,
                group.token,
                group.name,
                me
            ),
            shareText = "Join ${group.name} on Cipher",
            onDismiss = { showQr = false }
        )
    }

    if (renaming && group != null) {
        TextPrompt(
            title = "Rename room",
            initial = group.name,
            placeholder = "Room name",
            confirmLabel = "Rename",
            mono = false,
            onConfirm = { value ->
                viewModel.renameGroup(peer, value)
                renaming = false
            },
            onDismiss = { renaming = false }
        )
    }

    if (adding) {
        TextPrompt(
            title = "Add someone",
            initial = "",
            placeholder = "username",
            confirmLabel = "Add",
            mono = true,
            onConfirm = { value ->
                val username = Invites.usernameFrom(value)
                if (username != null) viewModel.addMembers(peer, listOf(username))
                adding = false
            },
            onDismiss = { adding = false }
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = SurfaceElevated,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Leave this room?") },
            text = {
                Text(
                    "The others are told you left and this copy of the room is erased " +
                        "from your phone. You can be invited back with the room link."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        viewModel.leaveGroup(peer)
                        onLeft()
                    }
                ) {
                    Text("Leave", color = WarningRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun MemberRow(
    member: String,
    isMe: Boolean,
    isAdmin: Boolean,
    online: Boolean,
    fingerprint: String?,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        IdenticonAvatar(username = member, size = 38.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isMe) "you" else "@$member",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                if (isAdmin) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "opened it",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = SoftMint
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            MonoKeyText(text = fingerprint ?: "key not fetched")
        }
        if (online && !isMe) OnlineDot()
    }
}

/** A one-field dialog, used for renaming a room and adding a member. */
@Composable
private fun TextPrompt(
    title: String,
    initial: String,
    placeholder: String,
    confirmLabel: String,
    mono: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text(title) },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Canvas)
                    .border(1.dp, Hairline, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = { raw ->
                            value = if (mono) raw.filter { !it.isWhitespace() }.take(24)
                            else raw.take(40)
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = TextPrimary,
                            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
                        ),
                        cursorBrush = SolidColor(SignalGreen),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank()
            ) {
                Text(confirmLabel, color = if (value.isNotBlank()) SignalGreen else TextSecondary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
