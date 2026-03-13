package terminal.buffer

internal object ViewportProjector {
    fun projectAllRows(
        logicalLines: List<LogicalLine>,
        width: Int,
    ): List<VisualRow> {
        return logicalLines.flatMapIndexed { index, line ->
            projectLogicalLine(line = line, logicalLineIndex = index, width = width)
        }
    }

    fun project(
        logicalLines: List<LogicalLine>,
        width: Int,
        height: Int,
        maxScrollbackLines: Int,
    ): ViewportProjection {
        val allRows = projectAllRows(logicalLines = logicalLines, width = width)

        val visibleRows = when {
            allRows.size >= height -> allRows.takeLast(height)
            else -> allRows + MutableList(height - allRows.size) { blankVisualRow(width, logicalLines.lastIndex.coerceAtLeast(0)) }
        }

        val scrollbackRows = if (allRows.size > height) {
            allRows.dropLast(height).takeLast(maxScrollbackLines)
        } else {
            emptyList()
        }

        return ViewportProjection(scrollbackRows = scrollbackRows, visibleRows = visibleRows)
    }

    private fun blankVisualRow(width: Int, logicalLineIndex: Int): VisualRow {
        return VisualRow(
            logicalLineIndex = logicalLineIndex,
            startGraphemeIndex = 0,
            startDisplayColumn = 0,
            graphemeCount = 0,
            displayWidth = 0,
            screenLine = ScreenLine.blank(width),
        )
    }
}

internal fun projectLogicalLine(
    line: LogicalLine,
    logicalLineIndex: Int,
    width: Int,
): List<VisualRow> {
    require(width > 0) { "width must be positive" }

    val graphemes = line.graphemes()
    if (graphemes.isEmpty()) {
        return listOf(
            VisualRow(
                logicalLineIndex = logicalLineIndex,
                startGraphemeIndex = 0,
                startDisplayColumn = 0,
                graphemeCount = 0,
                displayWidth = 0,
                screenLine = ScreenLine.blank(width),
            ),
        )
    }

    val rows = mutableListOf<VisualRow>()
    var index = 0

    while (index < graphemes.size) {
        val row = ScreenLine.blank(width)
        var displayWidth = 0
        val rowStart = index
        val rowStartDisplayColumn = graphemes.take(rowStart).sumOf { it.displayWidth }

        while (index < graphemes.size) {
            val grapheme = graphemes[index]
            if (displayWidth + grapheme.displayWidth > width) {
                break
            }

            if (!grapheme.isBlank()) {
                row.writeGrapheme(
                    column = displayWidth,
                    kind = CellKind.GraphemeStart(grapheme.text, grapheme.displayWidth),
                    attributes = grapheme.attributes,
                )
            }
            displayWidth += grapheme.displayWidth
            index += 1
        }

        rows += VisualRow(
            logicalLineIndex = logicalLineIndex,
            startGraphemeIndex = rowStart,
            startDisplayColumn = rowStartDisplayColumn,
            graphemeCount = index - rowStart,
            displayWidth = displayWidth,
            screenLine = row,
        )
    }

    return rows
}
