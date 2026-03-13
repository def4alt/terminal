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
    private val logicalScreenLines = mutableListOf<LogicalLine>()
    private val logicalScrollbackLines = mutableListOf<LogicalLine>()
    private var currentAttributes = CellAttributes()
    private var cursorColumn = 0
    private var cursorRow = 0

    init {
        syncLogicalStateFromRows()
    }

    fun cursorColumn(): Int = cursorColumn

    fun cursorRow(): Int = cursorRow

    fun currentAttributes(): CellAttributes = currentAttributes

    fun setCurrentAttributes(attributes: CellAttributes) {
        currentAttributes = attributes
    }

    fun write(text: String) {
        for (grapheme in segmentGraphemes(text)) {
            writeGrapheme(grapheme)
        }
        syncLogicalScreenFromScreenRows()
    }

    fun fillLine(character: Char?) {
        screen[cursorRow] = BufferRow(ScreenLine.filled(width, fillCell(character)))
        syncLogicalScreenFromScreenRows()
    }

    fun insert(text: String) {
        for (grapheme in segmentGraphemes(text)) {
            normalizeCursor()
            for (cell in grapheme.toCells(currentAttributes)) {
                insertCellAt(cursorRow, cursorColumn, cell)
                advanceCursorOneCell()
            }
        }
        syncLogicalScreenFromScreenRows()
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
        logicalScreenLines.clear()
        logicalScreenLines.addAll(MutableList(height) { blankLogicalLine() })
    }

    fun clearScreenAndScrollback() {
        logicalScrollbackLines.clear()
        currentAttributes = CellAttributes()
        clearScreen()
    }

    fun resize(newWidth: Int, newHeight: Int) {
        require(newWidth > 0) { "newWidth must be positive" }
        require(newHeight > 0) { "newHeight must be positive" }

        val logicalCursor = currentLogicalCursor()

        if (newWidth != width) {
            width = newWidth
            rebuildScreenFromLogicalLines(logicalScreenLines)
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
        syncLogicalStateFromRows()
        restoreLogicalCursor(logicalCursor)
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

    fun screenLineAt(row: Int): String = screenProjection().visibleRows[row].screenLine.toDisplayText()

    fun screenCellAt(column: Int, row: Int): Cell = screenProjection().visibleRows[row].screenLine.cellAt(column)

    fun screenCharacterAt(column: Int, row: Int): String? = characterOf(screenCellAt(column, row))

    fun screenAttributesAt(column: Int, row: Int): CellAttributes = screenCellAt(column, row).attributes

    fun historyCellAt(column: Int, row: Int): Cell = historyProjectionRows()[row].screenLine.cellAt(column)

    fun historyCharacterAt(column: Int, row: Int): String? = characterOf(historyCellAt(column, row))

    fun historyAttributesAt(column: Int, row: Int): CellAttributes = historyCellAt(column, row).attributes

    fun historyLineAt(row: Int): String = historyProjectionRows()[row].screenLine.toDisplayText()

    fun screenText(): String = screenProjection().visibleRows.joinToString("\n") { it.screenLine.toDisplayText() }

    fun historyText(): String = historyProjectionRows().joinToString("\n") { it.screenLine.toDisplayText() }

    private fun blankCell(): Cell = Cell()

    private fun blankLine(): ScreenLine = ScreenLine.blank(width)

    private fun blankRow(): BufferRow = BufferRow(blankLine())

    private fun blankLogicalLine(): LogicalLine = LogicalLine().apply { append(List(width) { StyledGrapheme.blank() }) }

    private fun characterOf(cell: Cell): String? = when (val kind = cell.kind) {
        CellKind.Empty -> null
        CellKind.Continuation -> null
        is CellKind.GraphemeStart -> kind.text
    }

    private fun historyRows(): List<BufferRow> = scrollbackRows() + screen

    private fun fillCell(character: Char?): Cell {
        if (character == null) {
            return blankCell()
        }

        return Cell(kind = CellKind.GraphemeStart(character.toString(), 1), attributes = currentAttributes)
    }

    private fun deleteOneCharacter() {
        normalizeCursor()

        val cursor = currentLogicalCursor()
        val logicalLines = editLogicalScreenLines()
        val line = logicalLines.getOrNull(cursor.lineIndex) ?: return
        val deleteIndex = graphemeIndexAtOrAfterDisplayColumn(line, cursor.displayColumn) ?: return

        line.delete(deleteIndex, 1)
        rebuildScreenFromLogicalLines(logicalLines)
        restoreLogicalCursor(cursor)
        normalizeCursorPosition()
    }

    private fun backspaceOneCharacter() {
        normalizeCursor()

        val cursor = currentLogicalCursor()
        val logicalLines = editLogicalScreenLines()
        val line = logicalLines.getOrNull(cursor.lineIndex) ?: return
        val deleteIndex = graphemeIndexBeforeDisplayColumn(line, cursor.displayColumn) ?: return

        line.delete(deleteIndex, 1)
        rebuildScreenFromLogicalLines(logicalLines)
        restoreLogicalCursor(cursor.withDisplayColumn(displayColumnForGraphemeIndex(line, deleteIndex)))
        normalizeCursorPosition()
    }

    private fun graphemeIndexAtOrAfterDisplayColumn(line: LogicalLine, displayColumn: Int): Int? {
        var column = 0

        for ((index, grapheme) in line.graphemes().withIndex()) {
            if (column >= displayColumn) {
                return index
            }

            column += grapheme.displayWidth
            if (column > displayColumn) {
                return index
            }
        }

        return null
    }

    private fun graphemeIndexBeforeDisplayColumn(line: LogicalLine, displayColumn: Int): Int? {
        if (displayColumn == 0) {
            return null
        }

        var column = 0
        for ((index, grapheme) in line.graphemes().withIndex()) {
            val nextColumn = column + grapheme.displayWidth
            if (nextColumn >= displayColumn) {
                return index
            }
            column = nextColumn
        }

        return line.graphemeCount().takeIf { it > 0 }?.minus(1)
    }

    private fun displayColumnForGraphemeIndex(line: LogicalLine, graphemeIndex: Int): Int {
        return line.graphemes().take(graphemeIndex).sumOf { it.displayWidth }
    }

    private fun rebuildScreenFromLogicalLines(logicalLines: List<LogicalLine>) {
        val rows = ViewportProjector.projectAllRows(
            logicalLines = logicalLines,
            width = width,
        ).map { visualRow ->
            BufferRow(
                line = visualRow.screenLine,
                wrapsFromPrevious = visualRow.startDisplayColumn > 0,
            )
        }

        val finalRows = when {
            rows.size >= height -> rows.take(height)
            else -> rows + MutableList(height - rows.size) { blankRow() }
        }

        screen.clear()
        screen.addAll(finalRows)
        syncLogicalScreenFromScreenRows()
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
        syncLogicalStateFromRows()
    }

    private fun moveLineToScrollback(line: BufferRow) {
        logicalScrollbackLines.addAll(rowsToLogicalLines(listOf(line), keepTrailingBlanks = true))
        trimLogicalScrollback()
    }

    private fun currentLogicalCursor(): LogicalCursor {
        var logicalLineIndex = -1
        var row = 0

        while (row <= cursorRow) {
            if (!screen[row].wrapsFromPrevious) {
                logicalLineIndex += 1
            }
            row += 1
        }

        var displayColumn = cursorColumn
        var previousRow = cursorRow - 1
        while (previousRow >= 0 && screen[previousRow + 1].wrapsFromPrevious) {
            displayColumn += screen[previousRow].line.styledGraphemes().sumOf { it.displayWidth }
            previousRow -= 1
        }

        return LogicalCursor(lineIndex = logicalLineIndex.coerceAtLeast(0), displayColumn = displayColumn)
    }

    private fun restoreLogicalCursor(position: LogicalCursor) {
        val clamped = position.copy(
            lineIndex = position.lineIndex.coerceIn(0, logicalScreenLines.lastIndex.coerceAtLeast(0)),
            displayColumn = position.displayColumn.coerceAtLeast(0),
        )
        val (column, row) = CursorMapper.logicalToScreen(
            cursor = clamped,
            projection = screenProjection(),
            width = width,
        )
        cursorColumn = column
        cursorRow = row
    }

    private fun screenProjection(): ViewportProjection {
        return ViewportProjector.project(
            logicalLines = logicalScreenLines,
            width = width,
            height = height,
            maxScrollbackLines = 0,
        )
    }

    private fun historyProjectionRows(): List<VisualRow> {
        val projection = ViewportProjector.project(
            logicalLines = logicalScrollbackLines + logicalScreenLines,
            width = width,
            height = height,
            maxScrollbackLines = maxScrollbackLines,
        )
        return projection.scrollbackRows + projection.visibleRows
    }

    private fun syncLogicalStateFromRows() {
        syncLogicalScreenFromScreenRows()
    }

    private fun syncLogicalScreenFromScreenRows() {
        logicalScreenLines.clear()
        logicalScreenLines.addAll(rowsToLogicalLines(screen, keepTrailingBlanks = true))
    }

    private fun scrollbackRows(): List<BufferRow> {
        return ViewportProjector.projectAllRows(
            logicalLines = logicalScrollbackLines,
            width = width,
        ).map { visualRow ->
            BufferRow(
                line = visualRow.screenLine,
                wrapsFromPrevious = visualRow.startDisplayColumn > 0,
            )
        }
    }

    private fun trimLogicalScrollback() {
        while (logicalScrollbackLines.size > maxScrollbackLines) {
            logicalScrollbackLines.removeFirst()
        }
    }

    private fun editLogicalScreenLines(): MutableList<LogicalLine> {
        return logicalScreenLines.map { it.trimmedCopy() }.toMutableList()
    }

    private fun rowsToLogicalLines(rows: List<BufferRow>, keepTrailingBlanks: Boolean): MutableList<LogicalLine> {
        val logicalLines = mutableListOf<LogicalLine>()

        for (row in rows) {
            if (!row.wrapsFromPrevious || logicalLines.isEmpty()) {
                logicalLines += row.line.toLogicalLine(keepTrailingBlanks = keepTrailingBlanks)
                continue
            }
            logicalLines.last().append(row.line.toLogicalLine(keepTrailingBlanks = keepTrailingBlanks).graphemes())
        }

        return logicalLines
    }
}

internal fun Grapheme.toCells(attributes: CellAttributes): List<Cell> {
    val cells = mutableListOf(Cell(kind = CellKind.GraphemeStart(text, displayWidth), attributes = attributes))
    repeat(displayWidth - 1) {
        cells += Cell(kind = CellKind.Continuation, attributes = attributes)
    }
    return cells
}
