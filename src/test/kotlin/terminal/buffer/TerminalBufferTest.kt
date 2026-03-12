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

    @Test
    fun insert_text_shifts_existing_cells_right_from_cursor_position() {
        val buffer = TerminalBuffer(width = 5, height = 2, maxScrollbackLines = 5)

        buffer.writeText("abc")
        buffer.setCursorPosition(column = 1, row = 0)
        buffer.insertText("Z")

        assertEquals("aZbc ", buffer.getScreenLine(0))
    }

    @Test
    fun insert_text_wraps_overflow_onto_following_visible_rows() {
        val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

        buffer.writeText("abcd")
        buffer.writeText("ef")
        buffer.setCursorPosition(column = 2, row = 0)
        buffer.insertText("XY")

        assertEquals("abXY", buffer.getScreenLine(0))
        assertEquals("cdef", buffer.getScreenLine(1))
    }

    @Test
    fun insert_text_overflow_at_screen_bottom_pushes_top_rows_into_scrollback() {
        val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

        buffer.writeText("abcd")
        buffer.writeText("efgh")
        buffer.setCursorPosition(column = 1, row = 0)
        buffer.insertText("Z")

        assertEquals("eZfg", buffer.getScreenLine(0))
        assertEquals("h   ", buffer.getScreenLine(1))
        assertEquals("abcd\neZfg\nh   ", buffer.getHistoryContent())
    }

    @Test
    fun insert_text_moves_cursor_to_position_after_inserted_text() {
        val buffer = TerminalBuffer(width = 5, height = 2, maxScrollbackLines = 5)

        buffer.writeText("abc")
        buffer.setCursorPosition(column = 1, row = 0)
        buffer.insertText("XY")

        assertEquals(3, buffer.getCursorColumn())
        assertEquals(0, buffer.getCursorRow())
        assertEquals("aXYbc", buffer.getScreenLine(0))
    }

    @Test
    fun insert_empty_line_at_bottom_scrolls_top_visible_line_into_scrollback() {
        val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

        buffer.writeText("abcd")
        buffer.writeText("ef")
        buffer.insertEmptyLineAtBottom()

        assertEquals("ef  ", buffer.getScreenLine(0))
        assertEquals("    ", buffer.getScreenLine(1))
        assertEquals("abcd\nef  \n    ", buffer.getHistoryContent())
    }

    @Test
    fun scrollback_is_trimmed_to_maximum_size() {
        val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 1)

        buffer.writeText("abcd")
        buffer.writeText("efgh")
        buffer.insertEmptyLineAtBottom()

        assertEquals("efgh\n    \n    ", buffer.getHistoryContent())
    }

    @Test
    fun clear_screen_resets_visible_content_but_keeps_scrollback() {
        val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

        buffer.writeText("abcdefghi")
        buffer.clearScreen()

        assertEquals("    ", buffer.getScreenLine(0))
        assertEquals("    ", buffer.getScreenLine(1))
        assertEquals("abcd\n    \n    ", buffer.getHistoryContent())
        assertEquals(0, buffer.getCursorColumn())
        assertEquals(0, buffer.getCursorRow())
    }

    @Test
    fun clear_screen_and_scrollback_resets_all_content_cursor_and_attributes() {
        val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)
        val attributes = CellAttributes(
            foreground = TerminalColor.YELLOW,
            background = TerminalColor.BLUE,
            styles = setOf(TextStyle.UNDERLINE),
        )

        buffer.setCurrentAttributes(attributes)
        buffer.writeText("abcdefghi")
        buffer.clearScreenAndScrollback()

        assertEquals("    ", buffer.getScreenLine(0))
        assertEquals("    ", buffer.getScreenLine(1))
        assertEquals("    \n    ", buffer.getHistoryContent())
        assertEquals(CellAttributes(), buffer.getCurrentAttributes())
        assertEquals(0, buffer.getCursorColumn())
        assertEquals(0, buffer.getCursorRow())
    }

    @Test
    fun get_screen_cell_returns_character_and_attributes_for_visible_position() {
        val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)
        val attributes = CellAttributes(
            foreground = TerminalColor.MAGENTA,
            background = TerminalColor.BLACK,
            styles = setOf(TextStyle.BOLD),
        )

        buffer.setCurrentAttributes(attributes)
        buffer.writeText("Q")

        assertEquals(Cell('Q', attributes), buffer.getScreenCell(column = 0, row = 0))
    }

    @Test
    fun get_history_cell_returns_character_and_attributes_for_combined_history_position() {
        val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

        buffer.writeText("abcdefghi")

        assertEquals(Cell('a', CellAttributes()), buffer.getHistoryCell(column = 0, row = 0))
        assertEquals(Cell('e', CellAttributes()), buffer.getHistoryCell(column = 0, row = 1))
    }

    @Test
    fun get_screen_line_returns_visible_row_as_plain_string() {
        val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

        buffer.writeText("abcd")

        assertEquals("abcd", buffer.getScreenLine(0))
    }

    @Test
    fun get_history_line_returns_combined_row_as_plain_string() {
        val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

        buffer.writeText("abcdefghi")

        assertEquals("abcd", buffer.getHistoryLine(0))
        assertEquals("efgh", buffer.getHistoryLine(1))
        assertEquals("i   ", buffer.getHistoryLine(2))
    }

    @Test
    fun get_screen_content_returns_visible_rows_joined_by_newlines() {
        val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

        buffer.writeText("abcdef")

        assertEquals("abcd\nef  ", buffer.getScreenContent())
    }

    @Test
    fun get_history_content_returns_scrollback_then_screen_joined_by_newlines() {
        val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

        buffer.writeText("abcdefghi")

        assertEquals("abcd\nefgh\ni   ", buffer.getHistoryContent())
    }
}
