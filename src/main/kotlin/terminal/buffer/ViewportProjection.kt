package terminal.buffer

internal data class ViewportProjection(
    val scrollbackRows: List<VisualRow>,
    val visibleRows: List<VisualRow>,
)
