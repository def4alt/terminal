package terminal.buffer

class TerminalBuffer(
    val width: Int,
    val height: Int,
    val maxScrollbackLines: Int,
) {
    private val screen = MutableList(height) { blankLine() }
    private val scrollback = mutableListOf<List<Cell>>()
    private var currentAttributes = CellAttributes()
    private var cursorColumn = 0
    private var cursorRow = 0

    fun getCursorColumn(): Int = cursorColumn

    fun getCursorRow(): Int = cursorRow

    fun getCurrentAttributes(): CellAttributes = currentAttributes

    fun setCurrentAttributes(attributes: CellAttributes) {
        currentAttributes = attributes
    }

    fun setCursorPosition(column: Int, row: Int) {
        cursorColumn = column
        cursorRow = row
        clampCursor()
    }

    fun moveCursorUp(count: Int = 1) {
        cursorRow -= count
        clampCursor()
    }

    fun moveCursorDown(count: Int = 1) {
        cursorRow += count
        clampCursor()
    }

    fun moveCursorLeft(count: Int = 1) {
        cursorColumn -= count
        clampCursor()
    }

    fun moveCursorRight(count: Int = 1) {
        cursorColumn += count
        clampCursor()
    }

    fun getScreenLine(row: Int): String = lineToString(screen[row])

    fun getScreenContent(): String = screen.joinToString("\n", transform = ::lineToString)

    fun getHistoryContent(): String = (scrollback + screen).joinToString("\n", transform = ::lineToString)

    private fun blankCell(): Cell = Cell()

    private fun blankLine(): MutableList<Cell> = MutableList(width) { blankCell() }

    private fun lineToString(line: List<Cell>): String = line.joinToString("") { cell ->
        cell.character?.toString() ?: " "
    }

    private fun clampCursor() {
        cursorColumn = cursorColumn.coerceIn(0, width - 1)
        cursorRow = cursorRow.coerceIn(0, height - 1)
    }
}
