package terminal.buffer

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

class TerminalBufferCli {
    fun run() {
        print(renderHelp())
    }
}
