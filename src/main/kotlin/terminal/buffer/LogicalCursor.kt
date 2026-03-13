package terminal.buffer

internal data class LogicalCursor(
    val lineIndex: Int,
    val displayColumn: Int,
) {
    fun withDisplayColumn(displayColumn: Int): LogicalCursor = copy(displayColumn = displayColumn)
}
