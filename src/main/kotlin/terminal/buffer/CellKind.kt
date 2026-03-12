package terminal.buffer

sealed interface CellKind {
    data object Empty : CellKind

    data class GraphemeStart(
        val text: String,
        val displayWidth: Int,
    ) : CellKind

    data object Continuation : CellKind
}
