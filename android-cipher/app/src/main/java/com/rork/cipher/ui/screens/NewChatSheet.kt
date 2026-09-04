package com.rork.cipher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.cipher.data.DirectoryUser
import com.rork.cipher.data.Invite
import com.rork.cipher.data.Invites
import com.rork.cipher.ui.CipherViewModel
import com.rork.cipher.ui.components.IdenticonAvatar
import com.rork.cipher.ui.components.MonoKeyText
import com.rork.cipher.ui.components.OnlineDot
import com.rork.cipher.ui.theme.Canvas
import com.rork.cipher.ui.theme.Hairline
import com.rork.cipher.ui.theme.SignalGreen
import com.rork.cipher.ui.theme.SoftMint
import com.rork.cipher.ui.theme.SurfaceElevated
import com.rork.cipher.ui.theme.TextPrimary
import com.rork.cipher.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/** Start a thread by looking a username up on the hub — the only discovery path. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatSheet(
    viewModel: CipherViewModel,
    onDismiss: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val online by viewModel.onlinePeers.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<DirectoryUser>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val trimmed = query.removePrefix("@").trim().lowercase()
        if (trimmed.length < 2) {
            matches = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(280)
        matches = viewModel.search(trimmed)
        searching = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceElevated
    ) {
        Column(modifier = Modifier.imePadding()) {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    text = "New chat",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Usernames are exact. Paste an invite link, or scan theirs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(27.dp))
                        .background(Canvas)
                        .border(1.dp, Hairline, RoundedCornerShape(27.dp))
                        .padding(horizontal = 18.dp)
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = SignalGreen)
                    Spacer(Modifier.width(12.dp))
                    Text(text = "@", color = SoftMint, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(4.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = "username",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { raw ->
                                query = Invites.usernameFrom(raw)
                                    ?: raw.filter { c -> !c.isWhitespace() }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                            cursorBrush = SolidColor(SignalGreen),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                imeAction = ImeAction.Search
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searching) {
                        CircularProgressIndicator(
                            color = SignalGreen,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                // Face-to-face discovery: no label, just the glyph, so the
                // sheet still reads as one search field.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Canvas)
                        .border(1.dp, Hairline, CircleShape)
                        .clickable { scanning = true }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QrCodeScanner,
                        contentDescription = "Scan their QR code",
                        tint = SignalGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Your own notebook, one tap away and never mixed in with people.
            if (query.removePrefix("@").trim().length < 2) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openNotes()?.let(onOpenChat) }
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Canvas)
                            .border(1.dp, SignalGreen.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BookmarkBorder,
                            contentDescription = null,
                            tint = SignalGreen,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Note to self",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "Keys, links and files, sealed to your own key",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            if (!searching && matches.isEmpty()) {
                Text(
                    text = if (query.removePrefix("@").trim().length < 2) {
                        "Type at least two characters of their username."
                    } else {
                        "No account registered as @${query.removePrefix("@")}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(matches, key = { it.username }) { user ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat(user.username) }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Box {
                            IdenticonAvatar(username = user.username, size = 42.dp)
                            if (user.online || online.contains(user.username)) {
                                OnlineDot(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .border(2.dp, SurfaceElevated, CircleShape)
                                        .padding(1.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "@${user.username}",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(3.dp))
                            MonoKeyText(text = user.fingerprint)
                        }
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = SignalGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (scanning) {
        ScanSheet(
            onDismiss = { scanning = false },
            onScanned = { payload ->
                scanning = false
                when (val invite = Invites.parse(payload)) {
                    is Invite.User -> onOpenChat(invite.username)
                    is Invite.Verify -> onOpenChat(invite.username)
                    is Invite.Room -> viewModel.joinGroup(invite)?.let(onOpenChat)
                    null -> Unit
                }
            }
        )
    }
}
