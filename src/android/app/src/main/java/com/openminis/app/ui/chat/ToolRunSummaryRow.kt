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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
 * [local #337] One row standing in for a run of finished tool calls, plus the
 * pills themselves when the user taps it open.
 *
 * The point is vertical: an agentic turn emits dozens of pills before its final
 * answer, and every one of them occupies a row in the transcript. Replacing a
 * settled run with a single line keeps the answer where the reader expects to
 * find it, and one tap brings the whole run back.
 *
 * The run's own state is never hidden — the header names how many calls folded
 * and how many of them failed, so a collapsed run still reads as a summary
 * rather than a gap.
 */
@Composable
internal fun ToolRunSummaryRow(
    blocks: List<AssistantBlock>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onStop: (() -> Unit)? = null,
) {
    val failed = blocks.count {
        it.toolStatus == ToolBlockStatus.FAILED || it.toolStatus == ToolBlockStatus.CANCELLED
    }
    val total = blocks.size

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
                text = if (failed > 0) {
                    "$total tool calls · $failed failed"
                } else {
                    "$total tool calls"
                },
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
                blocks.forEach { b ->
                    ToolCallPill(
                        block = b,
                        allToolBlocks = blocks,
                        onStop = onStop,
                        onOpenDetail = onOpenDetail,
                    )
                }
            }
        }
    }
}