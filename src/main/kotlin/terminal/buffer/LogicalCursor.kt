package terminal.buffer

internal data class LogicalCursor(
    val lineIndex: Int,
    val graphemeIndex: Int,
    val graphemeColumnOffset: Int = 0,
)
