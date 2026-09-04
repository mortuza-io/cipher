package com.rork.cipher.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.cipher.data.CryptoBox
import com.rork.cipher.ui.theme.Canvas
import com.rork.cipher.ui.theme.Hairline
import com.rork.cipher.ui.theme.OnSignal
import com.rork.cipher.ui.theme.SignalGreen
import com.rork.cipher.ui.theme.SoftMint
import com.rork.cipher.ui.theme.SurfaceElevated
import com.rork.cipher.ui.theme.TextPrimary
import com.rork.cipher.ui.theme.TextSecondary
import com.rork.cipher.ui.theme.WarningRed

/** The signup hand-off: the account key is shown once and never recoverable. */
@Composable
fun AccountKeyScreen(
    username: String,
    accountKey: String,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var saved by remember { mutableStateOf(false) }
    val rows = remember(accountKey) { CryptoBox.keyRows(accountKey) }
    val buttonScale by animateFloatAsState(
        targetValue = if (saved) 1f else 0.98f,
        animationSpec = tween(220),
        label = "cta"
    )
    val saver = rememberKeyFileSaver(username, accountKey)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(1.dp, SignalGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Key, contentDescription = null, tint = SignalGreen)
                }
                Spacer(Modifier.size(16.dp))
                Text(
                    text = "@$username is yours",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "This key is the only way back into your account. We cannot recover it.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )

            Spacer(Modifier.height(28.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                    .padding(vertical = 24.dp, horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                rows.forEach { row ->
                    Text(
                        text = row,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        fontSize = 17.sp,
                        letterSpacing = 1.6.sp,
                        color = SoftMint
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = { copyKey(context, accountKey) },
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
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Copy key")
                }
                OutlinedButton(
                    onClick = { saver() },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftMint),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Save as file")
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { saved = !saved }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = saved,
                    onCheckedChange = { saved = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = SignalGreen,
                        checkmarkColor = OnSignal,
                        uncheckedColor = Hairline
                    )
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = "I have saved my key somewhere safe",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (saved) TextPrimary else WarningRed
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Button(
                onClick = onEnter,
                enabled = saved,
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SignalGreen,
                    contentColor = OnSignal,
                    disabledContainerColor = SurfaceElevated,
                    disabledContentColor = TextSecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .scale(buttonScale)
            ) {
                Text("Enter Cipher", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun rememberKeyFileSaver(username: String, accountKey: String): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(keyFileBody(username, accountKey).toByteArray())
            }
        }.isSuccess
        Toast.makeText(
            context,
            if (ok) "Key file saved" else "Could not save the key file",
            Toast.LENGTH_SHORT
        ).show()
    }
    return { launcher.launch("cipher-key-$username.txt") }
}

private fun keyFileBody(username: String, accountKey: String): String = buildString {
    appendLine("Cipher account key")
    appendLine("username: @$username")
    appendLine("fingerprint: ${CryptoBox.fingerprint(username)}")
    appendLine()
    CryptoBox.keyRows(accountKey).forEach { appendLine(it) }
    appendLine()
    appendLine("This key is the only way back into the account. Keep it offline.")
}

fun copyKey(context: Context, accountKey: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    manager?.setPrimaryClip(ClipData.newPlainText("Cipher account key", accountKey))
    Toast.makeText(context, "Key copied to clipboard", Toast.LENGTH_SHORT).show()
}

/** Small circular close affordance used on full-screen moments. */
@Composable
fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = SignalGreen) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(SurfaceElevated)
    ) {
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "Close",
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}
