package terminal.buffer

fun segmentGraphemes(text: String): List<Grapheme> {
    if (text.isEmpty()) {
        return emptyList()
    }

    val result = mutableListOf<StringBuilder>()
    var index = 0

    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        val chars = String(Character.toChars(codePoint))

        when {
            result.isEmpty() -> result += StringBuilder(chars)
            codePoint.isCombiningMark() -> result.last().append(chars)
            codePoint.isEmojiModifier() -> result.last().append(chars)
            codePoint == ZERO_WIDTH_JOINER -> {
                result.last().append(chars)
                index += Character.charCount(codePoint)
                if (index < text.length) {
                    val nextCodePoint = text.codePointAt(index)
                    result.last().append(String(Character.toChars(nextCodePoint)))
                    index += Character.charCount(nextCodePoint)
                }
                continue
            }
            codePoint.isRegionalIndicator() && result.last().endsWithRegionalIndicator() -> result.last().append(chars)
            else -> result += StringBuilder(chars)
        }

        index += Character.charCount(codePoint)
    }

    return result.map { Grapheme(it.toString(), 1) }
}

private fun Int.isCombiningMark(): Boolean {
    return when (Character.getType(this)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
        -> true

        else -> false
    }
}

private fun Int.isEmojiModifier(): Boolean = this in 0x1F3FB..0x1F3FF

private fun Int.isRegionalIndicator(): Boolean = this in 0x1F1E6..0x1F1FF

private fun StringBuilder.endsWithRegionalIndicator(): Boolean {
    if (isEmpty()) {
        return false
    }

    val codePoint = toString().codePointBefore(length)
    return codePoint.isRegionalIndicator()
}

private const val ZERO_WIDTH_JOINER = 0x200D
