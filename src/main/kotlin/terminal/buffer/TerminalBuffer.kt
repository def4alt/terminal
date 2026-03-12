package terminal.buffer

class TerminalBuffer(
    val width: Int,
    val height: Int,
    val maxScrollbackLines: Int,
) {
    private val screen = MutableList(height) { blankLine() }
    private val scrollback = mutableListOf<List<Cell>>()
    private var cursorColumn = 0
    private var cursorRow = 0

    fun getCursorColumn(): Int = cursorColumn

    fun getCursorRow(): Int = cursorRow

    fun getScreenLine(row: Int): String = lineToString(screen[row])

    fun getScreenContent(): String = screen.joinToString("\n", transform = ::lineToString)

    fun getHistoryContent(): String = (scrollback + screen).joinToString("\n", transform = ::lineToString)

    private fun blankCell(): Cell = Cell()

    private fun blankLine(): MutableList<Cell> = MutableList(width) { blankCell() }

    private fun lineToString(line: List<Cell>): String = line.joinToString("") { cell ->
        cell.character?.toString() ?: " "
    }
}
