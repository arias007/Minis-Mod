package com.openminis.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openminis.app.ui.theme.ChatColors

/**
 * [local #337] One row standing in for a finished run of process — reasoning,
 * tool calls and the narration between them — plus those rows themselves when
 * the user taps it open.
 *
 * The point is vertical: an agentic turn emits a Deep Thinking card, a pill,
 * and a one-line narration, then the next one, dozens of times before its final
 * answer. Every one occupies a row. Replacing a settled run with a single line
 * keeps the answer where the reader expects to find it.
 *
 * The run's own state is never hidden — the header names how many calls folded
 * and how many failed, so a collapsed run still reads as a summary rather than
 * a gap.
 *
 * [local #337] NO HEIGHT ANIMATION on expand. The first cut used
 * `AnimatedVisibility(expandVertically)`, and a run can be dozens of pills:
 * every frame re-measured all of them, so opening a long run took visibly
 * longer than it should and felt like the tap had missed ("展开折叠不灵敏
 * 卡顿"). Worse, the animation was interrupted by the LazyColumn's own
 * scroll anchoring, which is what produced the "有时展不开" — the row started
 * animating, the list re-anchored, and the animation landed on a stale
 * geometry. The content now appears in one frame. It is a disclosure, not an
 * animation, and the tap registers immediately either way.
 */
@Composable
internal fun ToolRunSummaryRow(
    blocks: List<AssistantBlock>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onStop: (() -> Unit)? = null,
) {
    val tools = blocks.filter { it.kind == "tool_use" }
    val thinkingBlocks = blocks.filter { it.kind == "thinking" }
    val narrationBlocks = blocks.filter { it.kind == "text" && it.content.isNotEmpty() }
    val failed = tools.count {
        it.toolStatus == ToolBlockStatus.FAILED || it.toolStatus == ToolBlockStatus.CANCELLED
    }

    // What the header calls itself. With reasoning and narration folded in,
    // "7 tool calls" understated the row — the header has to match what tapping
    // reveals, or the count reads as a lie.
    val parts = mutableListOf<String>()
    if (thinkingBlocks.isNotEmpty()) {
        parts += "${thinkingBlocks.size} thinking block" + if (thinkingBlocks.size == 1) "" else "s"
    }
    if (tools.isNotEmpty()) {
        parts += "${tools.size} tool call" + if (tools.size == 1) "" else "s"
    }
    if (narrationBlocks.isNotEmpty()) {
        parts += "${narrationBlocks.size} note" + if (narrationBlocks.size == 1) "" else "s"
    }
    if (failed > 0) parts += "$failed failed"
    val label = parts.joinToString(" · ")

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ChatColors.toolCapsuleBg)
                .clickable(onClickLabel = if (expanded) "Collapse" else "Expand", onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (failed > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
        }

        // Plain `if`, not AnimatedVisibility — see the KDoc. One frame, no
        // per-frame re-measure of a run that can be dozens of rows.
        if (expanded) {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                // Block order, not kind order — a folded run is a transcript and
                // the interleaving IS the record of how the agent worked.
                blocks.forEach { b ->
                    when (b.kind) {
                        "tool_use" -> ToolCallPill(
                            block = b,
                            allToolBlocks = tools,
                            onStop = onStop,
                            onOpenDetail = onOpenDetail,
                        )
                        "thinking" -> ThinkingBlock(
                            block = b,
                            // A folded block is settled by construction — the
                            // builder never folds the live trailing one.
                            isStreaming = false,
                            isLast = false,
                        )
                        // Narration between steps. Rendered as plain text, not
                        // full markdown: these are one-liners, and pulling the
                        // markdown renderer in here would let a stray fence or
                        // table inside a folded note blow up the row.
                        "text" -> if (b.content.isNotEmpty()) {
                            Text(
                                text = b.content,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}