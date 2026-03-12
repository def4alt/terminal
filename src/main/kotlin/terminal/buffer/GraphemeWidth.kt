package terminal.buffer

fun measureDisplayWidth(grapheme: String): Int {
    if (grapheme.isEmpty()) {
        return 0
    }

    val codePoints = grapheme.codePoints().toArray()

    if (codePoints.all { it.isRegionalIndicator() }) {
        return 2
    }

    if (codePoints.any { it.isEmojiPresentationLike() || it.isEmojiModifier() }) {
        return 2
    }

    val base = codePoints.firstOrNull { !it.isCombiningMark() } ?: codePoints.first()
    return if (base.isWideCodePoint()) 2 else 1
}

internal fun Int.isWideCodePoint(): Boolean {
    return this in 0x1100..0x115F ||
        this in 0x2E80..0xA4CF ||
        this in 0xAC00..0xD7A3 ||
        this in 0xF900..0xFAFF ||
        this in 0xFE10..0xFE19 ||
        this in 0xFE30..0xFE6F ||
        this in 0xFF00..0xFF60 ||
        this in 0xFFE0..0xFFE6 ||
        this in 0x1F300..0x1FAFF
}

internal fun Int.isCombiningMark(): Boolean {
    return when (Character.getType(this)) {
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
        -> true

        else -> false
    }
}

internal fun Int.isEmojiModifier(): Boolean = this in 0x1F3FB..0x1F3FF

internal fun Int.isRegionalIndicator(): Boolean = this in 0x1F1E6..0x1F1FF

private fun Int.isEmojiPresentationLike(): Boolean {
    return this in 0x1F300..0x1FAFF || this in 0x2600..0x27BF
}
