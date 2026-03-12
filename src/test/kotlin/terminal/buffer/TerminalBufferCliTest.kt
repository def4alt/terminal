package terminal.buffer

import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.StringReader
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalBufferCliTest {
    @Test
    fun render_help_includes_core_commands() {
        val help = renderHelp()

        assertTrue(help.contains("write <text>"))
        assertTrue(help.contains("insert <text>"))
        assertTrue(help.contains("show"))
        assertTrue(help.contains("quit"))
    }

    @Test
    fun render_snapshot_includes_screen_history_cursor_and_attributes() {
        val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

        val snapshot = renderSnapshot(buffer)

        assertTrue(snapshot.contains("Screen:"))
        assertTrue(snapshot.contains("History:"))
        assertTrue(snapshot.contains("Cursor: (0, 0)"))
        assertTrue(snapshot.contains("Attributes:"))
    }

    @Test
    fun render_snapshot_formats_empty_buffer_readably() {
        val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

        val snapshot = renderSnapshot(buffer)

        assertTrue(snapshot.contains("    \n    "))
    }

    @Test
    fun execute_help_writes_help_text_and_continues() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)

        val shouldContinue = cli.execute("help")

        assertTrue(shouldContinue)
        assertTrue(output.toString().contains("Available commands:"))
    }

    @Test
    fun execute_show_writes_full_snapshot_and_continues() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)

        val shouldContinue = cli.execute("show")

        assertTrue(shouldContinue)
        assertTrue(output.toString().contains("Screen:"))
        assertTrue(output.toString().contains("History:"))
    }

    @Test
    fun execute_quit_returns_false_to_end_session() {
        val cli = TerminalBufferCli(output = StringBuilder())

        val shouldContinue = cli.execute("quit")

        assertFalse(shouldContinue)
    }

    @Test
    fun execute_cursor_prints_current_position() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)

        cli.execute("cursor")

        assertTrue(output.toString().contains("Cursor: (0, 0)"))
    }

    @Test
    fun execute_set_cursor_moves_cursor() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)

        cli.execute("set-cursor 2 1")
        cli.execute("cursor")

        assertTrue(output.toString().contains("Cursor: (2, 1)"))
    }

    @Test
    fun execute_move_right_updates_cursor() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)

        cli.execute("move right 3")
        cli.execute("cursor")

        assertTrue(output.toString().contains("Cursor: (3, 0)"))
    }

    @Test
    fun execute_screen_prints_visible_content_only() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)

        cli.execute("screen")

        assertTrue(output.toString().contains("Screen:"))
    }

    @Test
    fun execute_history_prints_scrollback_plus_screen_content() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)

        cli.execute("history")

        assertTrue(output.toString().contains("History:"))
    }

    @Test
    fun execute_attrs_prints_current_attributes() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)

        cli.execute("attrs")

        assertTrue(output.toString().contains("fg=default"))
    }

    @Test
    fun execute_write_updates_screen_content() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)

        cli.execute("write hello")
        cli.execute("screen")

        assertTrue(output.toString().contains("hello"))
    }

    @Test
    fun execute_insert_updates_screen_content() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)

        cli.execute("write abc")
        cli.execute("set-cursor 1 0")
        cli.execute("insert Z")
        cli.execute("screen")

        assertTrue(output.toString().contains("aZbc"))
    }

    @Test
    fun execute_fill_with_character_updates_current_row() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)

        cli.execute("fill =")
        cli.execute("screen")

        assertTrue(output.toString().contains("========"))
    }

    @Test
    fun execute_fill_empty_clears_current_row() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)

        cli.execute("write hello")
        cli.execute("set-cursor 0 0")
        cli.execute("fill empty")
        cli.execute("screen")

        assertTrue(output.toString().contains("        "))
    }

    @Test
    fun execute_append_line_scrolls_screen() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)

        cli.execute("write one")
        cli.execute("append-line")
        cli.execute("history")

        assertTrue(output.toString().contains("one"))
    }

    @Test
    fun execute_clear_screen_resets_visible_rows_only() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)

        cli.execute("write hello")
        cli.execute("clear-screen")
        cli.execute("screen")

        assertTrue(output.toString().contains("        \n        \n        \n        "))
    }

    @Test
    fun execute_clear_all_resets_screen_and_history() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)

        cli.execute("write hello")
        cli.execute("clear-all")
        cli.execute("history")

        assertTrue(output.toString().contains("        \n        \n        \n        "))
    }

    @Test
    fun execute_set_attrs_updates_current_attributes() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)

        cli.execute("set-attrs green default bold underline")
        cli.execute("attrs")

        assertTrue(output.toString().contains("fg=green"))
        assertTrue(output.toString().contains("bg=default"))
        assertTrue(output.toString().contains("bold"))
        assertTrue(output.toString().contains("underline"))
    }

    @Test
    fun execute_set_attrs_accepts_default_colors_and_no_styles() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)

        cli.execute("set-attrs default default")
        cli.execute("attrs")

        assertTrue(output.toString().contains("fg=default bg=default styles=none"))
    }

    @Test
    fun execute_set_attrs_rejects_unknown_colors_or_styles() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)

        cli.execute("set-attrs nope default blink")

        assertTrue(output.toString().contains("Invalid attributes"))
    }

    @Test
    fun execute_unknown_command_writes_helpful_error() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)

        cli.execute("wat")

        assertTrue(output.toString().contains("Unknown command: wat"))
    }

    @Test
    fun execute_missing_arguments_writes_helpful_error() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)

        cli.execute("set-cursor")

        assertTrue(output.toString().contains("Invalid command usage"))
    }

    @Test
    fun execute_reset_reinitializes_buffer_with_original_dimensions() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)

        cli.execute("write hello")
        cli.execute("reset")
        cli.execute("screen")

        assertTrue(output.toString().contains("        \n        \n        \n        "))
    }

    @Test
    fun run_prints_help_on_startup() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(
            input = BufferedReader(StringReader("quit\n")),
            output = output,
        )

        cli.run()

        assertTrue(output.toString().contains("Available commands:"))
    }

    @Test
    fun run_processes_multiple_commands_until_quit() {
        val output = StringBuilder()
        val cli = TerminalBufferCli(
            input = BufferedReader(StringReader("write hello\nscreen\nquit\n")),
            output = output,
        )

        cli.run()

        assertTrue(output.toString().contains("hello"))
        assertTrue(output.toString().contains("buffer> "))
    }
}
