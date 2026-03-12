package terminal.buffer

import org.junit.jupiter.api.Test
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
}
