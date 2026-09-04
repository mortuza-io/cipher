package com.rork.cipher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.cipher.data.ClaimResult
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

private val USERNAME_RULE = Regex("^[a-z0-9._]{3,20}$")

@Composable
fun WelcomeScreen(
    onClaim: suspend (String) -> ClaimResult,
    onUseKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var claiming by remember { mutableStateOf(false) }
    val clean = username.trim().lowercase()
    val valid = USERNAME_RULE.matches(clean)

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
        Spacer(Modifier.height(56.dp))
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = SignalGreen)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "Cipher",
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "No phone number. No email. Claim a username and it becomes your entire identity.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )

        Spacer(Modifier.height(40.dp))
        Text(
            text = "CLAIM A USERNAME",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            letterSpacing = 1.4.sp
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it.filter { c -> !c.isWhitespace() }
                error = null
            },
            singleLine = true,
            enabled = !claiming,
            prefix = {
                Text(text = "@", color = SoftMint, fontFamily = FontFamily.Monospace)
            },
            placeholder = { Text("nightferry", color = TextSecondary) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
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
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut()
        ) {
            Text(
                text = error.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = WarningRed
            )
        }
        if (error == null) {
            Text(
                text = "3–20 characters · lowercase letters, numbers, dot, underscore",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                if (!valid) {
                    error = "That username doesn't fit the format."
                    return@Button
                }
                claiming = true
                error = null
                scope.launch {
                    when (val result = onClaim(clean)) {
                        is ClaimResult.Success -> Unit
                        ClaimResult.Taken -> error = "@$clean is already taken."
                        is ClaimResult.Failed -> error = result.message
                    }
                    claiming = false
                }
            },
            enabled = clean.isNotEmpty() && !claiming,
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
            if (claiming) {
                CircularProgressIndicator(
                    color = OnSignal,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text("Claim username", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onUseKey, enabled = !claiming) {
                Icon(
                    Icons.Outlined.Key,
                    contentDescription = null,
                    tint = SoftMint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text("I already have an account key", color = SoftMint)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}
