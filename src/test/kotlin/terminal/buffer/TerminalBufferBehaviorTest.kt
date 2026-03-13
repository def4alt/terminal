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

            buffer.write("abcdefghi")

            assertEquals("abcd", buffer.historyLineAt(0))
            assertEquals("efgh", buffer.historyLineAt(1))
            assertEquals("i   ", buffer.historyLineAt(2))
            assertEquals("abcd\nefgh\ni   ", buffer.historyText())
        }

        @Test
        fun character_and_attribute_access_return_null_for_empty_cells_and_values_for_written_cells() {
            val buffer = buffer()

            buffer.write("abcdefghi")

            assertEquals("a", buffer.historyCharacterAt(0, 0))
            assertEquals("i", buffer.historyCharacterAt(0, 2))
            assertEquals(null, buffer.historyCharacterAt(1, 2))
            assertEquals(CellAttributes(), buffer.historyAttributesAt(0, 0))
            assertEquals(CellAttributes(), buffer.historyAttributesAt(1, 2))
        }

        @Test
        fun clearing_the_screen_keeps_scrollback_but_replaces_visible_rows_with_blanks() {
            val buffer = buffer()

            buffer.write("abcdefghi")
            buffer.clearScreen()

            assertEquals("abcd\n    \n    ", buffer.historyText())
        }
    }

    @Nested
    inner class CursorBehavior {
        @Test
        fun cursor_starts_at_the_screen_origin() {
            val buffer = buffer()

            assertEquals(0, buffer.cursorColumn())
            assertEquals(0, buffer.cursorRow())
        }

        @Test
        fun cursor_movement_is_clamped_to_screen_bounds() {
            val buffer = buffer()

            buffer.moveCursorRight(99)
            buffer.moveCursorDown(99)
            assertEquals(3, buffer.cursorColumn())
            assertEquals(1, buffer.cursorRow())

            buffer.moveCursorLeft(99)
            buffer.moveCursorUp(99)
            assertEquals(0, buffer.cursorColumn())
            assertEquals(0, buffer.cursorRow())
        }

        @Test
        fun setting_cursor_on_a_wide_character_continuation_normalizes_to_the_grapheme_start() {
            val buffer = buffer(width = 6)

            buffer.write("👍🏻a")
            buffer.setCursorPosition(column = 1, row = 0)

            assertEquals(0, buffer.cursorColumn())
            assertEquals(0, buffer.cursorRow())
        }
    }

    @Nested
    inner class WriteBehavior {
        @Test
        fun writing_text_overwrites_from_the_cursor_and_advances_it() {
            val buffer = buffer()

            buffer.write("abc")

            assertEquals("abc ", buffer.screenLineAt(0))
            assertEquals(3, buffer.cursorColumn())
            assertEquals(0, buffer.cursorRow())
        }

        @Test
        fun writing_at_the_end_of_a_row_wraps_onto_the_next_row() {
            val buffer = buffer(height = 3)

            buffer.setCursorPosition(column = 3, row = 0)
            buffer.write("AB")

            assertEquals("   A", buffer.screenLineAt(0))
            assertEquals("B   ", buffer.screenLineAt(1))
        }

        @Test
        fun writing_past_the_bottom_scrolls_the_oldest_visible_row_into_history() {
            val buffer = buffer()

            buffer.write("abcdefghi")

            assertEquals("efgh", buffer.screenLineAt(0))
            assertEquals("i   ", buffer.screenLineAt(1))
            assertEquals("abcd\nefgh\ni   ", buffer.historyText())
        }
    }

    @Nested
    inner class FillBehavior {
        @Test
        fun fill_replaces_only_the_current_visible_row() {
            val buffer = buffer()

            buffer.write("abcd")
            buffer.setCursorPosition(column = 0, row = 1)
            buffer.fillLine('x')

            assertEquals("abcd", buffer.screenLineAt(0))
            assertEquals("xxxx", buffer.screenLineAt(1))
        }

        @Test
        fun fill_with_null_clears_the_current_row() {
            val buffer = buffer()

            buffer.write("abcd")
            buffer.setCursorPosition(column = 0, row = 1)
            buffer.write("xy")
            buffer.setCursorPosition(column = 0, row = 1)
            buffer.fillLine(null)

            assertEquals("    ", buffer.screenLineAt(1))
        }

        @Test
        fun fill_uses_the_current_attributes_for_the_whole_row() {
            val buffer = buffer()
            val attributes = CellAttributes(
                foreground = TerminalColor.GREEN,
                background = TerminalColor.BLACK,
                styles = setOf(TextStyle.BOLD),
            )

            buffer.setCurrentAttributes(attributes)
            buffer.fillLine('x')

            assertEquals(attributes, buffer.screenAttributesAt(0, 0))
            assertEquals(attributes, buffer.screenAttributesAt(3, 0))
        }
    }

    @Nested
    inner class InsertBehavior {
        @Test
        fun inserting_text_shifts_existing_cells_to_the_right() {
            val buffer = buffer(width = 5)

            buffer.write("abc")
            buffer.setCursorPosition(column = 1, row = 0)
            buffer.insert("Z")

            assertEquals("aZbc ", buffer.screenLineAt(0))
        }

        @Test
        fun inserting_text_can_wrap_onto_following_visible_rows() {
            val buffer = buffer()

            buffer.write("abcdef")
            buffer.setCursorPosition(column = 2, row = 0)
            buffer.insert("XY")

            assertEquals("abXY", buffer.screenLineAt(0))
            assertEquals("cdef", buffer.screenLineAt(1))
        }

        @Test
        fun deleting_text_reflows_following_content_across_visual_rows() {
            val buffer = buffer(height = 3)

            buffer.write("abcdef")
            buffer.setCursorPosition(column = 1, row = 0)
            buffer.deleteCharacters()

            assertEquals("acde", buffer.screenLineAt(0))
            assertEquals("f   ", buffer.screenLineAt(1))
        }

        @Test
        fun backspace_reflows_following_content_across_visual_rows() {
            val buffer = buffer(height = 3)

            buffer.write("abcdef")
            buffer.setCursorPosition(column = 0, row = 1)
            buffer.backspace()

            assertEquals("abce", buffer.screenLineAt(0))
            assertEquals("f   ", buffer.screenLineAt(1))
        }
    }

    @Nested
    inner class ResizeBehavior {
        @Test
        fun growing_width_preserves_existing_visible_content_and_pads_on_the_right() {
            val buffer = buffer()

            buffer.write("a界")
            buffer.resize(newWidth = 6, newHeight = 2)

            assertEquals("a界   ", buffer.screenLineAt(0))
        }

        @Test
        fun shrinking_width_keeps_only_whole_graphemes_that_still_fit() {
            val buffer = buffer(width = 6)

            buffer.write("ab界")
            buffer.resize(newWidth = 3, newHeight = 2)

            assertEquals("ab ", buffer.screenLineAt(0))
        }

        @Test
        fun shrinking_height_moves_trimmed_rows_into_scrollback() {
            val buffer = buffer(height = 3)

            buffer.fillLine('a')
            buffer.setCursorPosition(0, 1)
            buffer.fillLine('b')
            buffer.setCursorPosition(0, 2)
            buffer.fillLine('c')
            buffer.resize(newWidth = 4, newHeight = 2)

            assertEquals("aaaa\nbbbb\ncccc", buffer.historyText())
        }

        @Test
        fun resize_clamps_the_cursor_into_the_new_bounds() {
            val buffer = buffer(width = 6, height = 3)

            buffer.setCursorPosition(column = 5, row = 2)
            buffer.resize(newWidth = 3, newHeight = 2)

            assertEquals(2, buffer.cursorColumn())
            assertEquals(1, buffer.cursorRow())
        }

        @Test
        fun growing_width_reflows_previously_wrapped_text_back_together() {
            val buffer = buffer(height = 3)

            buffer.write("abcdef")

            assertEquals("abcd", buffer.screenLineAt(0))
            assertEquals("ef  ", buffer.screenLineAt(1))

            buffer.resize(newWidth = 6, newHeight = 3)

            assertEquals("abcdef", buffer.screenLineAt(0))
            assertEquals("      ", buffer.screenLineAt(1))
        }

        @Test
        fun growing_width_reflows_without_splitting_wide_graphemes() {
            val buffer = buffer(height = 3)

            buffer.write("a界bc")
            buffer.resize(newWidth = 6, newHeight = 3)

            assertEquals("a界bc ", buffer.screenLineAt(0))
        }

        @Test
        fun resize_keeps_cursor_attached_to_the_same_logical_content_after_reflow() {
            val buffer = buffer(height = 3)

            buffer.write("abcdef")
            buffer.setCursorPosition(column = 2, row = 1)

            buffer.resize(newWidth = 6, newHeight = 3)

            assertEquals(0, buffer.cursorColumn())
            assertEquals(1, buffer.cursorRow())
        }
    }

    @Nested
    inner class UnicodeBehavior {
        @Test
        fun wide_graphemes_occupy_a_start_cell_and_a_continuation_cell() {
            val buffer = buffer(width = 6)

            buffer.write("界")

            assertEquals(CellKind.GraphemeStart("界", 2), buffer.screenCellAt(0, 0).kind)
            assertEquals(CellKind.Continuation, buffer.screenCellAt(1, 0).kind)
        }

        @Test
        fun cursor_movement_skips_continuation_cells_of_wide_graphemes() {
            val buffer = buffer(width = 6)

            buffer.write("界a")
            buffer.setCursorPosition(0, 0)
            buffer.moveCursorRight()

            assertEquals(2, buffer.cursorColumn())
        }

        @Test
        fun overwriting_on_a_continuation_cell_clears_the_whole_grapheme() {
            val buffer = buffer(width = 8)

            buffer.write("👍🏻a")
            buffer.setCursorPosition(1, 0)
            buffer.write("b")

            assertEquals("b a     ", buffer.screenLineAt(0))
        }
    }
}
