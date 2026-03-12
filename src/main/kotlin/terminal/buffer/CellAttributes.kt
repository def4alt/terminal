package terminal.buffer

data class CellAttributes(
    val foreground: TerminalColor = TerminalColor.DEFAULT,
    val background: TerminalColor = TerminalColor.DEFAULT,
    val styles: Set<TextStyle> = emptySet(),
)
