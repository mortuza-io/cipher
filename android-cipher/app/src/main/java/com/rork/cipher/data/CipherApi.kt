package com.rork.cipher.data

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.util.concurrent.TimeUnit

/**
 * Where the hub wants a file's bytes to go.
 *
 * `mode` is "r2" when the project has an R2 bucket configured — [put] is then
 * a short-lived presigned URL the client writes to directly, so the bytes
 * never pass through the Worker. Otherwise the file is posted here in chunks
 * of [chunk] characters.
 */
data class UploadTarget(
    val mode: String,
    val put: String?,
    val chunk: Int,
    val limit: Long
)

/** Where a recipient should read a file from. */
data class DownloadTarget(
    val mode: String,
    val get: String?,
    val chunks: Int
)

/** Session material returned by the hub when signing in with an account key. */
data class RemoteAccount(
    val username: String,
    val publicKey: String,
    val sealedPrivateKey: String,
    val createdAt: Long,
    val settings: Settings
)

/** Thin HTTP client for the Cipher hub. Also owns the shared WebSocket client. */
class CipherApi {

    val client: HttpClient = HttpClient(OkHttp) {
        engine {
            config {
                // Keeps the TCP path warm through NATs and phone radios. It is
                // not proof the hub is listening — Cloudflare answers protocol
                // pings at the edge — so app-level liveness is judged from the
                // hub's own beat instead.
                pingInterval(30, TimeUnit.SECONDS)
                retryOnConnectionFailure(true)
            }
        }
        install(WebSockets)
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
        expectSuccess = false
    }

    /**
     * Opens the hub socket. The request timeout is lifted for this one call: a
     * live session is meant to outlive any single request budget.
     */
    suspend fun socket(url: String, block: suspend DefaultClientWebSocketSession.() -> Unit) {
        client.webSocket(
            urlString = url,
            request = { timeout { requestTimeoutMillis = Long.MAX_VALUE } },
            block = block
        )
    }

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun body(response: HttpResponse): JsonObject? = runCatching {
        json.parseToJsonElement(response.bodyAsText()).jsonObject
    }.getOrNull()

    suspend fun claim(
        username: String,
        authDigest: String,
        publicKey: String,
        sealedPrivateKey: String
    ): ClaimResult = runCatching {
        val response = client.post("${Endpoints.BASE_URL}/v1/claim") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject {
                        put("username", username)
                        put("authDigest", authDigest)
                        put("publicKey", publicKey)
                        put("sealedPrivateKey", sealedPrivateKey)
                    }
                )
            )
        }
        when (response.status.value) {
            200 -> ClaimResult.Success("")
            409 -> ClaimResult.Taken
            else -> ClaimResult.Failed("The server refused that username.")
        }
    }.getOrElse {
        Log.w(TAG, "claim failed: ${it.message}")
        ClaimResult.Failed("Can't reach Cipher. Check your connection.")
    }

    suspend fun login(authDigest: String): RemoteAccount? = runCatching {
        val response = client.post("${Endpoints.BASE_URL}/v1/login") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject { put("authDigest", authDigest) }
                )
            )
        }
        if (response.status.value != 200) return null
        val payload = body(response) ?: return null
        val settings = payload["settings"]?.jsonObject
        RemoteAccount(
            username = payload["username"]?.jsonPrimitive?.content.orEmpty(),
            publicKey = payload["publicKey"]?.jsonPrimitive?.content.orEmpty(),
            sealedPrivateKey = payload["sealedPrivateKey"]?.jsonPrimitive?.content.orEmpty(),
            createdAt = payload["createdAt"]?.jsonPrimitive?.long ?: 0L,
            settings = Settings(
                receipts = settings?.get("receipts")?.jsonPrimitive?.boolean ?: true,
                typing = settings?.get("typing")?.jsonPrimitive?.boolean ?: true,
                presence = settings?.get("presence")?.jsonPrimitive?.boolean ?: true,
                strangers = settings?.get("strangers")?.jsonPrimitive?.boolean ?: true
            )
        )
    }.getOrElse {
        Log.w(TAG, "login failed: ${it.message}")
        null
    }

    suspend fun user(username: String): DirectoryUser? = runCatching {
        val response = client.get("${Endpoints.BASE_URL}/v1/user") {
            parameter("u", username)
        }
        if (response.status.value != 200) return null
        body(response)?.get("user")?.jsonObject?.let(::toUser)
    }.getOrElse {
        Log.w(TAG, "lookup failed: ${it.message}")
        null
    }

    suspend fun search(query: String): List<DirectoryUser> = runCatching {
        val response = client.get("${Endpoints.BASE_URL}/v1/search") {
            parameter("q", query)
        }
        if (response.status.value != 200) return emptyList()
        body(response)?.get("users")?.jsonArray
            ?.mapNotNull { runCatching { toUser(it.jsonObject) }.getOrNull() }
            .orEmpty()
    }.getOrElse {
        Log.w(TAG, "search failed: ${it.message}")
        emptyList()
    }

    /** Uploads one encrypted attachment. The hub only ever sees this ciphertext. */
    suspend fun putBlob(
        authDigest: String,
        id: String,
        to: String,
        cipher: String
    ): Boolean = runCatching {
        val response = client.post("${Endpoints.BASE_URL}/v1/blob") {
            contentType(ContentType.Application.Json)
            timeout { requestTimeoutMillis = 60_000 }
            setBody(
                json.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject {
                        put("authDigest", authDigest)
                        put("id", id)
                        put("to", to)
                        put("cipher", cipher)
                    }
                )
            )
        }
        if (response.status.value != 200) {
            Log.w(TAG, "blob upload refused: ${response.status.value}")
        }
        response.status.value == 200
    }.getOrElse {
        Log.w(TAG, "blob upload failed: ${it.message}")
        false
    }

    suspend fun getBlob(username: String, authDigest: String, id: String): String? = runCatching {
        val response = client.get("${Endpoints.BASE_URL}/v1/blob") {
            timeout { requestTimeoutMillis = 60_000 }
            parameter("u", username)
            parameter("a", authDigest)
            parameter("id", id)
        }
        if (response.status.value != 200) return null
        body(response)?.get("cipher")?.jsonPrimitive?.content
    }.getOrElse {
        Log.w(TAG, "blob download failed: ${it.message}")
        null
    }

    // ------------------------------------------------------------------ files

    /** Opens an upload and learns where the bytes should go. */
    suspend fun beginFile(
        authDigest: String,
        id: String,
        to: String,
        size: Long
    ): UploadTarget? = runCatching {
        val response = client.post("${Endpoints.BASE_URL}/v1/file/begin") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject {
                        put("authDigest", authDigest)
                        put("id", id)
                        put("to", to)
                        put("size", size)
                    }
                )
            )
        }
        val payload = body(response)
        // A refusal for size is worth reporting precisely, so the bubble can
        // name the actual ceiling instead of a vague failure.
        if (response.status.value == 413) {
            return UploadTarget(
                mode = "too-large",
                put = null,
                chunk = 0,
                limit = payload?.get("limit")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            )
        }
        if (response.status.value != 200 || payload == null) {
            Log.w(TAG, "file begin refused: ${response.status.value}")
            return null
        }
        UploadTarget(
            mode = payload["mode"]?.jsonPrimitive?.content ?: "do",
            put = payload["put"]?.jsonPrimitive?.content,
            chunk = payload["chunk"]?.jsonPrimitive?.content?.toIntOrNull() ?: 700_000,
            limit = payload["limit"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
        )
    }.getOrElse {
        Log.w(TAG, "file begin failed: ${it.message}")
        null
    }

    /** Writes sealed bytes straight to R2 through a presigned link. */
    suspend fun putToUrl(url: String, body: ByteArray): Boolean = runCatching {
        val response = client.put(url) {
            timeout { requestTimeoutMillis = 180_000 }
            setBody(body)
        }
        if (response.status.value !in 200..299) {
            Log.w(TAG, "file upload refused: ${response.status.value}")
        }
        response.status.value in 200..299
    }.getOrElse {
        Log.w(TAG, "file upload failed: ${it.message}")
        false
    }

    suspend fun getFromUrl(url: String): ByteArray? = runCatching {
        val response = client.get(url) { timeout { requestTimeoutMillis = 180_000 } }
        if (response.status.value != 200) return null
        response.bodyAsBytes()
    }.getOrElse {
        Log.w(TAG, "file download failed: ${it.message}")
        null
    }

    suspend fun putFileChunk(
        authDigest: String,
        id: String,
        seq: Int,
        cipher: String
    ): Boolean = runCatching {
        val response = client.post("${Endpoints.BASE_URL}/v1/file/chunk") {
            contentType(ContentType.Application.Json)
            timeout { requestTimeoutMillis = 60_000 }
            setBody(
                json.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject {
                        put("authDigest", authDigest)
                        put("id", id)
                        put("seq", seq)
                        put("cipher", cipher)
                    }
                )
            )
        }
        response.status.value == 200
    }.getOrElse {
        Log.w(TAG, "chunk upload failed: ${it.message}")
        false
    }

    /** Marks an upload complete so a half-sent file is never served. */
    suspend fun commitFile(authDigest: String, id: String, chunks: Int): Boolean = runCatching {
        val response = client.post("${Endpoints.BASE_URL}/v1/file/commit") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject {
                        put("authDigest", authDigest)
                        put("id", id)
                        put("chunks", chunks)
                    }
                )
            )
        }
        response.status.value == 200
    }.getOrElse {
        Log.w(TAG, "file commit failed: ${it.message}")
        false
    }

    suspend fun openFile(
        username: String,
        authDigest: String,
        id: String
    ): DownloadTarget? = runCatching {
        val response = client.get("${Endpoints.BASE_URL}/v1/file") {
            parameter("u", username)
            parameter("a", authDigest)
            parameter("id", id)
        }
        val payload = body(response)
        if (response.status.value != 200 || payload == null) return null
        DownloadTarget(
            mode = payload["mode"]?.jsonPrimitive?.content ?: "do",
            get = payload["get"]?.jsonPrimitive?.content,
            chunks = payload["chunks"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        )
    }.getOrElse {
        Log.w(TAG, "file open failed: ${it.message}")
        null
    }

    suspend fun getFileChunk(
        username: String,
        authDigest: String,
        id: String,
        seq: Int
    ): String? = runCatching {
        val response = client.get("${Endpoints.BASE_URL}/v1/file/chunk") {
            timeout { requestTimeoutMillis = 60_000 }
            parameter("u", username)
            parameter("a", authDigest)
            parameter("id", id)
            parameter("seq", seq)
        }
        if (response.status.value != 200) return null
        body(response)?.get("cipher")?.jsonPrimitive?.content
    }.getOrElse {
        Log.w(TAG, "chunk download failed: ${it.message}")
        null
    }

    // ------------------------------------------------------------------ push

    /**
     * Hands this device's push address to the hub.
     *
     * The token is an address, not a mailbox: it lets the hub ask Google to
     * wake this installation, and carries nothing about the account beyond the
     * fact that it wants to be woken.
     */
    suspend fun registerDevice(authDigest: String, token: String): Boolean = runCatching {
        val response = client.post("${Endpoints.BASE_URL}/v1/push/register") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject {
                        put("authDigest", authDigest)
                        put("token", token)
                    }
                )
            )
        }
        if (response.status.value != 200) {
            Log.w(TAG, "push register refused: ${response.status.value}")
            return false
        }
        // The hub answers whether it can actually send, so a project without
        // Firebase credentials does not leave the app believing it is covered.
        body(response)?.get("push")?.jsonPrimitive?.boolean ?: false
    }.getOrElse {
        Log.w(TAG, "push register failed: ${it.message}")
        false
    }

    /** Tells the hub to stop waking this device. */
    suspend fun forgetDevice(token: String): Boolean = runCatching {
        val response = client.post("${Endpoints.BASE_URL}/v1/push/forget") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject { put("token", token) }
                )
            )
        }
        response.status.value == 200
    }.getOrElse {
        Log.w(TAG, "push forget failed: ${it.message}")
        false
    }

    suspend fun deleteAccount(authDigest: String): Boolean = runCatching {
        val response = client.post("${Endpoints.BASE_URL}/v1/delete") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    JsonObject.serializer(),
                    buildJsonObject { put("authDigest", authDigest) }
                )
            )
        }
        response.status.value == 200
    }.getOrElse {
        Log.w(TAG, "delete failed: ${it.message}")
        false
    }

    private fun toUser(node: JsonObject): DirectoryUser = DirectoryUser(
        username = node["username"]?.jsonPrimitive?.content.orEmpty(),
        publicKey = node["publicKey"]?.jsonPrimitive?.content.orEmpty(),
        online = node["online"]?.jsonPrimitive?.boolean ?: false,
        createdAt = node["createdAt"]?.jsonPrimitive?.long ?: 0L
    )

    private companion object {
        const val TAG = "CipherApi"
    }
}
