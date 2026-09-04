package com.rork.cipher.data

import android.app.Activity
import android.app.Application
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.security.PrivateKey
import javax.crypto.SecretKey
import kotlin.random.Random

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState

    /**
     * The vault is sealed. `pin` is true when a screen-lock PIN can open it and
     * `biometric` when the phone's own biometrics can too; otherwise only the
     * account key will do.
     */
    data class Locked(
        val username: String,
        val pin: Boolean = false,
        val biometric: Boolean = false
    ) : SessionState

    data class Active(val account: Account) : SessionState
}

/**
 * Single source of truth for Cipher.
 *
 * Owns the account, the live hub connection, the decrypted threads and the
 * encrypted on-device mirror. Message bodies, quoted replies and reactions are
 * all encrypted before they leave the device and are only ever decrypted here.
 */
class CipherRepository(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("cipher_vault", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api = CipherApi()
    private val notifier = Notifier(context)
    private val photos = PhotoStore(context)
    private val voices = VoiceStore(context)
    private val files = FileStore(context)
    private val player = VoicePlayer()

    /** The one voice note currently open, so every bubble can follow along. */
    val voicePlayback: StateFlow<VoicePlayback?> = player.state

    private val _session = MutableStateFlow<SessionState>(SessionState.Loading)
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private val _threads = MutableStateFlow<List<Thread>>(emptyList())
    val threads: StateFlow<List<Thread>> = _threads.asStateFlow()

    private val _typingPeers = MutableStateFlow<Set<String>>(emptySet())
    val typingPeers: StateFlow<Set<String>> = _typingPeers.asStateFlow()

    private val _onlinePeers = MutableStateFlow<Set<String>>(emptySet())
    val onlinePeers: StateFlow<Set<String>> = _onlinePeers.asStateFlow()

    private val _connection = MutableStateFlow(ConnectionState.OFFLINE)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _blocked = MutableStateFlow<Set<String>>(emptySet())
    val blocked: StateFlow<Set<String>> = _blocked.asStateFlow()

    /** Messages written now and queued to leave later, newest deadline last. */
    private val _scheduled = MutableStateFlow<List<ScheduledMessage>>(emptyList())
    val scheduled: StateFlow<List<ScheduledMessage>> = _scheduled.asStateFlow()

    /** Last hub rejection worth surfacing, e.g. a blocked or closed inbox. */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var accountKey: String? = null
    private var vaultSecret: SecretKey? = null
    private var exchangeKey: PrivateKey? = null
    private var peerKeys: MutableMap<String, String> = mutableMapOf()
    private val conversationKeys: MutableMap<String, SecretKey> = mutableMapOf()

    /** Peer to the exact public key that was checked face to face. */
    private var verifiedKeys: MutableMap<String, String> = mutableMapOf()

    /** Peers whose verification currently holds, for the UI to watch. */
    private val _verified = MutableStateFlow<Set<String>>(emptySet())
    val verified: StateFlow<Set<String>> = _verified.asStateFlow()

    /**
     * Peers whose key changed underneath us and who have not been looked at
     * since. This is the one security event Cipher raises its voice about.
     */
    private val _keyAlarms = MutableStateFlow<Set<String>>(emptySet())
    val keyAlarms: StateFlow<Set<String>> = _keyAlarms.asStateFlow()
    private var lastSeq: Long = 0L

    private var socketJob: Job? = null
    private var burnJob: Job? = null

    /** Frames survive a dropped socket here and are re-sent by the next session. */
    private val outbox = Outbox()

    /** Kicks the reconnect loop out of its backoff (foreground, network back). */
    private val wakeSignal = Channel<Unit>(Channel.CONFLATED)

    /** The live session, kept so the watchdog can cut a socket that went quiet. */
    @Volatile
    private var live: DefaultClientWebSocketSession? = null

    @Volatile
    private var lastInboundAt: Long = 0L

    /** When the last liveness ping went out; zeroed by any inbound frame. */
    @Volatile
    private var probeSentAt: Long = 0L

    private val network = NetworkMonitor(context) { wake("network") }

    /** False in airplane mode or with no usable network at all. */
    val hasNetwork: StateFlow<Boolean> = network.online

    private var typingClearJobs: MutableMap<String, Job> = mutableMapOf()
    private var lastTypingSentAt: Long = 0L

    /** Last re-send attempt per message id, so a stalled send retries at most once a window. */
    private val retryAttempts: MutableMap<String, Long> = mutableMapOf()

    /** Per-member receipts for a fanned-out room message, keyed by canonical id. */
    private val groupAcks: MutableMap<String, MutableMap<String, DeliveryState>> = mutableMapOf()

    /** Peer whose conversation is on screen — inbound messages there are read instantly. */
    @Volatile
    private var activePeer: String? = null

    /**
     * True only while a Cipher screen is actually in front of the user.
     *
     * A conversation left open when the app went to the background is not
     * "being read" — without this, backgrounding from a chat silently swallowed
     * every notification from that person.
     */
    @Volatile
    private var visible: Boolean = false

    /**
     * Signalled whenever inbound work lands, so a push wake-up knows the phone
     * has caught up and can stop holding the process awake.
     */
    private val pushSettled = Channel<Unit>(Channel.CONFLATED)

    /**
     * True once the hub can wake this phone through Android's push channel.
     *
     * While this holds, Cipher needs nothing of its own running in the
     * background and shows no permanent notice.
     */
    private val _pushReady = MutableStateFlow(false)
    val pushReady: StateFlow<Boolean> = _pushReady.asStateFlow()

    /** This installation's push address, kept so it can be withdrawn on sign-out. */
    private var deviceToken: String? = null

    init {
        watchVisibility()
        restoreSession()
        startBurnTicker()
        watchPush()
        watchDelivery()
    }

    /** Tracks whether any Cipher screen is in front of the user right now. */
    private fun watchVisibility() {
        val application = app as? Application ?: return
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                private var started = 0

                override fun onActivityStarted(activity: Activity) {
                    started++
                    visible = true
                }

                override fun onActivityStopped(activity: Activity) {
                    started = (started - 1).coerceAtLeast(0)
                    if (started == 0) visible = false
                }

                override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, out: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }

    fun currentKey(): String? = accountKey

    fun account(): Account? = (_session.value as? SessionState.Active)?.account

    fun clearError() {
        _lastError.value = null
    }

    // ------------------------------------------------------------- lifecycle

    private fun restoreSession() {
        val vault = readVault()
        val account = vault.account
        _settings.value = vault.settings
        _blocked.value = vault.blocked.toSet()
        val storedKey = prefs.getString(KEY_UNLOCKED, null)
        val sealedShut = vault.settings.lockMode != LockMode.NONE ||
            vault.settings.requireKeyOnOpen
        _session.value = when {
            account == null -> SessionState.SignedOut
            !sealedShut &&
                storedKey != null &&
                CryptoBox.authDigest(storedKey) == account.authDigest -> {
                openVault(storedKey, account, vault)
                SessionState.Active(account)
            }
            else -> lockedState(account.username, vault.settings)
        }
    }

    /** Which doors the lock screen should offer for this account. */
    private fun lockedState(
        username: String,
        settings: Settings = _settings.value
    ): SessionState.Locked = SessionState.Locked(
        username = username,
        pin = settings.lockMode == LockMode.PIN && prefs.contains(KEY_PIN_SEALED),
        biometric = settings.lockMode == LockMode.PIN &&
            settings.biometricUnlock &&
            prefs.contains(KEY_BIO_SEALED) &&
            BiometricVault.isReady()
    )

    private fun openVault(key: String, account: Account, vault: Vault) {
        accountKey = key
        val secret = CryptoBox.vaultKey(key)
        vaultSecret = secret
        exchangeKey = CryptoBox.unsealPrivateKey(account.sealedPrivateKey, secret)
        peerKeys = vault.peerKeys.toMutableMap()
        conversationKeys.clear()
        verifiedKeys = vault.verifiedKeys.toMutableMap()
        _keyAlarms.value = vault.keyAlarms.toSet()
        refreshVerified()
        lastSeq = vault.lastSeq
        _threads.value = vault.threads.map { it.toThread(secret) }
        _scheduled.value = vault.scheduled.mapNotNull { it.toScheduled(secret) }
        _settings.value = vault.settings
        _blocked.value = vault.blocked.toSet()
        // The old "require key on reopen" switch is superseded by the app lock.
        if (vault.settings.requireKeyOnOpen && vault.settings.lockMode == LockMode.NONE) {
            _settings.value = vault.settings.copy(requireKeyOnOpen = false)
            persist(account)
        }
        connect(account)
    }

    /** Claims a username on the hub and mints the account key. */
    suspend fun createAccount(username: String): ClaimResult {
        val clean = username.removePrefix("@").trim().lowercase()
        val key = CryptoBox.generateAccountKey()
        val secret = CryptoBox.vaultKey(key)
        val pair = withContext(Dispatchers.Default) { CryptoBox.generateExchangeKeyPair() }
        val publicKey = CryptoBox.encodePublicKey(pair.public)
        val sealed = CryptoBox.sealPrivateKey(pair.private, secret)
        val digest = CryptoBox.authDigest(key)

        return when (val result = api.claim(clean, digest, publicKey, sealed)) {
            is ClaimResult.Taken -> result
            is ClaimResult.Failed -> result
            is ClaimResult.Success -> {
                val account = Account(
                    username = clean,
                    authDigest = digest,
                    publicKey = publicKey,
                    sealedPrivateKey = sealed,
                    createdAt = System.currentTimeMillis()
                )
                accountKey = key
                vaultSecret = secret
                exchangeKey = pair.private
                peerKeys = mutableMapOf(clean to publicKey)
                conversationKeys.clear()
                verifiedKeys = mutableMapOf()
                _verified.value = emptySet()
                _keyAlarms.value = emptySet()
                lastSeq = 0L
                _threads.value = emptyList()
                _scheduled.value = emptyList()
                _settings.value = Settings()
                _blocked.value = emptySet()
                prefs.edit().putString(KEY_UNLOCKED, key).apply()
                persist(account)
                _session.value = SessionState.Active(account)
                connect(account)
                ClaimResult.Success(key)
            }
        }
    }

    /**
     * Signs in with an account key. The hub is asked first so a fresh device can
     * restore the account; a local vault is accepted when the hub is unreachable.
     */
    suspend fun unlock(rawKey: String): UnlockResult {
        val key = CryptoBox.normalizeKey(rawKey)
        val digest = CryptoBox.authDigest(key)
        val remote = api.login(digest)

        if (remote != null) {
            val secret = CryptoBox.vaultKey(key)
            if (CryptoBox.unsealPrivateKey(remote.sealedPrivateKey, secret) == null) {
                return UnlockResult.Failed("That key can't open this account.")
            }
            val account = Account(
                username = remote.username,
                authDigest = digest,
                publicKey = remote.publicKey,
                sealedPrivateKey = remote.sealedPrivateKey,
                createdAt = remote.createdAt
            )
            val stored = readVault()
            val vault = if (stored.account?.username == remote.username) stored else Vault()
            prefs.edit().putString(KEY_UNLOCKED, key).apply()
            val merged = vault.copy(
                account = account,
                settings = vault.settings.copy(
                    receipts = remote.settings.receipts,
                    typing = remote.settings.typing,
                    presence = remote.settings.presence,
                    strangers = remote.settings.strangers
                )
            )
            openVault(key, account, merged)
            persist(account)
            _session.value = SessionState.Active(account)
            return UnlockResult.Success
        }

        val vault = readVault()
        val local = vault.account
        if (local != null && local.authDigest == digest) {
            prefs.edit().putString(KEY_UNLOCKED, key).apply()
            openVault(key, local, vault)
            _session.value = SessionState.Active(local)
            return UnlockResult.Success
        }
        return UnlockResult.WrongKey
    }

    /** Locks the session: the key leaves storage, ciphertext stays on disk. */
    fun lockVault() {
        val username = account()?.username
        disconnect()
        accountKey = null
        vaultSecret = null
        exchangeKey = null
        conversationKeys.clear()
        _threads.value = emptyList()
        _scheduled.value = emptyList()
        notifier.clearAll()
        // Any file the user opened left a readable copy behind on purpose.
        // Locking the vault takes it back.
        scope.launch { files.clearOpened() }
        prefs.edit().remove(KEY_UNLOCKED).apply()
        _session.value = username?.let { lockedState(it) } ?: SessionState.SignedOut
    }

    // -------------------------------------------------------------- app lock

    /**
     * Turns on the screen lock. The account key stops resting in plain storage
     * and is re-sealed under a key stretched from the PIN, so the PIN itself is
     * what opens the vault from now on.
     */
    fun setPin(pin: String): Boolean {
        val key = accountKey ?: return false
        val salt = CryptoBox.randomSalt()
        val derived = CryptoBox.pinKey(pin, salt) ?: return false
        prefs.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_SEALED, CryptoBox.encrypt(key, derived))
            .remove(KEY_UNLOCKED)
            .apply()
        updateSettings { it.copy(lockMode = LockMode.PIN) }
        return true
    }

    fun hasPin(): Boolean = prefs.contains(KEY_PIN_SEALED)

    fun verifyPin(pin: String): Boolean = keyFromPin(pin) != null

    private fun keyFromPin(pin: String): String? {
        val stored = readVault().account ?: return null
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return null
        val sealed = prefs.getString(KEY_PIN_SEALED, null) ?: return null
        val derived = CryptoBox.pinKey(pin, salt) ?: return null
        val key = CryptoBox.decrypt(sealed, derived) ?: return null
        return key.takeIf { CryptoBox.authDigest(it) == stored.authDigest }
    }

    /** Opens the vault with the screen-lock PIN, entirely offline. */
    fun unlockWithPin(pin: String): Boolean = openLocally(keyFromPin(pin) ?: return false)

    /** Called right after a successful biometric prompt on the lock screen. */
    fun unlockWithBiometric(): Boolean {
        val sealed = prefs.getString(KEY_BIO_SEALED, null) ?: return false
        val key = BiometricVault.open(sealed) ?: return false
        return openLocally(key)
    }

    private fun openLocally(key: String): Boolean {
        val vault = readVault()
        val stored = vault.account ?: return false
        if (CryptoBox.authDigest(key) != stored.authDigest) return false
        openVault(key, stored, vault)
        _session.value = SessionState.Active(stored)
        return true
    }

    /** Removes the screen lock; the vault opens itself again on this device. */
    fun clearLock() {
        val key = accountKey
        val editor = prefs.edit()
            .remove(KEY_PIN_SALT)
            .remove(KEY_PIN_SEALED)
            .remove(KEY_BIO_SEALED)
        if (key != null) editor.putString(KEY_UNLOCKED, key)
        editor.apply()
        BiometricVault.clear()
        updateSettings { it.copy(lockMode = LockMode.NONE, biometricUnlock = false) }
    }

    /** Called right after a successful biometric prompt while already unlocked. */
    fun enableBiometricUnlock(): Boolean {
        val key = accountKey ?: return false
        val sealed = BiometricVault.seal(key) ?: return false
        prefs.edit().putString(KEY_BIO_SEALED, sealed).apply()
        updateSettings { it.copy(biometricUnlock = true) }
        return true
    }

    fun disableBiometricUnlock() {
        prefs.edit().remove(KEY_BIO_SEALED).apply()
        BiometricVault.clear()
        updateSettings { it.copy(biometricUnlock = false) }
    }

    /** Re-seals the app after it has been away from the screen. */
    fun lockForAppLock() {
        if (_settings.value.lockMode == LockMode.NONE) return
        if (_session.value !is SessionState.Active) return
        lockVault()
    }

    /** Signs out and wipes this device. The account itself survives on the hub. */
    fun signOut() {
        disconnect()
        wipeLocal()
    }

    /** Deletes the account on the hub, then wipes this device. */
    fun deleteAccount() {
        val digest = account()?.authDigest
        disconnect()
        if (digest != null) scope.launch { api.deleteAccount(digest) }
        wipeLocal()
    }

    private fun wipeLocal() {
        accountKey = null
        vaultSecret = null
        exchangeKey = null
        peerKeys.clear()
        conversationKeys.clear()
        verifiedKeys.clear()
        _verified.value = emptySet()
        _keyAlarms.value = emptySet()
        lastSeq = 0L
        _threads.value = emptyList()
        _scheduled.value = emptyList()
        _typingPeers.value = emptySet()
        _onlinePeers.value = emptySet()
        _settings.value = Settings()
        _blocked.value = emptySet()
        notifier.clearAll()
        player.stop()
        releasePush()
        scope.launch {
            photos.clear()
            voices.clear()
            files.clear()
        }
        prefs.edit().clear().apply()
        _session.value = SessionState.SignedOut
    }

    // ------------------------------------------------------------------ push

    /**
     * Keeps the hub's copy of this device's push address current.
     *
     * Registration follows the account rather than the app's lifecycle: a
     * signed-in phone is registered, a signed-out one is not, and a phone that
     * cannot get an address at all (no Google Play services) reports that
     * honestly so the fallback can take over.
     */
    private fun watchPush() {
        scope.launch {
            _session
                .map { (it as? SessionState.Active)?.account?.authDigest }
                .distinctUntilChanged()
                .collect { digest ->
                    if (digest == null) {
                        _pushReady.value = false
                        return@collect
                    }
                    val token = Push.token(app)
                    if (token == null) {
                        _pushReady.value = false
                        Log.i(TAG, "no push channel on this device; using own connection")
                        return@collect
                    }
                    deviceToken = token
                    _pushReady.value = api.registerDevice(digest, token)
                }
        }
    }

    /** Android rotates push addresses on its own; a stale one means a silent phone. */
    fun onPushToken(token: String) {
        val digest = account()?.authDigest ?: return
        scope.launch {
            deviceToken = token
            _pushReady.value = api.registerDevice(digest, token)
        }
    }

    /**
     * A wake-up arrived.
     *
     * The push itself said nothing, so the work of finding out what happened is
     * done here: reconnect, pull whatever is waiting, decrypt it on this phone
     * and only then write a notification. A locked vault cannot decrypt
     * anything, so it gets a notice that says only that something arrived.
     *
     * This suspends until the backlog actually lands, because Android keeps a
     * woken process alive only while the push is being handled — returning
     * early would freeze the app mid-reconnect and the notification would never
     * be written.
     */
    suspend fun onPushWake() {
        when (_session.value) {
            is SessionState.Active -> {
                // Drop any signal left over from earlier traffic so the wait
                // below is answered by this wake-up's own delivery.
                pushSettled.tryReceive()
                wake("push")
                withTimeoutOrNull(PUSH_WAIT_MS) { pushSettled.receive() }
            }
            is SessionState.Locked -> {
                if (_settings.value.notifications) notifier.notifySealed()
            }
            else -> Unit
        }
    }

    /** Withdraws this device so nothing can wake it for an account it has left. */
    private fun releasePush() {
        val token = deviceToken
        deviceToken = null
        _pushReady.value = false
        scope.launch {
            if (token != null) api.forgetDevice(token)
            Push.forget()
        }
    }

    // ------------------------------------------------- background delivery

    /** The ongoing notice the delivery service posts while it holds the socket. */
    fun deliveryNotification(): Notification = notifier.connectionNotification()

    /**
     * Runs the delivery service exactly while it is wanted — which is rarely.
     *
     * Push made this a fallback. A phone the hub can wake needs nothing of its
     * own running in the background, so the service is refused outright while
     * [pushReady] holds, whatever the switch says. It only becomes available on
     * a phone with no push channel at all, and even then it is an explicit
     * opt-in, because the permanent notice Android charges for it is not
     * something everyone will pay.
     */
    private fun watchDelivery() {
        scope.launch {
            combine(_session, _settings, _pushReady) { session, settings, push ->
                session is SessionState.Active &&
                    settings.notifications &&
                    settings.keepActive &&
                    !push
            }
                .distinctUntilChanged()
                .collect { wanted -> applyDelivery(wanted) }
        }
    }

    private fun applyDelivery(wanted: Boolean) {
        val intent = Intent(app, DeliveryService::class.java)
        runCatching {
            if (wanted) ContextCompat.startForegroundService(app, intent)
            else app.stopService(intent)
        }.onFailure {
            // Android refuses a service start from the background; the next
            // foreground pass through this flow picks it up.
            Log.w(TAG, "background delivery unavailable: ${it.message}")
        }
    }

    // -------------------------------------------------------------- discovery

    suspend fun lookup(username: String): DirectoryUser? {
        val user = api.user(username.removePrefix("@").trim().lowercase())
        if (user != null && user.publicKey.isNotEmpty()) rememberKey(user.username, user.publicKey)
        return user
    }

    suspend fun search(query: String): List<DirectoryUser> {
        val me = account()?.username
        return api.search(query)
            .filter { it.username != me }
            .onEach { if (it.publicKey.isNotEmpty()) rememberKey(it.username, it.publicKey) }
    }

    fun knowsKey(peer: String): Boolean = peerKeys.containsKey(peer)

    private fun rememberKey(username: String, publicKey: String) {
        val previous = peerKeys[username]
        if (previous == publicKey) return
        peerKeys[username] = publicKey
        conversationKeys.remove(username)
        // Learning a key for the first time is ordinary. Learning a *different*
        // one for somebody already known is the shape of an interception, and
        // is the one event Cipher refuses to let past quietly.
        if (previous != null && previous.isNotEmpty()) raiseKeyAlarm(username)
        refreshVerified()
        persist()
    }

    // ----------------------------------------------------------- verification

    /**
     * The peer's key is not the one that was there before.
     *
     * Any verification is void from this moment, the conversation carries a
     * note where it happened, and the phone says so out loud — a key swap is
     * exactly what somebody reading the conversation would have to do.
     */
    private fun raiseKeyAlarm(peer: String) {
        verifiedKeys.remove(peer)
        _keyAlarms.value = _keyAlarms.value + peer
        note(peer, "@$peer's security code changed. Verify it again before you send anything private.")
        if (_settings.value.notifications) notifier.notifyKeyChange(peer)
    }

    private fun refreshVerified() {
        _verified.value = verifiedKeys
            .filter { (peer, key) -> peerKeys[peer] == key }
            .keys
            .toSet()
    }

    /** The public key Cipher is currently encrypting to, if it has one. */
    fun peerKey(peer: String): String? = peerKeys[peer]

    /** Fingerprint of the peer's real key — not a hash of their name. */
    fun keyFingerprint(peer: String): String? = peerKeys[peer]?.let { CryptoBox.fingerprint(it) }

    fun myKeyFingerprint(): String? = account()?.publicKey?.let { CryptoBox.fingerprint(it) }

    /** The code both phones must show for this conversation, in groups of four. */
    fun safetyNumber(peer: String): List<String>? {
        val mine = account()?.publicKey ?: return null
        val theirs = peerKeys[peer] ?: return null
        return CryptoBox.safetyNumber(mine, theirs)
    }

    fun safetyCode(peer: String): String? {
        val mine = account()?.publicKey ?: return null
        val theirs = peerKeys[peer] ?: return null
        return CryptoBox.safetyCode(mine, theirs)
    }

    /** Fetches the peer's key if this device has never seen it. */
    suspend fun ensureKey(peer: String): Boolean {
        if (peerKeys.containsKey(peer)) return true
        val fetched = api.user(peer)?.publicKey ?: return false
        if (fetched.isEmpty()) return false
        rememberKey(peer, fetched)
        return true
    }

    fun verification(peer: String): Verification = when {
        _keyAlarms.value.contains(peer) -> Verification.CHANGED
        !peerKeys.containsKey(peer) -> Verification.UNKNOWN
        _verified.value.contains(peer) -> Verification.VERIFIED
        else -> Verification.UNVERIFIED
    }

    /** Records that this exact key was checked in person. */
    fun markVerified(peer: String) {
        val key = peerKeys[peer] ?: return
        verifiedKeys[peer] = key
        _keyAlarms.value = _keyAlarms.value - peer
        refreshVerified()
        persist()
    }

    fun clearVerified(peer: String) {
        if (verifiedKeys.remove(peer) == null) return
        refreshVerified()
        persist()
    }

    /** Silences the red banner without claiming the new key was checked. */
    fun dismissKeyAlarm(peer: String) {
        if (!_keyAlarms.value.contains(peer)) return
        _keyAlarms.value = _keyAlarms.value - peer
        persist()
    }

    // ---------------------------------------------------------------- threads

    fun thread(peer: String): Thread? = _threads.value.firstOrNull { it.peer == peer }

    /** True when this thread is the one addressed to yourself. */
    fun isSelf(peer: String): Boolean = peer.isNotEmpty() && peer == account()?.username

    /**
     * Opens the notebook addressed to yourself.
     *
     * It is an ordinary encrypted conversation whose other end happens to be
     * this same account, so a note is sealed like any other message and comes
     * back with the history when the account key is used on a new phone.
     */
    fun openNotes(): String? {
        val me = account()?.username ?: return null
        if (thread(me) == null) {
            _threads.value = _threads.value + Thread(peer = me, messages = emptyList())
            persist()
        }
        return me
    }

    fun openThread(peer: String) {
        activePeer = peer
        notifier.clear(peer)
        val room = thread(peer)?.group
        if (thread(peer) == null && !isRoom(peer)) {
            _threads.value = _threads.value + Thread(
                peer = peer,
                messages = emptyList(),
                burnMinutes = _settings.value.defaultBurnMinutes
            )
            persist()
        }
        markRead(peer)
        val me = account()?.username
        scope.launch {
            if (room != null && me != null) room.others(me).forEach { conversationKey(it) }
            else if (!isRoom(peer)) conversationKey(peer)
        }
    }

    fun closeThread(peer: String) {
        if (activePeer == peer) activePeer = null
        persist()
    }

    /** Keeps an unsent draft alive when the conversation is left. */
    fun setDraft(peer: String, text: String) {
        val current = thread(peer) ?: return
        if (current.draft == text) return
        _threads.value = _threads.value.map {
            if (it.peer == peer) it.copy(draft = text) else it
        }
    }

    // ----------------------------------------------------------------- rooms

    private fun isRoom(threadId: String): Boolean = threadId.startsWith(GROUP_PREFIX)

    /** Thread id for a room, so rooms and usernames can never collide. */
    private fun roomThreadId(groupId: String): String = "$GROUP_PREFIX$groupId"

    /**
     * Opens a room. There is no group on the hub: the roster is agreed between
     * devices and every message is fanned out over the pairwise channels that
     * already exist, so a room is exactly as encrypted as a private chat.
     */
    fun createGroup(name: String, members: List<String>): String? {
        val me = account()?.username ?: return null
        if (vaultSecret == null) return null
        val group = GroupInfo(
            id = CryptoBox.randomId(),
            name = name.trim().take(GROUP_NAME_LIMIT).ifEmpty { "New room" },
            members = (listOf(me) + members.map { it.lowercase() }).distinct(),
            admin = me,
            token = CryptoBox.randomToken(),
            version = 1L,
            createdAt = System.currentTimeMillis()
        )
        val threadId = roomThreadId(group.id)
        _threads.value = _threads.value + Thread(
            peer = threadId,
            messages = emptyList(),
            group = group,
            burnMinutes = _settings.value.defaultBurnMinutes
        )
        note(threadId, "You created ${group.name}")
        pushRoster(group, "@$me created ${group.name}")
        persist()
        return threadId
    }

    /** Adds people to a room and pushes the new roster to everyone. */
    fun addMembers(threadId: String, users: List<String>) {
        val group = thread(threadId)?.group ?: return
        val me = account()?.username ?: return
        val additions = users
            .map { it.removePrefix("@").trim().lowercase() }
            .filter { it.isNotEmpty() && !group.members.contains(it) }
            .distinct()
        if (additions.isEmpty()) return
        val next = group.copy(
            members = group.members + additions,
            version = group.version + 1
        )
        update(threadId) { it.copy(group = next) }
        val line = additions.joinToString(", ") { "@$it" } + " added by @$me"
        note(threadId, line)
        pushRoster(next, line)
    }

    fun renameGroup(threadId: String, name: String) {
        val group = thread(threadId)?.group ?: return
        val clean = name.trim().take(GROUP_NAME_LIMIT)
        if (clean.isEmpty() || clean == group.name) return
        val next = group.copy(name = clean, version = group.version + 1)
        update(threadId) { it.copy(group = next) }
        note(threadId, "Room renamed to $clean")
        pushRoster(next, "Room renamed to $clean")
    }

    /** Tells the room I am gone, then forgets it on this device. */
    fun leaveGroup(threadId: String) {
        val group = thread(threadId)?.group ?: return
        val me = account()?.username ?: return
        val envelope = MessageEnvelope(
            text = "",
            kind = KIND_LEAVE,
            g = group.id,
            sys = "@$me left"
        )
        group.others(me).forEach { sendControl(it, envelope) }
        deleteThread(threadId)
    }

    /**
     * Joins a room from its invite link by asking the inviter to vouch. The link
     * carries the room secret; a member checks it before admitting anyone, so a
     * guessed room id opens nothing.
     */
    fun joinGroup(invite: Invite.Room): String? {
        val me = account()?.username ?: return null
        if (invite.inviter == me || vaultSecret == null) return null
        val threadId = roomThreadId(invite.id)
        if (thread(threadId) == null) {
            _threads.value = _threads.value + Thread(
                peer = threadId,
                messages = emptyList(),
                group = GroupInfo(
                    id = invite.id,
                    name = invite.name.ifEmpty { "Room" },
                    members = listOf(invite.inviter, me),
                    admin = invite.inviter,
                    token = invite.token,
                    version = 0L,
                    createdAt = System.currentTimeMillis()
                )
            )
            note(threadId, "Asking @${invite.inviter} to let you in…")
            persist()
        }
        sendControl(
            invite.inviter,
            MessageEnvelope(text = "", kind = KIND_JOIN, g = invite.id, gt = invite.token)
        )
        return threadId
    }

    /** The room's shareable link, or null when this thread is not a room. */
    fun groupLink(threadId: String): String? {
        val group = thread(threadId)?.group ?: return null
        val me = account()?.username ?: return null
        return Invites.roomLink(group.id, group.token, group.name, me)
    }

    private fun rosterEnvelope(group: GroupInfo, line: String?): MessageEnvelope = MessageEnvelope(
        text = "",
        kind = KIND_ROSTER,
        g = group.id,
        gn = group.name,
        gm = group.members,
        ga = group.admin,
        gt = group.token,
        gv = group.version,
        sys = line
    )

    private fun pushRoster(group: GroupInfo, line: String?) {
        val me = account()?.username ?: return
        val envelope = rosterEnvelope(group, line)
        group.others(me).forEach { sendControl(it, envelope) }
    }

    /** A centred room note, held on this device only. */
    private fun note(threadId: String, text: String) {
        if (thread(threadId) == null) return
        val now = System.currentTimeMillis()
        update(threadId) { thread ->
            if (thread.messages.any { it.system && it.text == text && now - it.at < 60_000L }) thread
            else thread.copy(
                messages = thread.messages + Message(
                    id = "s-$now-${(0..9999).random()}",
                    text = text,
                    outgoing = false,
                    at = now,
                    state = DeliveryState.READ,
                    system = true
                )
            )
        }
    }

    /** Sends a payload that mutates state instead of appearing as a bubble. */
    private fun sendControl(to: String, envelope: MessageEnvelope) {
        if (to == account()?.username) return
        scope.launch {
            val key = conversationKey(to) ?: return@launch
            val now = System.currentTimeMillis()
            transmit(
                buildJsonObject {
                    put("t", "send")
                    put("id", "c-$now-${(0..99999).random()}")
                    put("to", to)
                    put("cipher", CryptoBox.encrypt(encodeEnvelope(envelope), key))
                    put("at", now)
                }
            )
        }
    }

    private fun wireId(messageId: String, member: String): String =
        "$messageId$WIRE_SEPARATOR$member"

    /** Sends one room message as a separate sealed copy per member. */
    private suspend fun fanOut(
        group: GroupInfo,
        messageId: String,
        envelope: MessageEnvelope,
        at: Long,
        burnMinutes: Int?,
        blobs: Map<String, String> = emptyMap()
    ) {
        val me = account()?.username ?: return
        group.others(me).forEach { member ->
            val key = conversationKey(member) ?: return@forEach
            val body = blobs[member]?.let { envelope.copy(blob = it) } ?: envelope
            transmit(
                buildJsonObject {
                    put("t", "send")
                    put("id", wireId(messageId, member))
                    put("to", member)
                    put("cipher", CryptoBox.encrypt(encodeEnvelope(body), key))
                    put("at", at)
                    blobs[member]?.let { put("blob", it) }
                    envelope.qid?.let { put("reply", it) }
                    if (burnMinutes != null) put("ttl", burnMinutes * 60L)
                }
            )
        }
    }

    /**
     * Compresses, seals and sends a photo. The image is encrypted under a
     * one-off media key before it is uploaded; that key travels inside the
     * message envelope, which is itself encrypted for the conversation.
     */
    fun sendPhoto(
        peer: String,
        uri: Uri,
        caption: String = "",
        replyTo: Message? = null,
        edit: PhotoEdit = PhotoEdit(),
        locked: Boolean = false,
        stamp: Long = System.currentTimeMillis()
    ) {
        val account = account() ?: return
        if (vaultSecret == null) return
        if (_blocked.value.contains(peer)) {
            _lastError.value = "Unblock @$peer to send photos."
            return
        }

        val now = stamp
        val existing = thread(peer)
        val room = existing?.group
        val burnMinutes = existing?.burnMinutes
        val messageId = "m-$now-${(0..9999).random()}"
        val blobId = "b-$now-${(0..99999).random()}"
        val placeholder = Message(
            id = messageId,
            text = caption.trim(),
            outgoing = true,
            at = now,
            state = DeliveryState.PENDING,
            replyTo = replyTo?.id,
            replyText = replyTo?.preview?.take(QUOTE_LIMIT),
            expiresAt = burnMinutes?.let { now + it * 60_000L } ?: 0L,
            uploading = true,
            from = if (room != null) account.username else null
        )
        if (existing == null) {
            if (isRoom(peer)) return
            _threads.value = _threads.value + Thread(peer = peer, messages = emptyList())
        }
        update(peer) { it.copy(messages = it.messages + placeholder, unread = 0, draft = "") }
        setTyping(peer, false)

        scope.launch {
            val prepared = photos.prepare(uri, edit)
            if (prepared == null) {
                failPhoto(peer, messageId, "That image could not be read.")
                return@launch
            }
            val targets = room?.others(account.username) ?: listOf(peer)
            if (targets.isEmpty()) {
                failPhoto(peer, messageId, "This room has nobody else in it yet.")
                return@launch
            }

            val mediaKey = CryptoBox.generateMediaKey()
            val sealed = CryptoBox.encryptBytes(prepared.bytes, mediaKey)
            val blobs = mutableMapOf<String, String>()
            targets.forEachIndexed { index, member ->
                val id = if (room == null) blobId else "$blobId-$index"
                if (api.putBlob(account.authDigest, id, member, sealed)) blobs[member] = id
            }
            val localBlob = blobs.values.firstOrNull()
            if (localBlob == null) {
                failPhoto(peer, messageId, "The photo could not be uploaded.")
                return@launch
            }
            photos.store(localBlob, sealed, prepared.bytes)

            val ref = PhotoRef(
                blob = localBlob,
                mediaKey = CryptoBox.encodeSecret(mediaKey),
                width = prepared.width,
                height = prepared.height,
                thumb = prepared.thumb,
                locked = locked
            )
            update(peer) { thread ->
                thread.copy(
                    messages = thread.messages.map { message ->
                        if (message.id != messageId) message
                        else message.copy(photo = ref, uploading = false)
                    }
                )
            }

            val envelope = envelopeFor(
                placeholder.copy(photo = ref, uploading = false),
                room
            )
            if (room != null) {
                fanOut(room, messageId, envelope, placeholder.at, burnMinutes, blobs)
                return@launch
            }
            val conversation = conversationKey(peer)
            if (conversation == null) {
                failPhoto(peer, messageId, "Could not find the key for @$peer.")
                return@launch
            }
            transmit(
                buildJsonObject {
                    put("t", "send")
                    put("id", messageId)
                    put("to", peer)
                    put("cipher", CryptoBox.encrypt(encodeEnvelope(envelope), conversation))
                    put("at", placeholder.at)
                    put("blob", ref.blob)
                    if (placeholder.replyTo != null) put("reply", placeholder.replyTo)
                    if (burnMinutes != null) put("ttl", burnMinutes * 60L)
                }
            )
        }
    }

    private fun failAttachment(peer: String, messageId: String, reason: String) =
        failPhoto(peer, messageId, reason)

    private fun failPhoto(peer: String, messageId: String, reason: String) {
        update(peer) { it.copy(messages = it.messages.filterNot { m -> m.id == messageId }) }
        _lastError.value = reason
    }

    /**
     * Seals and sends a voice note.
     *
     * The audio is already in memory when it arrives here, encrypted under a
     * one-off media key before it is uploaded, exactly like a photo. The
     * waveform and the length travel inside the encrypted envelope, so the hub
     * cannot even tell how long a recording is.
     */
    fun sendVoice(peer: String, recording: Recording, replyTo: Message? = null) {
        val account = account() ?: return
        if (vaultSecret == null) return
        if (_blocked.value.contains(peer)) {
            _lastError.value = "Unblock @$peer to send voice messages."
            return
        }

        val now = System.currentTimeMillis()
        val existing = thread(peer)
        val room = existing?.group
        val burnMinutes = existing?.burnMinutes
        val messageId = "m-$now-${(0..9999).random()}"
        val blobId = "v-$now-${(0..99999).random()}"
        val placeholder = Message(
            id = messageId,
            text = "",
            outgoing = true,
            at = now,
            state = DeliveryState.PENDING,
            replyTo = replyTo?.id,
            replyText = replyTo?.preview?.take(QUOTE_LIMIT),
            expiresAt = burnMinutes?.let { now + it * 60_000L } ?: 0L,
            uploading = true,
            from = if (room != null) account.username else null
        )
        if (existing == null) {
            if (isRoom(peer)) return
            _threads.value = _threads.value + Thread(peer = peer, messages = emptyList())
        }
        update(peer) { it.copy(messages = it.messages + placeholder, unread = 0) }
        setTyping(peer, false)

        scope.launch {
            val targets = room?.others(account.username) ?: listOf(peer)
            if (targets.isEmpty()) {
                failAttachment(peer, messageId, "This room has nobody else in it yet.")
                return@launch
            }

            val mediaKey = CryptoBox.generateMediaKey()
            val sealed = CryptoBox.encryptBytes(recording.bytes, mediaKey)
            val blobs = mutableMapOf<String, String>()
            targets.forEachIndexed { index, member ->
                val id = if (room == null) blobId else "$blobId-$index"
                if (api.putBlob(account.authDigest, id, member, sealed)) blobs[member] = id
            }
            val localBlob = blobs.values.firstOrNull()
            if (localBlob == null) {
                failAttachment(peer, messageId, "That voice message could not be uploaded.")
                return@launch
            }
            voices.store(localBlob, sealed, recording.bytes)

            val ref = VoiceRef(
                blob = localBlob,
                mediaKey = CryptoBox.encodeSecret(mediaKey),
                durationMs = recording.durationMs,
                levels = recording.levels
            )
            update(peer) { thread ->
                thread.copy(
                    messages = thread.messages.map { message ->
                        if (message.id != messageId) message
                        else message.copy(voice = ref, uploading = false)
                    }
                )
            }

            val envelope = envelopeFor(
                placeholder.copy(voice = ref, uploading = false),
                room
            )
            if (room != null) {
                fanOut(room, messageId, envelope, placeholder.at, burnMinutes, blobs)
                return@launch
            }
            val conversation = conversationKey(peer)
            if (conversation == null) {
                failAttachment(peer, messageId, "Could not find the key for @$peer.")
                return@launch
            }
            transmit(
                buildJsonObject {
                    put("t", "send")
                    put("id", messageId)
                    put("to", peer)
                    put("cipher", CryptoBox.encrypt(encodeEnvelope(envelope), conversation))
                    put("at", placeholder.at)
                    put("blob", ref.blob)
                    if (placeholder.replyTo != null) put("reply", placeholder.replyTo)
                    if (burnMinutes != null) put("ttl", burnMinutes * 60L)
                }
            )
        }
    }

    /**
     * Seals and sends a file.
     *
     * The document is read, sealed under a one-off media key and only then
     * uploaded, exactly like a photo. Where the sealed bytes land is the hub's
     * decision: an R2 bucket when the project has one configured, its own
     * chunked storage otherwise. Either way the store holds ciphertext it
     * cannot open, under a name it never learns — the file's real name travels
     * inside the encrypted envelope.
     */
    fun sendFile(peer: String, uri: Uri, replyTo: Message? = null) {
        val account = account() ?: return
        if (vaultSecret == null) return
        if (_blocked.value.contains(peer)) {
            _lastError.value = "Unblock @$peer to send files."
            return
        }

        val now = System.currentTimeMillis()
        val existing = thread(peer)
        val room = existing?.group
        val burnMinutes = existing?.burnMinutes
        val messageId = "m-$now-${(0..9999).random()}"
        val blobId = "f-$now-${(0..99999).random()}"
        val placeholder = Message(
            id = messageId,
            text = "",
            outgoing = true,
            at = now,
            state = DeliveryState.PENDING,
            replyTo = replyTo?.id,
            replyText = replyTo?.preview?.take(QUOTE_LIMIT),
            expiresAt = burnMinutes?.let { now + it * 60_000L } ?: 0L,
            uploading = true,
            from = if (room != null) account.username else null
        )
        if (existing == null) {
            if (isRoom(peer)) return
            _threads.value = _threads.value + Thread(peer = peer, messages = emptyList())
        }
        update(peer) { it.copy(messages = it.messages + placeholder, unread = 0) }
        setTyping(peer, false)

        scope.launch {
            val picked = files.inspect(uri)
            if (picked == null) {
                failAttachment(peer, messageId, "That file could not be read.")
                return@launch
            }
            val bytes = files.read(uri)
            if (bytes == null || bytes.isEmpty()) {
                failAttachment(peer, messageId, "That file could not be read.")
                return@launch
            }
            val targets = room?.others(account.username) ?: listOf(peer)
            if (targets.isEmpty()) {
                failAttachment(peer, messageId, "This room has nobody else in it yet.")
                return@launch
            }

            val mediaKey = CryptoBox.generateMediaKey()
            val sealed = CryptoBox.encryptBytes(bytes, mediaKey)
            val blobs = mutableMapOf<String, String>()
            var refusal: String? = null
            targets.forEachIndexed { index, member ->
                val id = if (room == null) blobId else "$blobId-$index"
                when (val result = uploadFile(account.authDigest, id, member, sealed)) {
                    is FileUpload.Done -> blobs[member] = id
                    is FileUpload.TooLarge -> refusal =
                        "That file is too large. The limit is ${formatBytes(result.limit)}."
                    FileUpload.Failed -> Unit
                }
            }
            val localBlob = blobs.values.firstOrNull()
            if (localBlob == null) {
                failAttachment(peer, messageId, refusal ?: "That file could not be uploaded.")
                return@launch
            }
            files.store(localBlob, sealed)

            val ref = FileRef(
                blob = localBlob,
                mediaKey = CryptoBox.encodeSecret(mediaKey),
                name = picked.name,
                size = bytes.size.toLong(),
                mime = picked.mime
            )
            update(peer) { thread ->
                thread.copy(
                    messages = thread.messages.map { message ->
                        if (message.id != messageId) message
                        else message.copy(file = ref, uploading = false)
                    }
                )
            }

            val envelope = envelopeFor(placeholder.copy(file = ref, uploading = false), room)
            if (room != null) {
                fanOut(room, messageId, envelope, placeholder.at, burnMinutes, blobs)
                return@launch
            }
            val conversation = conversationKey(peer)
            if (conversation == null) {
                failAttachment(peer, messageId, "Could not find the key for @$peer.")
                return@launch
            }
            transmit(
                buildJsonObject {
                    put("t", "send")
                    put("id", messageId)
                    put("to", peer)
                    put("cipher", CryptoBox.encrypt(encodeEnvelope(envelope), conversation))
                    put("at", placeholder.at)
                    put("blob", ref.blob)
                    if (placeholder.replyTo != null) put("reply", placeholder.replyTo)
                    if (burnMinutes != null) put("ttl", burnMinutes * 60L)
                }
            )
        }
    }

    private sealed interface FileUpload {
        data object Done : FileUpload
        data object Failed : FileUpload
        data class TooLarge(val limit: Long) : FileUpload
    }

    /** Pushes sealed bytes wherever the hub says they belong. */
    private suspend fun uploadFile(
        authDigest: String,
        id: String,
        to: String,
        sealed: String
    ): FileUpload {
        val target = api.beginFile(authDigest, id, to, sealed.length.toLong())
            ?: return FileUpload.Failed
        if (target.mode == "too-large") return FileUpload.TooLarge(target.limit)

        if (target.mode == "r2" && target.put != null) {
            val sent = api.putToUrl(target.put, sealed.toByteArray(Charsets.UTF_8))
            if (!sent) return FileUpload.Failed
            return if (api.commitFile(authDigest, id, 1)) FileUpload.Done else FileUpload.Failed
        }

        val size = target.chunk.coerceAtLeast(1)
        val total = (sealed.length + size - 1) / size
        for (seq in 0 until total) {
            val slice = sealed.substring(seq * size, minOf((seq + 1) * size, sealed.length))
            if (!api.putFileChunk(authDigest, id, seq, slice)) return FileUpload.Failed
        }
        return if (api.commitFile(authDigest, id, total)) FileUpload.Done else FileUpload.Failed
    }

    /**
     * Fetches and decrypts a file, using the encrypted disk cache first.
     *
     * The bytes are returned in memory. Nothing readable touches disk unless
     * the user then chooses to open or save the file.
     */
    suspend fun fileBytes(ref: FileRef): ByteArray? {
        val key = CryptoBox.decodeSecret(ref.mediaKey) ?: return null
        files.fromCache(ref.blob, key)?.let { return it }
        val account = account() ?: return null

        val target = api.openFile(account.username, account.authDigest, ref.blob) ?: return null
        val sealed = if (target.mode == "r2" && target.get != null) {
            api.getFromUrl(target.get)?.toString(Charsets.UTF_8)
        } else {
            val builder = StringBuilder()
            for (seq in 0 until target.chunks) {
                val part = api.getFileChunk(
                    account.username,
                    account.authDigest,
                    ref.blob,
                    seq
                ) ?: return null
                builder.append(part)
            }
            builder.toString()
        } ?: return null

        val plain = CryptoBox.decryptBytes(sealed, key) ?: return null
        files.store(ref.blob, sealed)
        return plain
    }

    /** True when a file has already been downloaded and can be opened offline. */
    fun fileReady(ref: FileRef): Boolean = files.isCached(ref.blob)

    /**
     * Decrypts a file and hands it to another app.
     *
     * This is the one moment Cipher writes plaintext out, and it happens only
     * because the user asked for the file. The staged copy lives in a private
     * directory that is wiped when the vault locks.
     */
    suspend fun openFile(ref: FileRef): Uri? {
        val bytes = fileBytes(ref)
        if (bytes == null) {
            _lastError.value = "That file could not be opened."
            return null
        }
        return files.stageForOpen(ref.name, bytes)
    }

    /** Copies a file into the phone's Downloads folder at the user's request. */
    suspend fun saveFile(ref: FileRef): Boolean {
        val bytes = fileBytes(ref)
        if (bytes == null) {
            _lastError.value = "That file could not be downloaded."
            return false
        }
        val saved = files.saveToDownloads(ref.name, ref.mime, bytes)
        if (saved == null) {
            _lastError.value = "That file could not be saved."
            return false
        }
        return true
    }

    /** Fetches and decrypts a voice note, using the encrypted disk cache first. */
    suspend fun voiceBytes(ref: VoiceRef): ByteArray? {
        val key = CryptoBox.decodeSecret(ref.mediaKey) ?: return null
        voices.fromCache(ref.blob, key)?.let { return it }
        val account = account() ?: return null
        val sealed = api.getBlob(account.username, account.authDigest, ref.blob) ?: return null
        val plain = CryptoBox.decryptBytes(sealed, key) ?: return null
        voices.store(ref.blob, sealed, plain)
        return plain
    }

    /**
     * Play, pause or resume a voice note, whichever the tap means.
     *
     * A note that has never been opened is fetched and decrypted first, and its
     * bubble shows that it is working rather than looking dead on the tap.
     */
    fun toggleVoice(ref: VoiceRef) {
        val current = playbackFor(ref.blob)
        if (current != null && !current.loading) {
            if (current.playing) player.pause() else player.resume()
            return
        }
        if (current?.loading == true) return
        val ready = voices.cached(ref.blob)
        if (ready != null) {
            player.play(ref.blob, ready, ref.durationMs)
            return
        }
        player.loading(ref.blob, ref.durationMs)
        scope.launch {
            val bytes = voiceBytes(ref)
            if (bytes == null) {
                player.stop()
                _lastError.value = "That voice message could not be opened."
            } else {
                player.play(ref.blob, bytes, ref.durationMs)
            }
        }
    }

    private fun playbackFor(blob: String): VoicePlayback? =
        player.state.value?.takeIf { it.blob == blob }

    /** @param fraction where in the open note to jump to, 0..1. */
    fun seekVoice(fraction: Float) = player.seek(fraction)

    fun stopVoice() = player.stop()

    /** Fetches and decrypts an attachment, using the encrypted disk cache first. */
    suspend fun photoBytes(ref: PhotoRef): ByteArray? {
        val key = CryptoBox.decodeSecret(ref.mediaKey) ?: return null
        photos.fromCache(ref.blob, key)?.let { return it }
        val account = account() ?: return null
        val sealed = api.getBlob(account.username, account.authDigest, ref.blob) ?: return null
        val plain = CryptoBox.decryptBytes(sealed, key) ?: return null
        photos.store(ref.blob, sealed, plain)
        return plain
    }

    /**
     * @param burnAfterMinutes a self-destruct clock for this one message,
     *   overriding whatever the thread's own timer says.
     */
    fun sendMessage(
        peer: String,
        text: String,
        replyTo: Message? = null,
        secret: Boolean = false,
        burnAfterMinutes: Int? = null
    ): String? {
        val body = text.trim()
        if (body.isEmpty() || vaultSecret == null) return null
        if (_blocked.value.contains(peer)) {
            _lastError.value = "Unblock @$peer to send messages."
            return null
        }
        val now = System.currentTimeMillis()
        val existing = thread(peer)
        val room = existing?.group
        val burnMinutes = burnAfterMinutes ?: existing?.burnMinutes
        val message = Message(
            id = "m-$now-${(0..9999).random()}",
            text = body,
            outgoing = true,
            at = now,
            state = DeliveryState.PENDING,
            replyTo = replyTo?.id,
            replyText = replyTo?.preview?.take(QUOTE_LIMIT),
            expiresAt = burnMinutes?.let { now + it * 60_000L } ?: 0L,
            secret = secret,
            from = if (room != null) account()?.username else null
        )
        if (existing == null) {
            if (isRoom(peer)) return null
            _threads.value = _threads.value + Thread(peer = peer, messages = emptyList())
        }
        update(peer) { it.copy(messages = it.messages + message, unread = 0, draft = "") }
        setTyping(peer, false)

        scope.launch {
            val envelope = envelopeFor(message, room)
            if (room != null) {
                fanOut(room, message.id, envelope, message.at, burnMinutes)
                return@launch
            }
            val key = conversationKey(peer)
            if (key == null) {
                Log.w(TAG, "no conversation key for $peer")
                _lastError.value = "Could not find the key for @$peer."
                return@launch
            }
            transmit(
                buildJsonObject {
                    put("t", "send")
                    put("id", message.id)
                    put("to", peer)
                    put("cipher", CryptoBox.encrypt(encodeEnvelope(envelope), key))
                    put("at", message.at)
                    if (message.replyTo != null) put("reply", message.replyTo)
                    if (burnMinutes != null) put("ttl", burnMinutes * 60L)
                }
            )
        }
        return message.id
    }

    // ------------------------------------------------- replying from the shade

    /** True while this phone can actually seal a message without being opened. */
    private fun canReply(): Boolean = vaultSecret != null && account() != null

    /**
     * Sends a reply typed straight into the notification shade.
     *
     * The text is sealed here, on the phone, exactly as it would be from the
     * conversation itself. A sealed vault cannot encrypt anything, so in that
     * state the alert says to open Cipher instead of quietly losing the words.
     *
     * @return true once the hub has taken the message, false if it could not
     *   be sealed or never left the phone in time.
     */
    suspend fun replyFromNotification(threadId: String, text: String): Boolean {
        if (!canReply()) return false
        val id = sendMessage(threadId, text) ?: return false
        markRead(threadId)
        wake("reply")
        // The process was woken only for this broadcast, so the send has to be
        // waited on: returning early would let Android freeze the app with the
        // message still queued on this device.
        return withTimeoutOrNull(REPLY_WAIT_MS) {
            _threads.first { list ->
                val sent = list.firstOrNull { it.peer == threadId }
                    ?.messages
                    ?.firstOrNull { it.id == id }
                sent != null && sent.state != DeliveryState.PENDING
            }
            true
        } ?: false
    }

    /** Says in the shade what became of a reply typed there. */
    fun reportReply(threadId: String, title: String, text: String, sent: Boolean) {
        when {
            sent -> notifier.notifyReplySent(
                threadId = threadId,
                title = title,
                text = text,
                preview = _settings.value.notificationPreview
            )
            else -> notifier.notifyReplyStuck(threadId, title, sealed = !canReply())
        }
    }

    /** Clears a conversation's alert and tells the peer it was read. */
    fun readFromNotification(threadId: String) {
        notifier.clear(threadId)
        if (thread(threadId) == null) return
        markRead(threadId)
    }

    /** The sealed body for a message, including its room stamp when in a room. */
    private fun envelopeFor(message: Message, room: GroupInfo?): MessageEnvelope = MessageEnvelope(
        text = message.text,
        qid = message.replyTo,
        q = message.replyText,
        kind = when {
            message.voice != null -> VOICE_KIND
            message.photo != null -> PHOTO_KIND
            message.file != null -> FILE_KIND
            else -> null
        },
        blob = message.voice?.blob ?: message.photo?.blob ?: message.file?.blob,
        mk = message.voice?.mediaKey ?: message.photo?.mediaKey ?: message.file?.mediaKey,
        w = message.photo?.width ?: 0,
        h = message.photo?.height ?: 0,
        thumb = message.photo?.thumb,
        pl = message.photo?.locked == true,
        s = message.secret,
        g = room?.id,
        gn = room?.name,
        gm = room?.members ?: emptyList(),
        ga = room?.admin,
        gv = room?.version ?: 0L,
        mid = if (room != null) message.id else null,
        vd = message.voice?.durationMs ?: 0L,
        vl = message.voice?.let { packLevels(it.levels) },
        fn = message.file?.name,
        fs = message.file?.size ?: 0L,
        fm = message.file?.mime
    )

    /**
     * Sends a batch picked in one go.
     *
     * Each photo is its own message, in the order they were chosen, and the
     * caption written in the preview rides with the first one — the same way a
     * batch reads everywhere else. The stamps are spaced by a millisecond so
     * the batch cannot shuffle itself while the uploads race.
     */
    fun sendPhotos(
        peer: String,
        drafts: List<PhotoDraft>,
        caption: String = "",
        replyTo: Message? = null
    ) {
        if (drafts.isEmpty()) return
        val base = System.currentTimeMillis()
        drafts.forEachIndexed { index, draft ->
            sendPhoto(
                peer = peer,
                uri = draft.uri,
                caption = if (index == 0) caption else "",
                replyTo = if (index == 0) replyTo else null,
                edit = draft.edit,
                locked = draft.locked,
                stamp = base + index
            )
        }
    }

    /** A screen-sized rehearsal of a picked image, for the send preview. */
    suspend fun previewPhoto(uri: Uri, edit: PhotoEdit, maxEdge: Int): Bitmap? =
        photos.preview(uri, edit, maxEdge)

    /**
     * Copies a photo into the phone's gallery, if its sender allowed that.
     *
     * A locked photo is refused here as well as in the UI: the decision belongs
     * to whoever sent it, and it must not depend on a button being hidden.
     */
    suspend fun savePhotoToGallery(ref: PhotoRef): PhotoSaveResult {
        if (ref.locked) return PhotoSaveResult.LOCKED
        val bytes = photoBytes(ref) ?: return PhotoSaveResult.UNREADABLE
        return if (photos.saveToGallery(bytes)) PhotoSaveResult.SAVED
        else PhotoSaveResult.WRITE_FAILED
    }

    /**
     * Rewrites one of my own messages. The superseded text is kept on both
     * devices so the edit can be audited, and travels as a control payload
     * rather than a new bubble.
     */
    fun editMessage(threadId: String, messageId: String, text: String) {
        val thread = thread(threadId) ?: return
        val message = thread.messages.firstOrNull { it.id == messageId } ?: return
        val body = text.trim().take(MESSAGE_LIMIT)
        if (!message.editable || body.isEmpty() || body == message.text) return
        val now = System.currentTimeMillis()
        update(threadId) { current ->
            current.copy(
                messages = current.messages.map {
                    if (it.id != messageId) it
                    else it.copy(
                        text = body,
                        edits = it.edits + MessageEdit(it.text, now),
                        editedAt = now
                    )
                }
            )
        }
        val room = thread.group
        val envelope = MessageEnvelope(
            text = body,
            kind = KIND_EDIT,
            eid = messageId,
            eat = now,
            g = room?.id
        )
        val me = account()?.username
        if (room != null && me != null) room.others(me).forEach { sendControl(it, envelope) }
        else sendControl(threadId, envelope)
    }

    /**
     * Re-encrypts and re-transmits a message that never reached the hub. The hub
     * de-duplicates by id, so a double send is harmless.
     */
    fun resend(peer: String, messageId: String) {
        val thread = thread(peer) ?: return
        val message = thread.messages.firstOrNull { it.id == messageId } ?: return
        if (!message.outgoing || message.state != DeliveryState.PENDING) return
        if (message.uploading || message.system) return
        val room = thread.group
        // An attachment that finished uploading is worth pushing again: the
        // bytes are already in the store, so this only re-sends the envelope
        // that points at them. A room copy is not, because each member got a
        // separate blob and those ids are not kept once the fan-out is done.
        val blob = message.photo?.blob ?: message.voice?.blob ?: message.file?.blob
        if (blob != null && room != null) return
        val burnMinutes = thread.burnMinutes
        scope.launch {
            val envelope = envelopeFor(message, room)
            if (room != null) {
                fanOut(room, message.id, envelope, message.at, burnMinutes)
                return@launch
            }
            val key = conversationKey(peer) ?: return@launch
            transmit(
                buildJsonObject {
                    put("t", "send")
                    put("id", message.id)
                    put("to", peer)
                    put("cipher", CryptoBox.encrypt(encodeEnvelope(envelope), key))
                    put("at", message.at)
                    if (blob != null) put("blob", blob)
                    if (message.replyTo != null) put("reply", message.replyTo)
                    if (burnMinutes != null) put("ttl", burnMinutes * 60L)
                }
            )
        }
    }

    /** Flushes every message still stuck on PENDING after a reconnect. */
    private fun retryPending() {
        _threads.value.forEach { thread ->
            thread.messages
                .filter {
                    it.outgoing &&
                        it.state == DeliveryState.PENDING &&
                        !it.uploading &&
                        !it.system
                }
                .forEach { resend(thread.peer, it.id) }
        }
    }

    /** Toggles my encrypted emoji reaction on a message. */
    fun react(peer: String, messageId: String, emoji: String) {
        val me = account()?.username ?: return
        val current = thread(peer)?.messages?.firstOrNull { it.id == messageId } ?: return
        val next = if (current.reactions[me] == emoji) "" else emoji

        update(peer) { thread ->
            thread.copy(
                messages = thread.messages.map { message ->
                    if (message.id != messageId) message
                    else {
                        val reactions = message.reactions.toMutableMap()
                        if (next.isEmpty()) reactions.remove(me) else reactions[me] = next
                        message.copy(reactions = reactions.toMap())
                    }
                }
            )
        }
        val room = thread(peer)?.group
        if (room != null) {
            val envelope = MessageEnvelope(
                text = next,
                kind = KIND_REACT,
                eid = messageId,
                g = room.id,
                eat = System.currentTimeMillis()
            )
            room.others(me).forEach { sendControl(it, envelope) }
            return
        }
        scope.launch {
            val key = conversationKey(peer) ?: return@launch
            transmit(
                buildJsonObject {
                    put("t", "react")
                    put("id", messageId)
                    put("cipher", if (next.isEmpty()) "" else CryptoBox.encrypt(next, key))
                }
            )
        }
    }

    /** Retracts one of my messages on every device and on the hub. */
    fun unsend(peer: String, messageId: String) {
        val room = thread(peer)?.group
        val me = account()?.username
        val wireIds = if (room != null && me != null) {
            room.others(me).map { wireId(messageId, it) }
        } else {
            listOf(messageId)
        }
        wireIds.forEach { id ->
            transmit(
                buildJsonObject {
                    put("t", "unsend")
                    put("id", id)
                }
            )
        }
        removeMessages(setOf(messageId))
    }

    /** Removes a message from this device only. */
    fun deleteMessageLocally(peer: String, messageId: String) {
        update(peer) { current ->
            current.copy(
                messages = current.messages.filterNot { m -> m.id == messageId },
                pinnedId = current.pinnedId?.takeUnless { it == messageId }
            )
        }
    }

    /**
     * Puts a self-destruct clock on a single message, on both devices.
     *
     * A deadline can only be brought forward, never pushed back: once a message
     * is set to burn, neither side can quietly grant it a longer life.
     */
    fun burnMessage(threadId: String, messageId: String, minutes: Int) {
        val thread = thread(threadId) ?: return
        val message = thread.messages.firstOrNull { it.id == messageId } ?: return
        if (message.system) return
        val expiresAt = System.currentTimeMillis() + minutes * 60_000L
        if (message.expiresAt in 1..expiresAt) return
        applyExpiry(threadId, messageId, expiresAt)

        val room = thread.group
        val envelope = MessageEnvelope(
            text = "",
            kind = KIND_BURN,
            eid = messageId,
            eat = expiresAt,
            g = room?.id
        )
        val me = account()?.username
        if (room != null && me != null) room.others(me).forEach { sendControl(it, envelope) }
        else sendControl(threadId, envelope)
    }

    /**
     * Arms a message to burn a while after it is *read* rather than after a
     * fixed delay. The clock is started by whoever opens it, and their device
     * mirrors the resulting deadline back, so both copies die together.
     */
    fun burnMessageOnRead(threadId: String, messageId: String, afterMs: Long) {
        val thread = thread(threadId) ?: return
        val message = thread.messages.firstOrNull { it.id == messageId } ?: return
        if (message.system || message.ephemeral) return
        update(threadId) { current ->
            current.copy(
                messages = current.messages.map {
                    if (it.id == messageId) it.copy(burnOnReadMs = afterMs) else it
                }
            )
        }

        val room = thread.group
        val envelope = MessageEnvelope(
            text = "",
            kind = KIND_BURN,
            eid = messageId,
            bor = afterMs,
            g = room?.id
        )
        val me = account()?.username
        if (room != null && me != null) room.others(me).forEach { sendControl(it, envelope) }
        else sendControl(threadId, envelope)
    }

    /**
     * Starts the clock on every burn-after-reading message in a thread I have
     * just opened, and tells the other side the deadline I landed on.
     */
    private fun armReadBurns(threadId: String) {
        val thread = thread(threadId) ?: return
        val armable = thread.messages.filter { !it.outgoing && it.burnsOnRead }
        if (armable.isEmpty()) return
        val now = System.currentTimeMillis()
        val room = thread.group
        val me = account()?.username
        update(threadId) { current ->
            current.copy(
                messages = current.messages.map { message ->
                    if (armable.none { it.id == message.id }) message
                    else message.copy(expiresAt = now + message.burnOnReadMs)
                }
            )
        }
        armable.forEach { message ->
            val envelope = MessageEnvelope(
                text = "",
                kind = KIND_BURN,
                eid = message.id,
                eat = now + message.burnOnReadMs,
                g = room?.id
            )
            if (room != null && me != null) room.others(me).forEach { sendControl(it, envelope) }
            else sendControl(threadId, envelope)
        }
    }

    /**
     * Sends a copy of a message into another thread.
     *
     * A forwarded photo is re-sealed for the new recipient rather than pointing
     * at the old blob: the hub hands a blob only to the account it was uploaded
     * for, so a forward has to travel as a fresh, separately encrypted upload.
     */
    fun forwardMessage(fromThreadId: String, messageId: String, toThreadId: String) {
        val message = thread(fromThreadId)?.messages?.firstOrNull { it.id == messageId } ?: return
        if (message.system || message.uploading) return
        val voice = message.voice
        if (voice != null) {
            scope.launch {
                val bytes = voiceBytes(voice)
                if (bytes == null) {
                    _lastError.value = "That voice message could not be opened."
                    return@launch
                }
                sendVoice(
                    toThreadId,
                    Recording(bytes, voice.durationMs, voice.levels)
                )
            }
            return
        }
        val photo = message.photo
        if (photo == null) {
            sendMessage(toThreadId, message.text, null, message.secret)
            return
        }
        scope.launch {
            val bytes = photoBytes(photo)
            if (bytes == null) {
                _lastError.value = "That photo could not be opened."
                return@launch
            }
            val staged = photos.stage(bytes)
            if (staged == null) {
                _lastError.value = "That photo could not be forwarded."
                return@launch
            }
            sendPhoto(toThreadId, staged, message.text)
        }
    }

    /** Keeps one message at the top of its thread, on this device only. */
    fun setPinnedMessage(threadId: String, messageId: String?) =
        update(threadId) { it.copy(pinnedId = messageId) }

    private fun applyExpiry(threadId: String, messageId: String, expiresAt: Long) {
        update(threadId) { current ->
            current.copy(
                messages = current.messages.map {
                    if (it.id == messageId) it.copy(expiresAt = expiresAt) else it
                }
            )
        }
    }

    fun markRead(peer: String) {
        armReadBurns(peer)
        val current = thread(peer)
        if ((current?.unread ?: 0) > 0 || current?.mentioned == true) {
            update(peer) { it.copy(unread = 0, mentioned = false) }
        }
        notifier.clear(peer)
        if (isRoom(peer) || isSelf(peer)) return
        transmit(
            buildJsonObject {
                put("t", "read")
                put("peer", peer)
            }
        )
    }

    /** Throttled typing signal — at most one "on" every three seconds. */
    fun setTyping(peer: String, on: Boolean) {
        if (!_settings.value.typing || isRoom(peer) || isSelf(peer)) return
        val now = System.currentTimeMillis()
        if (on && now - lastTypingSentAt < 3_000L) return
        lastTypingSentAt = if (on) now else 0L
        transmit(
            buildJsonObject {
                put("t", "typing")
                put("to", peer)
                put("on", on)
            }
        )
    }

    fun togglePin(peer: String) = update(peer) { it.copy(pinned = !it.pinned) }

    fun toggleMute(peer: String) = update(peer) { it.copy(muted = !it.muted) }

    fun setBurn(peer: String, minutes: Int?) = update(peer) { it.copy(burnMinutes = minutes) }

    fun deleteThread(peer: String) {
        _threads.value = _threads.value.filterNot { it.peer == peer }
        _scheduled.value = _scheduled.value.filterNot { it.peer == peer }
        notifier.clear(peer)
        persist()
    }

    // ------------------------------------------------------------- scheduled

    /**
     * Queues a message to leave at [at]. Nothing is handed to the hub now: the
     * text waits encrypted in this device's vault, so an unsent message is
     * invisible to everyone until its moment comes.
     *
     * @return false when the time has already passed or there is nothing to send.
     */
    fun scheduleMessage(
        peer: String,
        text: String,
        at: Long,
        secret: Boolean = false,
        burnMinutes: Int? = null,
        replyTo: Message? = null
    ): Boolean {
        val body = text.trim().take(MESSAGE_LIMIT)
        if (body.isEmpty() || vaultSecret == null) return false
        if (at <= System.currentTimeMillis()) return false
        val item = ScheduledMessage(
            id = "s-${System.currentTimeMillis()}-${(0..9999).random()}",
            peer = peer,
            text = body,
            at = at,
            secret = secret,
            burnMinutes = burnMinutes,
            replyTo = replyTo?.id,
            replyText = replyTo?.preview?.take(QUOTE_LIMIT)
        )
        _scheduled.value = (_scheduled.value + item).sortedBy { it.at }
        setTyping(peer, false)
        persist()
        return true
    }

    /** Drops a queued message before it is ever sent. */
    fun cancelScheduled(id: String) {
        if (_scheduled.value.none { it.id == id }) return
        _scheduled.value = _scheduled.value.filterNot { it.id == id }
        persist()
    }

    /** Sends a queued message immediately instead of waiting for its clock. */
    fun sendScheduledNow(id: String) {
        val item = _scheduled.value.firstOrNull { it.id == id } ?: return
        _scheduled.value = _scheduled.value.filterNot { it.id == id }
        persist()
        dispatchScheduled(item)
    }

    /** Hands every queued message whose moment has arrived to the normal send path. */
    private fun flushScheduled(now: Long) {
        val due = _scheduled.value.filter { it.at <= now }
        if (due.isEmpty()) return
        _scheduled.value = _scheduled.value.filterNot { item -> due.any { it.id == item.id } }
        persist()
        due.forEach { dispatchScheduled(it) }
    }

    private fun dispatchScheduled(item: ScheduledMessage) {
        val quoted = item.replyTo?.let { id ->
            thread(item.peer)?.messages?.firstOrNull { it.id == id }
        }
        sendMessage(
            peer = item.peer,
            text = item.text,
            replyTo = quoted,
            secret = item.secret,
            burnAfterMinutes = item.burnMinutes
        )
    }

    fun clearMessages(peer: String) = update(peer) { it.copy(messages = emptyList()) }

    // ----------------------------------------------------------------- blocks

    fun isBlocked(peer: String): Boolean = _blocked.value.contains(peer)

    fun setBlocked(peer: String, on: Boolean) {
        _blocked.value = if (on) _blocked.value + peer else _blocked.value - peer
        if (on) {
            _typingPeers.value = _typingPeers.value - peer
            _onlinePeers.value = _onlinePeers.value - peer
        }
        persist()
        transmit(
            buildJsonObject {
                put("t", "block")
                put("user", peer)
                put("on", on)
            }
        )
    }

    // --------------------------------------------------------------- settings

    fun updateSettings(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        _settings.value = next
        if (!next.notifications) notifier.clearAll()
        persist()
        transmit(
            buildJsonObject {
                put("t", "settings")
                put("receipts", next.receipts)
                put("typing", next.typing)
                put("presence", next.presence)
                put("strangers", next.strangers)
            }
        )
    }

    // ------------------------------------------------------------- connection

    /**
     * Holds one live socket to the hub for as long as the account is unlocked.
     *
     * The loop never gives up: it waits for a network rather than retrying into
     * airplane mode, backs off with jitter after a genuine failure, and is woken
     * instantly when the app returns to the foreground or the network comes
     * back. Queued frames are owned by [outbox], not by the session, so nothing
     * is lost when a socket dies mid-write.
     */
    private fun connect(account: Account) {
        socketJob?.cancel()
        socketJob = scope.launch {
            var backoff = INITIAL_BACKOFF_MS
            while (isActive) {
                if (!network.online.value) {
                    _connection.value = ConnectionState.OFFLINE
                    network.online.first { it }
                    backoff = INITIAL_BACKOFF_MS
                }
                _connection.value = ConnectionState.CONNECTING
                val url = "${Endpoints.SOCKET_URL}?u=${account.username}&a=${account.authDigest}"
                val startedAt = System.currentTimeMillis()
                try {
                    api.socket(url) { runSession(this) }
                } catch (error: Exception) {
                    if (!isActive) break
                    Log.w(TAG, "socket dropped: ${error.message}")
                }
                live = null
                _connection.value = ConnectionState.OFFLINE
                _onlinePeers.value = emptySet()
                _typingPeers.value = emptySet()
                if (!isActive) break
                // A session that ran for a while is not a failing endpoint, so
                // an ordinary drop reconnects fast instead of inheriting the
                // backoff of an earlier outage.
                if (System.currentTimeMillis() - startedAt > HEALTHY_SESSION_MS) {
                    backoff = INITIAL_BACKOFF_MS
                }
                waitBeforeRetry(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    /** Sleeps out the backoff, but returns early the moment something wakes us. */
    private suspend fun waitBeforeRetry(backoff: Long) {
        val spread = (backoff / 3).coerceAtLeast(1L)
        val wait = (backoff + Random.nextLong(-spread, spread + 1)).coerceAtLeast(250L)
        withTimeoutOrNull(wait) { wakeSignal.receive() }
    }

    private suspend fun runSession(socket: DefaultClientWebSocketSession) = coroutineScope {
        live = socket
        lastInboundAt = System.currentTimeMillis()
        probeSentAt = 0L
        _connection.value = ConnectionState.ONLINE

        socket.send(
            Frame.Text(
                encode(
                    buildJsonObject {
                        put("t", "sync")
                        put("since", lastSeq)
                    }
                )
            )
        )
        val settings = _settings.value
        socket.send(
            Frame.Text(
                encode(
                    buildJsonObject {
                        put("t", "settings")
                        put("receipts", settings.receipts)
                        put("typing", settings.typing)
                        put("presence", settings.presence)
                        put("strangers", settings.strangers)
                    }
                )
            )
        )

        val writer = launch { pump(socket) }
        val watchdog = launch { watchSocket(socket) }
        outbox.kick()
        try {
            for (frame in socket.incoming) {
                lastInboundAt = System.currentTimeMillis()
                probeSentAt = 0L
                if (frame is Frame.Text) handleEvent(frame.readText())
            }
        } finally {
            writer.cancel()
            watchdog.cancel()
            live = null
        }
    }

    /**
     * Drains the outbox into the socket. A frame is only removed once the
     * socket accepted it; a failed write leaves it at the head of the queue and
     * ends the pump, so the next session sends it again.
     */
    private suspend fun pump(socket: DefaultClientWebSocketSession) {
        while (true) {
            val frame = outbox.head()
            if (frame == null) {
                outbox.awaitWork()
                continue
            }
            if (outbox.isStale(frame, System.currentTimeMillis())) {
                outbox.drop(frame)
                continue
            }
            val sent = runCatching { socket.send(Frame.Text(frame.payload)) }.isSuccess
            if (!sent) {
                Log.w(TAG, "write failed, keeping ${frame.kind} queued")
                return
            }
            outbox.drop(frame)
        }
    }

    /**
     * Watches for a socket that has genuinely stopped answering.
     *
     * The hub sends a beat every 25s, so a live connection is never silent for
     * long. Only after [PING_AFTER_MS] of nothing at all does one ping go out,
     * and only if that ping is still unanswered [PROBE_GRACE_MS] later is the
     * socket replaced. A quiet chat is not a broken one: nothing here cuts a
     * connection that is still talking to us.
     */
    private suspend fun watchSocket(socket: DefaultClientWebSocketSession) {
        while (true) {
            delay(WATCHDOG_TICK_MS)
            val now = System.currentTimeMillis()
            val probe = probeSentAt
            if (probe > 0L && lastInboundAt < probe && now - probe > PROBE_GRACE_MS) {
                Log.w(TAG, "hub silent for ${(now - lastInboundAt) / 1000}s, rebuilding")
                socket.cancel()
                return
            }
            if (probe == 0L && now - lastInboundAt > PING_AFTER_MS) {
                probeSentAt = now
                val ok = runCatching {
                    socket.send(Frame.Text(encode(buildJsonObject { put("t", "ping") })))
                }.isSuccess
                if (!ok) {
                    socket.cancel()
                    return
                }
            }
        }
    }

    /**
     * Called when the app comes back on screen or the network returns.
     *
     * A connection carrying recent traffic is left completely alone — there is
     * nothing to fix, and re-checking a working socket is how a stable line
     * gets broken. Only a session that has been quiet for a while is asked to
     * prove itself, and only a session that is actually down reconnects.
     */
    /**
     * A pull on Home: prove the line is real, then ask for anything missed.
     *
     * The socket is not torn down for the sake of it — a working one is asked
     * for a fresh sync and that is that. A socket that will not answer, or a
     * connection that is already down, is rebuilt, and this waits for the
     * result so the spinner on Home tells the truth about what happened.
     *
     * @return true once the phone is connected and has asked the hub to catch
     *   it up; false when the line could not be brought back in time.
     */
    suspend fun resync(): Boolean {
        if (accountKey == null) return false
        pushSettled.tryReceive()
        val socket = live
        if (_connection.value == ConnectionState.ONLINE && socket != null) {
            val asked = runCatching {
                socket.send(
                    Frame.Text(
                        encode(
                            buildJsonObject {
                                put("t", "sync")
                                put("since", lastSeq)
                            }
                        )
                    )
                )
            }.isSuccess
            if (asked) {
                retryPending()
                outbox.kick()
                withTimeoutOrNull(RESYNC_SETTLE_MS) { pushSettled.receive() }
                return true
            }
            Log.w(TAG, "pull found a dead socket, rebuilding")
            runCatching { socket.cancel() }
        }
        wake("pull")
        val back = withTimeoutOrNull(RESYNC_CONNECT_MS) {
            connection.first { it == ConnectionState.ONLINE }
        } != null
        if (back) withTimeoutOrNull(RESYNC_SETTLE_MS) { pushSettled.receive() }
        return back
    }

    fun wake(reason: String = "foreground") {
        if (accountKey == null) return
        val now = System.currentTimeMillis()
        if (_connection.value == ConnectionState.ONLINE) {
            val socket = live
            val quiet = now - lastInboundAt
            if (socket != null && quiet > FRESH_MS && probeSentAt == 0L) {
                Log.i(TAG, "checking a socket quiet for ${quiet / 1000}s ($reason)")
                probeSentAt = now
                scope.launch {
                    val ok = runCatching {
                        socket.send(Frame.Text(encode(buildJsonObject { put("t", "ping") })))
                    }.isSuccess
                    if (!ok) socket.cancel()
                }
            }
        } else {
            Log.i(TAG, "reconnect requested: $reason")
            wakeSignal.trySend(Unit)
        }
        outbox.kick()
    }

    private fun disconnect() {
        socketJob?.cancel()
        socketJob = null
        live = null
        outbox.clear()
        _connection.value = ConnectionState.OFFLINE
    }

    private fun encode(payload: JsonObject): String =
        json.encodeToString(JsonObject.serializer(), payload)

    private fun transmit(payload: JsonObject) {
        if (accountKey == null) return
        val kind = payload["t"]?.jsonPrimitive?.content.orEmpty()
        outbox.add(encode(payload), kind)
    }

    // ------------------------------------------------------------------ events

    private fun handleEvent(raw: String) {
        val event = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        when (event["t"]?.jsonPrimitive?.content) {
            "ready" -> {
                val list = event["blocked"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.content }
                    ?.toSet()
                if (list != null) _blocked.value = list
                Log.i(TAG, "hub session ready")
                retryPending()
            }
            "history" -> scope.launch { applyHistory(event) }
            "msg" -> scope.launch { applyIncoming(event) }
            "ack" -> applyAck(event)
            "typing" -> applyTyping(event)
            "reaction" -> scope.launch { applyReaction(event) }
            "unsend" -> {
                val id = event["id"]?.jsonPrimitive?.content ?: return
                removeMessages(setOf(id))
            }
            "burn" -> {
                val ids = event["ids"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet()
                    ?: return
                removeMessages(ids)
            }
            "blocked" -> {
                val users = event["users"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet()
                    ?: return
                _blocked.value = users
                persist()
            }
            "presence" -> {
                val user = event["user"]?.jsonPrimitive?.content ?: return
                val online = event["online"]?.jsonPrimitive?.boolean ?: false
                _onlinePeers.value = if (online && !isBlocked(user)) _onlinePeers.value + user
                else _onlinePeers.value - user
            }
            "presenceSnapshot" -> {
                val snapshot = event["online"]?.jsonObject ?: return
                _onlinePeers.value = snapshot.entries
                    .filter { it.value.jsonPrimitive.boolean }
                    .map { it.key }
                    .filterNot { isBlocked(it) }
                    .toSet()
            }
            // Liveness only: the inbound loop has already stamped the clock.
            "beat", "pong" -> Unit
            "gone" -> wipeLocal()
            "error" -> applyError(event["code"]?.jsonPrimitive?.content)
        }
    }

    private fun applyError(code: String?) {
        Log.w(TAG, "hub error: $code")
        _lastError.value = when (code) {
            "blocked" -> "That person can't be reached."
            "not_accepting" -> "They only accept messages from people they wrote first."
            "unknown_user" -> "That username no longer exists."
            "too_long" -> "That message is too long to encrypt in one go."
            "bad_message" -> "That message could not be sent."
            else -> return
        }
    }

    private suspend fun applyHistory(event: JsonObject) {
        val me = account()?.username ?: return
        val rows = event["messages"]?.jsonArray ?: return
        if (rows.isEmpty()) return

        // Anything that arrived while this phone was disconnected comes back in
        // this batch, and it is exactly what a push wake-up is fetching — so it
        // has to alert, not land silently. A first sync on a fresh device is not
        // news, though: restoring years of history must not ring once per line.
        val catchingUp = lastSeq > 0L
        val arrivals = LinkedHashMap<String, List<Message>>()

        val grouped = LinkedHashMap<String, MutableList<Message>>()
        val controls = mutableListOf<Triple<String, String, MessageEnvelope>>()
        var highest = lastSeq
        for (element in rows) {
            val node = element.jsonObject
            val from = node["from"]?.jsonPrimitive?.content ?: continue
            val to = node["to"]?.jsonPrimitive?.content ?: continue
            val peer = if (from == me) to else from
            val key = conversationKey(peer) ?: continue
            val plain = CryptoBox.decrypt(node["cipher"]?.jsonPrimitive?.content.orEmpty(), key)
                ?: continue
            val envelope = decodeEnvelope(plain)
            highest = maxOf(highest, node["seq"]?.jsonPrimitive?.long ?: 0L)
            if (isControl(envelope)) {
                controls += Triple(from, peer, envelope)
                continue
            }
            val room = if (envelope.g != null) adoptRoster(from, envelope) ?: continue else null
            val threadId = if (room != null) roomThreadId(room.id) else peer
            grouped.getOrPut(threadId) { mutableListOf() }.add(
                Message(
                    id = envelope.mid ?: node["id"]?.jsonPrimitive?.content.orEmpty(),
                    text = envelope.text,
                    outgoing = from == me,
                    at = node["at"]?.jsonPrimitive?.long ?: System.currentTimeMillis(),
                    state = stateOf(node["state"]?.jsonPrimitive?.content),
                    replyTo = envelope.qid,
                    replyText = envelope.q,
                    expiresAt = node["expiresAt"]?.jsonPrimitive?.long ?: 0L,
                    reactions = decodeReactions(node["reactions"], key),
                    photo = envelope.toPhotoRef(),
                    voice = envelope.toVoiceRef(),
                    file = envelope.toFileRef(),
                    deliveredAt = node["deliveredAt"]?.jsonPrimitive?.long ?: 0L,
                    readAt = node["readAt"]?.jsonPrimitive?.long ?: 0L,
                    secret = envelope.s,
                    from = if (room != null) from else null
                )
            )
        }
        lastSeq = highest

        val current = _threads.value.associateBy { it.peer }.toMutableMap()
        for ((peer, incoming) in grouped) {
            val existing = current[peer] ?: Thread(peer = peer, messages = emptyList())
            val merged = LinkedHashMap<String, Message>()
            existing.messages.forEach { merged[it.id] = it }
            incoming.forEach { merged[it.id] = it }
            val ordered = merged.values.sortedBy { it.at }
            val onScreen = visible && activePeer == peer
            val unreadCount = if (onScreen) 0 else ordered.count {
                !it.outgoing && it.state != DeliveryState.READ
            }
            val known = existing.messages.mapTo(mutableSetOf()) { it.id }
            val fresh = incoming.filter {
                !it.outgoing && it.id !in known && it.state != DeliveryState.READ
            }
            val room = existing.group
            val called = room != null && fresh.any { callsMe(it, room) }
            if (catchingUp && fresh.isNotEmpty() && !onScreen && (!existing.muted || called)) {
                arrivals[peer] = fresh
            }
            current[peer] = existing.copy(
                messages = ordered,
                unread = maxOf(existing.unread, unreadCount),
                mentioned = existing.mentioned || (called && !onScreen)
            )
        }
        _threads.value = current.values.toList()
        persist()
        controls.forEach { (from, peer, envelope) -> applyControl(from, peer, envelope) }
        if (_settings.value.notifications) {
            arrivals.forEach { (threadId, messages) ->
                val room = thread(threadId)?.group
                notifier.notifyMessage(
                    title = room?.name ?: "@$threadId",
                    threadId = threadId,
                    count = messages.size,
                    lines = alertLines(messages, room != null),
                    mention = room != null && messages.any { callsMe(it, room) },
                    canReply = canReply()
                )
            }
        }
        if (visible) activePeer?.let { markRead(it) }
        pushSettled.trySend(Unit)
    }

    /**
     * What a notification is allowed to print for these messages.
     *
     * Empty when previews are switched off, which is the signal to the notifier
     * to fall back to naming only who wrote. A hidden message stays hidden here
     * too — [Message.preview] redacts it before this ever sees it.
     */
    /** True when this room message writes my name, or calls the whole room. */
    private fun callsMe(message: Message, room: GroupInfo): Boolean {
        if (message.secret || message.system || message.text.isBlank()) return false
        val me = account()?.username ?: return false
        return mentionsMe(message.text, room.members, me)
    }

    private fun alertLines(messages: List<Message>, isRoom: Boolean): List<String> {
        if (!_settings.value.notificationPreview) return emptyList()
        return messages.takeLast(ALERT_LINES).map { message ->
            val body = message.preview.ifBlank { "Message" }
            if (isRoom && message.from != null) "@${message.from}: $body" else body
        }
    }

    private suspend fun applyIncoming(event: JsonObject) {
        val from = event["from"]?.jsonPrimitive?.content ?: return
        if (isBlocked(from)) return
        // A message from myself is a note I wrote on another phone: it belongs
        // on my side of the thread, it is already read, and it must never ring.
        val mine = isSelf(from)
        val key = conversationKey(from) ?: return
        val plain = CryptoBox.decrypt(event["cipher"]?.jsonPrimitive?.content.orEmpty(), key)
            ?: return
        val envelope = decodeEnvelope(plain)
        lastSeq = maxOf(lastSeq, event["seq"]?.jsonPrimitive?.long ?: 0L)
        if (isControl(envelope)) {
            applyControl(from, from, envelope)
            return
        }

        val room = if (envelope.g != null) adoptRoster(from, envelope) ?: return else null
        val threadId = if (room != null) roomThreadId(room.id) else from
        val message = Message(
            id = envelope.mid ?: event["id"]?.jsonPrimitive?.content.orEmpty(),
            text = envelope.text,
            outgoing = mine,
            at = event["at"]?.jsonPrimitive?.long ?: System.currentTimeMillis(),
            state = if (mine) DeliveryState.READ else DeliveryState.DELIVERED,
            replyTo = envelope.qid,
            replyText = envelope.q,
            expiresAt = event["expiresAt"]?.jsonPrimitive?.long ?: 0L,
            photo = envelope.toPhotoRef(),
            voice = envelope.toVoiceRef(),
            file = envelope.toFileRef(),
            deliveredAt = System.currentTimeMillis(),
            secret = envelope.s,
            from = if (room != null) from else null
        )

        if (thread(threadId) == null) {
            if (room != null) return
            _threads.value = _threads.value + Thread(peer = threadId, messages = emptyList())
        }
        // A chat only counts as open while it is actually in front of the user;
        // one left on screen when the app went away must still alert.
        val isOpen = visible && activePeer == threadId
        val muted = thread(threadId)?.muted ?: false
        // Being named in a room outranks muting it: a muted room is background
        // noise until somebody addresses you directly.
        val callsMe = room != null && callsMe(message, room)
        update(threadId) { thread ->
            if (thread.messages.any { it.id == message.id }) thread
            else thread.copy(
                messages = thread.messages + message,
                unread = if (isOpen || mine) 0 else thread.unread + 1,
                mentioned = thread.mentioned || (callsMe && !isOpen)
            )
        }
        if (isOpen) markRead(threadId)
        else if (!mine && _settings.value.notifications && (!muted || callsMe)) {
            notifier.notifyMessage(
                title = room?.name ?: "@$from",
                threadId = threadId,
                count = 1,
                lines = alertLines(listOf(message), room != null),
                mention = callsMe,
                canReply = canReply()
            )
        }
        pushSettled.trySend(Unit)
    }

    // ------------------------------------------------------- control payloads

    private fun isControl(envelope: MessageEnvelope): Boolean =
        envelope.kind != null &&
            envelope.kind != PHOTO_KIND &&
            envelope.kind != VOICE_KIND &&
            envelope.kind != FILE_KIND

    /**
     * Applies a payload that changes an existing thread: a roster update, a join
     * request, a departure, an edit or a room reaction. Every one of these is
     * idempotent, so replaying history on a new device lands in the same place.
     */
    private fun applyControl(from: String, dmThreadId: String, envelope: MessageEnvelope) {
        val me = account()?.username ?: return
        val threadId = envelope.g?.let { roomThreadId(it) } ?: dmThreadId
        when (envelope.kind) {
            KIND_ROSTER -> {
                val room = adoptRoster(from, envelope) ?: return
                envelope.sys
                    ?.takeIf { it.isNotBlank() }
                    ?.let { note(roomThreadId(room.id), it) }
            }

            KIND_JOIN -> {
                if (from == me) return
                val group = thread(threadId)?.group ?: return
                if (group.token.isEmpty() || group.token != envelope.gt) {
                    Log.w(TAG, "join refused: bad room token")
                    return
                }
                if (group.members.contains(from)) {
                    sendControl(from, rosterEnvelope(group, null))
                    return
                }
                val next = group.copy(
                    members = group.members + from,
                    version = group.version + 1
                )
                update(threadId) { it.copy(group = next) }
                note(threadId, "@$from joined")
                pushRoster(next, "@$from joined")
            }

            KIND_LEAVE -> {
                val group = thread(threadId)?.group ?: return
                if (!group.members.contains(from)) return
                val next = group.copy(
                    members = group.members.filterNot { it == from },
                    version = group.version + 1
                )
                update(threadId) { it.copy(group = next) }
                note(threadId, envelope.sys?.takeIf { it.isNotBlank() } ?: "@$from left")
            }

            KIND_EDIT -> applyEdit(threadId, envelope)

            KIND_REACT -> applyRoomReaction(from, threadId, envelope)

            KIND_BURN -> applyBurn(threadId, envelope)
        }
    }

    /**
     * Learns or refreshes a room from any payload carrying its roster. The
     * highest roster version wins, and a roster without me in it means I am no
     * longer a member.
     */
    private fun adoptRoster(from: String, envelope: MessageEnvelope): GroupInfo? {
        val groupId = envelope.g ?: return null
        val me = account()?.username ?: return null
        val threadId = roomThreadId(groupId)
        val existing = thread(threadId)?.group
        val roster = envelope.gm.filter { it.isNotBlank() }

        if (existing == null) {
            if (roster.isNotEmpty() && !roster.contains(me)) return null
            val group = GroupInfo(
                id = groupId,
                name = envelope.gn?.takeIf { it.isNotBlank() } ?: "Room",
                members = roster.ifEmpty { listOf(from, me) },
                admin = envelope.ga?.takeIf { it.isNotBlank() } ?: from,
                token = envelope.gt.orEmpty(),
                version = envelope.gv,
                createdAt = System.currentTimeMillis()
            )
            _threads.value = _threads.value + Thread(
                peer = threadId,
                messages = emptyList(),
                group = group,
                burnMinutes = _settings.value.defaultBurnMinutes
            )
            persist()
            return group
        }

        if (roster.isEmpty() || envelope.gv <= existing.version) return existing
        if (!roster.contains(me)) {
            deleteThread(threadId)
            return null
        }
        val next = existing.copy(
            name = envelope.gn?.takeIf { it.isNotBlank() } ?: existing.name,
            members = roster,
            admin = envelope.ga?.takeIf { it.isNotBlank() } ?: existing.admin,
            token = envelope.gt?.takeIf { it.isNotEmpty() } ?: existing.token,
            version = envelope.gv
        )
        update(threadId) { it.copy(group = next) }
        return next
    }

    private fun applyEdit(threadId: String, envelope: MessageEnvelope) {
        val id = envelope.eid ?: return
        val body = envelope.text.trim()
        if (body.isEmpty()) return
        val at = envelope.eat.takeIf { it > 0L } ?: System.currentTimeMillis()
        val target = thread(threadId)?.messages?.firstOrNull { it.id == id } ?: return
        if (target.editedAt >= at || target.text == body) return
        update(threadId) { thread ->
            thread.copy(
                messages = thread.messages.map {
                    if (it.id != id) it
                    else it.copy(
                        text = body,
                        edits = it.edits + MessageEdit(it.text, at),
                        editedAt = at
                    )
                }
            )
        }
    }

    /**
     * The other side put a self-destruct clock on a message. The earliest
     * deadline always wins, so a later one cannot revive a burning message.
     */
    private fun applyBurn(threadId: String, envelope: MessageEnvelope) {
        val id = envelope.eid ?: return
        val target = thread(threadId)?.messages?.firstOrNull { it.id == id } ?: return
        val expiresAt = envelope.eat
        if (expiresAt <= 0L) {
            // Burn-after-reading: no deadline yet, just the rule.
            val after = envelope.bor
            if (after <= 0L || target.ephemeral) return
            update(threadId) { current ->
                current.copy(
                    messages = current.messages.map {
                        if (it.id == id) it.copy(burnOnReadMs = after) else it
                    }
                )
            }
            return
        }
        if (target.expiresAt in 1..expiresAt) return
        applyExpiry(threadId, id, expiresAt)
    }

    private fun applyRoomReaction(
        from: String,
        threadId: String,
        envelope: MessageEnvelope
    ) {
        val id = envelope.eid ?: return
        if (thread(threadId)?.messages?.none { it.id == id } != false) return
        val emoji = envelope.text.trim()
        update(threadId) { thread ->
            thread.copy(
                messages = thread.messages.map { message ->
                    if (message.id != id) message
                    else {
                        val reactions = message.reactions.toMutableMap()
                        if (emoji.isEmpty()) reactions.remove(from) else reactions[from] = emoji
                        message.copy(reactions = reactions.toMap())
                    }
                }
            )
        }
    }

    private suspend fun applyReaction(event: JsonObject) {
        val id = event["id"]?.jsonPrimitive?.content ?: return
        val from = event["from"]?.jsonPrimitive?.content ?: return
        val cipher = event["cipher"]?.jsonPrimitive?.content.orEmpty()
        val peer = _threads.value.firstOrNull { thread ->
            thread.messages.any { it.id == id }
        }?.peer ?: return
        val emoji = if (cipher.isEmpty()) null else {
            val key = conversationKey(peer) ?: return
            CryptoBox.decrypt(cipher, key)
        }
        update(peer) { thread ->
            thread.copy(
                messages = thread.messages.map { message ->
                    if (message.id != id) message
                    else {
                        val reactions = message.reactions.toMutableMap()
                        if (emoji.isNullOrEmpty()) reactions.remove(from)
                        else reactions[from] = emoji
                        message.copy(reactions = reactions.toMap())
                    }
                }
            )
        }
    }

    private fun applyAck(event: JsonObject) {
        val wire = event["id"]?.jsonPrimitive?.content ?: return
        val id = wire.substringBefore(WIRE_SEPARATOR)
        val member = wire.substringAfter(WIRE_SEPARATOR, "")
        val state = stateOf(event["state"]?.jsonPrimitive?.content)
        val expiresAt = event["expiresAt"]?.jsonPrimitive?.long ?: 0L
        val deliveredAt = event["deliveredAt"]?.jsonPrimitive?.long ?: 0L
        val readAt = event["readAt"]?.jsonPrimitive?.long ?: 0L
        lastSeq = maxOf(lastSeq, event["seq"]?.jsonPrimitive?.long ?: 0L)
        if (member.isNotEmpty()) groupAcks.getOrPut(id) { mutableMapOf() }[member] = state
        _threads.value = _threads.value.map { thread ->
            if (thread.messages.none { it.id == id }) thread
            else thread.copy(
                messages = thread.messages.map {
                    if (it.id != id) it
                    else {
                        val next = if (member.isEmpty()) state else roomState(thread, id, it.state)
                        it.copy(
                            state = if (it.state.ordinal < next.ordinal) next else it.state,
                            expiresAt = if (expiresAt > 0L) expiresAt else it.expiresAt,
                            deliveredAt = maxOf(it.deliveredAt, deliveredAt),
                            readAt = maxOf(it.readAt, readAt)
                        )
                    }
                }
            )
        }
        persist()
    }

    /** A room message is only as delivered as its least-arrived copy. */
    private fun roomState(
        thread: Thread,
        messageId: String,
        fallback: DeliveryState
    ): DeliveryState {
        val me = account()?.username ?: return fallback
        val targets = thread.group?.others(me) ?: return fallback
        if (targets.isEmpty()) return DeliveryState.SENT
        val acks = groupAcks[messageId] ?: return fallback
        return targets.minOf { acks[it] ?: DeliveryState.PENDING }
    }

    private fun applyTyping(event: JsonObject) {
        val from = event["from"]?.jsonPrimitive?.content ?: return
        if (isBlocked(from)) return
        val on = event["on"]?.jsonPrimitive?.boolean ?: false
        typingClearJobs.remove(from)?.cancel()
        if (!on) {
            _typingPeers.value = _typingPeers.value - from
            return
        }
        _typingPeers.value = _typingPeers.value + from
        typingClearJobs[from] = scope.launch {
            delay(6_000L)
            _typingPeers.value = _typingPeers.value - from
        }
    }

    private fun removeMessages(ids: Set<String>) {
        if (ids.isEmpty()) return
        var touched = false
        val gone = _threads.value.flatMap { it.messages }.filter { ids.contains(it.id) }
        val droppedBlobs = gone.mapNotNull { it.photo?.blob }
        val droppedVoices = gone.mapNotNull { it.voice?.blob }
        val droppedFiles = gone.mapNotNull { it.file?.blob }
        // A note that dies while it is playing stops playing.
        if (droppedVoices.contains(player.openBlob)) player.stop()
        _threads.value = _threads.value.map { thread ->
            if (thread.messages.none { ids.contains(it.id) }) thread
            else {
                touched = true
                thread.copy(
                    messages = thread.messages.filterNot { ids.contains(it.id) },
                    pinnedId = thread.pinnedId?.takeUnless { ids.contains(it) }
                )
            }
        }
        if (droppedBlobs.isNotEmpty() || droppedVoices.isNotEmpty() || droppedFiles.isNotEmpty()) {
            scope.launch {
                droppedBlobs.forEach { photos.forget(it) }
                droppedVoices.forEach { voices.forget(it) }
                droppedFiles.forEach { files.forget(it) }
            }
        }
        if (touched) persist()
    }

    /** Local mirror of the hub's timer: expired messages vanish on their own. */
    private fun startBurnTicker() {
        burnJob?.cancel()
        burnJob = scope.launch {
            while (isActive) {
                delay(5_000L)
                val now = System.currentTimeMillis()
                val expired = _threads.value
                    .flatMap { it.messages }
                    .filter { it.expiresAt in 1 until now }
                    .map { it.id }
                    .toSet()
                if (expired.isNotEmpty()) removeMessages(expired)
                flushScheduled(now)
                retryStalled(now)
            }
        }
    }

    /**
     * Re-sends any outgoing message whose ack never came back. The hub keys on
     * message id, so a duplicate send is answered with the original receipt.
     */
    private fun retryStalled(now: Long) {
        if (_connection.value != ConnectionState.ONLINE) return
        _threads.value.forEach { thread ->
            thread.messages.forEach { message ->
                if (!message.outgoing) return@forEach
                if (message.state != DeliveryState.PENDING) {
                    retryAttempts.remove(message.id)
                    return@forEach
                }
                if (message.uploading || message.system) return@forEach
                val last = retryAttempts[message.id] ?: message.at
                if (now - last < STALL_RETRY_MS) return@forEach
                retryAttempts[message.id] = now
                resend(thread.peer, message.id)
            }
        }
    }

    private fun stateOf(raw: String?): DeliveryState = when (raw) {
        "read" -> DeliveryState.READ
        "delivered" -> DeliveryState.DELIVERED
        "sent" -> DeliveryState.SENT
        "pending" -> DeliveryState.PENDING
        else -> runCatching { DeliveryState.valueOf(raw.orEmpty()) }
            .getOrDefault(DeliveryState.SENT)
    }

    /** Resolves — and caches — the ECDH conversation key for a peer. */
    private suspend fun conversationKey(peer: String): SecretKey? {
        conversationKeys[peer]?.let { return it }
        val me = account()?.username ?: return null
        val privateKey = exchangeKey ?: return null
        val encoded = peerKeys[peer] ?: api.user(peer)?.publicKey?.also {
            if (it.isNotEmpty()) rememberKey(peer, it)
        } ?: return null
        val publicKey = CryptoBox.decodePublicKey(encoded) ?: return null
        val derived = CryptoBox.conversationKey(privateKey, publicKey, me, peer) ?: return null
        conversationKeys[peer] = derived
        return derived
    }

    // ------------------------------------------------------------- envelopes

    private fun encodeEnvelope(envelope: MessageEnvelope): String =
        json.encodeToString(MessageEnvelope.serializer(), envelope)

    private fun MessageEnvelope.toVoiceRef(): VoiceRef? {
        if (kind != VOICE_KIND) return null
        val blobId = blob ?: return null
        val key = mk ?: return null
        return VoiceRef(
            blob = blobId,
            mediaKey = key,
            durationMs = vd,
            levels = vl?.let { unpackLevels(it) } ?: emptyList()
        )
    }

    private fun MessageEnvelope.toFileRef(): FileRef? {
        if (kind != FILE_KIND) return null
        val blobId = blob ?: return null
        val key = mk ?: return null
        return FileRef(
            blob = blobId,
            mediaKey = key,
            name = fn.orEmpty().ifEmpty { "file" },
            size = fs,
            mime = fm.orEmpty()
        )
    }

    private fun MessageEnvelope.toPhotoRef(): PhotoRef? {
        if (kind != PHOTO_KIND) return null
        val blobId = blob ?: return null
        val key = mk ?: return null
        return PhotoRef(
            blob = blobId,
            mediaKey = key,
            width = w,
            height = h,
            thumb = thumb.orEmpty(),
            locked = pl
        )
    }

    /** Reads a v3 envelope, falling back to the original plain-text format. */
    private fun decodeEnvelope(plain: String): MessageEnvelope {
        if (!plain.startsWith("{")) return MessageEnvelope(text = plain)
        return runCatching { json.decodeFromString(MessageEnvelope.serializer(), plain) }
            .getOrElse { MessageEnvelope(text = plain) }
    }

    private fun decodeReactions(
        node: kotlinx.serialization.json.JsonElement?,
        key: SecretKey
    ): Map<String, String> {
        val map = runCatching { node?.jsonObject }.getOrNull() ?: return emptyMap()
        return map.entries.mapNotNull { (user, value) ->
            val cipher = runCatching { value.jsonPrimitive.content }.getOrNull() ?: return@mapNotNull null
            val emoji = CryptoBox.decrypt(cipher, key) ?: return@mapNotNull null
            user to emoji
        }.toMap()
    }

    // -------------------------------------------------------------- persistence

    private fun update(peer: String, transform: (Thread) -> Thread) {
        _threads.value = _threads.value.map { if (it.peer == peer) transform(it) else it }
        persist()
    }

    private fun readVault(): Vault = runCatching {
        prefs.getString(KEY_VAULT, null)?.let { json.decodeFromString<Vault>(it) } ?: Vault()
    }.getOrElse {
        Log.w(TAG, "Vault unreadable, starting fresh")
        Vault()
    }

    private fun persist(account: Account? = null) {
        val current = account ?: account() ?: return
        val key = vaultSecret ?: return
        val vault = Vault(
            account = current,
            threads = _threads.value.map { it.toStored(key) },
            settings = _settings.value,
            lastSeq = lastSeq,
            peerKeys = peerKeys.toMap(),
            blocked = _blocked.value.toList(),
            scheduled = _scheduled.value.map { it.toStored(key) },
            verifiedKeys = verifiedKeys.toMap(),
            keyAlarms = _keyAlarms.value.toList()
        )
        runCatching { prefs.edit().putString(KEY_VAULT, json.encodeToString(vault)).apply() }
            .onFailure { Log.w(TAG, "Could not persist vault") }
    }

    private fun Thread.toStored(key: SecretKey): StoredThread = StoredThread(
        peer = peer,
        messages = messages.map { message ->
            StoredMessage(
                id = message.id,
                cipher = CryptoBox.encrypt(message.text, key),
                outgoing = message.outgoing,
                at = message.at,
                state = message.state.name,
                replyTo = message.replyTo,
                replyCipher = message.replyText?.let { CryptoBox.encrypt(it, key) },
                expiresAt = message.expiresAt,
                burnOnReadMs = message.burnOnReadMs,
                reactionsCipher = if (message.reactions.isEmpty()) null
                else CryptoBox.encrypt(encodeReactions(message.reactions), key),
                photoCipher = message.photo?.let {
                    CryptoBox.encrypt(json.encodeToString(PhotoRef.serializer(), it), key)
                },
                voiceCipher = message.voice?.let {
                    CryptoBox.encrypt(json.encodeToString(VoiceRef.serializer(), it), key)
                },
                fileCipher = message.file?.let {
                    CryptoBox.encrypt(json.encodeToString(FileRef.serializer(), it), key)
                },
                deliveredAt = message.deliveredAt,
                readAt = message.readAt,
                secret = message.secret,
                from = message.from,
                editsCipher = message.edits
                    .takeIf { it.isNotEmpty() }
                    ?.let { CryptoBox.encrypt(encodeEdits(it), key) },
                editedAt = message.editedAt,
                system = message.system
            )
        },
        pinned = pinned,
        muted = muted,
        burnMinutes = burnMinutes,
        unread = unread,
        mentioned = mentioned,
        draftCipher = draft.takeIf { it.isNotEmpty() }?.let { CryptoBox.encrypt(it, key) },
        pinnedId = pinnedId,
        group = group
    )

    private fun StoredThread.toThread(key: SecretKey): Thread = Thread(
        peer = peer,
        messages = messages.map { stored ->
            Message(
                id = stored.id,
                text = CryptoBox.decrypt(stored.cipher, key) ?: "\u2014 undecryptable \u2014",
                outgoing = stored.outgoing,
                at = stored.at,
                state = runCatching { DeliveryState.valueOf(stored.state) }
                    .getOrDefault(DeliveryState.SENT),
                replyTo = stored.replyTo,
                replyText = stored.replyCipher?.let { CryptoBox.decrypt(it, key) },
                expiresAt = stored.expiresAt,
                burnOnReadMs = stored.burnOnReadMs,
                reactions = stored.reactionsCipher
                    ?.let { CryptoBox.decrypt(it, key) }
                    ?.let { decodeStoredReactions(it) }
                    ?: emptyMap(),
                photo = stored.photoCipher
                    ?.let { CryptoBox.decrypt(it, key) }
                    ?.let { raw ->
                        runCatching {
                            json.decodeFromString(PhotoRef.serializer(), raw)
                        }.getOrNull()
                    },
                voice = stored.voiceCipher
                    ?.let { CryptoBox.decrypt(it, key) }
                    ?.let { raw ->
                        runCatching {
                            json.decodeFromString(VoiceRef.serializer(), raw)
                        }.getOrNull()
                    },
                file = stored.fileCipher
                    ?.let { CryptoBox.decrypt(it, key) }
                    ?.let { raw ->
                        runCatching {
                            json.decodeFromString(FileRef.serializer(), raw)
                        }.getOrNull()
                    },
                deliveredAt = stored.deliveredAt,
                readAt = stored.readAt,
                secret = stored.secret,
                from = stored.from,
                edits = stored.editsCipher
                    ?.let { CryptoBox.decrypt(it, key) }
                    ?.let { decodeEdits(it) }
                    ?: emptyList(),
                editedAt = stored.editedAt,
                system = stored.system
            )
        },
        pinned = pinned,
        muted = muted,
        burnMinutes = burnMinutes,
        unread = unread,
        mentioned = mentioned,
        draft = draftCipher?.let { CryptoBox.decrypt(it, key) }.orEmpty(),
        pinnedId = pinnedId,
        group = group
    )

    private fun ScheduledMessage.toStored(key: SecretKey): StoredScheduled = StoredScheduled(
        id = id,
        peer = peer,
        cipher = CryptoBox.encrypt(text, key),
        at = at,
        secret = secret,
        burnMinutes = burnMinutes,
        replyTo = replyTo,
        replyCipher = replyText?.let { CryptoBox.encrypt(it, key) }
    )

    /** Null when the queued text cannot be opened with this vault key. */
    private fun StoredScheduled.toScheduled(key: SecretKey): ScheduledMessage? {
        val text = CryptoBox.decrypt(cipher, key) ?: return null
        return ScheduledMessage(
            id = id,
            peer = peer,
            text = text,
            at = at,
            secret = secret,
            burnMinutes = burnMinutes,
            replyTo = replyTo,
            replyText = replyCipher?.let { CryptoBox.decrypt(it, key) }
        )
    }

    private fun encodeReactions(reactions: Map<String, String>): String =
        json.encodeToString(
            MapSerializer(String.serializer(), String.serializer()),
            reactions
        )

    private fun decodeStoredReactions(raw: String): Map<String, String> = runCatching {
        json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), raw)
    }.getOrDefault(emptyMap())

    private fun encodeEdits(edits: List<MessageEdit>): String =
        json.encodeToString(ListSerializer(MessageEdit.serializer()), edits)

    private fun decodeEdits(raw: String): List<MessageEdit> = runCatching {
        json.decodeFromString(ListSerializer(MessageEdit.serializer()), raw)
    }.getOrDefault(emptyList())

    private companion object {
        const val TAG = "CipherRepository"
        const val KEY_VAULT = "vault_json"
        const val KEY_UNLOCKED = "unlocked_key"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_PIN_SEALED = "pin_sealed_key"
        const val KEY_BIO_SEALED = "bio_sealed_key"
        const val QUOTE_LIMIT = 160
        const val MESSAGE_LIMIT = 4_000
        const val PHOTO_KIND = "photo"
        const val VOICE_KIND = "voice"
        const val FILE_KIND = "file"

        /** Control kinds: payloads that change a thread instead of adding to it. */
        const val KIND_EDIT = "edit"
        const val KIND_REACT = "react"
        const val KIND_ROSTER = "roster"
        const val KIND_JOIN = "join"
        const val KIND_LEAVE = "leave"
        const val KIND_BURN = "burn"

        /** Rooms are threads whose id is prefixed, so they cannot clash with usernames. */
        const val GROUP_PREFIX = "g:"
        const val GROUP_NAME_LIMIT = 40

        /** Separates a room message's canonical id from the member it was sealed for. */
        const val WIRE_SEPARATOR = "~"

        /** How long an unacked message waits before it is quietly sent again. */
        const val STALL_RETRY_MS = 8_000L

        /** Reconnect timing: fast first retry, capped growth, jitter on top. */
        const val INITIAL_BACKOFF_MS = 800L
        const val MAX_BACKOFF_MS = 20_000L

        /** A session this long counts as healthy, so its drop retries fast. */
        const val HEALTHY_SESSION_MS = 20_000L

        /**
         * The hub beats every 25s, so silence — not idleness — is the only
         * signal worth acting on. Two missed beats earn a single question, and
         * the answer gets a generous window: a working connection is never cut
         * for being quiet, only for going unanswered.
         */
        // The hub beats every 25s, so half a minute of complete silence already
        // means something is wrong. Waiting a full minute to find that out was
        // exactly how a message could arrive a minute late on a socket that had
        // quietly died under a network change.
        const val PING_AFTER_MS = 35_000L
        const val PROBE_GRACE_MS = 8_000L
        const val WATCHDOG_TICK_MS = 3_000L

        /** How long a pull on Home waits for a connection, and then for its backlog. */
        const val RESYNC_CONNECT_MS = 8_000L
        const val RESYNC_SETTLE_MS = 2_500L

        /** Recent traffic that makes a foreground probe pointless. */
        const val FRESH_MS = 12_000L

        /** How long a push wake-up holds the process while the backlog lands. */
        const val PUSH_WAIT_MS = 12_000L

        /** Most previews one expanded notification prints before it summarises. */
        const val ALERT_LINES = 6

        /** How long a shade reply holds the woken process while it is sent. */
        const val REPLY_WAIT_MS = 9_000L
    }
}
