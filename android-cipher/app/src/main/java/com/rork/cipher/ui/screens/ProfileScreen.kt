package com.rork.cipher.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.cipher.data.ConnectionState
import com.rork.cipher.data.Invites
import com.rork.cipher.data.LockMode
import com.rork.cipher.ui.CipherViewModel
import com.rork.cipher.ui.DeepLinks
import com.rork.cipher.ui.burnLabel
import com.rork.cipher.ui.components.IdenticonAvatar
import com.rork.cipher.ui.components.MonoKeyText
import com.rork.cipher.ui.theme.Canvas
import com.rork.cipher.ui.theme.DarkPalette
import com.rork.cipher.ui.theme.Hairline
import com.rork.cipher.ui.theme.LightPalette
import com.rork.cipher.ui.theme.Palette
import com.rork.cipher.ui.theme.OnSignal
import com.rork.cipher.ui.theme.SignalGreen
import com.rork.cipher.ui.theme.SoftMint
import com.rork.cipher.ui.theme.SurfaceElevated
import com.rork.cipher.ui.theme.TextPrimary
import com.rork.cipher.ui.theme.TextSecondary
import com.rork.cipher.ui.theme.WarningRed

private val BURN_CHOICES = listOf<Int?>(null, 60, 60 * 24, 60 * 24 * 7)

/** Identity, account key and every preference — plus the two ways out. */
@Composable
fun ProfileScreen(
    viewModel: CipherViewModel,
    username: String,
    contentPadding: PaddingValues,
    onOpenKey: () -> Unit,
    onOpenLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val threads by viewModel.threads.collectAsStateWithLifecycle()
    val blocked by viewModel.blocked.collectAsStateWithLifecycle()
    val pushReady by viewModel.pushReady.collectAsStateWithLifecycle()
    val systemBlocked = notificationsBlockedBySystem()

    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }

    val inviteLink = remember(username) { Invites.userLink(username) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Canvas)
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IdenticonAvatar(username = username, size = 64.dp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "@$username",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                MonoKeyText(text = "key · ${viewModel.myKeyFingerprint() ?: "sealed"}")
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, SignalGreen.copy(alpha = 0.45f), RoundedCornerShape(13.dp))
                    .clickable { showQr = true }
            ) {
                Icon(
                    Icons.Outlined.QrCode2,
                    contentDescription = "Show my invite code",
                    tint = SignalGreen,
                    modifier = Modifier.size(21.dp)
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        ConnectionLine(state = connection, threadCount = threads.count { it.messages.isNotEmpty() })

        Spacer(Modifier.height(26.dp))
        GroupLabel("SECURITY")
        Spacer(Modifier.height(12.dp))
        SettingsCard {
            NavRow(
                title = "Account key",
                body = "Hidden behind a blur · tap to view, copy or save it",
                icon = Icons.Outlined.Key,
                onClick = onOpenKey
            )
            CardDivider()
            NavRow(
                title = "App lock",
                body = if (settings.lockMode == LockMode.PIN) {
                    if (settings.biometricUnlock) "PIN · biometrics can open it"
                    else "PIN required every time Cipher opens"
                } else {
                    "Add a PIN or biometrics before Cipher will open"
                },
                icon = Icons.Outlined.Lock,
                onClick = onOpenLock
            )
        }

        Spacer(Modifier.height(30.dp))
        GroupLabel("PRIVACY")
        Spacer(Modifier.height(12.dp))
        SettingsCard {
            SettingSwitch(
                title = "Read receipts",
                body = "Let people see when you have opened their message",
                checked = settings.receipts,
                onCheckedChange = { value ->
                    viewModel.updateSettings { it.copy(receipts = value) }
                }
            )
            CardDivider()
            SettingSwitch(
                title = "Typing indicator",
                body = "Show typing dots while you write",
                checked = settings.typing,
                onCheckedChange = { value ->
                    viewModel.updateSettings { it.copy(typing = value) }
                }
            )
            CardDivider()
            SettingSwitch(
                title = "Show me as online",
                body = "Your green dot appears for people you talk to",
                checked = settings.presence,
                onCheckedChange = { value ->
                    viewModel.updateSettings { it.copy(presence = value) }
                }
            )
            CardDivider()
            SettingSwitch(
                title = "Anyone can message me",
                body = if (settings.strangers) "Any username can start a thread with you"
                else "Only people you have messaged first can reach you",
                checked = settings.strangers,
                onCheckedChange = { value ->
                    viewModel.updateSettings { it.copy(strangers = value) }
                }
            )
        }

        Spacer(Modifier.height(30.dp))
        GroupLabel("APPEARANCE")
        Spacer(Modifier.height(12.dp))
        SettingsCard {
            val mode = when {
                settings.themeFollowsSystem -> ThemeMode.AUTO
                settings.darkTheme -> ThemeMode.NIGHT
                else -> ThemeMode.DAY
            }
            ThemePicker(
                mode = mode,
                onPick = { picked ->
                    if (picked != mode) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.updateSettings {
                            it.copy(
                                darkTheme = picked != ThemeMode.DAY,
                                themeFollowsSystem = picked == ThemeMode.AUTO
                            )
                        }
                    }
                }
            )
        }

        Spacer(Modifier.height(26.dp))
        GroupLabel("THIS DEVICE")
        Spacer(Modifier.height(12.dp))
        SettingsCard {
            SettingSwitch(
                title = "Notifications",
                body = "Alert me when a new message arrives",
                checked = settings.notifications,
                onCheckedChange = { value ->
                    viewModel.updateSettings { it.copy(notifications = value) }
                }
            )
            CardDivider()
            // Cipher's own switch means nothing while Android is refusing the
            // alerts, and that refusal is invisible from inside the app — so it
            // is said plainly here, with the way to undo it one tap away.
            if (settings.notifications && systemBlocked) {
                NavRow(
                    title = "Android is blocking these alerts",
                    body = "Notifications are turned off for Cipher in system settings",
                    icon = Icons.Outlined.NotificationsOff,
                    tint = WarningRed,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        openNotificationSettings(context)
                    }
                )
                CardDivider()
            }
            // Decryption happens on this phone, so the words can be printed in
            // the alert. It is still a switch, because a notification is
            // readable by anyone holding the handset.
            if (settings.notifications) {
                SettingSwitch(
                    title = "Show message text",
                    body = "Print what was written in the notification. Off shows only who wrote. Hidden messages stay hidden either way, and a locked screen never shows the text.",
                    checked = settings.notificationPreview,
                    onCheckedChange = { value ->
                        viewModel.updateSettings { it.copy(notificationPreview = value) }
                    }
                )
                CardDivider()
            }
            // The fallback only exists on a phone Cipher cannot be woken on.
            // Everywhere else, showing a switch for something that is already
            // handled would be offering a worse option for no reason.
            if (!pushReady) {
                SettingSwitch(
                    title = "Keep app active",
                    body = "This phone has no push channel, so messages only arrive while Cipher is open. Turning this on holds the encrypted connection open instead \u2014 Android shows a permanent notice while it runs.",
                    checked = settings.keepActive,
                    onCheckedChange = { value ->
                        viewModel.updateSettings { it.copy(keepActive = value) }
                    }
                )
                CardDivider()
            }
            SettingSwitch(
                title = "Block screenshots",
                body = "Hide Cipher from screenshots and the app switcher",
                checked = settings.blockScreenshots,
                onCheckedChange = { value ->
                    viewModel.updateSettings { it.copy(blockScreenshots = value) }
                }
            )
            CardDivider()
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    text = "Default disappearing timer",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Applied to new conversations you start",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BURN_CHOICES.forEach { option ->
                        BurnChip(
                            label = option?.let { burnLabel(it) } ?: "Off",
                            selected = settings.defaultBurnMinutes == option,
                            onClick = {
                                viewModel.updateSettings { it.copy(defaultBurnMinutes = option) }
                            }
                        )
                    }
                }
            }
        }

        if (blocked.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            GroupLabel("BLOCKED")
            Spacer(Modifier.height(12.dp))
            SettingsCard {
                blocked.sorted().forEachIndexed { index, peer ->
                    if (index > 0) CardDivider()
                    BlockedRow(
                        peer = peer,
                        fingerprint = viewModel.keyFingerprint(peer),
                        onUnblock = { viewModel.setBlocked(peer, false) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Blocked accounts cannot message you, see your dot or your typing.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        Spacer(Modifier.height(30.dp))
        OutlinedButton(
            onClick = { confirmSignOut = true },
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("Log out")
        }

        Spacer(Modifier.height(6.dp))
        TextButton(
            onClick = { confirmDelete = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Outlined.DeleteForever,
                contentDescription = null,
                tint = WarningRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = "Delete account permanently", color = WarningRed)
        }
        Spacer(Modifier.height(40.dp))
    }

    if (showQr) {
        InviteQrSheet(
            title = "@$username",
            caption = "Hold this up. The other phone scans it and lands straight in an " +
                "encrypted thread with you — no numbers, no address book.",
            link = inviteLink,
            shareText = "Message me privately on Cipher — I am @$username.",
            onDismiss = { showQr = false },
            onScan = {
                showQr = false
                scanning = true
            }
        )
    }

    if (scanning) {
        ScanSheet(
            onDismiss = { scanning = false },
            onScanned = { payload ->
                scanning = false
                DeepLinks.offer(payload)
            }
        )
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = "Log out of @$username?",
            body = "Everything on this phone is erased. Your account and its history stay on Cipher — sign back in with your account key to restore them. Without that key it is gone for good.",
            confirmLabel = "Log out and wipe",
            confirmColor = TextPrimary,
            onConfirm = {
                confirmSignOut = false
                viewModel.signOut()
            },
            onDismiss = { confirmSignOut = false }
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete @$username forever?",
            body = "The username is released, every stored message is destroyed and the account key stops working. This cannot be undone.",
            confirmLabel = "Delete everything",
            confirmColor = WarningRed,
            onConfirm = {
                confirmDelete = false
                viewModel.deleteAccount()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

/** Which palette the interface is painted in. */
enum class ThemeMode { NIGHT, DAY, AUTO }

/**
 * Night, daylight or whatever Android is doing — picked from live miniatures of
 * the interface rather than a bare switch, so you see the palette before you
 * commit to it.
 */
@Composable
private fun ThemePicker(
    mode: ThemeMode,
    onPick: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val systemPalette = if (isSystemInDarkTheme()) DarkPalette else LightPalette
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            text = "Theme",
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when (mode) {
                ThemeMode.NIGHT -> "Every screen, sheet and bubble, painted dark"
                ThemeMode.DAY -> "Every screen, sheet and bubble, painted for daylight"
                ThemeMode.AUTO -> "Follows your phone's own light and dark setting"
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeTile(
                label = "Night",
                icon = Icons.Outlined.DarkMode,
                palette = DarkPalette,
                selected = mode == ThemeMode.NIGHT,
                onClick = { onPick(ThemeMode.NIGHT) },
                modifier = Modifier.weight(1f)
            )
            ThemeTile(
                label = "Day",
                icon = Icons.Outlined.LightMode,
                palette = LightPalette,
                selected = mode == ThemeMode.DAY,
                onClick = { onPick(ThemeMode.DAY) },
                modifier = Modifier.weight(1f)
            )
            ThemeTile(
                label = "Auto",
                icon = Icons.Outlined.BrightnessAuto,
                palette = systemPalette,
                selected = mode == ThemeMode.AUTO,
                onClick = { onPick(ThemeMode.AUTO) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** A postage-stamp Cipher thread painted in the palette it offers. */
@Composable
private fun ThemeTile(
    label: String,
    icon: ImageVector,
    palette: Palette,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val border by animateColorAsState(
        targetValue = if (selected) SignalGreen else Hairline,
        label = "themeBorder"
    )
    val width by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        label = "themeBorderWidth"
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(palette.canvas)
            .border(width, border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (selected) Icons.Outlined.Check else icon,
                contentDescription = if (selected) "Selected" else null,
                tint = palette.signal,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = palette.textPrimary,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(palette.surfaceElevated)
                .border(1.dp, palette.hairline, RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.height(5.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(palette.signal)
            )
        }
    }
}

/** A settings row that leads somewhere or fires an action, with a chevron. */
@Composable
private fun NavRow(
    title: String,
    body: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = SignalGreen
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
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** True when Android itself is dropping Cipher's notifications, re-checked on return. */
@Composable
private fun notificationsBlockedBySystem(): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var blocked by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle) {
        val refresh = {
            blocked = !NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        refresh()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return blocked
}

private fun openNotificationSettings(context: android.content.Context) {
    val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@Composable
private fun BlockedRow(
    peer: String,
    fingerprint: String?,
    onUnblock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            Icons.Outlined.Block,
            contentDescription = null,
            tint = WarningRed,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "@$peer",
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            MonoKeyText(text = fingerprint ?: "key not fetched")
        }
        Text(
            text = "Unblock",
            style = MaterialTheme.typography.labelLarge,
            color = SignalGreen,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onUnblock)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
        letterSpacing = 1.4.sp,
        modifier = modifier
    )
}

@Composable
private fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
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
private fun CardDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Hairline)
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnSignal,
                checkedTrackColor = SignalGreen,
                checkedBorderColor = SignalGreen,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Canvas,
                uncheckedBorderColor = Hairline
            )
        )
    }
}

@Composable
private fun BurnChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) SignalGreen else Canvas)
            .border(
                1.dp,
                if (selected) SignalGreen else Hairline,
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) OnSignal else TextPrimary
        )
    }
}

@Composable
private fun ConnectionLine(
    state: ConnectionState,
    threadCount: Int,
    modifier: Modifier = Modifier
) {
    val label = when (state) {
        ConnectionState.ONLINE -> "connected · live"
        ConnectionState.CONNECTING -> "connecting…"
        ConnectionState.OFFLINE -> "offline"
    }
    val tint = when (state) {
        ConnectionState.ONLINE -> SignalGreen
        ConnectionState.CONNECTING -> SoftMint
        ConnectionState.OFFLINE -> WarningRed
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceElevated)
            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(tint)
        )
        Spacer(Modifier.width(10.dp))
        MonoKeyText(text = label, color = tint)
        Spacer(Modifier.weight(1f))
        MonoKeyText(text = "$threadCount threads")
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    confirmColor: androidx.compose.ui.graphics.Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = confirmColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
