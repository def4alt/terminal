package terminal.buffer

class TerminalBuffer(
    val width: Int,
    val height: Int,
    val maxScrollbackLines: Int,
) {
    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
    }

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

    fun writeText(text: String) {
        for (character in text) {
            screen[cursorRow][cursorColumn] = Cell(character = character, attributes = currentAttributes)
            advanceCursor()
        }
    }

    fun fillLine(character: Char?) {
        screen[cursorRow] = MutableList(width) { Cell(character = character, attributes = currentAttributes) }
    }

    fun insertText(text: String) {
        for (character in text) {
            insertCellAt(cursorRow, cursorColumn, Cell(character = character, attributes = currentAttributes))
            advanceCursor()
        }
    }

    fun insertEmptyLineAtBottom() {
        scrollUpOneLine()
    }

    fun clearScreen() {
        repeat(height) { row ->
            screen[row] = blankLine()
        }
        cursorColumn = 0
        cursorRow = 0
    }

    fun clearScreenAndScrollback() {
        scrollback.clear()
        currentAttributes = CellAttributes()
        clearScreen()
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

    fun getScreenCell(column: Int, row: Int): Cell = screen[row][column]

    fun getHistoryCell(column: Int, row: Int): Cell = (scrollback + screen)[row][column]

    fun getHistoryLine(row: Int): String = lineToString((scrollback + screen)[row])

    fun getScreenContent(): String = screen.joinToString("\n", transform = ::lineToString)

    fun getHistoryContent(): String = (scrollback + screen).joinToString("\n", transform = ::lineToString)

    private fun blankCell(): Cell = Cell()

    private fun blankLine(): MutableList<Cell> = MutableList(width) { blankCell() }

    private fun lineToString(line: List<Cell>): String = line.joinToString("") { cell ->
        cell.character?.toString() ?: " "
    }

    private fun insertCellAt(row: Int, column: Int, cell: Cell) {
        var currentRow = row
        var currentColumn = column
        var carry = cell

        while (true) {
            for (index in currentColumn until width) {
                val displaced = screen[currentRow][index]
                screen[currentRow][index] = carry
                carry = displaced
            }

            if (carry == Cell()) {
                return
            }

            currentColumn = 0
            if (currentRow < height - 1) {
                currentRow += 1
            } else {
                scrollUpOneLine()
                currentRow = height - 1
            }
        }
    }

    private fun advanceCursor() {
        if (cursorColumn < width - 1) {
            cursorColumn += 1
            return
        }

        cursorColumn = 0
        if (cursorRow < height - 1) {
            cursorRow += 1
            return
        }

        scrollUpOneLine()
    }

    private fun clampCursor() {
        cursorColumn = cursorColumn.coerceIn(0, width - 1)
        cursorRow = cursorRow.coerceIn(0, height - 1)
    }

    private fun scrollUpOneLine() {
        scrollback += screen.removeFirst().toList()
        if (scrollback.size > maxScrollbackLines) {
            scrollback.removeFirst()
        }
        screen += blankLine()
        cursorRow = height - 1
    }
}
