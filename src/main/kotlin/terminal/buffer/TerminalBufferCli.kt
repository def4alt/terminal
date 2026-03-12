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
        return when (commandLine.trim()) {
            "help" -> {
                output.append(renderHelp())
                true
            }

            "show" -> {
                output.append(renderSnapshot(buffer)).append('\n')
                true
            }

            "quit", "exit" -> false
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
