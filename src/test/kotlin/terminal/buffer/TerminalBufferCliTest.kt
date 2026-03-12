package terminal.buffer

import org.junit.jupiter.api.Test
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
}
