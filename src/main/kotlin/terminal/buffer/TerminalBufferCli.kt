package terminal.buffer

import java.io.BufferedReader

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

class TerminalBufferCli {
    private val buffer = TerminalBuffer(width = 8, height = 4, maxScrollbackLines = 20)

    constructor()

    constructor(output: Appendable) {
        this.output = output
    }

    private var output: Appendable = System.out

    fun run() {
        output.append(renderHelp())
    }

    internal fun execute(commandLine: String): Boolean {
        val trimmed = commandLine.trim()

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

            trimmed == "quit" || trimmed == "exit" -> false
            else -> {
                output.append("Unknown command\n")
                true
            }
        }
    }
}

private fun formatAttributes(attributes: CellAttributes): String {
    val styles = if (attributes.styles.isEmpty()) "none" else attributes.styles.joinToString(",") { it.name.lowercase() }
    return "fg=${attributes.foreground.name.lowercase()} bg=${attributes.background.name.lowercase()} styles=$styles"
}
