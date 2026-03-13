package terminal.buffer

internal object CursorMapper {
    fun logicalToScreen(
        cursor: LogicalCursor,
        projection: ViewportProjection,
        width: Int,
    ): Pair<Int, Int> {
        for ((rowIndex, row) in projection.visibleRows.withIndex()) {
            if (row.logicalLineIndex != cursor.lineIndex) {
                continue
            }

            val rowStart = row.startDisplayColumn
            val rowEnd = rowStart + row.displayWidth
            if (cursor.displayColumn < rowStart || cursor.displayColumn > rowEnd) {
                continue
            }

            val hasContinuation = rowIndex + 1 < projection.visibleRows.size &&
                projection.visibleRows[rowIndex + 1].logicalLineIndex == row.logicalLineIndex &&
                projection.visibleRows[rowIndex + 1].startDisplayColumn == rowEnd

            if (cursor.displayColumn == rowEnd && hasContinuation) {
                return 0 to (rowIndex + 1)
            }

            val column = if (cursor.displayColumn == rowEnd && row.displayWidth < width) {
                row.displayWidth
            } else {
                (cursor.displayColumn - rowStart).coerceIn(0, width - 1)
            }
            return column to rowIndex
        }

        return 0 to 0
    }

    fun screenToLogical(
        row: Int,
        column: Int,
        projection: ViewportProjection,
        logicalLines: List<LogicalLine>,
    ): LogicalCursor {
        val visualRow = projection.visibleRows.getOrNull(row)
            ?: return LogicalCursor(lineIndex = 0, displayColumn = 0)

        val logicalLine = logicalLines[visualRow.logicalLineIndex]
        val clampedColumn = column.coerceIn(0, visualRow.screenLine.width() - 1)
        val normalizedColumn = visualRow.screenLine.normalizeColumn(clampedColumn)

        val displayColumn = if (normalizedColumn > visualRow.displayWidth) {
            visualRow.startDisplayColumn + visualRow.displayWidth
        } else {
            visualRow.startDisplayColumn + normalizedColumn.coerceAtMost(visualRow.displayWidth)
        }

        return LogicalCursor(
            lineIndex = visualRow.logicalLineIndex,
            displayColumn = minOf(displayColumn, logicalLine.displayWidth()),
        )
    }
}
