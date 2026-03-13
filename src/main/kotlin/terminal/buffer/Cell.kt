package terminal.buffer

data class Cell(
    val kind: CellKind = CellKind.Empty,
    val attributes: CellAttributes = CellAttributes(),
)
