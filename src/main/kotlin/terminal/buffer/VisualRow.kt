package terminal.buffer

internal data class VisualRow(
    val logicalLineIndex: Int,
    val startGraphemeIndex: Int,
    val graphemeCount: Int,
    val displayWidth: Int,
    val screenLine: ScreenLine,
)
