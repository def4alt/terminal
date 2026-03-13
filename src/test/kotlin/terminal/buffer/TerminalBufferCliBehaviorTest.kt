package terminal.buffer

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class TerminalBufferCliBehaviorTest {
    private fun runCommands(vararg commands: String): String {
        val output = StringBuilder()
        val cli = TerminalBufferCli(output = output)
        commands.forEach(cli::execute)
        return output.toString()
    }

    @Nested
    inner class FillCommand {
        @Test
        fun fill_uses_only_the_first_character_of_the_argument() {
            val rendered = runCommands("fill xyz", "screen")

            assertTrue(rendered.contains("xxxxxxxx"))
        }
    }
}
