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
        for (grapheme in text.toGraphemes()) {
            writeGrapheme(grapheme)
        }
    }

    fun fillLine(character: Char?) {
        screen[cursorRow] = MutableList(width) { fillCell(character) }
    }

    fun insertText(text: String) {
        for (grapheme in text.toGraphemes()) {
            normalizeCursor()
            val cells = grapheme.toCells(currentAttributes)
            for (cell in cells) {
                insertCellAt(cursorRow, cursorColumn, cell)
                advanceCursorOneCell()
            }
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
        normalizeCursor()
    }

    fun moveCursorUp(count: Int = 1) {
        cursorRow -= count
        clampCursor()
        normalizeCursor()
    }

    fun moveCursorDown(count: Int = 1) {
        cursorRow += count
        clampCursor()
        normalizeCursor()
    }

    fun moveCursorLeft(count: Int = 1) {
        repeat(count) {
            if (cursorColumn > 0) {
                cursorColumn -= 1
            }
            while (cursorColumn > 0 && screen[cursorRow][cursorColumn].kind == CellKind.Continuation) {
                cursorColumn -= 1
            }
        }
        clampCursor()
    }

    fun moveCursorRight(count: Int = 1) {
        repeat(count) {
            if (cursorColumn < width - 1) {
                cursorColumn += 1
            }
            while (cursorColumn < width - 1 && screen[cursorRow][cursorColumn].kind == CellKind.Continuation) {
                cursorColumn += 1
            }
        }
        clampCursor()
        normalizeCursor()
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
        when (val kind = cell.kind) {
            CellKind.Empty -> " "
            CellKind.Continuation -> ""
            is CellKind.GraphemeStart -> kind.text
        }
    }

    private fun fillCell(character: Char?): Cell {
        if (character == null) {
            return blankCell()
        }

        return Cell(kind = CellKind.GraphemeStart(character.toString(), 1), attributes = currentAttributes)
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

    private fun writeGrapheme(grapheme: String) {
        normalizeCursor()
        val displayWidth = measureDisplayWidth(grapheme)
        if (displayWidth == 2 && cursorColumn == width - 1) {
            advanceToNextWritePosition()
        }

        for (cell in grapheme.toCells(currentAttributes)) {
            clearGraphemeAt(cursorRow, cursorColumn)
            screen[cursorRow][cursorColumn] = cell
            advanceCursorOneCell()
        }
    }

    private fun advanceCursorOneCell() {
        if (cursorColumn < width - 1) {
            cursorColumn += 1
            return
        }

        advanceToNextWritePosition()
    }

    private fun advanceToNextWritePosition() {
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

    private fun normalizeCursor() {
        while (cursorColumn > 0 && screen[cursorRow][cursorColumn].kind == CellKind.Continuation) {
            cursorColumn -= 1
        }
    }

    private fun clearGraphemeAt(row: Int, column: Int) {
        val line = screen[row]
        when (line[column].kind) {
            CellKind.Empty -> return
            CellKind.Continuation -> {
                var lead = column - 1
                while (lead >= 0 && line[lead].kind == CellKind.Continuation) {
                    lead -= 1
                }
                clearFromLead(line, lead)
            }

            is CellKind.GraphemeStart -> clearFromLead(line, column)
        }
    }

    private fun clearFromLead(line: MutableList<Cell>, lead: Int) {
        if (lead !in line.indices) {
            return
        }

        val cell = line[lead]
        line[lead] = blankCell()
        val kind = cell.kind
        if (kind is CellKind.GraphemeStart && kind.displayWidth == 2 && lead + 1 in line.indices) {
            line[lead + 1] = blankCell()
        }
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

private fun String.toGraphemes(): List<String> {
    val graphemes = mutableListOf<String>()
    var index = 0

    while (index < length) {
        val codePoint = codePointAt(index)
        graphemes += String(Character.toChars(codePoint))
        index += Character.charCount(codePoint)
    }

    return graphemes
}

private fun String.toCells(attributes: CellAttributes): List<Cell> {
    val width = measureDisplayWidth(this)
    val cells = mutableListOf(Cell(kind = CellKind.GraphemeStart(this, width), attributes = attributes))
    repeat(width - 1) {
        cells += Cell(kind = CellKind.Continuation, attributes = attributes)
    }
    return cells
}
