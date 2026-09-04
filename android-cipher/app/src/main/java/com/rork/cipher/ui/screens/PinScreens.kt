package com.rork.cipher.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rork.cipher.data.LockMode
import com.rork.cipher.ui.CipherViewModel
import com.rork.cipher.ui.biometricsAvailable
import com.rork.cipher.ui.components.MonoKeyText
import com.rork.cipher.ui.rememberBiometricGate
import com.rork.cipher.ui.theme.Canvas
import com.rork.cipher.ui.theme.Hairline
import com.rork.cipher.ui.theme.OnSignal
import com.rork.cipher.ui.theme.SignalGreen
import com.rork.cipher.ui.theme.SoftMint
import com.rork.cipher.ui.theme.SurfaceElevated
import com.rork.cipher.ui.theme.TextPrimary
import com.rork.cipher.ui.theme.TextSecondary
import com.rork.cipher.ui.theme.WarningRed
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

private const val PIN_LENGTH = 6

/**
 * The lock screen for an account that has a screen-lock PIN. The account key
 * never appears here: the PIN unwraps it, or the phone's own biometrics do.
 */
@Composable
fun PinLockScreen(
    username: String,
    biometricOffered: Boolean,
    onSubmit: (String) -> Boolean,
    onBiometric: () -> Unit,
    onUseKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .background(Canvas)
            .padding(horizontal = 28.dp)
    ) {
        Spacer(Modifier.weight(0.6f))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .border(1.dp, SignalGreen, CircleShape)
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = SignalGreen,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Cipher is locked",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        MonoKeyText(text = "@$username")
        Spacer(Modifier.height(30.dp))
        PinPad(
            entered = entered,
            error = error,
            onDigit = { digit ->
                if (entered.length < PIN_LENGTH) {
                    error = null
                    entered += digit
                }
            },
            onBackspace = { entered = entered.dropLast(1) },
            onComplete = { pin ->
                if (onSubmit(pin)) {
                    true
                } else {
                    error = "Wrong PIN"
                    entered = ""
                    false
                }
            },
            onEnteredChange = { entered = it },
            biometric = if (biometricOffered) onBiometric else null
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onUseKey) {
            Text("Use my account key instead", color = TextSecondary)
        }
        Spacer(Modifier.weight(1f))
    }
}

/** Where the screen lock is turned on, changed or switched off. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockScreen(
    viewModel: CipherViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hasPin = settings.lockMode == LockMode.PIN
    val canBiometric = remember { biometricsAvailable(context) }
    var setup by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    val enableBiometrics = rememberBiometricGate(
        title = "Turn on biometric unlock",
        subtitle = "Cipher will open with your fingerprint or face",
        onSuccess = {
            notice = if (viewModel.enableBiometricUnlock()) null
            else "This phone would not hold the key. PIN unlock still works."
        },
        onFailure = { message -> notice = message }
    )

    Scaffold(
        modifier = modifier,
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                title = { Text("App lock", color = TextPrimary) },
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
            Spacer(Modifier.height(6.dp))
            Text(
                text = "A PIN seals Cipher itself. Your account key stops resting on this " +
                    "phone in the open \u2014 it is re-sealed under the PIN, so the PIN is what " +
                    "opens your conversations from now on.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(22.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            ) {
                LockRow(
                    title = "Require a PIN",
                    body = if (hasPin) "Asked for whenever Cipher is reopened"
                    else "Six digits, kept on this phone only",
                    checked = hasPin,
                    onCheckedChange = { on ->
                        notice = null
                        if (on) setup = true else viewModel.clearLock()
                    }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Hairline)
                )
                LockRow(
                    title = "Unlock with biometrics",
                    body = when {
                        !hasPin -> "Set a PIN first"
                        !canBiometric -> "No fingerprint or face is enrolled on this phone"
                        settings.biometricUnlock -> "Your fingerprint or face opens Cipher"
                        else -> "Skip the PIN when your finger or face is recognised"
                    },
                    checked = settings.biometricUnlock,
                    enabled = hasPin && canBiometric,
                    onCheckedChange = { on ->
                        notice = null
                        if (on) enableBiometrics() else viewModel.disableBiometricUnlock()
                    }
                )
                if (hasPin) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Hairline)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { setup = true }
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Pin,
                            contentDescription = null,
                            tint = SignalGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = "Change PIN",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                    }
                }
            }
            val message = notice
            if (message != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningRed
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, Hairline, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Icon(
                    Icons.Outlined.Fingerprint,
                    contentDescription = null,
                    tint = SoftMint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "If you forget the PIN, your account key still opens Cipher. " +
                        "There is no other way in \u2014 not for us either.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (setup) {
        PinSetupSheet(
            onDismiss = { setup = false },
            onPin = { pin ->
                val ok = viewModel.setPin(pin)
                if (!ok) notice = "The PIN could not be set on this device."
                setup = false
            }
        )
    }
}

/** Two-step PIN creation: enter, then confirm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinSetupSheet(
    onDismiss: () -> Unit,
    onPin: (String) -> Unit
) {
    var first by remember { mutableStateOf<String?>(null) }
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceElevated
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text(
                text = if (first == null) "Choose a PIN" else "Enter it again",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Six digits. Nothing about it leaves this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            PinPad(
                entered = entered,
                error = error,
                onDigit = { digit ->
                    if (entered.length < PIN_LENGTH) {
                        error = null
                        entered += digit
                    }
                },
                onBackspace = { entered = entered.dropLast(1) },
                onEnteredChange = { entered = it },
                onComplete = { pin ->
                    val opening = first
                    when {
                        opening == null -> {
                            first = pin
                            entered = ""
                            false
                        }

                        opening == pin -> {
                            onPin(pin)
                            true
                        }

                        else -> {
                            error = "Those did not match"
                            first = null
                            entered = ""
                            false
                        }
                    }
                },
                biometric = null
            )
        }
    }
}

/**
 * Six dots and a keypad. The dots fill as digits land and the whole row shakes
 * once when a PIN is refused, so the failure is felt rather than read.
 */
@Composable
private fun PinPad(
    entered: String,
    error: String?,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onEnteredChange: (String) -> Unit,
    onComplete: (String) -> Boolean,
    biometric: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(entered) {
        if (entered.length < PIN_LENGTH) return@LaunchedEffect
        val accepted = onComplete(entered)
        if (!accepted) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onEnteredChange("")
            shake.snapTo(0f)
            shake.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 320
                    (-14f) at 60
                    14f at 130
                    (-8f) at 200
                    0f at 320
                }
            )
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.offset { IntOffset(shake.value.toInt(), 0) }
        ) {
            repeat(PIN_LENGTH) { index ->
                val filled = index < entered.length
                val size by animateDpAsState(
                    targetValue = if (filled) 14.dp else 11.dp,
                    animationSpec = spring(),
                    label = "pinDot$index"
                )
                Box(
                    modifier = Modifier
                        .size(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(size)
                            .clip(CircleShape)
                            .background(if (filled) SignalGreen else Hairline)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = error ?: " ",
            style = MaterialTheme.typography.labelLarge,
            color = WarningRed
        )
        Spacer(Modifier.height(14.dp))
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { digit ->
                    PinKey(label = digit.toString(), onClick = { onDigit(digit) })
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(70.dp), contentAlignment = Alignment.Center) {
                if (biometric != null) {
                    IconButton(onClick = biometric) {
                        Icon(
                            Icons.Outlined.Fingerprint,
                            contentDescription = "Unlock with biometrics",
                            tint = SignalGreen,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
            PinKey(label = "0", onClick = { onDigit('0') })
            Box(modifier = Modifier.size(70.dp), contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = {
                        scope.launch { onBackspace() }
                    }
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Backspace,
                        contentDescription = "Delete",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PinKey(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(70.dp)
            .clip(CircleShape)
            .background(SurfaceElevated)
            .border(1.dp, Hairline, CircleShape)
            .clickable(interactionSource = interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 24.sp,
            color = TextPrimary
        )
    }
}

@Composable
private fun LockRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) TextPrimary else TextSecondary
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
            enabled = enabled,
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
