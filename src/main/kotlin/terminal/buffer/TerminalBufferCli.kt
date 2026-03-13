package terminal.buffer

import java.io.BufferedReader
import java.io.InputStreamReader

fun main() {
    TerminalBufferCli().run()
}

fun renderHelp(): String = buildString {
    appendLine("Available commands:")
    appendLine("help")
    appendLine("show")
    appendLine("screen")
    appendLine("history")
    appendLine("cursor")
    appendLine("set-cursor <column> <row>")
    appendLine("move <up|down|left|right> <count>")
    appendLine("attrs")
    appendLine("set-attrs <fg> <bg> [styles...]")
    appendLine("write <text>")
    appendLine("insert <text>")
    appendLine("fill <char|empty>")
    appendLine("append-line")
    appendLine("clear-screen")
    appendLine("clear-all")
    appendLine("resize <width> <height>")
    appendLine("reset")
    appendLine("quit")
}

fun renderSnapshot(buffer: TerminalBuffer): String = buildString {
    appendLine("Screen:")
    appendLine(buffer.getScreenContent())
    appendLine("History:")
    appendLine(buffer.getHistoryContent())
    appendLine("Cursor: (${buffer.getCursorColumn()}, ${buffer.getCursorRow()})")
    append("Attributes: ${formatAttributes(buffer.getCurrentAttributes())}")
}

class TerminalBufferCli(
    private val input: BufferedReader = BufferedReader(InputStreamReader(System.`in`)),
    private val output: Appendable = System.out,
    private val width: Int = 8,
    private val height: Int = 4,
    private val scrollback: Int = 20,
) {
    private var buffer = newBuffer()

    fun run() {
        output.append(renderHelp())

        while (true) {
            output.append("buffer> ")
            val line = input.readLine() ?: break
            if (!execute(line)) {
                break
            }
        }
    }

    internal fun execute(commandLine: String): Boolean {
        val trimmed = commandLine.trim()
        val parts = trimmed.split(" ").filter { it.isNotBlank() }

        if (trimmed.isEmpty()) {
            return true
        }

        return when {
            parts == listOf("help") -> {
                output.append(renderHelp())
                true
            }

            parts == listOf("show") -> {
                output.append(renderSnapshot(buffer)).append('\n')
                true
            }

            parts == listOf("cursor") -> {
                output.append("Cursor: (${buffer.getCursorColumn()}, ${buffer.getCursorRow()})\n")
                true
            }

            parts.firstOrNull() == "set-cursor" -> {
                val coordinates = parseTwoInts(parts) ?: return invalidUsage()
                buffer.setCursorPosition(coordinates.first, coordinates.second)
                true
            }

            parts.firstOrNull() == "move" -> {
                if (parts.size != 3) {
                    return invalidUsage()
                }
                val count = parts[2].toIntOrNull() ?: return invalidUsage()

                when (parts[1]) {
                    "up" -> buffer.moveCursorUp(count)
                    "down" -> buffer.moveCursorDown(count)
                    "left" -> buffer.moveCursorLeft(count)
                    "right" -> buffer.moveCursorRight(count)
                    else -> return invalidUsage()
                }

                true
            }

            parts == listOf("screen") -> {
                output.append("Screen:\n").append(buffer.getScreenContent()).append('\n')
                true
            }

            parts == listOf("history") -> {
                output.append("History:\n").append(buffer.getHistoryContent()).append('\n')
                true
            }

            parts == listOf("attrs") -> {
                output.append("Attributes: ${formatAttributes(buffer.getCurrentAttributes())}\n")
                true
            }

            trimmed.startsWith("set-attrs ") -> {
                val result = parseAttributes(trimmed.removePrefix("set-attrs "))
                if (result == null) {
                    output.append("Invalid attributes\n")
                } else {
                    buffer.setCurrentAttributes(result)
                }
                true
            }

            parts == listOf("set-attrs") -> invalidUsage()

            trimmed.startsWith("write ") -> {
                buffer.writeText(commandLine.substringAfter("write "))
                true
            }

            trimmed.startsWith("insert ") -> {
                buffer.insertText(commandLine.substringAfter("insert "))
                true
            }

            parts.firstOrNull() == "fill" && parts.size >= 2 -> {
                val value = trimmed.substringAfter("fill ")
                buffer.fillLine(if (value == "empty") null else value.first())
                true
            }

            parts.firstOrNull() == "resize" -> {
                val size = parseTwoInts(parts) ?: return invalidUsage()
                buffer.resize(newWidth = size.first, newHeight = size.second)
                true
            }

            parts == listOf("append-line") -> {
                buffer.insertEmptyLineAtBottom()
                true
            }

            parts == listOf("clear-screen") -> {
                buffer.clearScreen()
                true
            }

            parts == listOf("clear-all") -> {
                buffer.clearScreenAndScrollback()
                true
            }

            parts == listOf("reset") -> {
                buffer = newBuffer()
                true
            }

            parts == listOf("quit") || parts == listOf("exit") -> false
            else -> {
                output.append("Unknown command: $trimmed\n")
                true
            }
        }
    }

    private fun invalidUsage(): Boolean {
        output.append("Invalid command usage\n")
        return true
    }

    private fun parseTwoInts(parts: List<String>): Pair<Int, Int>? {
        if (parts.size != 3) {
            return null
        }

        val first = parts[1].toIntOrNull() ?: return null
        val second = parts[2].toIntOrNull() ?: return null
        return first to second
    }

    private fun newBuffer(): TerminalBuffer = TerminalBuffer(width = width, height = height, maxScrollbackLines = scrollback)
}

private fun formatAttributes(attributes: CellAttributes): String {
    val styles = if (attributes.styles.isEmpty()) "none" else attributes.styles.joinToString(",") { it.name.lowercase() }
    return "fg=${attributes.foreground.name.lowercase()} bg=${attributes.background.name.lowercase()} styles=$styles"
}

private fun parseAttributes(arguments: String): CellAttributes? {
    val parts = arguments.split(" ").filter { it.isNotBlank() }
    if (parts.size < 2) {
        return null
    }

    val foreground = parseColor(parts[0]) ?: return null
    val background = parseColor(parts[1]) ?: return null
    val styles = parts.drop(2).map { parseStyle(it) ?: return null }.toSet()

    return CellAttributes(foreground = foreground, background = background, styles = styles)
}

private fun parseColor(value: String): TerminalColor? = when (value.lowercase()) {
    "default" -> TerminalColor.DEFAULT
    "black" -> TerminalColor.BLACK
    "red" -> TerminalColor.RED
    "green" -> TerminalColor.GREEN
    "yellow" -> TerminalColor.YELLOW
    "blue" -> TerminalColor.BLUE
    "magenta" -> TerminalColor.MAGENTA
    "cyan" -> TerminalColor.CYAN
    "white" -> TerminalColor.WHITE
    "bright_black" -> TerminalColor.BRIGHT_BLACK
    "bright_red" -> TerminalColor.BRIGHT_RED
    "bright_green" -> TerminalColor.BRIGHT_GREEN
    "bright_yellow" -> TerminalColor.BRIGHT_YELLOW
    "bright_blue" -> TerminalColor.BRIGHT_BLUE
    "bright_magenta" -> TerminalColor.BRIGHT_MAGENTA
    "bright_cyan" -> TerminalColor.BRIGHT_CYAN
    "bright_white" -> TerminalColor.BRIGHT_WHITE
    else -> null
}

private fun parseStyle(value: String): TextStyle? = when (value.lowercase()) {
    "bold" -> TextStyle.BOLD
    "italic" -> TextStyle.ITALIC
    "underline" -> TextStyle.UNDERLINE
    else -> null
}
