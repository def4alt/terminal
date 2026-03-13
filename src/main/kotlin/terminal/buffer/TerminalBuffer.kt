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
    private val logicalScreenLines = mutableListOf<LogicalLine>()
    private val logicalScrollbackLines = mutableListOf<LogicalLine>()
    private var currentAttributes = CellAttributes()
    private var cursorColumn = 0
    private var cursorRow = 0

    private data class LogicalCursorPosition(
        val logicalLineIndex: Int,
        val displayColumn: Int,
    )

    init {
        syncLogicalStateFromRows()
    }

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
        syncLogicalScreenFromScreenRows()
    }

    fun fillLine(character: Char?) {
        screen[cursorRow] = BufferRow(ScreenLine.filled(width, fillCell(character)))
        syncLogicalScreenFromScreenRows()
    }

    fun insertText(text: String) {
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
        syncLogicalScreenFromScreenRows()
    }

    fun clearScreenAndScrollback() {
        scrollback.clear()
        logicalScrollbackLines.clear()
        currentAttributes = CellAttributes()
        clearScreen()
    }

    fun resize(newWidth: Int, newHeight: Int) {
        require(newWidth > 0) { "newWidth must be positive" }
        require(newHeight > 0) { "newHeight must be positive" }

        val logicalCursor = currentLogicalCursorPosition()

        if (newWidth != width) {
            resizeWidth(scrollback, newWidth)
            resizeWidth(screen, newWidth, preserveRowCount = height)
            width = newWidth
            syncLogicalStateFromRows()
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

    fun getScreenLine(row: Int): String = screenProjection().visibleRows[row].screenLine.toDisplayText()

    fun getScreenCell(column: Int, row: Int): Cell = screenProjection().visibleRows[row].screenLine.cellAt(column)

    fun getScreenCharacter(column: Int, row: Int): String? = characterOf(getScreenCell(column, row))

    fun getScreenAttributes(column: Int, row: Int): CellAttributes = getScreenCell(column, row).attributes

    fun getHistoryCell(column: Int, row: Int): Cell = historyProjectionRows()[row].screenLine.cellAt(column)

    fun getHistoryCharacter(column: Int, row: Int): String? = characterOf(getHistoryCell(column, row))

    fun getHistoryAttributes(column: Int, row: Int): CellAttributes = getHistoryCell(column, row).attributes

    fun getHistoryLine(row: Int): String = historyProjectionRows()[row].screenLine.toDisplayText()

    fun getScreenContent(): String = screenProjection().visibleRows.joinToString("\n") { it.screenLine.toDisplayText() }

    fun getHistoryContent(): String = historyProjectionRows().joinToString("\n") { it.screenLine.toDisplayText() }

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

        val cursor = currentLogicalCursorPosition()
        val logicalLines = rowsToLogicalLines(screen, preserveBlankCells = false)
        val line = logicalLines.getOrNull(cursor.logicalLineIndex) ?: return
        val deleteIndex = graphemeIndexAtOrAfterDisplayColumn(line, cursor.displayColumn) ?: return

        line.delete(deleteIndex, 1)
        rebuildScreenFromLogicalLines(logicalLines)
        restoreLogicalCursor(cursor)
        normalizeCursorPosition()
    }

    private fun backspaceOneCharacter() {
        normalizeCursor()

        val cursor = currentLogicalCursorPosition()
        val logicalLines = rowsToLogicalLines(screen, preserveBlankCells = false)
        val line = logicalLines.getOrNull(cursor.logicalLineIndex) ?: return
        val deleteIndex = graphemeIndexBeforeDisplayColumn(line, cursor.displayColumn) ?: return

        line.delete(deleteIndex, 1)
        rebuildScreenFromLogicalLines(logicalLines)
        restoreLogicalCursor(cursor.copy(displayColumn = displayColumnForGraphemeIndex(line, deleteIndex)))
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
        scrollback += line
        trimScrollback()
    }

    private fun resizeWidth(lines: MutableList<BufferRow>, newWidth: Int, preserveRowCount: Int? = null) {
        val logicalLines = rowsToLogicalLines(lines, preserveBlankCells = false)

        val resizedRows = ViewportProjector.projectAllRows(
            logicalLines = logicalLines,
            width = newWidth,
        ).map { visualRow ->
            BufferRow(
                line = visualRow.screenLine,
                wrapsFromPrevious = visualRow.startDisplayColumn > 0,
            )
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

    private fun currentLogicalCursorPosition(): LogicalCursorPosition {
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
            displayColumn += rowDisplayWidth(screen[previousRow].line)
            previousRow -= 1
        }

        return LogicalCursorPosition(logicalLineIndex = logicalLineIndex.coerceAtLeast(0), displayColumn = displayColumn)
    }

    private fun restoreLogicalCursor(position: LogicalCursorPosition) {
        var currentLogicalLineIndex = -1
        var row = 0

        while (row < screen.size) {
            if (!screen[row].wrapsFromPrevious) {
                currentLogicalLineIndex += 1
            }

            if (currentLogicalLineIndex == position.logicalLineIndex) {
                var remaining = position.displayColumn
                var currentRow = row

                while (true) {
                    val rowWidth = rowDisplayWidth(screen[currentRow].line)
                    val hasContinuation = currentRow + 1 < screen.size && screen[currentRow + 1].wrapsFromPrevious

                    if (remaining < rowWidth) {
                        cursorRow = currentRow
                        cursorColumn = remaining
                        return
                    }

                    if (remaining == rowWidth) {
                        if (hasContinuation) {
                            cursorRow = currentRow + 1
                            cursorColumn = 0
                            return
                        }

                        if (rowWidth < width) {
                            cursorRow = currentRow
                            cursorColumn = rowWidth
                            return
                        }

                        cursorRow = minOf(currentRow + 1, screen.lastIndex)
                        cursorColumn = 0
                        return
                    }

                    if (!hasContinuation) {
                        cursorRow = currentRow
                        cursorColumn = minOf(rowWidth, width - 1)
                        return
                    }

                    remaining -= rowWidth
                    currentRow += 1
                }
            }

            row += 1
        }
    }

    private fun rowDisplayWidth(line: ScreenLine): Int {
        return line.styledGraphemes().sumOf { it.displayWidth }
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
        logicalScrollbackLines.clear()
        logicalScrollbackLines.addAll(rowsToLogicalLines(scrollback, preserveBlankCells = true))
    }

    private fun syncLogicalScreenFromScreenRows() {
        logicalScreenLines.clear()
        logicalScreenLines.addAll(rowsToLogicalLines(screen, preserveBlankCells = true))
    }

    private fun rowsToLogicalLines(rows: List<BufferRow>, preserveBlankCells: Boolean = false): MutableList<LogicalLine> {
        val logicalLines = mutableListOf<LogicalLine>()

        for (row in rows) {
            if (!row.wrapsFromPrevious || logicalLines.isEmpty()) {
                logicalLines += LogicalLine()
            }
            val units = if (preserveBlankCells) row.line.styledUnitsPreservingBlanks() else row.line.styledGraphemes()
            logicalLines.last().append(units)
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
