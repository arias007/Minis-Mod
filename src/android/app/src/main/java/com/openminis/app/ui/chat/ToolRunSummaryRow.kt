package com.openminis.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
 * [local #337] One row standing in for a run of finished process — reasoning and
 * tool calls together — plus the individual rows themselves when the user taps
 * it open.
 *
 * The point is vertical: an agentic turn emits a Deep Thinking card per
 * reasoning step and a pill per tool call before its final answer, and every
 * one of them occupies a row in the transcript. Replacing a settled run with a
 * single line keeps the answer where the reader expects to find it, and one tap
 * brings the whole run back.
 *
 * The run's own state is never hidden — the header names how many calls folded
 * and how many of them failed, so a collapsed run still reads as a summary
 * rather than a gap.
 *
 * [local #337] Both kinds fold. A real turn interleaves thinking → tool →
 * thinking → tool → answer, so folding only the pills left the reasoning cards
 * stranded between this row and the answer — which is most of the vertical space
 * the feature exists to reclaim. They render through the same ThinkingBlock the
 * standalone path uses, so a folded block still opens, still shows its token
 * count, and still obeys the user's Appearance setting.
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
    val failed = tools.count {
        it.toolStatus == ToolBlockStatus.FAILED || it.toolStatus == ToolBlockStatus.CANCELLED
    }

    // What the header calls itself. With reasoning folded in, "7 tool calls"
    // understated the row — the thought blocks are the bulk of it. Report the
    // two kinds separately so the number matches what tapping reveals.
    val label = buildString {
        if (thinkingBlocks.isNotEmpty()) {
            append(thinkingBlocks.size)
            append(if (thinkingBlocks.size == 1) " thinking block" else " thinking blocks")
        }
        if (tools.isNotEmpty()) {
            if (isNotEmpty()) append(" · ")
            append(tools.size)
            append(if (tools.size == 1) " tool call" else " tool calls")
        }
        if (failed > 0) {
            append(" · ")
            append(failed)
            append(if (failed == 1) " failed" else " failed")
        }
    }

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

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                // Block order, not kind order — a folded run is a transcript
                // and interleaving IS the record of how the agent worked.
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
                            // builder never folds the live trailing one — so
                            // isStreaming is false here regardless.
                            isStreaming = false,
                            isLast = false,
                        )
                    }
                }
            }
        }
    }
}