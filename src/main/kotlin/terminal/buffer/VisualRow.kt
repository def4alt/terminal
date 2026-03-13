package terminal.buffer

internal data class VisualRow(
    val logicalLineIndex: Int,
    val startDisplayColumn: Int,
    val displayWidth: Int,
    val screenLine: ScreenLine,
)
