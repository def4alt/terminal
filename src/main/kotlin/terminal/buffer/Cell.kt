package terminal.buffer

data class Cell(
    val character: Char? = null,
    val attributes: CellAttributes = CellAttributes(),
)
