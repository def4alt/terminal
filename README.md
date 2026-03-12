# Terminal Buffer

This is my small project for implementing a terminal text buffer in Kotlin.
The project focuses on the core data structure that a terminal emulator would use
to store visible text, preserve scrollback history, and track cursor state.

It is intentionally a library-first implementation with a small interactive CLI,
not a full terminal UI. The buffer is the interesting part here: shells write into it,
and a renderer or richer UI could sit on top later.

## Architecture

```mermaid
flowchart TD
    S[Shell or producer] --> B[TerminalBuffer]

    B --> C[Cursor state]
    B --> A[Current attributes]
    B --> SCR[Visible screen lines]
    B --> H[Scrollback lines]

    A --> CELL[Cell values]
    SCR --> CELL
    H --> CELL

    CELL --> CH[Character or empty]
    CELL --> FG[Foreground color]
    CELL --> BG[Background color]
    CELL --> ST[Style flags]

    CLI[Interactive CLI] --> B
    T[JUnit tests] --> B
```

## What exists

- `TerminalBuffer` supports configurable width, height, and maximum scrollback size.
- The project includes an interactive CLI for manually exercising the buffer.
- The buffer stores screen content separately from scrollback history.
- Each cell stores a `CellKind` plus foreground color, background color, and style flags.
- The buffer tracks current attributes that are applied to future edits.
- Cursor position can be read, set, and moved with bounds clamping.
- Editing supports overwrite writes, insert writes, line fill, bottom-line insertion, screen clear, and screen+scrollback clear.
- Content access supports reading cells, lines, visible screen content, and combined history+screen content.
- The CLI currently supports `help`, `show`, `cursor`, `set-cursor`, `move`, `screen`, `history`, `attrs`, `set-attrs`, `write`, `insert`, `fill`, `append-line`, `clear-screen`, `clear-all`, `reset`, and `quit`.
- The project includes behavior-focused unit tests with edge cases and boundary conditions.

## Solution overview

The implementation keeps the model small on purpose.
`TerminalBuffer` owns the mutable state: screen lines, scrollback lines, current attributes,
and cursor position. `Cell` and `CellAttributes` are immutable value types so written content
keeps the attributes it had at write time.

The cell model now uses three explicit states:

- `CellKind.Empty`
- `CellKind.GraphemeStart(text, displayWidth)`
- `CellKind.Continuation`

That lets the buffer represent both normal single-cell text and wide characters more cleanly.
A wide grapheme is stored as one lead cell plus one continuation cell.

The visible screen is stored as a fixed-height list of lines. Each line contains a fixed number
of cells. Scrollback is stored as a bounded FIFO list of lines. When content moves past the bottom
of the screen, the top visible line is moved into scrollback and the oldest scrollback line is
discarded if the configured capacity is exceeded.

This keeps the architecture clear and easy to test, even if it is not the most optimized possible
representation for a real production terminal emulator.

## Trade-offs and decisions

- The project is delivered as a library plus a simple CLI, not a full terminal app. The spec asks for the terminal buffer core data structure, and the tests still act as the main behavior documentation.
- The model favors readability and clean code over aggressive optimization.
- Cells are immutable values, which makes tests and behavior easier to reason about.
- Wide characters are modeled explicitly as grapheme-start plus continuation cells rather than as raw chars in isolated cells.
- Writing now works at grapheme-cluster level instead of raw code point level.
- Rendering reconstructs visible text from grapheme-start cells and does not print continuation cells as fake spaces.
- Screen and history access are exposed through explicit read methods instead of exposing internal collections.
- There is no ANSI parser, renderer, or escape-sequence handling in this project.
- The CLI is intentionally line-based and lightweight rather than a curses-style TUI.
- The implementation now covers common grapheme cases like combining marks, emoji skin-tone modifiers, ZWJ emoji sequences, flags, and wide CJK characters.
- Full Unicode grapheme-boundary correctness across all edge cases is still a future improvement, along with resize behavior.

## Example usage in code

```kotlin
val buffer = TerminalBuffer(width = 8, height = 3, maxScrollbackLines = 10)

buffer.writeText("hello")
buffer.setCursorPosition(column = 1, row = 0)
buffer.insertText("X")
buffer.fillLine('=')

println(buffer.getScreenContent())
println(buffer.getHistoryContent())
```

For concrete behavior examples, see `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`.
For CLI behavior, see `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt`.

## Local development

Run tests:

```sh
./gradlew test
```

Run the interactive CLI:

```sh
./gradlew run
```

For actual interactive use, Gradle's progress UI can get in the way.
These are better options:

```sh
./gradlew --console=plain -q run
```

Or install and run the CLI directly:

```sh
./gradlew installDist
./build/install/terminal-buffer/bin/terminal-buffer
```

Example CLI session:

```text
help
write hello
show
cursor
set-cursor 1 0
insert X
move right 2
set-attrs green default bold
attrs
fill =
append-line
history
clear-screen
reset
quit
```

The CLI is intentionally simple: it is a manual playground for the buffer, not a terminal emulator UI.

- `write <text>` and `insert <text>` treat everything after the command name as raw text.
- `fill <char|empty>` accepts either `empty` or the first character after `fill `.
- `set-attrs <fg> <bg> <styles...>` uses names like `default`, `green`, `bright_red`, `bold`, `italic`, and `underline`.
- `history` prints scrollback plus the current screen, while `screen` prints only the visible screen.

## Unicode notes

The buffer now stores visible text as grapheme-oriented cells:

- `CellKind.Empty`
- `CellKind.GraphemeStart(text, displayWidth)`
- `CellKind.Continuation`

This means a visible grapheme like `界`, `👍🏻`, `🇵🇱`, or `👨‍👩‍👧‍👦` is treated as one logical write unit.
If it takes two terminal cells, the buffer stores one grapheme-start cell followed by one continuation cell.

The current implementation includes tests for:

- ASCII text
- combining-mark sequences like `é`
- emoji modifier sequences like `👍🏻`
- ZWJ sequences like `👨‍👩‍👧‍👦`
- flag sequences like `🇵🇱`
- wide CJK characters like `界`

The segmentation and width logic is pragmatic rather than fully Unicode-complete, but it avoids the broken spacing and codepoint-splitting behavior that the earlier version had.

One important detail: string reconstruction APIs like `getScreenLine()` return visible grapheme text plus blanks for truly empty cells. Continuation cells are not rendered as extra spaces anymore.

## Layout

- `src/main/kotlin/terminal/buffer/TerminalBuffer.kt` - main buffer implementation
- `src/main/kotlin/terminal/buffer/TerminalBufferCli.kt` - interactive CLI and command handling
- `src/main/kotlin/terminal/buffer/Cell.kt` - cell value type
- `src/main/kotlin/terminal/buffer/CellKind.kt` - empty, grapheme-start, and continuation cell states
- `src/main/kotlin/terminal/buffer/Grapheme.kt` - internal grapheme model
- `src/main/kotlin/terminal/buffer/GraphemeSegmenter.kt` - pragmatic grapheme segmentation
- `src/main/kotlin/terminal/buffer/GraphemeWidth.kt` - grapheme display width rules
- `src/main/kotlin/terminal/buffer/CellAttributes.kt` - foreground/background/style attributes
- `src/main/kotlin/terminal/buffer/TerminalColor.kt` - 16-color terminal palette plus default
- `src/main/kotlin/terminal/buffer/TextStyle.kt` - supported text styles
- `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt` - behavior and edge-case tests
- `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt` - CLI and command behavior tests
- `docs/plans` - implementation planning documents used during development

## Improvements I would make next

- Follow the terminal emulator rabbit hole further with more complex ANSI behavior, cursor modes, and similar features.
- Add explicit `getCharacterAt` and `getAttributesAt` methods if the public API should mirror the spec wording more directly.
- Tighten grapheme segmentation and width measurement toward fuller Unicode correctness.
- Implement resize behavior with clearly defined retention rules.
- Revisit some naming around `history` vs `screen + scrollback` accessors to make the API even more explicit.
- Improve CLI ergonomics with better argument parsing and maybe command aliases.
