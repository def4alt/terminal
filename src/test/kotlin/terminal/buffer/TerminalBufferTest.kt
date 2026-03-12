package terminal.buffer

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TerminalBufferTest {
    @Test
    fun cell_defaults_to_blank_character_and_default_attributes() {
        val cell = Cell()

        assertEquals(null, cell.character)
        assertEquals(TerminalColor.DEFAULT, cell.attributes.foreground)
        assertEquals(TerminalColor.DEFAULT, cell.attributes.background)
        assertEquals(emptySet(), cell.attributes.styles)
    }
}
