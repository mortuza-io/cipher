package com.rork.cipher.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.rork.cipher.BuildConfig
import com.rork.cipher.Config
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Cipher's push wake-ups.
 *
 * Android freezes an app that is not on screen, so a chat app has two ways to
 * hear about a message: hold its own socket open behind a permanent
 * notification, or let the system's shared push channel tap it on the shoulder.
 * This is the second, which is what every mainstream messenger uses and why
 * none of them shows a permanent notice.
 *
 * Nothing about a message travels through Google. The push says only "wake";
 * the phone then opens its own encrypted connection and finds out what arrived
 * by decrypting it here.
 *
 * Firebase is configured from environment values at build time rather than a
 * `google-services.json` checked into the project.
 */
object Push {

    /**
     * One Firebase value, taken from the build or from the project config.
     *
     * Both are generated, and either one alone is enough, so a value is read
     * from whichever carries it rather than from a single source that might be
     * regenerated empty.
     */
    private fun value(name: String, baked: String): String =
        baked.trim().ifEmpty { Config.allValues[name]?.trim().orEmpty() }

    private val projectId: String =
        value("RORK_PUBLIC_FIREBASE_PROJECT_ID", BuildConfig.FIREBASE_PROJECT_ID)
    private val appId: String =
        value("RORK_PUBLIC_FIREBASE_APP_ID", BuildConfig.FIREBASE_APP_ID)
    private val apiKey: String =
        value("RORK_PUBLIC_FIREBASE_API_KEY", BuildConfig.FIREBASE_API_KEY)
    private val senderId: String =
        value("RORK_PUBLIC_FIREBASE_SENDER_ID", BuildConfig.FIREBASE_SENDER_ID)

    /** True when the project carries Firebase values; false leaves push off. */
    val isConfigured: Boolean =
        projectId.isNotEmpty() &&
            appId.isNotEmpty() &&
            apiKey.isNotEmpty() &&
            senderId.isNotEmpty()

    /**
     * Brings Firebase up by hand.
     *
     * Safe to call more than once — a second call finds the app already there
     * and does nothing.
     */
    fun start(context: Context) {
        if (!isConfigured) return
        if (FirebaseApp.getApps(context).isNotEmpty()) return
        runCatching {
            FirebaseApp.initializeApp(
                context.applicationContext,
                FirebaseOptions.Builder()
                    .setProjectId(projectId)
                    .setApplicationId(appId)
                    .setApiKey(apiKey)
                    .setGcmSenderId(senderId)
                    .build()
            )
        }.onFailure { Log.w(TAG, "firebase init failed: ${it.message}") }
    }

    /**
     * This installation's push address.
     *
     * Null when Firebase is not configured or the phone has no Google Play
     * services — an emulator without them, or a device sold without them. The
     * app then falls back to holding its own connection open.
     */
    suspend fun token(context: Context): String? {
        if (!isConfigured) return null
        start(context)
        return suspendCancellableCoroutine { continuation ->
            runCatching {
                FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        if (!continuation.isActive) return@addOnCompleteListener
                        if (task.isSuccessful) {
                            continuation.resume(task.result)
                        } else {
                            Log.w(TAG, "no push token: ${task.exception?.message}")
                            continuation.resume(null)
                        }
                    }
            }.onFailure {
                Log.w(TAG, "push unavailable: ${it.message}")
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    /** Throws this installation's address away, so nothing can wake it again. */
    suspend fun forget(): Unit = suspendCancellableCoroutine { continuation ->
        runCatching {
            FirebaseMessaging.getInstance().deleteToken()
                .addOnCompleteListener {
                    if (continuation.isActive) continuation.resume(Unit)
                }
        }.onFailure {
            if (continuation.isActive) continuation.resume(Unit)
        }
    }

    private const val TAG = "Push"
}
