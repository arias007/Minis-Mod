package com.openminis.app.ui.sessions

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [local #359] Which sessions have content the user has not looked at yet.
 *
 * The signal is `ChatSessionEntity.updatedAt` — the millisecond stamp the row
 * already carries — compared against the stamp at the moment the session was
 * last opened. Deliberately NOT a new column on the entity: adding one would
 * mean a Room schema version bump, an exported migration JSON, and a migration
 * test, for a value that is fully derivable from what is already stored.
 *
 * Storage is a plain SharedPreferences string set, written once per open, so
 * the file never grows large enough for the cost to matter.
 */
object UnreadTracker {

    private const val PREFS = "minis_unread_tracker"
    private const val KEY_LAST_READ_PREFIX = "last_read_"

    private val lastRead = mutableStateOf<Map<String, Long>>(emptyMap())
    private val _lastReadFlow = MutableStateFlow<Map<String, Long>>(emptyMap())
    val lastReadMap: StateFlow<Map<String, Long>> = _lastReadFlow.asStateFlow()

    @Volatile
    private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Load once per process; subsequent calls are no-ops. */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val map = prefs(context).all.entries
                .filter { it.key.startsWith(KEY_LAST_READ_PREFIX) }
                .mapNotNull { e ->
                    val id = e.key.removePrefix(KEY_LAST_READ_PREFIX)
                    val v = e.value as? Long ?: return@mapNotNull null
                    id to v
                }.toMap()
            lastRead.value = map
            _lastReadFlow.value = map
            loaded = true
        }
    }

    /**
     * True when [updatedAt] is newer than the last time this session was read.
     *
     * With NO recorded stamp the session is unread as soon as it has been
     * touched at all — judged against [createdAt], not dismissed. The first
     * cut returned false in that case, on the theory that a fresh install
     * would otherwise light up every historical session at once. That is
     * exactly the wrong trade: the user installed the app to be told what
     * changed, and "every existing chat is unread" is the correct reading of a
     * session that has never been opened. A session with no messages yet
     * carries `updatedAt == createdAt`, so the empty ones stay quiet without
     * needing a special case.
     */
    fun isUnread(
        sessionId: String,
        updatedAt: Long,
        createdAt: Long,
        snapshot: Map<String, Long> = lastRead.value,
    ): Boolean {
        val seen = snapshot[sessionId] ?: return updatedAt > createdAt
        return updatedAt > seen
    }

    /** Called when a session is opened or focused. */
    fun markRead(context: Context, sessionId: String, updatedAt: Long) {
        prefs(context).edit().putLong(KEY_LAST_READ_PREFIX + sessionId, updatedAt).apply()
        val next = lastRead.value + (sessionId to updatedAt)
        lastRead.value = next
        _lastReadFlow.value = next
    }

    /** Drops the stamp, so a session that gains new content lights up again. */
    fun forget(context: Context, sessionId: String) {
        prefs(context).edit().remove(KEY_LAST_READ_PREFIX + sessionId).apply()
        val next = lastRead.value - sessionId
        lastRead.value = next
        _lastReadFlow.value = next
    }

    /** Test seam — resets the in-memory mirror without touching disk. */
    internal fun resetForTest() {
        lastRead.value = emptyMap()
        _lastReadFlow.value = emptyMap()
        loaded = false
    }
}