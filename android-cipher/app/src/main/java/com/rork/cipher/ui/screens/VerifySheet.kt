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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.cipher.data.Invite
import com.rork.cipher.data.Invites
import com.rork.cipher.data.Verification
import com.rork.cipher.ui.CipherViewModel
import com.rork.cipher.ui.components.MonoKeyText
import com.rork.cipher.ui.components.QrCode
import com.rork.cipher.ui.theme.Canvas
import com.rork.cipher.ui.theme.Hairline
import com.rork.cipher.ui.theme.OnSignal
import com.rork.cipher.ui.theme.SignalGreen
import com.rork.cipher.ui.theme.SurfaceElevated
import com.rork.cipher.ui.theme.TextPrimary
import com.rork.cipher.ui.theme.TextSecondary
import com.rork.cipher.ui.theme.WarningRed

/**
 * Checking that the key Cipher encrypts to is really the other person's.
 *
 * The number on this screen is derived from both public keys, so it is the
 * same on both phones and different the instant either key is swapped. Reading
 * it aloud is enough; scanning the other phone's code does the comparison for
 * you, on the device, without sending anything anywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifySheet(
    peer: String,
    viewModel: CipherViewModel,
    onDismiss: () -> Unit
) {
    val verifiedPeers by viewModel.verified.collectAsStateWithLifecycle()
    val alarms by viewModel.keyAlarms.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    var groups by remember(peer) { mutableStateOf(viewModel.safetyNumber(peer)) }
    var scanning by remember { mutableStateOf(false) }
    var mismatch by remember(peer) { mutableStateOf(false) }

    LaunchedEffect(peer) {
        if (groups == null) {
            viewModel.ensureKey(peer)
            groups = viewModel.safetyNumber(peer)
        }
    }

    val state = when {
        alarms.contains(peer) -> Verification.CHANGED
        groups == null -> Verification.UNKNOWN
        verifiedPeers.contains(peer) -> Verification.VERIFIED
        else -> Verification.UNVERIFIED
    }
    val accent = when (state) {
        Verification.CHANGED -> WarningRed
        Verification.VERIFIED -> SignalGreen
        else -> TextSecondary
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceElevated) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 40.dp)
        ) {
            Icon(
                imageVector = when (state) {
                    Verification.CHANGED -> Icons.Outlined.WarningAmber
                    Verification.VERIFIED -> Icons.Outlined.Verified
                    Verification.UNKNOWN -> Icons.Outlined.GppMaybe
                    Verification.UNVERIFIED -> Icons.Outlined.Shield
                },
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = when (state) {
                    Verification.CHANGED -> "@$peer's code changed"
                    Verification.VERIFIED -> "@$peer is verified"
                    else -> "Verify @$peer"
                },
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (state) {
                    Verification.CHANGED ->
                        "Their key is not the one this phone had. A reinstall does that \u2014 " +
                            "so does somebody stepping into the middle. Check the code again."

                    Verification.VERIFIED ->
                        "You checked this code in person and the key has not changed since."

                    Verification.UNKNOWN ->
                        "This phone has not fetched @$peer's key yet. Open the chat once " +
                            "you are online and the code will appear here."

                    Verification.UNVERIFIED ->
                        "Read this code together, or scan theirs. If the two match, " +
                            "nobody is sitting between you."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state == Verification.CHANGED) WarningRed else TextSecondary,
                textAlign = TextAlign.Center
            )

            val code = groups
            if (code != null) {
                Spacer(Modifier.height(20.dp))
                SafetyNumberCard(groups = code, accent = accent)
                Spacer(Modifier.height(18.dp))
                viewModel.safetyCode(peer)?.let { raw ->
                    QrCode(
                        content = Invites.verifyLink(viewModel.username().orEmpty(), raw),
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .padding(bottom = 6.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                MonoKeyText(text = "their key \u00b7 ${viewModel.keyFingerprint(peer).orEmpty()}")
                Spacer(Modifier.height(20.dp))

                if (mismatch) {
                    Text(
                        text = "That code did not match. Do not send anything private " +
                            "until you have checked again in person.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarningRed,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                }

                VerifyButton(
                    label = "Scan their code",
                    icon = Icons.Outlined.QrCodeScanner,
                    filled = false,
                    onClick = {
                        mismatch = false
                        scanning = true
                    }
                )
                Spacer(Modifier.height(10.dp))
                if (state == Verification.VERIFIED) {
                    VerifyButton(
                        label = "Remove verification",
                        icon = Icons.Outlined.Shield,
                        filled = false,
                        tint = WarningRed,
                        onClick = { viewModel.clearVerified(peer) }
                    )
                } else {
                    VerifyButton(
                        label = "We compared it \u00b7 mark verified",
                        icon = Icons.Outlined.Verified,
                        filled = true,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.markVerified(peer)
                            mismatch = false
                        }
                    )
                }
            }
        }
    }

    if (scanning) {
        ScanSheet(
            onDismiss = { scanning = false },
            onScanned = { payload ->
                scanning = false
                val invite = Invites.parse(payload)
                val expected = viewModel.safetyCode(peer)
                val matched = invite is Invite.Verify &&
                    invite.username == peer &&
                    expected != null &&
                    invite.code.equals(expected, ignoreCase = true)
                if (matched) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.markVerified(peer)
                    mismatch = false
                } else {
                    mismatch = true
                }
            }
        )
    }
}

/** The code itself: twelve blocks, read left to right, row by row. */
@Composable
private fun SafetyNumberCard(
    groups: List<String>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Canvas)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        groups.chunked(3).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(vertical = 5.dp)
            ) {
                row.forEach { block ->
                    Text(
                        text = block,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 17.sp,
                        letterSpacing = 2.sp,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun VerifyButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = SignalGreen
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(if (filled) tint else Canvas)
            .border(
                width = 1.dp,
                color = if (filled) tint else Hairline,
                shape = RoundedCornerShape(26.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (filled) OnSignal else tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (filled) OnSignal else TextPrimary
            )
        }
    }
}
