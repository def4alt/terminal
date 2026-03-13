package terminal.buffer

internal class LogicalLine(
    private val graphemes: MutableList<StyledGrapheme> = mutableListOf(),
) {
    fun insert(index: Int, items: List<StyledGrapheme>) {
        graphemes.addAll(index, items)
    }

    fun delete(index: Int, count: Int): List<StyledGrapheme> {
        val deleted = mutableListOf<StyledGrapheme>()

        repeat(count) {
            if (index >= graphemes.size) {
                return deleted
            }
            deleted += graphemes.removeAt(index)
        }

        return deleted
    }

    fun graphemeCount(): Int = graphemes.size
}
