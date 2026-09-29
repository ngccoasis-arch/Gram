package com.gram.core.media

import com.gram.core.tdlib.GramMessage
import com.gram.core.tdlib.MediaRef
import com.gram.core.tdlib.MessageContent
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaViewerRepositoryTest {
    @Test fun `viewer combines photos and videos but excludes image documents`() {
        val items = listOf(
            message(3, MessageContent.Video(MediaRef(3))),
            message(1, MessageContent.Photo(MediaRef(1))),
            message(2, MessageContent.Document(MediaRef(2, mimeType = "image/jpeg"), "scan.jpg")),
        )
        assertEquals(listOf(1L, 3L), MediaViewerRepository.sequence(items).map { it.message.id })
    }

    private fun message(id: Long, content: MessageContent) = GramMessage(id, 7, "x", false, id, content)
}
