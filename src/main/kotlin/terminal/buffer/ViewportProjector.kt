package terminal.buffer

internal object ViewportProjector

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

        while (index < graphemes.size) {
            val grapheme = graphemes[index]
            if (displayWidth + grapheme.displayWidth > width) {
                break
            }

            row.writeGrapheme(
                column = displayWidth,
                kind = CellKind.GraphemeStart(grapheme.text, grapheme.displayWidth),
                attributes = grapheme.attributes,
            )
            displayWidth += grapheme.displayWidth
            index += 1
        }

        rows += VisualRow(
            logicalLineIndex = logicalLineIndex,
            startGraphemeIndex = rowStart,
            graphemeCount = index - rowStart,
            displayWidth = displayWidth,
            screenLine = row,
        )
    }

    return rows
}
