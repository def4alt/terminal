package terminal.buffer

internal data class StyledGrapheme(
    val text: String,
    val displayWidth: Int,
    val attributes: CellAttributes,
) {
    fun isBlank(): Boolean = text.isEmpty()

    companion object {
        fun blank(): StyledGrapheme = StyledGrapheme(text = "", displayWidth = 1, attributes = CellAttributes())
    }
}
