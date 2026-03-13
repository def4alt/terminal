package terminal.buffer

private const val ESC = "\u001B["
private const val RESET = "${ESC}0m"

internal object AnsiSnapshotRenderer {
    fun renderSnapshot(buffer: TerminalBuffer): String = buildString {
        appendLine("Screen:")
        append(renderScreen(buffer))
        append(RESET).append('\n')
        appendLine("History:")
        append(buffer.getHistoryContent())
        append(RESET).append('\n')
        appendLine("Cursor: (${buffer.getCursorColumn()}, ${buffer.getCursorRow()})")
        append("Attributes: ${formatAttributes(buffer.getCurrentAttributes())}")
    }

    private fun renderScreen(buffer: TerminalBuffer): String {
        return (0 until buffer.height).joinToString("\n") { row -> renderRow(buffer, row) }
    }

    private fun renderRow(buffer: TerminalBuffer, row: Int): String = buildString {
        var activeAttributes = CellAttributes()

        for (column in 0 until buffer.width) {
            val cell = buffer.getScreenCell(column, row)
            when (val kind = cell.kind) {
                CellKind.Continuation -> Unit
                CellKind.Empty -> {
                    if (activeAttributes != CellAttributes()) {
                        append(RESET)
                        activeAttributes = CellAttributes()
                    }
                    append(' ')
                }

                is CellKind.GraphemeStart -> {
                    if (cell.attributes != activeAttributes) {
                        append(styleSequence(cell.attributes))
                        activeAttributes = cell.attributes
                    }
                    append(kind.text)
                }
            }
        }

        if (activeAttributes != CellAttributes()) {
            append(RESET)
        }
    }

    private fun styleSequence(attributes: CellAttributes): String {
        if (attributes == CellAttributes()) {
            return RESET
        }

        val codes = mutableListOf<Int>()
        foregroundCode(attributes.foreground)?.let(codes::add)
        backgroundCode(attributes.background)?.let(codes::add)
        if (TextStyle.BOLD in attributes.styles) {
            codes += 1
        }
        if (TextStyle.ITALIC in attributes.styles) {
            codes += 3
        }
        if (TextStyle.UNDERLINE in attributes.styles) {
            codes += 4
        }
        return "$ESC${codes.joinToString(";")}m"
    }

    private fun foregroundCode(color: TerminalColor): Int? = when (color) {
        TerminalColor.DEFAULT -> null
        TerminalColor.BLACK -> 30
        TerminalColor.RED -> 31
        TerminalColor.GREEN -> 32
        TerminalColor.YELLOW -> 33
        TerminalColor.BLUE -> 34
        TerminalColor.MAGENTA -> 35
        TerminalColor.CYAN -> 36
        TerminalColor.WHITE -> 37
        TerminalColor.BRIGHT_BLACK -> 90
        TerminalColor.BRIGHT_RED -> 91
        TerminalColor.BRIGHT_GREEN -> 92
        TerminalColor.BRIGHT_YELLOW -> 93
        TerminalColor.BRIGHT_BLUE -> 94
        TerminalColor.BRIGHT_MAGENTA -> 95
        TerminalColor.BRIGHT_CYAN -> 96
        TerminalColor.BRIGHT_WHITE -> 97
    }

    private fun backgroundCode(color: TerminalColor): Int? = when (color) {
        TerminalColor.DEFAULT -> null
        TerminalColor.BLACK -> 40
        TerminalColor.RED -> 41
        TerminalColor.GREEN -> 42
        TerminalColor.YELLOW -> 43
        TerminalColor.BLUE -> 44
        TerminalColor.MAGENTA -> 45
        TerminalColor.CYAN -> 46
        TerminalColor.WHITE -> 47
        TerminalColor.BRIGHT_BLACK -> 100
        TerminalColor.BRIGHT_RED -> 101
        TerminalColor.BRIGHT_GREEN -> 102
        TerminalColor.BRIGHT_YELLOW -> 103
        TerminalColor.BRIGHT_BLUE -> 104
        TerminalColor.BRIGHT_MAGENTA -> 105
        TerminalColor.BRIGHT_CYAN -> 106
        TerminalColor.BRIGHT_WHITE -> 107
    }
}
