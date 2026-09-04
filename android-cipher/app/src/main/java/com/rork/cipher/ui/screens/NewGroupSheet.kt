package com.rork.cipher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.cipher.data.Invites
import com.rork.cipher.ui.CipherViewModel
import com.rork.cipher.ui.components.IdenticonAvatar
import com.rork.cipher.ui.components.MonoKeyText
import com.rork.cipher.ui.theme.Canvas
import com.rork.cipher.ui.theme.Hairline
import com.rork.cipher.ui.theme.OnSignal
import com.rork.cipher.ui.theme.SignalGreen
import com.rork.cipher.ui.theme.SoftMint
import com.rork.cipher.ui.theme.SurfaceElevated
import com.rork.cipher.ui.theme.TextPrimary
import com.rork.cipher.ui.theme.TextSecondary

/**
 * Opens a room: a name and the people to seal it with. Members are picked from
 * the threads you already have, or typed in by exact username.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGroupSheet(
    viewModel: CipherViewModel,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit
) {
    val threads by viewModel.threads.collectAsStateWithLifecycle()
    val me = remember { viewModel.username().orEmpty() }
    val known = remember(threads) {
        threads.filterNot { it.isGroup }.map { it.peer }.filterNot { it == me }.distinct()
    }
    var name by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(setOf<String>()) }

    val addTyped: () -> Unit = {
        val username = Invites.usernameFrom(typed)
        if (username != null && username != me) {
            chosen = chosen + username
            typed = ""
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceElevated) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Groups, contentDescription = null, tint = SignalGreen)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "New room",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Everyone gets their own sealed copy of every message. The hub never " +
                    "sees a room, only unreadable one-to-one traffic.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(Modifier.height(18.dp))
            SheetField(
                value = name,
                onValueChange = { name = it.take(40) },
                placeholder = "Room name",
                mono = false
            )

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    SheetField(
                        value = typed,
                        onValueChange = { raw ->
                            typed = raw.filter { !it.isWhitespace() }
                        },
                        placeholder = "Add by username",
                        mono = true,
                        onSubmit = addTyped
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (typed.isBlank()) Canvas else SignalGreen)
                        .clickable(enabled = typed.isNotBlank(), onClick = addTyped)
                ) {
                    Icon(
                        Icons.Outlined.PersonAdd,
                        contentDescription = "Add username",
                        tint = if (typed.isBlank()) TextSecondary else OnSignal,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (chosen.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = chosen.sorted().joinToString(" \u00b7 ") { "@$it" },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = SoftMint
                )
            }

            if (known.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                MonoKeyText(text = "people you already talk to", color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.heightIn(max = 240.dp)
                ) {
                    items(known, key = { it }) { peer ->
                        MemberPick(
                            peer = peer,
                            selected = chosen.contains(peer),
                            onToggle = {
                                chosen = if (chosen.contains(peer)) chosen - peer
                                else chosen + peer
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            FilledTonalButton(
                onClick = {
                    val id = viewModel.createGroup(name, chosen.toList())
                    if (id != null) onCreated(id)
                },
                enabled = chosen.isNotEmpty(),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = SignalGreen,
                    contentColor = OnSignal,
                    disabledContainerColor = Canvas,
                    disabledContentColor = TextSecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = when (chosen.size) {
                        0 -> "Add someone first"
                        1 -> "Create room with 1 person"
                        else -> "Create room with ${chosen.size} people"
                    },
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

@Composable
private fun MemberPick(
    peer: String,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) SignalGreen.copy(alpha = 0.12f) else Canvas)
            .border(
                width = 1.dp,
                color = if (selected) SignalGreen else Hairline,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        IdenticonAvatar(username = peer, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = "@$peer",
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = SignalGreen,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SheetField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    mono: Boolean,
    modifier: Modifier = Modifier,
    onSubmit: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Canvas)
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
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
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = TextPrimary,
                    fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
                ),
                cursorBrush = SolidColor(SignalGreen),
                keyboardOptions = KeyboardOptions.Default,
                singleLine = true,
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { onSubmit?.invoke() }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
