package terminal.buffer

internal data class GraphemeRun(
    val text: String,
    val attributes: CellAttributes,
    val displayWidth: Int,
)

internal class ScreenLine private constructor(
    private val cells: MutableList<Cell>,
) {
    fun cellAt(column: Int): Cell = cells[column]

    fun replace(column: Int, cell: Cell) {
        cells[column] = cell
    }

    fun toCells(): MutableList<Cell> = cells.toMutableList()

    fun toDisplayText(): String = cells.joinToString("") { cell ->
        when (val kind = cell.kind) {
            CellKind.Empty -> " "
            CellKind.Continuation -> ""
            is CellKind.GraphemeStart -> kind.text
        }
    }

    fun visibleGraphemes(): List<GraphemeRun> {
        val graphemes = mutableListOf<GraphemeRun>()
        for (cell in cells) {
            val kind = cell.kind
            if (kind is CellKind.GraphemeStart) {
                graphemes += GraphemeRun(kind.text, cell.attributes, kind.displayWidth)
            }
        }
        return graphemes
    }

    fun resizeWidth(newWidth: Int): ScreenLine {
        require(newWidth > 0) { "newWidth must be positive" }

        val resized = blank(newWidth)
        var column = 0

        for (grapheme in visibleGraphemes()) {
            if (column + grapheme.displayWidth > newWidth) {
                break
            }

            for (cell in Grapheme(grapheme.text, grapheme.displayWidth).toCells(grapheme.attributes)) {
                resized.replace(column, cell)
                column += 1
            }
        }

        return resized
    }

    companion object {
        fun blank(width: Int): ScreenLine {
            require(width > 0) { "width must be positive" }
            return ScreenLine(MutableList(width) { Cell() })
        }

        fun fromCells(cells: MutableList<Cell>): ScreenLine = ScreenLine(cells)
    }
}
