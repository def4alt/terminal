package terminal.buffer

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TerminalBufferTest {
    private fun buffer(width: Int = 4, height: Int = 3, scrollback: Int = 5): TerminalBuffer {
        return TerminalBuffer(width = width, height = height, maxScrollbackLines = scrollback)
    }

    private fun attributes(
        foreground: TerminalColor,
        background: TerminalColor,
        vararg styles: TextStyle,
    ): CellAttributes {
        return CellAttributes(foreground = foreground, background = background, styles = styles.toSet())
    }

    private fun assertCursor(buffer: TerminalBuffer, column: Int, row: Int) {
        assertEquals(column, buffer.getCursorColumn())
        assertEquals(row, buffer.getCursorRow())
    }

    @Test
    fun cell_defaults_to_empty_kind_and_default_attributes() {
        val cell = Cell()

        assertEquals(CellKind.Empty, cell.kind)
        assertEquals(TerminalColor.DEFAULT, cell.attributes.foreground)
        assertEquals(TerminalColor.DEFAULT, cell.attributes.background)
        assertEquals(emptySet(), cell.attributes.styles)
    }

    @Test
    fun new_buffer_has_blank_screen_origin_cursor_and_empty_scrollback() {
        val buffer = buffer()

        assertCursor(buffer, column = 0, row = 0)
        assertEquals("    ", buffer.getScreenLine(0))
        assertEquals("    ", buffer.getScreenLine(1))
        assertEquals("    ", buffer.getScreenLine(2))
        assertEquals("    \n    \n    ", buffer.getScreenContent())
        assertEquals("    \n    \n    ", buffer.getHistoryContent())
    }

    @Test
    fun set_cursor_position_updates_cursor_when_inside_bounds() {
        val buffer = buffer()

        buffer.setCursorPosition(column = 2, row = 1)

        assertCursor(buffer, column = 2, row = 1)
    }

    @Test
    fun set_cursor_position_clamps_when_outside_bounds() {
        val buffer = buffer()

        buffer.setCursorPosition(column = 99, row = -4)

        assertCursor(buffer, column = 3, row = 0)
    }

    @Test
    fun move_cursor_methods_respect_screen_edges() {
        val buffer = buffer()

        buffer.moveCursorRight(10)
        buffer.moveCursorDown(10)

        assertCursor(buffer, column = 3, row = 2)

        buffer.moveCursorLeft(10)
        buffer.moveCursorUp(10)

        assertCursor(buffer, column = 0, row = 0)
    }

    @Test
    fun current_attributes_default_to_terminal_defaults() {
        val buffer = buffer()

        assertEquals(CellAttributes(), buffer.getCurrentAttributes())
    }

    @Test
    fun set_current_attributes_changes_attributes_for_future_edits() {
        val buffer = buffer()
        val attributes = attributes(TerminalColor.GREEN, TerminalColor.BLACK, TextStyle.BOLD, TextStyle.UNDERLINE)

        buffer.setCurrentAttributes(attributes)

        assertEquals(attributes, buffer.getCurrentAttributes())
    }

    @Test
    fun write_text_overwrites_cells_from_cursor_and_advances_cursor() {
        val buffer = buffer()

        buffer.writeText("abc")

        assertEquals("abc ", buffer.getScreenLine(0))
        assertCursor(buffer, column = 3, row = 0)
    }

    @Test
    fun write_text_uses_current_attributes_for_written_cells_only() {
        val buffer = buffer()
        val attributes = attributes(TerminalColor.RED, TerminalColor.WHITE, TextStyle.ITALIC)

        buffer.setCurrentAttributes(attributes)
        buffer.writeText("A")

        val writtenCell = buffer.getScreenCell(column = 0, row = 0)
        val untouchedCell = buffer.getScreenCell(column = 1, row = 0)

        assertEquals(CellKind.GraphemeStart("A", 1), writtenCell.kind)
        assertEquals(attributes, writtenCell.attributes)
        assertEquals(CellKind.Empty, untouchedCell.kind)
        assertEquals(CellAttributes(), untouchedCell.attributes)
    }

    @Test
    fun write_text_continues_on_next_screen_row_when_reaching_line_end() {
        val buffer = buffer()

        buffer.setCursorPosition(column = 3, row = 0)
        buffer.writeText("AB")

        assertEquals("   A", buffer.getScreenLine(0))
        assertEquals("B   ", buffer.getScreenLine(1))
        assertCursor(buffer, column = 1, row = 1)
    }

    @Test
    fun write_text_at_bottom_row_scrolls_content_into_scrollback_when_needed() {
        val buffer = buffer(height = 2)

        buffer.writeText("abcdefghi")

        assertEquals("efgh", buffer.getScreenLine(0))
        assertEquals("i   ", buffer.getScreenLine(1))
        assertEquals("abcd\nefgh\ni   ", buffer.getHistoryContent())
        assertCursor(buffer, column = 1, row = 1)
    }

    @Test
    fun fill_line_replaces_current_row_with_repeated_character_using_current_attributes() {
        val buffer = buffer(height = 2)
        val attributes = attributes(TerminalColor.CYAN, TerminalColor.BLACK, TextStyle.BOLD)

        buffer.setCursorPosition(column = 2, row = 1)
        buffer.setCurrentAttributes(attributes)
        buffer.fillLine('x')

        assertEquals("xxxx", buffer.getScreenLine(1))
        assertEquals(Cell(CellKind.GraphemeStart("x", 1), attributes), buffer.getScreenCell(column = 0, row = 1))
        assertEquals(Cell(CellKind.GraphemeStart("x", 1), attributes), buffer.getScreenCell(column = 3, row = 1))
    }

    @Test
    fun fill_line_with_null_clears_current_row_to_blank_cells() {
        val buffer = buffer(height = 2)

        buffer.setCursorPosition(column = 0, row = 1)
        buffer.writeText("test")
        buffer.setCursorPosition(column = 0, row = 1)
        buffer.fillLine(null)

        assertEquals("    ", buffer.getScreenLine(1))
        assertEquals(Cell(), buffer.getScreenCell(column = 2, row = 1))
    }

    @Test
    fun insert_text_shifts_existing_cells_right_from_cursor_position() {
        val buffer = buffer(width = 5, height = 2)

        buffer.writeText("abc")
        buffer.setCursorPosition(column = 1, row = 0)
        buffer.insertText("Z")

        assertEquals("aZbc ", buffer.getScreenLine(0))
    }

    @Test
    fun insert_text_wraps_overflow_onto_following_visible_rows() {
        val buffer = buffer(height = 2)

        buffer.writeText("abcd")
        buffer.writeText("ef")
        buffer.setCursorPosition(column = 2, row = 0)
        buffer.insertText("XY")

        assertEquals("abXY", buffer.getScreenLine(0))
        assertEquals("cdef", buffer.getScreenLine(1))
    }

    @Test
    fun insert_text_overflow_at_screen_bottom_pushes_top_rows_into_scrollback() {
        val buffer = buffer(height = 2)

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
        val buffer = buffer(width = 5, height = 2)

        buffer.writeText("abc")
        buffer.setCursorPosition(column = 1, row = 0)
        buffer.insertText("XY")

        assertCursor(buffer, column = 3, row = 0)
        assertEquals("aXYbc", buffer.getScreenLine(0))
    }

    @Test
    fun delete_characters_removes_text_at_cursor_and_shifts_remaining_content_left() {
        val buffer = buffer(width = 5, height = 2)

        buffer.writeText("abcde")
        buffer.setCursorPosition(column = 1, row = 0)
        buffer.deleteCharacters()

        assertEquals("acde ", buffer.getScreenLine(0))
        assertCursor(buffer, column = 1, row = 0)
    }

    @Test
    fun delete_characters_removes_wide_graphemes_as_one_unit() {
        val buffer = buffer(width = 6, height = 2)

        buffer.writeText("a界bc")
        buffer.setCursorPosition(column = 1, row = 0)
        buffer.deleteCharacters()

        assertEquals("abc   ", buffer.getScreenLine(0))
        assertEquals(CellKind.GraphemeStart("b", 1), buffer.getScreenCell(column = 1, row = 0).kind)
        assertEquals(CellKind.Empty, buffer.getScreenCell(column = 3, row = 0).kind)
    }

    @Test
    fun delete_characters_normalizes_from_continuation_cells_before_deleting() {
        val buffer = buffer(width = 6, height = 2)

        buffer.writeText("界ab")
        buffer.setCursorPosition(column = 1, row = 0)
        buffer.deleteCharacters()

        assertEquals("ab    ", buffer.getScreenLine(0))
        assertCursor(buffer, column = 0, row = 0)
    }

    @Test
    fun backspace_deletes_the_grapheme_before_the_cursor_and_shifts_left() {
        val buffer = buffer(width = 5, height = 2)

        buffer.writeText("abcde")
        buffer.backspace()

        assertEquals("abcd ", buffer.getScreenLine(0))
        assertCursor(buffer, column = 4, row = 0)
    }

    @Test
    fun backspace_removes_wide_graphemes_as_one_unit() {
        val buffer = buffer(width = 6, height = 2)

        buffer.writeText("a界bc")
        buffer.setCursorPosition(column = 3, row = 0)
        buffer.backspace()

        assertEquals("abc   ", buffer.getScreenLine(0))
        assertCursor(buffer, column = 1, row = 0)
    }

    @Test
    fun backspace_at_start_of_row_keeps_content_unchanged() {
        val buffer = buffer(width = 5, height = 2)

        buffer.writeText("abc")
        buffer.setCursorPosition(column = 0, row = 0)
        buffer.backspace()

        assertEquals("abc  ", buffer.getScreenLine(0))
        assertCursor(buffer, column = 0, row = 0)
    }

    @Test
    fun insert_empty_line_at_bottom_scrolls_top_visible_line_into_scrollback() {
        val buffer = buffer(height = 2)

        buffer.writeText("abcd")
        buffer.writeText("ef")
        buffer.insertEmptyLineAtBottom()

        assertEquals("ef  ", buffer.getScreenLine(0))
        assertEquals("    ", buffer.getScreenLine(1))
        assertEquals("abcd\nef  \n    ", buffer.getHistoryContent())
    }

    @Test
    fun scrollback_is_trimmed_to_maximum_size() {
        val buffer = buffer(height = 2, scrollback = 1)

        buffer.writeText("abcd")
        buffer.writeText("efgh")
        buffer.insertEmptyLineAtBottom()

        assertEquals("efgh\n    \n    ", buffer.getHistoryContent())
    }

    @Test
    fun clear_screen_resets_visible_content_but_keeps_scrollback() {
        val buffer = buffer(height = 2)

        buffer.writeText("abcdefghi")
        buffer.clearScreen()

        assertEquals("    ", buffer.getScreenLine(0))
        assertEquals("    ", buffer.getScreenLine(1))
        assertEquals("abcd\n    \n    ", buffer.getHistoryContent())
        assertCursor(buffer, column = 0, row = 0)
    }

    @Test
    fun clear_screen_and_scrollback_resets_all_content_cursor_and_attributes() {
        val buffer = buffer(height = 2)
        val attributes = attributes(TerminalColor.YELLOW, TerminalColor.BLUE, TextStyle.UNDERLINE)

        buffer.setCurrentAttributes(attributes)
        buffer.writeText("abcdefghi")
        buffer.clearScreenAndScrollback()

        assertEquals("    ", buffer.getScreenLine(0))
        assertEquals("    ", buffer.getScreenLine(1))
        assertEquals("    \n    ", buffer.getHistoryContent())
        assertEquals(CellAttributes(), buffer.getCurrentAttributes())
        assertCursor(buffer, column = 0, row = 0)
    }

    @Test
    fun get_screen_cell_returns_character_and_attributes_for_visible_position() {
        val buffer = buffer(height = 2)
        val attributes = attributes(TerminalColor.MAGENTA, TerminalColor.BLACK, TextStyle.BOLD)

        buffer.setCurrentAttributes(attributes)
        buffer.writeText("Q")

        assertEquals(Cell(CellKind.GraphemeStart("Q", 1), attributes), buffer.getScreenCell(column = 0, row = 0))
    }

    @Test
    fun get_screen_character_and_attributes_return_spec_level_view_of_screen_content() {
        val buffer = buffer(height = 2)
        val attributes = attributes(TerminalColor.MAGENTA, TerminalColor.BLACK, TextStyle.BOLD)

        buffer.setCurrentAttributes(attributes)
        buffer.writeText("Q")

        assertEquals("Q", buffer.getScreenCharacter(column = 0, row = 0))
        assertEquals(null, buffer.getScreenCharacter(column = 1, row = 0))
        assertEquals(attributes, buffer.getScreenAttributes(column = 0, row = 0))
        assertEquals(CellAttributes(), buffer.getScreenAttributes(column = 1, row = 0))
    }

    @Test
    fun get_history_cell_returns_character_and_attributes_for_combined_history_position() {
        val buffer = buffer(height = 2)

        buffer.writeText("abcdefghi")

        assertEquals(Cell(CellKind.GraphemeStart("a", 1), CellAttributes()), buffer.getHistoryCell(column = 0, row = 0))
        assertEquals(Cell(CellKind.GraphemeStart("e", 1), CellAttributes()), buffer.getHistoryCell(column = 0, row = 1))
    }

    @Test
    fun get_history_character_and_attributes_return_spec_level_view_of_scrollback_and_screen() {
        val buffer = buffer(height = 2)

        buffer.writeText("abcdefghi")

        assertEquals("a", buffer.getHistoryCharacter(column = 0, row = 0))
        assertEquals("e", buffer.getHistoryCharacter(column = 0, row = 1))
        assertEquals("i", buffer.getHistoryCharacter(column = 0, row = 2))
        assertEquals(null, buffer.getHistoryCharacter(column = 1, row = 2))
        assertEquals(CellAttributes(), buffer.getHistoryAttributes(column = 0, row = 0))
        assertEquals(CellAttributes(), buffer.getHistoryAttributes(column = 1, row = 2))
    }

    @Test
    fun write_text_stores_wide_grapheme_start_and_continuation_cells() {
        val buffer = buffer(height = 2)

        buffer.writeText("界")

        assertEquals(CellKind.GraphemeStart("界", 2), buffer.getScreenCell(column = 0, row = 0).kind)
        assertEquals(CellKind.Continuation, buffer.getScreenCell(column = 1, row = 0).kind)
        assertCursor(buffer, column = 2, row = 0)
    }

    @Test
    fun write_text_wraps_wide_grapheme_when_only_one_cell_remains() {
        val buffer = buffer(height = 2)

        buffer.setCursorPosition(column = 3, row = 0)
        buffer.writeText("界")

        assertEquals("    ", buffer.getScreenLine(0))
        assertEquals(CellKind.GraphemeStart("界", 2), buffer.getScreenCell(column = 0, row = 1).kind)
        assertEquals(CellKind.Continuation, buffer.getScreenCell(column = 1, row = 1).kind)
        assertCursor(buffer, column = 2, row = 1)
    }

    @Test
    fun get_screen_line_does_not_render_continuation_cells_as_visible_spaces() {
        val buffer = buffer(width = 6, height = 2)

        buffer.writeText("a界b")

        assertEquals("a界b  ", buffer.getScreenLine(0))
    }

    @Test
    fun resize_width_grow_preserves_existing_graphemes_and_pads_right_side() {
        val buffer = buffer(height = 2)

        buffer.writeText("a界")
        buffer.resize(newWidth = 6, newHeight = 2)

        assertEquals("a界   ", buffer.getScreenLine(0))
    }

    @Test
    fun resize_width_grow_preserves_attributes_of_surviving_graphemes() {
        val buffer = buffer(height = 2)
        val attributes = attributes(TerminalColor.GREEN, TerminalColor.BLACK, TextStyle.BOLD)

        buffer.setCurrentAttributes(attributes)
        buffer.writeText("界")
        buffer.resize(newWidth = 6, newHeight = 2)

        assertEquals(attributes, buffer.getScreenCell(column = 0, row = 0).attributes)
        assertEquals(attributes, buffer.getScreenCell(column = 1, row = 0).attributes)
    }

    @Test
    fun resize_width_shrink_keeps_only_whole_graphemes_that_fit() {
        val buffer = buffer(width = 6, height = 2)

        buffer.writeText("a界b")
        buffer.resize(newWidth = 3, newHeight = 2)

        assertEquals("a界", buffer.getScreenLine(0))
    }

    @Test
    fun resize_width_shrink_drops_wide_grapheme_that_no_longer_fits() {
        val buffer = buffer(width = 6, height = 2)

        buffer.writeText("ab界")
        buffer.resize(newWidth = 3, newHeight = 2)

        assertEquals("ab ", buffer.getScreenLine(0))
    }

    @Test
    fun resize_width_shrink_never_leaves_continuation_cells_visible() {
        val buffer = buffer(width = 6, height = 2)

        buffer.writeText("a界b")
        buffer.resize(newWidth = 2, newHeight = 2)

        assertEquals("a ", buffer.getScreenLine(0))
        assertEquals(CellKind.Empty, buffer.getScreenCell(column = 1, row = 0).kind)
    }

    @Test
    fun resize_height_grow_appends_blank_rows_at_bottom() {
        val buffer = buffer(height = 2)

        buffer.writeText("abcd")
        buffer.resize(newWidth = 4, newHeight = 4)

        assertEquals("abcd", buffer.getScreenLine(0))
        assertEquals("    ", buffer.getScreenLine(2))
        assertEquals("    ", buffer.getScreenLine(3))
    }

    @Test
    fun resize_height_shrink_moves_trimmed_top_rows_into_scrollback() {
        val buffer = buffer()

        buffer.fillLine('a')
        buffer.setCursorPosition(column = 0, row = 1)
        buffer.fillLine('b')
        buffer.setCursorPosition(column = 0, row = 2)
        buffer.fillLine('c')
        buffer.resize(newWidth = 4, newHeight = 2)

        assertEquals("aaaa\nbbbb\ncccc", buffer.getHistoryContent())
    }

    @Test
    fun resize_height_shrink_respects_scrollback_capacity() {
        val buffer = buffer(scrollback = 1)

        buffer.fillLine('a')
        buffer.setCursorPosition(column = 0, row = 1)
        buffer.fillLine('b')
        buffer.setCursorPosition(column = 0, row = 2)
        buffer.fillLine('c')
        buffer.resize(newWidth = 4, newHeight = 2)

        assertEquals("aaaa\nbbbb\ncccc", buffer.getHistoryContent())

        buffer.insertEmptyLineAtBottom()

        assertEquals("bbbb\ncccc\n    ", buffer.getHistoryContent())
    }

    @Test
    fun resize_clamps_cursor_into_new_bounds() {
        val buffer = buffer(width = 6)

        buffer.setCursorPosition(column = 5, row = 2)
        buffer.resize(newWidth = 3, newHeight = 2)

        assertCursor(buffer, column = 2, row = 1)
    }

    @Test
    fun resize_normalizes_cursor_off_continuation_cell() {
        val buffer = buffer(width = 6, height = 2)

        buffer.writeText("a界b")
        buffer.setCursorPosition(column = 2, row = 0)
        buffer.resize(newWidth = 3, newHeight = 2)

        assertEquals(1, buffer.getCursorColumn())
    }

    @Test
    fun move_cursor_right_skips_continuation_cells_of_wide_graphemes() {
        val buffer = buffer(width = 6, height = 2)

        buffer.writeText("界a")
        buffer.setCursorPosition(column = 0, row = 0)
        buffer.moveCursorRight()

        assertCursor(buffer, column = 2, row = 0)
    }

    @Test
    fun insert_text_keeps_wide_grapheme_cells_together() {
        val buffer = buffer(width = 6, height = 2)

        buffer.writeText("ab")
        buffer.setCursorPosition(column = 1, row = 0)
        buffer.insertText("界")

        assertEquals("a界b  ", buffer.getScreenLine(0))
        assertEquals(CellKind.GraphemeStart("界", 2), buffer.getScreenCell(column = 1, row = 0).kind)
        assertEquals(CellKind.Continuation, buffer.getScreenCell(column = 2, row = 0).kind)
    }

    @Test
    fun segmenter_keeps_ascii_as_single_codepoint_graphemes() {
        assertEquals(listOf("a", "b", "c"), segmentGraphemes("abc").map { it.text })
    }

    @Test
    fun segmenter_keeps_combining_mark_sequence_as_one_grapheme() {
        assertEquals(listOf("e\u0301"), segmentGraphemes("e\u0301").map { it.text })
    }

    @Test
    fun segmenter_keeps_emoji_modifier_sequence_as_one_grapheme() {
        assertEquals(listOf("👍🏻"), segmentGraphemes("👍🏻").map { it.text })
    }

    @Test
    fun segmenter_keeps_zwj_emoji_sequence_as_one_grapheme() {
        assertEquals(listOf("👨‍👩‍👧‍👦"), segmentGraphemes("👨‍👩‍👧‍👦").map { it.text })
    }

    @Test
    fun segmenter_keeps_flag_sequence_as_one_grapheme() {
        assertEquals(listOf("🇵🇱"), segmentGraphemes("🇵🇱").map { it.text })
    }

    @Test
    fun wide_cjk_grapheme_has_display_width_two() {
        assertEquals(2, measureDisplayWidth("界"))
    }

    @Test
    fun emoji_modifier_sequence_has_display_width_two() {
        assertEquals(2, measureDisplayWidth("👍🏻"))
    }

    @Test
    fun combining_mark_sequence_has_display_width_one() {
        assertEquals(1, measureDisplayWidth("e\u0301"))
    }

    @Test
    fun flag_sequence_has_display_width_two() {
        assertEquals(2, measureDisplayWidth("🇵🇱"))
    }

    @Test
    fun write_text_stores_emoji_modifier_sequence_in_one_grapheme_start() {
        val buffer = buffer(width = 6, height = 2)

        buffer.writeText("👍🏻")

        assertEquals(CellKind.GraphemeStart("👍🏻", 2), buffer.getScreenCell(0, 0).kind)
        assertEquals(CellKind.Continuation, buffer.getScreenCell(1, 0).kind)
    }

    @Test
    fun write_text_stores_zwj_sequence_in_one_grapheme_start() {
        val buffer = buffer(width = 8, height = 2)

        buffer.writeText("👨‍👩‍👧‍👦")

        assertEquals(CellKind.GraphemeStart("👨‍👩‍👧‍👦", 2), buffer.getScreenCell(0, 0).kind)
        assertEquals(CellKind.Continuation, buffer.getScreenCell(1, 0).kind)
    }

    @Test
    fun write_text_stores_combining_mark_sequence_in_one_grapheme_start() {
        val buffer = buffer(width = 6, height = 2)

        buffer.writeText("e\u0301")

        assertEquals(CellKind.GraphemeStart("e\u0301", 1), buffer.getScreenCell(0, 0).kind)
        assertCursor(buffer, column = 1, row = 0)
    }

    @Test
    fun cursor_advances_by_grapheme_display_width_not_codepoint_count() {
        val buffer = buffer(width = 6, height = 2)

        buffer.writeText("👍🏻a")

        assertCursor(buffer, column = 3, row = 0)
    }

    @Test
    fun insert_text_keeps_emoji_modifier_sequence_together() {
        val buffer = buffer(width = 8, height = 2)

        buffer.writeText("ab")
        buffer.setCursorPosition(column = 1, row = 0)
        buffer.insertText("👍🏻")

        assertEquals(CellKind.GraphemeStart("👍🏻", 2), buffer.getScreenCell(1, 0).kind)
        assertEquals(CellKind.Continuation, buffer.getScreenCell(2, 0).kind)
        assertEquals("a👍🏻b    ", buffer.getScreenLine(0))
    }

    @Test
    fun insert_text_keeps_zwj_sequence_together() {
        val buffer = buffer(width = 8, height = 2)

        buffer.writeText("ab")
        buffer.setCursorPosition(column = 1, row = 0)
        buffer.insertText("👨‍👩‍👧‍👦")

        assertEquals(CellKind.GraphemeStart("👨‍👩‍👧‍👦", 2), buffer.getScreenCell(1, 0).kind)
        assertEquals(CellKind.Continuation, buffer.getScreenCell(2, 0).kind)
    }

    @Test
    fun insert_before_wide_grapheme_does_not_split_existing_cluster() {
        val buffer = buffer(width = 8, height = 2)

        buffer.writeText("界b")
        buffer.setCursorPosition(column = 0, row = 0)
        buffer.insertText("a")

        assertEquals(CellKind.GraphemeStart("界", 2), buffer.getScreenCell(1, 0).kind)
        assertEquals(CellKind.Continuation, buffer.getScreenCell(2, 0).kind)
        assertEquals("a界b    ", buffer.getScreenLine(0))
    }

    @Test
    fun move_cursor_right_skips_continuation_cells_for_emoji_clusters() {
        val buffer = buffer(width = 8, height = 2)

        buffer.writeText("👍🏻a")
        buffer.setCursorPosition(column = 0, row = 0)
        buffer.moveCursorRight()

        assertEquals(2, buffer.getCursorColumn())
    }

    @Test
    fun set_cursor_position_normalizes_from_continuation_to_grapheme_start() {
        val buffer = buffer(width = 8, height = 2)

        buffer.writeText("👍🏻a")
        buffer.setCursorPosition(column = 1, row = 0)

        assertEquals(0, buffer.getCursorColumn())
    }

    @Test
    fun overwrite_on_continuation_clears_the_whole_grapheme() {
        val buffer = buffer(width = 8, height = 2)

        buffer.writeText("👍🏻a")
        buffer.setCursorPosition(column = 1, row = 0)
        buffer.writeText("b")

        assertEquals("b a     ", buffer.getScreenLine(0))
        assertEquals(CellKind.GraphemeStart("b", 1), buffer.getScreenCell(0, 0).kind)
        assertEquals(CellKind.Empty, buffer.getScreenCell(1, 0).kind)
    }

    @Test
    fun get_screen_line_returns_visible_row_as_plain_string() {
        val buffer = buffer(height = 2)

        buffer.writeText("abcd")

        assertEquals("abcd", buffer.getScreenLine(0))
    }

    @Test
    fun get_history_line_returns_combined_row_as_plain_string() {
        val buffer = buffer(height = 2)

        buffer.writeText("abcdefghi")

        assertEquals("abcd", buffer.getHistoryLine(0))
        assertEquals("efgh", buffer.getHistoryLine(1))
        assertEquals("i   ", buffer.getHistoryLine(2))
    }

    @Test
    fun get_screen_content_returns_visible_rows_joined_by_newlines() {
        val buffer = buffer(height = 2)

        buffer.writeText("abcdef")

        assertEquals("abcd\nef  ", buffer.getScreenContent())
    }

    @Test
    fun get_history_content_returns_scrollback_then_screen_joined_by_newlines() {
        val buffer = buffer(height = 2)

        buffer.writeText("abcdefghi")

        assertEquals("abcd\nefgh\ni   ", buffer.getHistoryContent())
    }

    @Test
    fun write_text_with_empty_string_does_not_change_content_or_cursor() {
        val buffer = buffer(height = 2)

        buffer.writeText("")

        assertEquals("    \n    ", buffer.getScreenContent())
        assertCursor(buffer, column = 0, row = 0)
    }

    @Test
    fun insert_text_with_empty_string_does_not_change_content_or_cursor() {
        val buffer = buffer(height = 2)

        buffer.insertText("")

        assertEquals("    \n    ", buffer.getScreenContent())
        assertCursor(buffer, column = 0, row = 0)
    }

    @Test
    fun move_cursor_by_zero_does_not_change_position() {
        val buffer = buffer(height = 2)

        buffer.setCursorPosition(column = 2, row = 1)
        buffer.moveCursorRight(0)
        buffer.moveCursorLeft(0)
        buffer.moveCursorDown(0)
        buffer.moveCursorUp(0)

        assertCursor(buffer, column = 2, row = 1)
    }

    @Test
    fun bottom_overflow_discards_oldest_scrollback_line_when_at_capacity() {
        val buffer = buffer(height = 2, scrollback = 2)

        buffer.writeText("abcdefghijklmnopq")

        assertEquals("efgh\nijkl\nmnop\nq   ", buffer.getHistoryContent())
    }

    @Test
    fun fill_line_preserves_other_rows() {
        val buffer = buffer(height = 2)

        buffer.writeText("abcd")
        buffer.setCursorPosition(column = 0, row = 1)
        buffer.fillLine('x')

        assertEquals("abcd", buffer.getScreenLine(0))
        assertEquals("xxxx", buffer.getScreenLine(1))
    }

    @Test
    fun constructor_rejects_non_positive_dimensions() {
        assertFailsWith<IllegalArgumentException> {
            TerminalBuffer(width = 0, height = 2, maxScrollbackLines = 5)
        }

        assertFailsWith<IllegalArgumentException> {
            TerminalBuffer(width = 2, height = 0, maxScrollbackLines = 5)
        }
    }
}
