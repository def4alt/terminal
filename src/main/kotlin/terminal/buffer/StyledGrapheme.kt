package terminal.buffer

internal data class StyledGrapheme(
    val text: String,
    val displayWidth: Int,
    val attributes: CellAttributes,
)
