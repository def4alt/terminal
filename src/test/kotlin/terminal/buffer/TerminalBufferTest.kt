package terminal.buffer

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TerminalBufferTest {
    @Test
    fun cell_defaults_to_blank_character_and_default_attributes() {
        val cell = Cell()

        assertEquals(null, cell.character)
        assertEquals(TerminalColor.DEFAULT, cell.attributes.foreground)
        assertEquals(TerminalColor.DEFAULT, cell.attributes.background)
        assertEquals(emptySet(), cell.attributes.styles)
    }

    @Test
    fun new_buffer_has_blank_screen_origin_cursor_and_empty_scrollback() {
        val buffer = TerminalBuffer(width = 4, height = 3, maxScrollbackLines = 5)

        assertEquals(0, buffer.getCursorColumn())
        assertEquals(0, buffer.getCursorRow())
        assertEquals("    ", buffer.getScreenLine(0))
        assertEquals("    ", buffer.getScreenLine(1))
        assertEquals("    ", buffer.getScreenLine(2))
        assertEquals("    \n    \n    ", buffer.getScreenContent())
        assertEquals("    \n    \n    ", buffer.getHistoryContent())
    }
}
