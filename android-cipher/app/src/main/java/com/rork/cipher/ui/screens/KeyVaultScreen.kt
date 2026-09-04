package com.rork.cipher.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.cipher.data.CryptoBox
import com.rork.cipher.ui.components.MonoKeyText
import com.rork.cipher.ui.theme.Canvas
import com.rork.cipher.ui.theme.Hairline
import com.rork.cipher.ui.theme.SignalGreen
import com.rork.cipher.ui.theme.SoftMint
import com.rork.cipher.ui.theme.SurfaceElevated
import com.rork.cipher.ui.theme.TextPrimary
import com.rork.cipher.ui.theme.TextSecondary
import com.rork.cipher.ui.theme.WarningRed
import kotlinx.coroutines.delay

/** How long the key stays legible before it blurs itself again. */
private const val KEY_VISIBLE_MS = 20_000L

/**
 * The account key, kept behind a blur on its own page. Screenshots are blocked
 * while this screen is open and the key re-hides itself after a short window,
 * so it is never left sitting readable on a table.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyVaultScreen(
    username: String,
    accountKey: String?,
    blockScreenshots: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var revealed by remember { mutableStateOf(false) }
    var remaining by remember { mutableStateOf(KEY_VISIBLE_MS) }
    val rows = remember(accountKey) { accountKey?.let { CryptoBox.keyRows(it) }.orEmpty() }
    val saver = rememberKeyFileSaver(username, accountKey.orEmpty())

    val blurRadius by animateDpAsState(
        targetValue = if (revealed) 0.dp else 9.dp,
        animationSpec = tween(260),
        label = "keyBlur"
    )
    val keyTint by animateColorAsState(
        targetValue = if (revealed) SoftMint else SoftMint.copy(alpha = 0.55f),
        animationSpec = tween(260),
        label = "keyTint"
    )
    val progress by animateFloatAsState(
        targetValue = if (revealed) (remaining.toFloat() / KEY_VISIBLE_MS) else 0f,
        animationSpec = tween(240),
        label = "keyCountdown"
    )

    /**
     * Screenshot blocking follows the account setting. Forcing it on here would
     * also blank the page on hardware that cannot compose a secure surface —
     * including remote displays — which would hide the key from its owner.
     */
    DisposableEffect(blockScreenshots) {
        val window = (context as? Activity)?.window
        val flags = window?.attributes?.flags ?: 0
        val alreadySecure = flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        if (blockScreenshots && !alreadySecure) {
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        onDispose {
            if (blockScreenshots && !alreadySecure) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    LaunchedEffect(revealed) {
        if (!revealed) {
            remaining = KEY_VISIBLE_MS
            return@LaunchedEffect
        }
        while (remaining > 0L) {
            delay(1_000L)
            remaining -= 1_000L
        }
        revealed = false
    }

    Scaffold(
        modifier = modifier,
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                title = { Text("Account key", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas)
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
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(1.dp, SignalGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Key,
                        contentDescription = null,
                        tint = SignalGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = "@$username",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(3.dp))
                    MonoKeyText(text = CryptoBox.fingerprint(username))
                }
            }

            Spacer(Modifier.height(22.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, if (revealed) SignalGreen else Hairline, RoundedCornerShape(18.dp))
                    .clickable(enabled = rows.isNotEmpty()) { revealed = !revealed }
                    .padding(vertical = 26.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.blur(blurRadius)
                ) {
                    if (rows.isEmpty()) {
                        Text(
                            text = "Key unavailable on this device",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    rows.forEach { row ->
                        Text(
                            text = row,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            letterSpacing = 1.6.sp,
                            color = keyTint
                        )
                    }
                }
                if (!revealed && rows.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Canvas.copy(alpha = 0.86f))
                            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Visibility,
                            contentDescription = null,
                            tint = SignalGreen,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Tap to reveal",
                            style = MaterialTheme.typography.labelLarge,
                            color = SignalGreen
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (revealed) Icons.Outlined.Visibility
                    else Icons.Outlined.VisibilityOff,
                    contentDescription = null,
                    tint = if (revealed) SignalGreen else TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        revealed -> "hides again in ${remaining / 1000}s"
                        blockScreenshots -> "hidden · screenshots blocked"
                        else -> "hidden · turn on Block screenshots for more"
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = if (revealed) SignalGreen else TextSecondary
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Hairline)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(SignalGreen)
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = { accountKey?.let { copyKey(context, it) } },
                    enabled = accountKey != null,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SurfaceElevated,
                        contentColor = SoftMint
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Copy key")
                }
                OutlinedButton(
                    onClick = { saver() },
                    enabled = accountKey != null,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftMint),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Save as file")
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = WarningRed,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "This key is your account",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Anyone holding it becomes @$username and can read everything. " +
                            "There is no reset and no recovery — keep it offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
