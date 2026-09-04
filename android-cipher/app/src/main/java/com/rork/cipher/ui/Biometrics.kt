package com.rork.cipher.ui

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Which authenticators Cipher will accept. Device credentials are only offered
 * from Android 11 up, where the support library can combine them with strong
 * biometrics in one prompt.
 */
private fun authenticators(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
        BiometricManager.Authenticators.BIOMETRIC_STRONG
    }

/** True when this phone has a usable fingerprint, face or device credential. */
fun biometricsAvailable(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(authenticators()) ==
        BiometricManager.BIOMETRIC_SUCCESS

/**
 * Returns a function that raises the system biometric prompt. The OS decides
 * whether the presented finger or face is genuine; this app never sees it.
 */
@Composable
fun rememberBiometricGate(
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val success = rememberUpdatedState(onSuccess)
    val failure = rememberUpdatedState(onFailure)

    return remember(activity, title, subtitle) {
        {
            if (activity == null) {
                failure.value("Biometrics are not available here.")
            } else {
                val prompt = BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                            result: BiometricPrompt.AuthenticationResult
                        ) {
                            success.value()
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence
                        ) {
                            failure.value(errString.toString())
                        }
                    }
                )
                val info = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .apply {
                        setAllowedAuthenticators(authenticators())
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                            setNegativeButtonText("Use PIN")
                        }
                    }
                    .build()
                runCatching { prompt.authenticate(info) }
                    .onFailure { failure.value("This phone cannot show a biometric prompt.") }
                Unit
            }
        }
    }
}
