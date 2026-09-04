package com.rork.cipher.data

import android.net.Uri

/** Something an invite link points at. */
sealed interface Invite {
    data class User(val username: String) : Invite

    /** A key check: whose screen the code came from, and what it read. */
    data class Verify(val username: String, val code: String) : Invite

    data class Room(
        val id: String,
        val token: String,
        val name: String,
        val inviter: String
    ) : Invite
}

/**
 * Cipher's invite links.
 *
 * A personal link is `cipher://u/<name>` and carries nothing but a public
 * username. A room link is `cipher://g/<id>?t=<token>&n=<name>&i=<inviter>`:
 * the token is the room secret a member checks before admitting anyone, so the
 * link is the invitation itself. Neither link ever contains key material.
 */
object Invites {

    private const val SCHEME = "cipher"
    private const val USER_HOST = "u"
    private const val ROOM_HOST = "g"
    private const val VERIFY_HOST = "v"

    fun userLink(username: String): String = "$SCHEME://$USER_HOST/$username"

    /**
     * The code shown on the verification screen. It carries no key material:
     * the number is a hash of both public keys, and it is only useful to the
     * one phone that can compute the same hash.
     */
    fun verifyLink(username: String, code: String): String =
        "$SCHEME://$VERIFY_HOST/$username?c=$code"

    fun roomLink(id: String, token: String, name: String, inviter: String): String =
        "$SCHEME://$ROOM_HOST/$id?t=${Uri.encode(token)}&n=${Uri.encode(name)}&i=$inviter"

    fun parse(uri: Uri?): Invite? {
        if (uri == null) return null
        if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
        return when {
            uri.host.equals(USER_HOST, ignoreCase = true) ->
                sanitize(uri.lastPathSegment.orEmpty())?.let { Invite.User(it) }

            uri.host.equals(VERIFY_HOST, ignoreCase = true) -> {
                val user = sanitize(uri.lastPathSegment.orEmpty())
                val code = uri.getQueryParameter("c").orEmpty().uppercase()
                if (user == null || code.isEmpty()) null else Invite.Verify(user, code)
            }

            uri.host.equals(ROOM_HOST, ignoreCase = true) -> {
                val id = uri.lastPathSegment?.trim().orEmpty()
                val token = uri.getQueryParameter("t").orEmpty()
                val inviter = sanitize(uri.getQueryParameter("i").orEmpty())
                if (id.isEmpty() || token.isEmpty() || inviter == null) null
                else Invite.Room(
                    id = id,
                    token = token,
                    name = uri.getQueryParameter("n")?.take(60).orEmpty(),
                    inviter = inviter
                )
            }

            else -> null
        }
    }

    /** Reads anything a user might paste: a full link, an `@handle` or a bare name. */
    fun parse(raw: String): Invite? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("$SCHEME://", ignoreCase = true)) {
            return parse(runCatching { Uri.parse(trimmed) }.getOrNull())
        }
        return sanitize(trimmed.removePrefix("@"))?.let { Invite.User(it) }
    }

    /** The username inside a personal link or `@handle`, if there is one. */
    fun usernameFrom(raw: String): String? =
        (parse(raw) as? Invite.User)?.username

    private fun sanitize(raw: String): String? = raw
        .lowercase()
        .filter { it.isLetterOrDigit() || it == '_' }
        .takeIf { it.isNotEmpty() }
}
