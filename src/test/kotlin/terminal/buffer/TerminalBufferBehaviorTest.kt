package terminal.buffer

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TerminalBufferBehaviorTest {
    private fun buffer(width: Int = 4, height: Int = 2, scrollback: Int = 5): TerminalBuffer {
        return TerminalBuffer(width = width, height = height, maxScrollbackLines = scrollback)
    }

    @Nested
    inner class HistoryAccess {
        @Test
        fun history_content_is_scrollback_followed_by_visible_screen() {
            val buffer = buffer()

            buffer.writeText("abcdefghi")

            assertEquals("abcd", buffer.getHistoryLine(0))
            assertEquals("efgh", buffer.getHistoryLine(1))
            assertEquals("i   ", buffer.getHistoryLine(2))
            assertEquals("abcd\nefgh\ni   ", buffer.getHistoryContent())
        }
    }

    @Nested
    inner class FillBehavior {
        @Test
        fun fill_replaces_only_the_current_visible_row() {
            val buffer = buffer()

            buffer.writeText("abcd")
            buffer.setCursorPosition(column = 0, row = 1)
            buffer.fillLine('x')

            assertEquals("abcd", buffer.getScreenLine(0))
            assertEquals("xxxx", buffer.getScreenLine(1))
        }
    }
}
