package terminal.buffer

import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.StringReader
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalBufferCliTest {
    private fun cli(output: StringBuilder = StringBuilder()): TerminalBufferCli {
        return TerminalBufferCli(output = output)
    }

    private fun runCommands(output: StringBuilder, vararg commands: String): String {
        val cli = cli(output)
        commands.forEach(cli::execute)
        return output.toString()
    }

    @Test
    fun render_help_includes_core_commands() {
        val help = renderHelp()

        assertTrue(help.contains("write <text>"))
        assertTrue(help.contains("insert <text>"))
        assertTrue(help.contains("delete <count>"))
        assertTrue(help.contains("backspace"))
        assertTrue(help.contains("resize <width> <height>"))
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
    fun render_snapshot_displays_emoji_clusters_without_fake_internal_spacing() {
        val buffer = TerminalBuffer(width = 8, height = 2, maxScrollbackLines = 5)

        buffer.writeText("a👍🏻b")

        val snapshot = renderSnapshot(buffer)

        assertTrue(snapshot.contains("a👍🏻b"))
        assertFalse(snapshot.contains("a👍🏻 b"))
    }

    @Test
    fun render_snapshot_uses_ansi_sequences_for_styled_screen_cells() {
        val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

        buffer.setCurrentAttributes(
            CellAttributes(
                foreground = TerminalColor.RED,
                background = TerminalColor.BLUE,
                styles = setOf(TextStyle.BOLD, TextStyle.UNDERLINE),
            ),
        )
        buffer.writeText("A")

        val snapshot = renderSnapshot(buffer)

        assertTrue(snapshot.contains("\u001B[31;44;1;4mA"))
    }

    @Test
    fun render_snapshot_resets_before_history_and_cursor_sections() {
        val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

        buffer.setCurrentAttributes(CellAttributes(foreground = TerminalColor.GREEN, styles = setOf(TextStyle.BOLD)))
        buffer.writeText("A")

        val snapshot = renderSnapshot(buffer)

        assertTrue(snapshot.contains("\u001B[0m\nHistory:"))
        assertTrue(snapshot.contains("\u001B[0m\nCursor:"))
    }

    @Test
    fun render_snapshot_uses_one_sequence_for_adjacent_cells_with_same_attributes() {
        val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

        buffer.setCurrentAttributes(CellAttributes(foreground = TerminalColor.RED, styles = setOf(TextStyle.BOLD)))
        buffer.writeText("AB")

        val snapshot = renderSnapshot(buffer)
        val styledSegment = snapshot.substringAfter("Screen:\n").substringBefore("\nHistory:")

        assertEquals(1, "\\u001B\\[31;1m".toRegex().findAll(styledSegment).count())
    }

    @Test
    fun render_snapshot_renders_wide_graphemes_once_without_styling_continuations_separately() {
        val buffer = TerminalBuffer(width = 6, height = 2, maxScrollbackLines = 5)

        buffer.setCurrentAttributes(CellAttributes(foreground = TerminalColor.BRIGHT_CYAN, background = TerminalColor.BLUE))
        buffer.writeText("界")

        val snapshot = renderSnapshot(buffer)

        assertTrue(snapshot.contains("\u001B[96;44m界"))
        assertFalse(snapshot.contains("\u001B[96;44m界\u001B[96;44m"))
    }

    @Test
    fun render_snapshot_for_empty_buffer_does_not_emit_style_sequences_inside_screen_rows() {
        val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

        val snapshot = renderSnapshot(buffer)
        val styledScreen = snapshot.substringAfter("Screen:\n").substringBefore("\u001B[0m\nHistory:")

        assertFalse(styledScreen.contains("\u001B[3"))
        assertFalse(styledScreen.contains("\u001B[4"))
        assertFalse(styledScreen.contains("\u001B[9"))
    }

    @Test
    fun render_snapshot_resets_before_default_cells_after_styled_cells() {
        val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

        buffer.setCurrentAttributes(CellAttributes(foreground = TerminalColor.RED, styles = setOf(TextStyle.BOLD)))
        buffer.writeText("A")

        val snapshot = renderSnapshot(buffer)

        assertTrue(snapshot.contains("\u001B[31;1mA\u001B[0m "))
    }

    @Test
    fun execute_help_writes_help_text_and_continues() {
        val output = StringBuilder()
        val cli = cli(output)

        val shouldContinue = cli.execute("help")

        assertTrue(shouldContinue)
        assertTrue(output.toString().contains("Available commands:"))
    }

    @Test
    fun execute_show_writes_full_snapshot_and_continues() {
        val output = StringBuilder()
        val cli = cli(output)

        val shouldContinue = cli.execute("show")

        assertTrue(shouldContinue)
        assertTrue(output.toString().contains("Screen:"))
        assertTrue(output.toString().contains("History:"))
    }

    @Test
    fun execute_quit_returns_false_to_end_session() {
        val cli = cli()

        val shouldContinue = cli.execute("quit")

        assertFalse(shouldContinue)
    }

    @Test
    fun execute_cursor_prints_current_position() {
        val output = StringBuilder()
        val cli = cli(output)

        cli.execute("cursor")

        assertTrue(output.toString().contains("Cursor: (0, 0)"))
    }

    @Test
    fun execute_set_cursor_moves_cursor() {
        val output = StringBuilder()
        val rendered = runCommands(output, "set-cursor 2 1", "cursor")

        assertTrue(rendered.contains("Cursor: (2, 1)"))
    }

    @Test
    fun execute_move_right_updates_cursor() {
        val output = StringBuilder()
        val rendered = runCommands(output, "move right 3", "cursor")

        assertTrue(rendered.contains("Cursor: (3, 0)"))
    }

    @Test
    fun execute_screen_prints_visible_content_only() {
        val output = StringBuilder()
        val cli = cli(output)

        cli.execute("screen")

        assertTrue(output.toString().contains("Screen:"))
    }

    @Test
    fun execute_history_prints_scrollback_plus_screen_content() {
        val output = StringBuilder()
        val cli = cli(output)

        cli.execute("history")

        assertTrue(output.toString().contains("History:"))
    }

    @Test
    fun execute_attrs_prints_current_attributes() {
        val output = StringBuilder()
        val cli = cli(output)

        cli.execute("attrs")

        assertTrue(output.toString().contains("fg=default"))
    }

    @Test
    fun execute_write_updates_screen_content() {
        val output = StringBuilder()
        val rendered = runCommands(output, "write hello", "screen")

        assertTrue(rendered.contains("hello"))
    }

    @Test
    fun execute_insert_updates_screen_content() {
        val output = StringBuilder()
        val rendered = runCommands(output, "write abc", "set-cursor 1 0", "insert Z", "screen")

        assertTrue(rendered.contains("aZbc"))
    }

    @Test
    fun execute_delete_removes_text_at_cursor() {
        val output = StringBuilder()
        val rendered = runCommands(output, "write abcde", "set-cursor 1 0", "delete 1", "screen")

        assertTrue(rendered.contains("acde "))
    }

    @Test
    fun execute_delete_rejects_invalid_arguments() {
        val output = StringBuilder()
        val cli = cli(output)

        cli.execute("delete nope")

        assertTrue(output.toString().contains("Invalid command usage"))
    }

    @Test
    fun execute_backspace_removes_text_before_cursor() {
        val output = StringBuilder()
        val rendered = runCommands(output, "write abcde", "backspace", "screen")

        assertTrue(rendered.contains("abcd "))
    }

    @Test
    fun execute_resize_updates_screen_dimensions() {
        val output = StringBuilder()
        val rendered = runCommands(output, "resize 4 2", "screen")

        assertTrue(rendered.contains("    \n    \n"))
    }

    @Test
    fun execute_resize_preserves_visible_content_with_current_policy() {
        val output = StringBuilder()
        val rendered = runCommands(output, "write a界", "resize 4 4", "screen")

        assertTrue(rendered.contains("a界 \n    \n    \n    \n"))
    }

    @Test
    fun execute_resize_rejects_invalid_arguments() {
        val output = StringBuilder()
        val cli = cli(output)

        cli.execute("resize nope 2")

        assertTrue(output.toString().contains("Invalid command usage"))
    }

    @Test
    fun execute_fill_with_character_updates_current_row() {
        val output = StringBuilder()
        val rendered = runCommands(output, "fill =", "screen")

        assertTrue(rendered.contains("========"))
    }

    @Test
    fun execute_fill_empty_clears_current_row() {
        val output = StringBuilder()
        val rendered = runCommands(output, "write hello", "set-cursor 0 0", "fill empty", "screen")

        assertTrue(rendered.contains("        "))
    }

    @Test
    fun execute_append_line_scrolls_screen() {
        val output = StringBuilder()
        val rendered = runCommands(output, "write one", "append-line", "history")

        assertTrue(rendered.contains("one"))
    }

    @Test
    fun execute_clear_screen_resets_visible_rows_only() {
        val output = StringBuilder()
        val rendered = runCommands(output, "write hello", "clear-screen", "screen")

        assertTrue(rendered.contains("        \n        \n        \n        "))
    }

    @Test
    fun execute_clear_all_resets_screen_and_history() {
        val output = StringBuilder()
        val rendered = runCommands(output, "write hello", "clear-all", "history")

        assertTrue(rendered.contains("        \n        \n        \n        "))
    }

    @Test
    fun execute_set_attrs_updates_current_attributes() {
        val output = StringBuilder()
        val rendered = runCommands(output, "set-attrs green default bold underline", "attrs")

        assertTrue(rendered.contains("fg=green"))
        assertTrue(rendered.contains("bg=default"))
        assertTrue(rendered.contains("bold"))
        assertTrue(rendered.contains("underline"))
    }

    @Test
    fun execute_set_attrs_accepts_default_colors_and_no_styles() {
        val output = StringBuilder()
        val rendered = runCommands(output, "set-attrs default default", "attrs")

        assertTrue(rendered.contains("fg=default bg=default styles=none"))
    }

    @Test
    fun execute_set_attrs_rejects_unknown_colors_or_styles() {
        val output = StringBuilder()
        val cli = cli(output)

        cli.execute("set-attrs nope default blink")

        assertTrue(output.toString().contains("Invalid attributes"))
    }

    @Test
    fun execute_unknown_command_writes_helpful_error() {
        val output = StringBuilder()
        val cli = cli(output)

        cli.execute("wat")

        assertTrue(output.toString().contains("Unknown command: wat"))
    }

    @Test
    fun execute_missing_arguments_writes_helpful_error() {
        val output = StringBuilder()
        val cli = cli(output)

        cli.execute("set-cursor")

        assertTrue(output.toString().contains("Invalid command usage"))
    }

    @Test
    fun execute_move_without_count_writes_helpful_error() {
        val output = StringBuilder()
        val cli = cli(output)

        val shouldContinue = cli.execute("move down")

        assertTrue(shouldContinue)
        assertTrue(output.toString().contains("Invalid command usage"))
    }

    @Test
    fun execute_move_with_invalid_count_writes_helpful_error() {
        val output = StringBuilder()
        val cli = cli(output)

        val shouldContinue = cli.execute("move down nope")

        assertTrue(shouldContinue)
        assertTrue(output.toString().contains("Invalid command usage"))
    }

    @Test
    fun execute_set_cursor_without_both_coordinates_writes_helpful_error() {
        val output = StringBuilder()
        val cli = cli(output)

        val shouldContinue = cli.execute("set-cursor 2")

        assertTrue(shouldContinue)
        assertTrue(output.toString().contains("Invalid command usage"))
    }

    @Test
    fun execute_set_cursor_with_invalid_coordinates_writes_helpful_error() {
        val output = StringBuilder()
        val cli = cli(output)

        val shouldContinue = cli.execute("set-cursor nope 1")

        assertTrue(shouldContinue)
        assertTrue(output.toString().contains("Invalid command usage"))
    }

    @Test
    fun execute_reset_reinitializes_buffer_with_original_dimensions() {
        val output = StringBuilder()
        val rendered = runCommands(output, "write hello", "reset", "screen")

        assertTrue(rendered.contains("        \n        \n        \n        "))
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

    @Test
    fun cli_write_and_show_preserve_emoji_modifier_sequence_visually() {
        val output = StringBuilder()
        val rendered = runCommands(output, "write 👍🏻a", "show")

        assertTrue(rendered.contains("👍🏻a"))
    }
}
