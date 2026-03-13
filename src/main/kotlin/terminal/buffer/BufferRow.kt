package terminal.buffer

internal data class BufferRow(
    var line: ScreenLine,
    var wrapsFromPrevious: Boolean = false,
)
