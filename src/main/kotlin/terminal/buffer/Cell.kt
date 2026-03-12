package terminal.buffer

data class Cell(
    val kind: CellKind = CellKind.Empty,
    val attributes: CellAttributes = CellAttributes(),
) {
    fun textOrNull(): String? = when (kind) {
        CellKind.Empty -> null
        CellKind.Continuation -> null
        is CellKind.GraphemeStart -> kind.text
    }
}
