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
}
