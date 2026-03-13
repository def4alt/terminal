package terminal.buffer

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalBufferCliBehaviorTest {
    private fun runCommands(vararg commands: String): String {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)
        commands.forEach(cli::execute)
        return output.toString()
    }

    private fun execute(command: String): Pair<Boolean, String> {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)
        return cli.execute(command) to output.toString()
    }

    @Nested
    inner class SessionCommands {
        @Test
        fun help_prints_the_available_command_list() {
            val rendered = runCommands("help")

            assertTrue(rendered.contains("Available commands:"))
            assertTrue(rendered.contains("resize <width> <height>"))
        }

        @Test
        fun quit_and_exit_end_the_session() {
            assertFalse(execute("quit").first)
            assertFalse(execute("exit").first)
        }

        @Test
        fun empty_input_is_ignored_without_producing_output() {
            val result = execute("   ")

            assertTrue(result.first)
            assertTrue(result.second.isEmpty())
        }
    }

    @Nested
    inner class CursorCommands {
        @Test
        fun set_cursor_and_move_update_the_reported_cursor_position() {
            val rendered = runCommands("set-cursor 2 1", "move left 1", "cursor")

            assertTrue(rendered.contains("Cursor: (1, 1)"))
        }

        @Test
        fun invalid_cursor_usage_returns_a_helpful_error() {
            val rendered = runCommands("set-cursor nope 1")

            assertTrue(rendered.contains("Invalid command usage"))
        }
    }

    @Nested
    inner class WriteAndInsertCommands {
        @Test
        fun write_updates_the_visible_screen() {
            val rendered = runCommands("write hello", "screen")

            assertTrue(rendered.contains("hello"))
        }

        @Test
        fun insert_shifts_existing_content_from_the_cursor() {
            val rendered = runCommands("write abc", "set-cursor 1 0", "insert Z", "screen")

            assertTrue(rendered.contains("aZbc"))
        }

        @Test
        fun show_renders_screen_history_cursor_and_attributes_together() {
            val rendered = runCommands("write hi", "show")

            assertTrue(rendered.contains("Screen:"))
            assertTrue(rendered.contains("History:"))
            assertTrue(rendered.contains("Cursor:"))
            assertTrue(rendered.contains("Attributes:"))
        }
    }

    @Nested
    inner class FillCommand {
        @Test
        fun fill_uses_only_the_first_character_of_the_argument() {
            val rendered = runCommands("fill xyz", "screen")

            assertTrue(rendered.contains("xxxxxxxx"))
        }

        @Test
        fun fill_empty_clears_the_current_row() {
            val rendered = runCommands("write hello", "set-cursor 0 0", "fill empty", "screen")

            assertTrue(rendered.contains("        "))
        }
    }

    @Nested
    inner class AttributeCommands {
        @Test
        fun set_attrs_changes_the_attributes_reported_by_attrs() {
            val rendered = runCommands("set-attrs green default bold underline", "attrs")

            assertTrue(rendered.contains("fg=green"))
            assertTrue(rendered.contains("bg=default"))
            assertTrue(rendered.contains("bold"))
            assertTrue(rendered.contains("underline"))
        }

        @Test
        fun invalid_attributes_produce_a_helpful_error() {
            val rendered = runCommands("set-attrs nope default blink")

            assertTrue(rendered.contains("Invalid attributes"))
        }
    }

    @Nested
    inner class HistoryAndScreenCommands {
        @Test
        fun history_shows_scrollback_followed_by_the_visible_screen() {
            val rendered = runCommands("write abcdefghi", "history")

            assertTrue(rendered.contains("abcd"))
            assertTrue(rendered.contains("efgh"))
            assertTrue(rendered.contains("i   "))
        }

        @Test
        fun clear_screen_blanks_only_the_visible_rows() {
            val rendered = runCommands("write abcdefghijklmnopqrstuvwxyz123456789", "clear-screen", "history")

            assertTrue(rendered.contains("abcdefgh"))
            assertTrue(rendered.contains("        "))
        }

        @Test
        fun clear_all_blanks_both_screen_and_history() {
            val rendered = runCommands("write hello", "clear-all", "history")

            assertTrue(rendered.contains("        \n        \n        \n        "))
        }

        @Test
        fun append_line_pushes_the_top_visible_line_into_history() {
            val rendered = runCommands("write one", "append-line", "history")

            assertTrue(rendered.contains("one"))
        }
    }

    @Nested
    inner class ResizeAndResetCommands {
        @Test
        fun resize_changes_the_visible_dimensions_while_preserving_content_under_the_current_policy() {
            val rendered = runCommands("write a界", "resize 4 4", "screen")

            assertTrue(rendered.contains("a界 \n    \n    \n    \n"))
        }

        @Test
        fun invalid_resize_arguments_produce_a_helpful_error() {
            val rendered = runCommands("resize nope 2")

            assertTrue(rendered.contains("Invalid command usage"))
        }

        @Test
        fun reset_restores_the_original_blank_buffer_dimensions() {
            val rendered = runCommands("write hello", "reset", "screen")

            assertTrue(rendered.contains("        \n        \n        \n        "))
        }
    }

    @Nested
    inner class ErrorHandling {
        @Test
        fun unknown_commands_are_reported_back_to_the_user() {
            val rendered = runCommands("wat")

            assertTrue(rendered.contains("Unknown command: wat"))
        }

        @Test
        fun malformed_move_commands_return_invalid_usage() {
            val rendered = runCommands("move down", "move down nope")

            assertTrue(rendered.contains("Invalid command usage"))
        }
    }
}
