package terminal.buffer

class TerminalBuffer(
    width: Int,
    height: Int,
    val maxScrollbackLines: Int,
) {
    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
    }

    var width: Int = width
        private set

    var height: Int = height
        private set

    private val screen = MutableList(height) { blankRow() }
    private val scrollback = mutableListOf<BufferRow>()
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
        for (grapheme in segmentGraphemes(text)) {
            writeGrapheme(grapheme)
        }
    }

    fun fillLine(character: Char?) {
        screen[cursorRow] = BufferRow(ScreenLine.filled(width, fillCell(character)))
    }

    fun insertText(text: String) {
        for (grapheme in segmentGraphemes(text)) {
            normalizeCursor()
            for (cell in grapheme.toCells(currentAttributes)) {
                insertCellAt(cursorRow, cursorColumn, cell)
                advanceCursorOneCell()
            }
        }
    }

    fun deleteCharacters(count: Int = 1) {
        require(count >= 0) { "count must be non-negative" }

        repeat(count) {
            deleteOneCharacter()
        }
    }

    fun backspace(count: Int = 1) {
        require(count >= 0) { "count must be non-negative" }

        repeat(count) {
            backspaceOneCharacter()
        }
    }

    fun insertEmptyLineAtBottom() {
        scrollUpOneLine()
    }

    fun clearScreen() {
        repeat(height) { row ->
            screen[row] = blankRow()
        }
        cursorColumn = 0
        cursorRow = 0
    }

    fun clearScreenAndScrollback() {
        scrollback.clear()
        currentAttributes = CellAttributes()
        clearScreen()
    }

    fun resize(newWidth: Int, newHeight: Int) {
        require(newWidth > 0) { "newWidth must be positive" }
        require(newHeight > 0) { "newHeight must be positive" }

        if (newWidth != width) {
            resizeWidth(scrollback, newWidth)
            resizeWidth(screen, newWidth, preserveRowCount = height)
            width = newWidth
        }

        when {
            newHeight > height -> repeat(newHeight - height) {
                screen += blankRow()
            }

            newHeight < height -> repeat(height - newHeight) {
                moveLineToScrollback(screen.removeFirst())
            }
        }

        height = newHeight
        normalizeCursorPosition()
    }

    fun setCursorPosition(column: Int, row: Int) {
        cursorColumn = column
        cursorRow = row
        normalizeCursorPosition()
    }

    fun moveCursorUp(count: Int = 1) {
        cursorRow -= count
        normalizeCursorPosition()
    }

    fun moveCursorDown(count: Int = 1) {
        cursorRow += count
        normalizeCursorPosition()
    }

    fun moveCursorLeft(count: Int = 1) {
        repeat(count) {
            if (cursorColumn > 0) {
                cursorColumn -= 1
            }
            while (cursorColumn > 0 && screen[cursorRow].line.cellAt(cursorColumn).kind == CellKind.Continuation) {
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
            while (cursorColumn < width - 1 && screen[cursorRow].line.cellAt(cursorColumn).kind == CellKind.Continuation) {
                cursorColumn += 1
            }
        }
        normalizeCursorPosition()
    }

    fun getScreenLine(row: Int): String = screen[row].line.toDisplayText()

    fun getScreenCell(column: Int, row: Int): Cell = screen[row].line.cellAt(column)

    fun getScreenCharacter(column: Int, row: Int): String? = characterOf(getScreenCell(column, row))

    fun getScreenAttributes(column: Int, row: Int): CellAttributes = getScreenCell(column, row).attributes

    fun getHistoryCell(column: Int, row: Int): Cell = historyRows()[row].line.cellAt(column)

    fun getHistoryCharacter(column: Int, row: Int): String? = characterOf(getHistoryCell(column, row))

    fun getHistoryAttributes(column: Int, row: Int): CellAttributes = getHistoryCell(column, row).attributes

    fun getHistoryLine(row: Int): String = historyRows()[row].line.toDisplayText()

    fun getScreenContent(): String = screen.joinToString("\n") { it.line.toDisplayText() }

    fun getHistoryContent(): String = historyRows().joinToString("\n") { it.line.toDisplayText() }

    private fun blankCell(): Cell = Cell()

    private fun blankLine(): ScreenLine = ScreenLine.blank(width)

    private fun blankRow(): BufferRow = BufferRow(blankLine())

    private fun characterOf(cell: Cell): String? = when (val kind = cell.kind) {
        CellKind.Empty -> null
        CellKind.Continuation -> null
        is CellKind.GraphemeStart -> kind.text
    }

    private fun historyRows(): List<BufferRow> = scrollback + screen

    private fun fillCell(character: Char?): Cell {
        if (character == null) {
            return blankCell()
        }

        return Cell(kind = CellKind.GraphemeStart(character.toString(), 1), attributes = currentAttributes)
    }

    private fun deleteOneCharacter() {
        normalizeCursor()

        val line = screen[cursorRow].line
        val graphemes = line.graphemes()
        val deleteIndex = graphemes.indexOfFirst { it.column >= cursorColumn }
        if (deleteIndex == -1) {
            return
        }

        val deleted = graphemes[deleteIndex]
        val rewritten = ScreenLine.blank(width)

        for ((index, grapheme) in graphemes.withIndex()) {
            if (index == deleteIndex) {
                continue
            }

            val targetColumn = if (grapheme.column < deleted.column) {
                grapheme.column
            } else {
                grapheme.column - deleted.kind.displayWidth
            }

            if (targetColumn < 0 || targetColumn + grapheme.kind.displayWidth > width) {
                continue
            }

            rewritten.writeGrapheme(targetColumn, grapheme.kind, grapheme.attributes)
        }

        screen[cursorRow] = BufferRow(rewritten, screen[cursorRow].wrapsFromPrevious)
        normalizeCursorPosition()
    }

    private fun backspaceOneCharacter() {
        normalizeCursor()
        if (cursorColumn == 0) {
            if (cursorRow == 0) {
                return
            }

            moveCursorToLastGraphemeOfPreviousRow() ?: return
            deleteOneCharacter()
            return
        }

        moveCursorLeft()
        deleteOneCharacter()
    }

    private fun moveCursorToLastGraphemeOfPreviousRow(): Unit? {
        val previousRow = cursorRow - 1
        val previousGrapheme = screen[previousRow].line.graphemes().lastOrNull() ?: return null

        cursorRow = previousRow
        cursorColumn = previousGrapheme.column
        return Unit
    }

    private fun insertCellAt(row: Int, column: Int, cell: Cell) {
        var currentRow = row
        var currentColumn = column
        var carry = cell

        while (true) {
            for (index in currentColumn until width) {
                val displaced = screen[currentRow].line.cellAt(index)
                screen[currentRow].line.replace(index, carry)
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

    private fun writeGrapheme(grapheme: Grapheme) {
        normalizeCursor()
        if (grapheme.displayWidth == 2 && cursorColumn == width - 1) {
            advanceToNextWritePosition()
        }

        for (cell in grapheme.toCells(currentAttributes)) {
            clearGraphemeAt(cursorRow, cursorColumn)
            screen[cursorRow].line.replace(cursorColumn, cell)
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
            screen[cursorRow].wrapsFromPrevious = true
            return
        }

        scrollUpOneLine(wrappedContinuation = true)
    }

    private fun clampCursor() {
        cursorColumn = cursorColumn.coerceIn(0, width - 1)
        cursorRow = cursorRow.coerceIn(0, height - 1)
    }

    private fun normalizeCursorPosition() {
        clampCursor()
        normalizeCursor()
    }

    private fun normalizeCursor() {
        while (cursorColumn > 0 && screen[cursorRow].line.cellAt(cursorColumn).kind == CellKind.Continuation) {
            cursorColumn -= 1
        }
    }

    private fun clearGraphemeAt(row: Int, column: Int) {
        val line = screen[row].line
        when (line.cellAt(column).kind) {
            CellKind.Empty -> return
            CellKind.Continuation -> {
                var lead = column - 1
                while (lead >= 0 && line.cellAt(lead).kind == CellKind.Continuation) {
                    lead -= 1
                }
                clearFromLead(line, lead)
            }

            is CellKind.GraphemeStart -> clearFromLead(line, column)
        }
    }

    private fun clearFromLead(line: ScreenLine, lead: Int) {
        if (lead !in 0 until width) {
            return
        }

        val cell = line.cellAt(lead)
        line.replace(lead, blankCell())
        val kind = cell.kind
        if (kind is CellKind.GraphemeStart && kind.displayWidth == 2 && lead + 1 < width) {
            line.replace(lead + 1, blankCell())
        }
    }

    private fun scrollUpOneLine(wrappedContinuation: Boolean = false) {
        moveLineToScrollback(screen.removeFirst())
        screen += BufferRow(blankLine(), wrapsFromPrevious = wrappedContinuation)
        cursorRow = height - 1
    }

    private fun moveLineToScrollback(line: BufferRow) {
        scrollback += line
        trimScrollback()
    }

    private fun resizeWidth(lines: MutableList<BufferRow>, newWidth: Int, preserveRowCount: Int? = null) {
        val logicalLines = mutableListOf<LogicalLine>()

        for (row in lines) {
            if (!row.wrapsFromPrevious || logicalLines.isEmpty()) {
                logicalLines += LogicalLine()
            }
            logicalLines.last().append(row.line.styledGraphemes())
        }

        val resizedRows = mutableListOf<BufferRow>()
        for (logicalLine in logicalLines) {
            val wrappedRows = logicalLine.wrap(newWidth)
            for ((index, wrappedRow) in wrappedRows.withIndex()) {
                resizedRows += BufferRow(line = wrappedRow, wrapsFromPrevious = index > 0)
            }
        }

        lines.clear()
        val finalRows = when {
            preserveRowCount == null -> resizedRows
            resizedRows.size >= preserveRowCount -> resizedRows.take(preserveRowCount)
            else -> resizedRows + MutableList(preserveRowCount - resizedRows.size) { BufferRow(ScreenLine.blank(newWidth)) }
        }
        lines.addAll(finalRows)
    }

    private fun trimScrollback() {
        while (scrollback.size > maxScrollbackLines) {
            scrollback.removeFirst()
        }
    }
}

internal fun Grapheme.toCells(attributes: CellAttributes): List<Cell> {
    val cells = mutableListOf(Cell(kind = CellKind.GraphemeStart(text, displayWidth), attributes = attributes))
    repeat(displayWidth - 1) {
        cells += Cell(kind = CellKind.Continuation, attributes = attributes)
    }
    return cells
}
