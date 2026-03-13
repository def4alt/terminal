package terminal.buffer

internal class LogicalLine(
    private val graphemes: MutableList<StyledGrapheme> = mutableListOf(),
) {
    fun append(items: List<StyledGrapheme>) {
        graphemes.addAll(items)
    }

    fun insert(index: Int, items: List<StyledGrapheme>) {
        graphemes.addAll(index, items)
    }

    fun replace(start: Int, count: Int, value: List<StyledGrapheme>) {
        delete(start, count)
        insert(start, value)
    }

    fun delete(index: Int, count: Int): List<StyledGrapheme> {
        val deleted = mutableListOf<StyledGrapheme>()

        repeat(count) {
            if (index >= graphemes.size) {
                return deleted
            }
            deleted += graphemes.removeAt(index)
        }

        return deleted
    }

    fun graphemeCount(): Int = graphemes.size

    fun graphemes(): List<StyledGrapheme> = graphemes.toList()

    fun copyLine(): LogicalLine = LogicalLine(graphemes.toMutableList())

    fun trimmedCopy(): LogicalLine {
        val copy = graphemes.toMutableList()
        while (copy.isNotEmpty() && copy.last().isBlank()) {
            copy.removeLast()
        }
        return LogicalLine(copy)
    }

    fun displayWidth(): Int = graphemes.sumOf { it.displayWidth }

    fun toDisplayText(): String = graphemes.joinToString(separator = "") { if (it.isBlank()) " " else it.text }

    fun wrap(width: Int): List<ScreenLine> {
        require(width > 0) { "width must be positive" }

        if (graphemes.isEmpty()) {
            return listOf(ScreenLine.blank(width))
        }

        val rows = mutableListOf<ScreenLine>()
        var row = ScreenLine.blank(width)
        var column = 0

        for (grapheme in graphemes) {
            if (column + grapheme.displayWidth > width) {
                rows += row
                row = ScreenLine.blank(width)
                column = 0
            }

            row.writeGrapheme(
                column = column,
                kind = CellKind.GraphemeStart(grapheme.text, grapheme.displayWidth),
                attributes = grapheme.attributes,
            )
            column += grapheme.displayWidth
        }

        rows += row
        return rows
    }
}
