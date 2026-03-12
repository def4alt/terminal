package terminal.buffer

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun set_cursor_position_updates_cursor_when_inside_bounds() {
        val buffer = TerminalBuffer(width = 4, height = 3, maxScrollbackLines = 5)

        buffer.setCursorPosition(column = 2, row = 1)

        assertEquals(2, buffer.getCursorColumn())
        assertEquals(1, buffer.getCursorRow())
    }

    @Test
    fun set_cursor_position_clamps_when_outside_bounds() {
        val buffer = TerminalBuffer(width = 4, height = 3, maxScrollbackLines = 5)

        buffer.setCursorPosition(column = 99, row = -4)

        assertEquals(3, buffer.getCursorColumn())
        assertEquals(0, buffer.getCursorRow())
    }

    @Test
    fun move_cursor_methods_respect_screen_edges() {
        val buffer = TerminalBuffer(width = 4, height = 3, maxScrollbackLines = 5)

        buffer.moveCursorRight(10)
        buffer.moveCursorDown(10)

        assertEquals(3, buffer.getCursorColumn())
        assertEquals(2, buffer.getCursorRow())

        buffer.moveCursorLeft(10)
        buffer.moveCursorUp(10)

        assertEquals(0, buffer.getCursorColumn())
        assertEquals(0, buffer.getCursorRow())
    }

    @Test
    fun current_attributes_default_to_terminal_defaults() {
        val buffer = TerminalBuffer(width = 4, height = 3, maxScrollbackLines = 5)

        assertEquals(CellAttributes(), buffer.getCurrentAttributes())
    }

    @Test
    fun set_current_attributes_changes_attributes_for_future_edits() {
        val buffer = TerminalBuffer(width = 4, height = 3, maxScrollbackLines = 5)
        val attributes = CellAttributes(
            foreground = TerminalColor.GREEN,
            background = TerminalColor.BLACK,
            styles = setOf(TextStyle.BOLD, TextStyle.UNDERLINE),
        )

        buffer.setCurrentAttributes(attributes)

        assertEquals(attributes, buffer.getCurrentAttributes())
    }

    @Test
    fun write_text_overwrites_cells_from_cursor_and_advances_cursor() {
        val buffer = TerminalBuffer(width = 4, height = 3, maxScrollbackLines = 5)

        buffer.writeText("abc")

        assertEquals("abc ", buffer.getScreenLine(0))
        assertEquals(3, buffer.getCursorColumn())
        assertEquals(0, buffer.getCursorRow())
    }

    @Test
    fun write_text_uses_current_attributes_for_written_cells_only() {
        val buffer = TerminalBuffer(width = 4, height = 3, maxScrollbackLines = 5)
        val attributes = CellAttributes(
            foreground = TerminalColor.RED,
            background = TerminalColor.WHITE,
            styles = setOf(TextStyle.ITALIC),
        )

        buffer.setCurrentAttributes(attributes)
        buffer.writeText("A")

        val writtenCell = buffer.getScreenCell(column = 0, row = 0)
        val untouchedCell = buffer.getScreenCell(column = 1, row = 0)

        assertEquals('A', writtenCell.character)
        assertEquals(attributes, writtenCell.attributes)
        assertNull(untouchedCell.character)
        assertEquals(CellAttributes(), untouchedCell.attributes)
    }

    @Test
    fun write_text_continues_on_next_screen_row_when_reaching_line_end() {
        val buffer = TerminalBuffer(width = 4, height = 3, maxScrollbackLines = 5)

        buffer.setCursorPosition(column = 3, row = 0)
        buffer.writeText("AB")

        assertEquals("   A", buffer.getScreenLine(0))
        assertEquals("B   ", buffer.getScreenLine(1))
        assertEquals(1, buffer.getCursorColumn())
        assertEquals(1, buffer.getCursorRow())
    }

    @Test
    fun write_text_at_bottom_row_scrolls_content_into_scrollback_when_needed() {
        val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

        buffer.writeText("abcdefghi")

        assertEquals("efgh", buffer.getScreenLine(0))
        assertEquals("i   ", buffer.getScreenLine(1))
        assertEquals("abcd\nefgh\ni   ", buffer.getHistoryContent())
        assertEquals(1, buffer.getCursorColumn())
        assertEquals(1, buffer.getCursorRow())
    }

    @Test
    fun fill_line_replaces_current_row_with_repeated_character_using_current_attributes() {
        val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)
        val attributes = CellAttributes(
            foreground = TerminalColor.CYAN,
            background = TerminalColor.BLACK,
            styles = setOf(TextStyle.BOLD),
        )

        buffer.setCursorPosition(column = 2, row = 1)
        buffer.setCurrentAttributes(attributes)
        buffer.fillLine('x')

        assertEquals("xxxx", buffer.getScreenLine(1))
        assertEquals(Cell('x', attributes), buffer.getScreenCell(column = 0, row = 1))
        assertEquals(Cell('x', attributes), buffer.getScreenCell(column = 3, row = 1))
    }

    @Test
    fun fill_line_with_null_clears_current_row_to_blank_cells() {
        val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

        buffer.setCursorPosition(column = 0, row = 1)
        buffer.writeText("test")
        buffer.setCursorPosition(column = 0, row = 1)
        buffer.fillLine(null)

        assertEquals("    ", buffer.getScreenLine(1))
        assertEquals(Cell(), buffer.getScreenCell(column = 2, row = 1))
    }
}
