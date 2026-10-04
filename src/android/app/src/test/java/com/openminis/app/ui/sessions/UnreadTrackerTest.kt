package com.openminis.app.ui.sessions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [local #359] The unread dot's decision function.
 *
 * The dot means "this session finished while you were elsewhere". Two rules
 * came out of using it, and both are pinned here because both are easy to
 * break by reading the timestamps alone:
 *
 *  - `updatedAt` advances on EVERY streamed chunk, so a running agentic turn
 *    keeps re-arming the dot the whole time it works. A session that is still
 *    running must not show it — there is nothing to catch the user up on
 *    until the work stops. This is enforced at the row (`!isActive`), and
 *    `shouldShowUnreadDot` below is the same test in isolation so it can be
 *    pinned without a Compose runtime.
 *  - With no recorded stamp the session counts as unread as soon as it has
 *    been touched at all (judged against `createdAt`), so a fresh install
 *    lights up every historical chat. Empty sessions carry
 *    `updatedAt == createdAt` and stay quiet without a special case.
 */
class UnreadTrackerTest {

    private val T0 = 1_700_000_000_000L

    private fun show(
        sessionId: String,
        updatedAt: Long,
        createdAt: Long,
        seen: Map<String, Long>,
        isActive: Boolean,
    ): Boolean = shouldShowUnreadDot(
        isUnread = UnreadTracker.isUnread(sessionId, updatedAt, createdAt, seen),
        isActive = isActive,
    )

    @Test
    fun `a finished session the user never opened is unread`() {
        assertTrue(
            show("a", updatedAt = T0 + 5000, createdAt = T0, seen = emptyMap(), isActive = false),
        )
    }

    @Test
    fun `an empty session stays quiet`() {
        // updatedAt == createdAt on a session with no messages, so there is
        // nothing to report.
        assertFalse(
            show("a", updatedAt = T0, createdAt = T0, seen = emptyMap(), isActive = false),
        )
    }

    @Test
    fun `a session read at its current stamp is not unread`() {
        val seen = mapOf("a" to T0 + 5000)
        assertFalse(show("a", T0 + 5000, T0, seen, isActive = false))
    }

    @Test
    fun `content after the read stamp marks it unread again`() {
        val seen = mapOf("a" to T0 + 5000)
        assertTrue(show("a", updatedAt = T0 + 9000, createdAt = T0, seen, isActive = false))
    }

    @Test
    fun `a RUNNING session shows no dot however new it is`() {
        // The reported bug: an agentic turn bumps updatedAt on every chunk, so
        // the dot blinked on and off for the whole run and then vanished the
        // moment the user opened the chat.
        assertFalse(
            show(
                "a",
                updatedAt = T0 + 99_000,
                createdAt = T0,
                seen = mapOf("a" to T0),
                isActive = true,
            ),
        )
    }

    @Test
    fun `the same session shows the dot once the run stops`() {
        val seen = mapOf("a" to T0)
        val updatedAt = T0 + 99_000
        assertFalse("while running", show("a", updatedAt, T0, seen, isActive = true))
        assertTrue("after it stops", show("a", updatedAt, T0, seen, isActive = false))
    }

    @Test
    fun `the running suppression does not weaken the read check`() {
        // Opening the session while it runs stamps it read; when the run ends
        // and writes more, it must light up again.
        val seen = mapOf("a" to T0 + 1000)
        assertTrue(show("a", updatedAt = T0 + 2000, createdAt = T0, seen, isActive = false))
        assertFalse(show("a", updatedAt = T0 + 1000, createdAt = T0, seen, isActive = false))
    }

    @Test
    fun `an untouched empty session that starts running still shows nothing`() {
        assertFalse(show("a", T0, T0, emptyMap(), isActive = true))
    }
}