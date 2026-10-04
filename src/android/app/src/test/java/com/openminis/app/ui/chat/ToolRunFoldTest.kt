package com.openminis.app.ui.chat

import com.openminis.app.data.model.ThinkingLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [local #337] Pins the two behaviours the first cut got wrong.
 *
 * 1. "能折叠但展不开" — the folded set was computed as empty whenever the
 *    summary was expanded, which meant the summary row itself was never
 *    emitted on expand. The pills came back as standalone rows and the header
 *    went with them, leaving nothing to tap shut again.
 * 2. "思考没被折叠" — only `tool_use` blocks were folded. Reasoning cards
 *    stayed stranded between the summary row and the answer, which is most of
 *    the vertical space the feature exists to reclaim.
 *
 * These call the real `buildFlatChatItems`, not a reimplementation, so a change
 * to the builder that reintroduces either bug fails here.
 */
class ToolRunFoldTest {

    private fun thinking(id: String) =
        AssistantBlock(id = id, kind = "thinking", content = "reasoning about $id")

    private fun tool(id: String, status: ToolBlockStatus = ToolBlockStatus.SUCCESS) =
        AssistantBlock(
            id = id,
            kind = "tool_use",
            content = "{}",
            toolStatus = status,
            toolName = "shell",
        )

    private fun text(id: String, content: String = "answer") =
        AssistantBlock(id = id, kind = "text", content = content)

    private fun assistant(
        blocks: List<AssistantBlock>,
        streaming: Boolean = false,
        thinkingLevel: ThinkingLevel? = null,
    ) = ChatMessage(
        id = "m1",
        role = "assistant",
        content = "",
        isStreaming = streaming,
        toolBlocks = blocks,
        thinkingLevel = thinkingLevel,
    )

    private fun summaryOf(items: List<FlatChatItem>): FlatChatItem.AssistantToolSummary? =
        items.filterIsInstance<FlatChatItem.AssistantToolSummary>().firstOrNull()

    private fun build(
        message: ChatMessage,
        expanded: Set<String> = emptySet(),
        collapse: Boolean = true,
    ) = buildFlatChatItems(
        messages = listOf(message),
        expandedToolSummaries = expanded,
        collapseToolRuns = collapse,
    )

    // ── Bug 1: the summary row must survive expansion ──────────────────────

    @Test
    fun `summary row is emitted when collapsed`() {
        val items = build(assistant(listOf(thinking("t1"), tool("c1"), text("x"))))
        val summary = summaryOf(items)
        assertTrue("collapsed run should produce a summary row", summary != null)
        assertFalse(summary!!.expanded)
    }

    @Test
    fun `summary row is STILL emitted when expanded and carries the flag`() {
        // This is the regression the user reported: expanding used to make
        // foldableToolIds empty, which made foldedRun empty, which meant no
        // summary row at all — so there was nothing left to tap to close it.
        val items = build(
            assistant(listOf(thinking("t1"), tool("c1"), text("x"))),
            expanded = setOf("m1"),
        )
        val summary = summaryOf(items)
        assertTrue("expanded run must KEEP its header row", summary != null)
        assertTrue("expanded flag must reach the renderer", summary!!.expanded)
    }

    @Test
    fun `expanded run keeps the blocks inside the summary row, not as siblings`() {
        val collapsed = build(assistant(listOf(thinking("t1"), tool("c1"), text("x"))))
        val expanded = build(
            assistant(listOf(thinking("t1"), tool("c1"), text("x"))),
            expanded = setOf("m1"),
        )

        // Collapsed: one summary row stands in for the process.
        assertEquals(1, collapsed.count { it is FlatChatItem.AssistantToolSummary })
        assertEquals(0, collapsed.count { it is FlatChatItem.AssistantToolUse })
        assertEquals(0, collapsed.count { it is FlatChatItem.AssistantThinking })

        // Expanded: the summary row stays, now carrying its block list, and the
        // renderer draws those blocks inside its own AnimatedVisibility. They
        // must NOT also appear as standalone rows — the same content would
        // render twice.
        assertEquals(1, expanded.count { it is FlatChatItem.AssistantToolSummary })
        assertEquals(
            "expanded content belongs to the summary row, not beside it",
            0,
            expanded.count { it is FlatChatItem.AssistantToolUse },
        )
        assertEquals(0, expanded.count { it is FlatChatItem.AssistantThinking })

        // The data the renderer's AnimatedVisibility needs is present.
        val summary = summaryOf(expanded)!!
        assertEquals(setOf("t1", "c1"), summary.blocks.map { it.id }.toSet())
    }

    @Test
    fun `expanded flag round-trips - collapsing again re-hides the rows`() {
        val msg = assistant(listOf(thinking("t1"), tool("c1"), text("x")))
        val reopened = build(msg, expanded = setOf("m1"))
        val recollapsed = build(msg, expanded = emptySet())

        // Same block list either way; only the flag moves. That is what lets
        // the header stay tappable across the whole cycle.
        assertEquals(summaryOf(reopened)!!.blocks.map { it.id }, summaryOf(recollapsed)!!.blocks.map { it.id })
        assertTrue(summaryOf(reopened)!!.expanded)
        assertFalse(summaryOf(recollapsed)!!.expanded)
    }

    // ── Bug 2: thinking folds alongside tools ──────────────────────────────

    @Test
    fun `settled thinking blocks fold into the summary`() {
        val items = build(assistant(listOf(thinking("t1"), tool("c1"), text("x"))))
        val summary = summaryOf(items)!!
        val ids = summary.blocks.map { it.id }
        assertTrue("thinking block must be in the folded run", "t1" in ids)
        assertTrue("tool block must be in the folded run", "c1" in ids)
    }

    @Test
    fun `folded run keeps original block order`() {
        // thinking → tool → thinking → tool → answer is the real shape of an
        // agentic turn. Rendering by kind instead of order would misrepresent
        // how the agent worked.
        val items = build(assistant(listOf(
            thinking("t1"), tool("c1"), thinking("t2"), tool("c2"), text("x"),
        )))
        val ids = summaryOf(items)!!.blocks.map { it.id }
        assertEquals(listOf("t1", "c1", "t2", "c2"), ids)
    }

    @Test
    fun `several thinking cards collapse into one row`() {
        val items = build(assistant(listOf(
            thinking("t1"), tool("c1"), thinking("t2"), tool("c2"),
            thinking("t3"), tool("c3"), text("x"),
        )))
        assertEquals(
            "four process rows plus three reasoning cards become ONE row",
            1,
            items.count { it is FlatChatItem.AssistantToolSummary },
        )
        assertEquals(0, items.count { it is FlatChatItem.AssistantThinking })
    }

    // ── Guards that must survive the change ───────────────────────────────

    @Test
    fun `live trailing reasoning is never folded`() {
        // Still streaming, no answer text yet → nothing folds at all; the
        // reasoning the user is watching stays live.
        val items = buildFlatChatItems(
            messages = listOf(
                ChatMessage(
                    id = "m1",
                    role = "assistant",
                    content = "",
                    isStreaming = true,
                    toolBlocks = listOf(thinking("t1"), tool("c1")),
                ),
            ),
        )
        assertEquals(0, items.count { it is FlatChatItem.AssistantToolSummary })
        assertTrue(items.any { it is FlatChatItem.AssistantThinking })
    }

    @Test
    fun `reasoning still folds while a later answer streams`() {
        // Answer text has begun, so this turn's process is settled — the
        // earlier reasoning is finished even though the message is still
        // streaming its text. Folding it is correct: the user is reading the
        // answer now, and a leftover reasoning card above it is exactly the
        // clutter the feature exists to clear.
        val items = build(
            assistant(
                blocks = listOf(thinking("t1"), tool("c1"), thinking("t2"), text("x")),
                streaming = true,
            ),
        )
        val folded = summaryOf(items)!!.blocks.map { it.id }
        assertEquals(listOf("t1", "c1", "t2"), folded)
        assertEquals(0, items.count { it is FlatChatItem.AssistantThinking })
    }

    @Test
    fun `reasoning with no answer text yet never folds, streaming or not`() {
        // The block is the LIVE tail — the model is still writing it. This is
        // the one case the user must be able to watch.
        val streaming = build(
            assistant(listOf(thinking("live"), tool("c1")), streaming = true),
        )
        assertEquals(0, streaming.count { it is FlatChatItem.AssistantToolSummary })
        assertTrue(streaming.any { it is FlatChatItem.AssistantThinking })

        // Same shape, turn aborted before any answer: still not folded.
        val stopped = build(assistant(listOf(thinking("live"), tool("c1"))))
        assertEquals(0, stopped.count { it is FlatChatItem.AssistantToolSummary })
        assertTrue(stopped.any { it is FlatChatItem.AssistantThinking })
    }

    @Test
    fun `running tool stays unfolded while settled ones fold`() {
        val items = build(assistant(listOf(
            tool("done", ToolBlockStatus.SUCCESS),
            tool("live", ToolBlockStatus.RUNNING),
            text("x"),
        )))
        assertTrue(items.any { it is FlatChatItem.AssistantToolUse })
        val folded = summaryOf(items)!!.blocks.map { it.id }
        assertTrue("done" in folded)
        assertFalse("a running tool is what the user is watching", "live" in folded)
    }

    @Test
    fun `failed and cancelled tools fold too`() {
        // A stopped turn used to leave a wall of dead pills; folding them is
        // the whole point of the feature.
        val items = build(assistant(listOf(
            tool("ok", ToolBlockStatus.SUCCESS),
            tool("bad", ToolBlockStatus.FAILED),
            tool("stopped", ToolBlockStatus.CANCELLED),
            tool("slow", ToolBlockStatus.TIMEOUT),
            text("x"),
        )))
        val folded = summaryOf(items)!!.blocks.map { it.id }.toSet()
        assertEquals(setOf("ok", "bad", "stopped", "slow"), folded)
    }

    @Test
    fun `no answer text means nothing folds`() {
        val items = build(assistant(listOf(thinking("t1"), tool("c1"), text("x", ""))))
        assertEquals(0, items.count { it is FlatChatItem.AssistantToolSummary })
    }

    @Test
    fun `thinking level OFF keeps reasoning out of the folded run`() {
        // T300: a forced-reasoning model streams reasoning_content even when the
        // wire request omits it. Folding one here and rendering it would
        // RESURFACE content the gate exists to hide.
        val items = build(
            assistant(
                blocks = listOf(thinking("t1"), tool("c1"), text("x")),
                thinkingLevel = ThinkingLevel.OFF,
            ),
        )
        val folded = summaryOf(items)!!.blocks.map { it.id }
        assertFalse("hidden reasoning must not fold", "t1" in folded)
        assertTrue("tools still fold", "c1" in folded)
    }

    @Test
    fun `thinking level ON folds reasoning normally`() {
        val items = build(
            assistant(
                blocks = listOf(thinking("t1"), tool("c1"), text("x")),
                thinkingLevel = ThinkingLevel.HIGH,
            ),
        )
        assertTrue("t1" in summaryOf(items)!!.blocks.map { it.id })
    }

    @Test
    fun `legacy message with no thinking snapshot folds reasoning`() {
        // thinkingLevel == null falls back to the chat's current level at the
        // render site; the builder mirrors that default.
        val items = build(assistant(listOf(thinking("t1"), tool("c1"), text("x"))))
        assertTrue("t1" in summaryOf(items)!!.blocks.map { it.id })
    }

    @Test
    fun `master switch off folds nothing`() {
        val items = build(
            assistant(listOf(thinking("t1"), tool("c1"), text("x"))),
            collapse = false,
        )
        assertEquals(0, items.count { it is FlatChatItem.AssistantToolSummary })
        assertEquals(1, items.count { it is FlatChatItem.AssistantToolUse })
        assertEquals(1, items.count { it is FlatChatItem.AssistantThinking })
    }

    @Test
    fun `answer text is never folded into the run`() {
        // The whole point is to make room for the answer; folding it would
        // hide the thing the user came for.
        val items = build(assistant(listOf(thinking("t1"), tool("c1"), text("x"))))
        assertFalse("x" in summaryOf(items)!!.blocks.map { it.id })
    }

    @Test
    fun `info blocks are left alone`() {
        val items = build(assistant(listOf(
            AssistantBlock(id = "i1", kind = "info", content = "notice"),
            tool("c1"),
            text("x"),
        )))
        assertTrue(items.any { it is FlatChatItem.AssistantInfo })
    }
}