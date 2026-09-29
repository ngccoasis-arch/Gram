package com.gram.core.media

import com.gram.core.tdlib.GramMessage
import com.gram.core.tdlib.MessageContent

data class ViewerItem(val message: GramMessage, val index: Int)

object MediaViewerRepository {
    /** Same-chat chronological photos and videos; image documents are intentionally excluded. */
    fun sequence(messages: List<GramMessage>): List<ViewerItem> = messages
        .filter { it.content is MessageContent.Photo || it.content is MessageContent.Video }
        .sortedBy { it.dateEpochSeconds }
        .mapIndexed { index, message -> ViewerItem(message, index) }

    fun adjacent(sequence: List<ViewerItem>, index: Int): List<ViewerItem> =
        listOfNotNull(sequence.getOrNull(index - 1), sequence.getOrNull(index + 1))
}
