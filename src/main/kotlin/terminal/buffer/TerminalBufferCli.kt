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
    appendLine("write <text>")
    appendLine("insert <text>")
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

        if (trimmed.isEmpty()) {
            return true
        }

        return when {
            trimmed == "help" -> {
                output.append(renderHelp())
                true
            }

            trimmed == "show" -> {
                output.append(renderSnapshot(buffer)).append('\n')
                true
            }

            trimmed == "cursor" -> {
                output.append("Cursor: (${buffer.getCursorColumn()}, ${buffer.getCursorRow()})\n")
                true
            }

            trimmed.startsWith("set-cursor ") -> {
                val parts = trimmed.split(" ")
                buffer.setCursorPosition(parts[1].toInt(), parts[2].toInt())
                true
            }

            trimmed == "set-cursor" -> invalidUsage()

            trimmed.startsWith("move ") -> {
                val parts = trimmed.split(" ")
                val count = parts[2].toInt()
                when (parts[1]) {
                    "up" -> buffer.moveCursorUp(count)
                    "down" -> buffer.moveCursorDown(count)
                    "left" -> buffer.moveCursorLeft(count)
                    "right" -> buffer.moveCursorRight(count)
                }
                true
            }

            trimmed == "move" -> invalidUsage()

            trimmed == "screen" -> {
                output.append("Screen:\n").append(buffer.getScreenContent()).append('\n')
                true
            }

            trimmed == "history" -> {
                output.append("History:\n").append(buffer.getHistoryContent()).append('\n')
                true
            }

            trimmed == "attrs" -> {
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

            trimmed == "set-attrs" -> invalidUsage()

            trimmed.startsWith("write ") -> {
                buffer.writeText(commandLine.substringAfter("write "))
                true
            }

            trimmed.startsWith("insert ") -> {
                buffer.insertText(commandLine.substringAfter("insert "))
                true
            }

            trimmed.startsWith("fill ") -> {
                val value = trimmed.substringAfter("fill ")
                buffer.fillLine(if (value == "empty") null else value.first())
                true
            }

            trimmed == "append-line" -> {
                buffer.insertEmptyLineAtBottom()
                true
            }

            trimmed == "clear-screen" -> {
                buffer.clearScreen()
                true
            }

            trimmed == "clear-all" -> {
                buffer.clearScreenAndScrollback()
                true
            }

            trimmed == "reset" -> {
                buffer = newBuffer()
                true
            }

            trimmed == "quit" || trimmed == "exit" -> false
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
