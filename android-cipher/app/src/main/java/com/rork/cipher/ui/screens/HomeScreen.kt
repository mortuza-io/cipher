package com.rork.cipher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.cipher.data.DirectoryUser
import com.rork.cipher.data.Thread
import com.rork.cipher.ui.CipherViewModel
import com.rork.cipher.ui.burnLabel
import com.rork.cipher.ui.components.CipherFilterChip
import com.rork.cipher.ui.components.ConnectionBanner
import com.rork.cipher.ui.components.DeliveryTicks
import com.rork.cipher.ui.components.EmptyState
import com.rork.cipher.ui.components.IdenticonAvatar
import com.rork.cipher.ui.components.MonoKeyText
import com.rork.cipher.ui.components.OnlineDot
import com.rork.cipher.ui.components.UnreadBadge
import com.rork.cipher.ui.ChatFilter
import com.rork.cipher.ui.relativeStamp
import com.rork.cipher.ui.theme.Canvas
import com.rork.cipher.ui.theme.Hairline
import com.rork.cipher.ui.theme.OnSignal
import com.rork.cipher.ui.theme.SignalGreen
import com.rork.cipher.ui.theme.SoftMint
import com.rork.cipher.ui.theme.SurfaceElevated
import com.rork.cipher.ui.theme.TextPrimary
import com.rork.cipher.ui.theme.TextSecondary
import com.rork.cipher.ui.theme.WarningRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Home: live username search on top, the people you talk to most, then every
 * conversation. Search results come from the hub — there is no local phone book.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: CipherViewModel,
    onOpenChat: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val threads by viewModel.threads.collectAsStateWithLifecycle()
    val online by viewModel.onlinePeers.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val hasNetwork by viewModel.hasNetwork.collectAsStateWithLifecycle()
    val filter by viewModel.chatFilter.collectAsStateWithLifecycle()
    val me = remember { viewModel.username().orEmpty() }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<DirectoryUser>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val pullState = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }
    var refreshNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        val trimmed = query.removePrefix("@").trim().lowercase()
        if (trimmed.length < 2) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(280)
        results = viewModel.search(trimmed)
        searching = false
    }

    val recents = remember(threads) {
        threads.filter { it.messages.isNotEmpty() && !it.isGroup }
            .sortedByDescending { it.lastActivity }
            .take(10)
            .map { it.peer }
    }
    val conversations = remember(threads, filter) {
        threads.filter { it.messages.isNotEmpty() }
            .filter {
                when (filter) {
                    ChatFilter.ALL -> true
                    ChatFilter.UNREAD -> it.unread > 0
                    ChatFilter.PINNED -> it.pinned
                }
            }
            .sortedWith(
                compareByDescending<Thread> { it.pinned }.thenByDescending { it.lastActivity }
            )
    }
    val searchMode = query.removePrefix("@").trim().length >= 2

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Canvas)
    ) {
        Spacer(Modifier.height(contentPadding.calculateTopPadding()))
        ConnectionBanner(state = connection, hasNetwork = hasNetwork)

        SearchField(
            query = query,
            onQueryChange = { query = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        AnimatedVisibility(visible = refreshNote != null, enter = fadeIn(), exit = fadeOut()) {
            MonoKeyText(
                text = refreshNote.orEmpty(),
                color = if (refreshNote?.startsWith("could") == true) WarningRed else SignalGreen,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
            )
        }

        // A pull is a demand, not a hint: it rebuilds the line to the hub when
        // the line is down and asks for everything missed either way.
        PullToRefreshBox(
            isRefreshing = refreshing,
            state = pullState,
            onRefresh = {
                if (!refreshing) {
                    refreshing = true
                    refreshNote = null
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        val ok = viewModel.resync()
                        refreshing = false
                        refreshNote = if (ok) "up to date" else "could not reach the hub"
                        delay(2_200L)
                        refreshNote = null
                    }
                }
            },
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = refreshing,
                    color = SignalGreen,
                    containerColor = SurfaceElevated,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            },
            modifier = Modifier.fillMaxSize()
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())
        ) {
            if (searchMode) {
                if (searching && results.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = SignalGreen,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                if (!searching && results.isEmpty()) {
                    item {
                        EmptyState(
                            title = "Nobody has that name",
                            body = "No account is registered as “${query.removePrefix("@")}”. Usernames are exact — ask for the spelling.",
                            footnote = "live lookup · nothing logged"
                        )
                    }
                }
                items(results, key = { it.username }) { user ->
                    UserRow(
                        user = user,
                        online = user.online || online.contains(user.username),
                        onClick = { onOpenChat(user.username) }
                    )
                    HorizontalDivider(
                        color = Hairline,
                        modifier = Modifier.padding(start = 76.dp, end = 20.dp)
                    )
                }
                return@LazyColumn
            }

            if (recents.isNotEmpty()) {
                item {
                    run {
                        Column {
                            Text(
                                text = "Recent",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                modifier = Modifier.padding(start = 20.dp, bottom = 12.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                items(recents, key = { it }) { peer ->
                                    RecentAvatar(
                                        peer = peer,
                                        online = online.contains(peer),
                                        onClick = { onOpenChat(peer) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                }
            }

            if (threads.any { it.messages.isNotEmpty() }) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 20.dp)
                    ) {
                        ChatFilter.entries.forEach { entry ->
                            CipherFilterChip(
                                label = entry.label,
                                selected = filter == entry,
                                onClick = { viewModel.setChatFilter(entry) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (conversations.isEmpty()) {
                item {
                    EmptyState(
                        title = when (filter) {
                            ChatFilter.ALL -> "No conversations yet"
                            ChatFilter.UNREAD -> "Nothing unread"
                            ChatFilter.PINNED -> "Nothing pinned"
                        },
                        body = "Type someone's exact username above. There is no directory to browse and no contact import.",
                        footnote = "end-to-end encrypted"
                    )
                }
            }

            items(conversations, key = { it.peer }) { thread ->
                ChatRow(
                    thread = thread,
                    online = online.contains(thread.peer) && thread.peer != me,
                    notes = me.isNotEmpty() && thread.peer == me,
                    onClick = { onOpenChat(thread.peer) },
                    onPin = { viewModel.togglePin(thread.peer) },
                    onMute = { viewModel.toggleMute(thread.peer) },
                    onBurn = { minutes -> viewModel.setBurn(thread.peer, minutes) },
                    onDelete = { viewModel.deleteThread(thread.peer) }
                )
                HorizontalDivider(
                    color = Hairline,
                    modifier = Modifier.padding(start = 76.dp, end = 20.dp)
                )
            }
        }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(SurfaceElevated)
            .border(1.dp, Hairline, RoundedCornerShape(28.dp))
            .padding(horizontal = 18.dp)
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = SignalGreen)
        Spacer(Modifier.width(14.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = "Find an exact username",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }
            BasicTextField(
                value = query,
                onValueChange = { onQueryChange(it.filter { c -> !c.isWhitespace() }) },
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
        if (query.isEmpty()) {
            Icon(Icons.Outlined.AlternateEmail, contentDescription = null, tint = SignalGreen)
        } else {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Clear search",
                tint = TextSecondary,
                modifier = Modifier.clickable { onQueryChange("") }
            )
        }
    }
}

@Composable
private fun RecentAvatar(
    peer: String,
    online: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box {
            IdenticonAvatar(username = peer, size = 56.dp)
            if (online) {
                OnlineDot(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .border(2.dp, Canvas, CircleShape)
                        .padding(1.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "@$peer",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun UserRow(
    user: DirectoryUser,
    online: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Box {
            IdenticonAvatar(username = user.username, size = 46.dp)
            if (online) {
                OnlineDot(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .border(2.dp, Canvas, CircleShape)
                        .padding(1.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "@${user.username}",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            MonoKeyText(text = "key · ${user.fingerprint}")
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .border(1.dp, SignalGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = "Key published",
                tint = SignalGreen,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
/** The "@" that says somebody wrote your name in this room. */
@Composable
private fun MentionBadge(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(SignalGreen)
    ) {
        Icon(
            imageVector = Icons.Outlined.AlternateEmail,
            contentDescription = "You were mentioned",
            tint = OnSignal,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun ChatRow(
    thread: Thread,
    online: Boolean,
    notes: Boolean,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onMute: () -> Unit,
    onBurn: (Int?) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    var burnOpen by remember { mutableStateOf(false) }
    val preview = thread.lastMessage

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .animateContentSize()
        ) {
            Box {
                if (thread.isGroup || notes) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevated)
                            .border(1.dp, SignalGreen.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (notes) Icons.Outlined.BookmarkBorder
                            else Icons.Outlined.Groups,
                            contentDescription = null,
                            tint = SignalGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    IdenticonAvatar(username = thread.peer, size = 46.dp)
                }
                if (online) {
                    OnlineDot(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .border(2.dp, Canvas, CircleShape)
                            .padding(1.dp)
                    )
                } else if (thread.pinned) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Canvas)
                            .border(1.dp, SignalGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = "Pinned",
                            tint = SignalGreen,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (notes) "Note to self" else thread.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    val room = thread.group
                    if (room != null) {
                        Spacer(Modifier.width(8.dp))
                        MonoKeyText(text = "${room.members.size}", color = TextSecondary)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (preview?.outgoing == true) {
                        DeliveryTicks(state = preview.state, pendingSince = preview.at)
                        Spacer(Modifier.width(5.dp))
                    }
                    val sender = preview?.from?.takeIf {
                        thread.isGroup && preview.outgoing.not() && !preview.system
                    }
                    Text(
                        text = when {
                            preview == null -> "encrypted thread ready"
                            sender != null -> "@$sender: ${preview.preview}"
                            else -> preview.preview
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (thread.unread > 0) TextPrimary else TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = relativeStamp(thread.lastActivity),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (thread.unread > 0) SignalGreen else TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                when {
                    // Being named outranks everything else this column can say:
                    // it is the one thing you scan a busy list for.
                    thread.mentioned -> Row(verticalAlignment = Alignment.CenterVertically) {
                        MentionBadge()
                        if (thread.unread > 0) {
                            Spacer(Modifier.width(6.dp))
                            UnreadBadge(count = thread.unread)
                        }
                    }
                    thread.unread > 0 -> UnreadBadge(count = thread.unread)
                    thread.muted -> Icon(
                        imageVector = Icons.Outlined.NotificationsOff,
                        contentDescription = "Muted",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    thread.burnMinutes != null -> Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Timer,
                            contentDescription = "Disappearing messages",
                            tint = SoftMint,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = burnLabel(thread.burnMinutes),
                            style = MaterialTheme.typography.labelSmall,
                            color = SoftMint
                        )
                    }
                    else -> Spacer(Modifier.height(18.dp))
                }
            }
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = SurfaceElevated
        ) {
            DropdownMenuItem(
                text = { Text(if (thread.pinned) "Unpin" else "Pin to top") },
                leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                onClick = {
                    onPin()
                    menuOpen = false
                }
            )
            DropdownMenuItem(
                text = { Text(if (thread.muted) "Unmute" else "Mute") },
                leadingIcon = { Icon(Icons.Outlined.NotificationsOff, contentDescription = null) },
                onClick = {
                    onMute()
                    menuOpen = false
                }
            )
            DropdownMenuItem(
                text = { Text("Disappearing messages") },
                leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    burnOpen = true
                }
            )
            DropdownMenuItem(
                text = { Text("Delete chat", color = WarningRed) },
                leadingIcon = {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = WarningRed)
                },
                onClick = {
                    onDelete()
                    menuOpen = false
                }
            )
        }

        DropdownMenu(
            expanded = burnOpen,
            onDismissRequest = { burnOpen = false },
            containerColor = SurfaceElevated
        ) {
            listOf<Int?>(null, 60, 60 * 24, 60 * 24 * 7).forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option?.let { "After ${burnLabel(it)}" } ?: "Off",
                            color = if (thread.burnMinutes == option) SignalGreen else TextPrimary
                        )
                    },
                    onClick = {
                        onBurn(option)
                        burnOpen = false
                    }
                )
            }
        }
    }
}
