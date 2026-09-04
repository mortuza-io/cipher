package com.rork.cipher.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rork.cipher.ui.components.MonoKeyText
import com.rork.cipher.ui.components.QrCode
import com.rork.cipher.ui.theme.Canvas
import com.rork.cipher.ui.theme.Hairline
import com.rork.cipher.ui.theme.SignalGreen
import com.rork.cipher.ui.theme.SoftMint
import com.rork.cipher.ui.theme.SurfaceElevated
import com.rork.cipher.ui.theme.TextPrimary
import com.rork.cipher.ui.theme.TextSecondary

/**
 * The face-to-face invite: a QR code of a `cipher://` link that another phone
 * can scan across a table. The code carries a username or a room token — never
 * key material — so showing it costs nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteQrSheet(
    title: String,
    caption: String,
    link: String,
    shareText: String,
    onDismiss: () -> Unit,
    onScan: (() -> Unit)? = null
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SurfaceElevated) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 36.dp)
        ) {
            Icon(Icons.Outlined.QrCode2, contentDescription = null, tint = SignalGreen)
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            QrCode(content = link, modifier = Modifier.size(248.dp))
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Canvas)
                    .border(1.dp, Hairline, RoundedCornerShape(12.dp))
                    .clickable { copyToClipboard(context, "Invite link", link) }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                MonoKeyText(text = link, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Outlined.ContentCopy,
                    contentDescription = "Copy link",
                    tint = SoftMint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = { shareLink(context, shareText, link) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SignalGreen,
                        contentColor = Canvas
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Share link")
                }
                if (onScan != null) {
                    OutlinedButton(
                        onClick = onScan,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftMint),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Icon(
                            Icons.Outlined.QrCode2,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Scan theirs")
                    }
                }
            }
        }
    }
}

fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun shareLink(context: Context, message: String, link: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "$message\n$link")
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Share invite")) }
}
