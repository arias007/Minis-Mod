package com.openminis.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * [local #362] Tappable options parsed out of a finished reply's closing list.
 *
 * The whole point is to remove retyping: the assistant just wrote these exact
 * words, so the answer should be one tap away. Tap FILLS the composer rather
 * than sending — an accidental send on a half-formed choice is worse than the
 * extra tap, and it leaves the wording editable when the model's phrasing is
 * not quite what the user meant.
 */
@Composable
internal fun ReplyOptionsRow(
    messageId: String,
    replyText: String,
    enabled: Boolean,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Parsing is pure and the reply is frozen once it completes, so this runs
    // once per message rather than per recomposition.
    val options = remember(messageId, replyText) {
        ReplyOptions.parse(replyText)?.options
    }
    if (!enabled) return
    if (options.isNullOrEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onPick(option) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = option,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}