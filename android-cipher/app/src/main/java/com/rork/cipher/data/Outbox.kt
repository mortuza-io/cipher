package com.rork.cipher.data

import kotlinx.coroutines.channels.Channel

/**
 * Frames waiting for a live socket.
 *
 * A frame is only dropped once the socket has actually accepted it, so a
 * connection that dies mid-write loses nothing — the very same queue is handed
 * to the next session. That is what keeps a message from sitting on "sending"
 * forever after the app was backgrounded and the socket quietly died.
 *
 * The queue is bounded so a long offline stretch cannot grow without limit,
 * and transient chatter (typing dots, heartbeats) is discarded once stale
 * instead of replaying minutes-old state on reconnect.
 */
class Outbox(private val limit: Int = 600) {

    /** One queued frame: the wire payload plus enough context to age it out. */
    data class Frame(val payload: String, val kind: String, val at: Long)

    private val lock = Any()
    private val items = ArrayDeque<Frame>()
    private val signal = Channel<Unit>(Channel.CONFLATED)

    val depth: Int get() = synchronized(lock) { items.size }

    fun add(payload: String, kind: String) {
        synchronized(lock) {
            if (items.size >= limit) items.removeFirst()
            items.addLast(Frame(payload, kind, System.currentTimeMillis()))
        }
        signal.trySend(Unit)
    }

    /** Oldest frame, left in place until [drop] confirms it went out. */
    fun head(): Frame? = synchronized(lock) { items.firstOrNull() }

    fun drop(frame: Frame) {
        synchronized(lock) {
            if (items.firstOrNull() === frame) items.removeFirst()
        }
    }

    /** Suspends until there is something to send. */
    suspend fun awaitWork() {
        signal.receive()
    }

    /** Nudges a waiting writer, e.g. right after a reconnect. */
    fun kick() {
        signal.trySend(Unit)
    }

    fun clear() {
        synchronized(lock) { items.clear() }
    }

    /** True when this frame is only meaningful right now. */
    fun isStale(frame: Frame, now: Long): Boolean =
        frame.kind in TRANSIENT && now - frame.at > TRANSIENT_LIFE_MS

    private companion object {
        val TRANSIENT = setOf("typing", "ping", "watch")
        const val TRANSIENT_LIFE_MS = 10_000L
    }
}
