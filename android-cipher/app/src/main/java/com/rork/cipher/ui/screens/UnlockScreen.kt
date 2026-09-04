package com.rork.cipher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.cipher.data.CryptoBox
import com.rork.cipher.data.UnlockResult
import com.rork.cipher.ui.theme.Canvas
import com.rork.cipher.ui.theme.Hairline
import com.rork.cipher.ui.theme.OnSignal
import com.rork.cipher.ui.theme.SignalGreen
import com.rork.cipher.ui.theme.SoftMint
import com.rork.cipher.ui.theme.SurfaceElevated
import com.rork.cipher.ui.theme.TextPrimary
import com.rork.cipher.ui.theme.TextSecondary
import com.rork.cipher.ui.theme.WarningRed
import kotlinx.coroutines.launch

/**
 * Sign in with an account key. The hub is asked first so the account and its
 * encrypted history can be restored on a brand-new device.
 */
@Composable
fun UnlockScreen(
    username: String?,
    onUnlock: suspend (String) -> UnlockResult,
    onBack: (() -> Unit)?,
    onForget: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    val shaped = CryptoBox.isKeyShaped(key)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.LockOpen, contentDescription = null, tint = SignalGreen)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = if (username != null) "Unlock @$username" else "Enter your account key",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Your key restores the account and every encrypted conversation, on any device.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )

        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = key,
            onValueChange = {
                key = it.uppercase()
                error = null
            },
            enabled = !working,
            placeholder = { Text("K7QD-2F8M-XPLA-…", color = TextSecondary) },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                letterSpacing = 1.sp
            ),
            minLines = 3,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done
            ),
            shape = RoundedCornerShape(16.dp),
            isError = error != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceElevated,
                unfocusedContainerColor = SurfaceElevated,
                errorContainerColor = SurfaceElevated,
                focusedBorderColor = SignalGreen,
                unfocusedBorderColor = Hairline,
                cursorColor = SignalGreen,
                focusedTextColor = SoftMint,
                unfocusedTextColor = SoftMint
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        AnimatedVisibility(visible = error != null, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = error.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = WarningRed
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (!shaped) {
                    error = "A Cipher key has 64 characters."
                    return@Button
                }
                working = true
                error = null
                scope.launch {
                    when (val result = onUnlock(key)) {
                        UnlockResult.Success -> Unit
                        UnlockResult.WrongKey -> error = "No account matches that key."
                        is UnlockResult.Failed -> error = result.message
                    }
                    working = false
                }
            },
            enabled = key.isNotBlank() && !working,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SignalGreen,
                contentColor = OnSignal,
                disabledContainerColor = SurfaceElevated,
                disabledContentColor = TextSecondary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (working) {
                CircularProgressIndicator(
                    color = OnSignal,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text("Unlock with key", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (username != null && onForget != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { confirmForget = true }, enabled = !working) {
                    Text(
                        text = if (confirmForget) "Tap below to confirm"
                        else "Not your account? Start over",
                        color = WarningRed
                    )
                }
            }
            if (confirmForget) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = onForget) {
                        Text("Remove @$username from this device", color = WarningRed)
                    }
                }
            }
        }
        Spacer(Modifier.size(48.dp))
    }
}
