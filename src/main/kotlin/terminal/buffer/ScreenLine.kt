package terminal.buffer

internal class ScreenLine private constructor(
    private val cells: MutableList<Cell>,
) {
    data class StyledGrapheme(
        val column: Int,
        val kind: CellKind.GraphemeStart,
        val attributes: CellAttributes,
    )

    fun cellAt(column: Int): Cell = cells[column]

    fun replace(column: Int, cell: Cell) {
        cells[column] = cell
    }

    fun width(): Int = cells.size

    fun toDisplayText(): String = buildString {
        for (cell in cells) {
            when (val kind = cell.kind) {
                CellKind.Empty -> append(' ')
                CellKind.Continuation -> Unit
                is CellKind.GraphemeStart -> append(kind.text)
            }
        }
    }

    fun resizeWidth(newWidth: Int): ScreenLine {
        require(newWidth > 0) { "newWidth must be positive" }

        val resized = blank(newWidth)
        var column = 0

        for (cell in cells) {
            val kind = cell.kind as? CellKind.GraphemeStart ?: continue
            if (column + kind.displayWidth > newWidth) {
                break
            }

            resized.writeGrapheme(column, kind, cell.attributes)
            column += kind.displayWidth
        }

        return resized
    }

    fun graphemes(): List<StyledGrapheme> {
        val graphemes = mutableListOf<StyledGrapheme>()

        for ((column, cell) in cells.withIndex()) {
            val kind = cell.kind as? CellKind.GraphemeStart ?: continue
            graphemes += StyledGrapheme(column = column, kind = kind, attributes = cell.attributes)
        }

        return graphemes
    }

    fun styledGraphemes(): List<terminal.buffer.StyledGrapheme> {
        return graphemes().map { grapheme ->
            terminal.buffer.StyledGrapheme(
                text = grapheme.kind.text,
                displayWidth = grapheme.kind.displayWidth,
                attributes = grapheme.attributes,
            )
        }
    }

    fun styledUnitsPreservingBlanks(): List<terminal.buffer.StyledGrapheme> {
        val units = mutableListOf<terminal.buffer.StyledGrapheme>()

        for (cell in cells) {
            when (val kind = cell.kind) {
                CellKind.Empty -> units += terminal.buffer.StyledGrapheme.blank()
                CellKind.Continuation -> Unit
                is CellKind.GraphemeStart -> units += terminal.buffer.StyledGrapheme(kind.text, kind.displayWidth, cell.attributes)
            }
        }

        return units
    }

    fun writeGrapheme(column: Int, kind: CellKind.GraphemeStart, attributes: CellAttributes) {
        for ((offset, cell) in Grapheme(kind.text, kind.displayWidth).toCells(attributes).withIndex()) {
            replace(column + offset, cell)
        }
    }

    fun normalizeColumn(column: Int): Int {
        var normalized = column.coerceIn(0, cells.lastIndex)
        while (normalized > 0 && cellAt(normalized).kind == CellKind.Continuation) {
            normalized -= 1
        }
        return normalized
    }

    companion object {
        fun blank(width: Int): ScreenLine {
            require(width > 0) { "width must be positive" }
            return ScreenLine(MutableList(width) { Cell() })
        }

        fun filled(width: Int, cell: Cell): ScreenLine {
            require(width > 0) { "width must be positive" }
            return ScreenLine(MutableList(width) { cell })
        }
    }
}
