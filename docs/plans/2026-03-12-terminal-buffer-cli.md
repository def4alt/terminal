# Terminal Buffer CLI Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a small interactive CLI for the existing Kotlin `TerminalBuffer` so a reviewer can run the program, type commands, and inspect screen, scrollback, cursor, and current attributes in real time.

**Architecture:** Keep the current buffer implementation as the core domain layer and add one thin CLI layer on top. The CLI should parse simple line-based commands, translate them to `TerminalBuffer` calls, and print deterministic snapshots or targeted responses. Avoid building a full TUI, command framework, or ANSI renderer; this should stay a lightweight interactive shell for exercising the buffer manually.

**Tech Stack:** Kotlin, Gradle, JUnit 5

**Status:** Done

---

## Repository Deliverables For This Phase

The finished repository should additionally contain:

- A runnable CLI entry point under `src/main/kotlin`
- A Gradle configuration that supports running the CLI with `./gradlew run`
- Tests covering command parsing and CLI behavior that does not require real terminal control
- Updated documentation showing how to launch and use the interactive CLI
- Incremental Conventional Commits that separate build setup, CLI behavior, and documentation work

## Architecture Principles

- Keep the CLI intentionally small and text-based.
- Do not turn the CLI into a terminal emulator UI, curses app, or ANSI-based interface.
- Keep parsing explicit and easy to understand rather than generic or over-abstracted.
- Prefer a tiny command dispatcher plus a snapshot formatter over a deep command class hierarchy.
- Let tests drive command behavior, error messages, and output formatting.
- Keep the buffer API independent from CLI concerns unless a tiny, clearly useful helper is required.

## Scope And Design Decisions

- The CLI should start a REPL-like loop that reads one command per line from standard input.
- Commands should be human-friendly and low ceremony.
- The CLI should print a concise help screen on startup and on demand.
- Invalid commands should return readable errors without crashing the session.
- The initial CLI should focus on buffer exploration, not scripting or file input.
- The CLI should expose both state-changing commands and inspection commands.
- The CLI should terminate with `quit` or `exit`.

## Recommended Command Set

Start with this command surface:

- `help` - show available commands
- `show` - print screen, history, cursor, and current attributes
- `screen` - print visible screen only
- `history` - print scrollback+screen content
- `cursor` - print current cursor position
- `move <direction> <count>` - move cursor, where direction is `up|down|left|right`
- `set-cursor <column> <row>` - move cursor directly
- `attrs` - print current attributes
- `set-attrs <fg> <bg> <styles...>` - update current attributes, e.g. `set-attrs green default bold underline`
- `write <text>` - call `writeText`
- `insert <text>` - call `insertText`
- `fill <char|empty>` - fill current line with a character or clear it with `empty`
- `append-line` - call `insertEmptyLineAtBottom`
- `clear-screen` - call `clearScreen`
- `clear-all` - call `clearScreenAndScrollback`
- `reset` - reinitialize the buffer using the same dimensions and scrollback limit
- `quit` / `exit` - leave the CLI

Keep quoting rules simple. For the first version, treat everything after `write ` or `insert ` as raw text.

## Target Project Layout

Use standard Kotlin/JVM Gradle layout and package-to-directory alignment.

```text
README.md
build.gradle.kts
src/main/kotlin/terminal/buffer/TerminalBuffer.kt
src/main/kotlin/terminal/buffer/TerminalBufferCli.kt
src/main/kotlin/terminal/buffer/TerminalBufferCliCommand.kt
src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt
src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
```

## CLI Design Sketch

Keep the CLI close to this structure:

```kotlin
package terminal.buffer

fun main() {
    TerminalBufferCli().run()
}

class TerminalBufferCli(
    private val input: BufferedReader = System.`in`.bufferedReader(),
    private val output: Appendable = System.out,
    private val width: Int = 8,
    private val height: Int = 4,
    private val scrollback: Int = 20,
) {
    fun run()
    internal fun execute(commandLine: String): Boolean
}
```

Possible parsing helper:

```kotlin
sealed interface TerminalBufferCliCommand {
    data object Help : TerminalBufferCliCommand
    data object Show : TerminalBufferCliCommand
    data class Write(val text: String) : TerminalBufferCliCommand
    data class Insert(val text: String) : TerminalBufferCliCommand
    data class Move(val direction: Direction, val count: Int) : TerminalBufferCliCommand
    // ...
}
```

This is only a sketch. If a sealed command model feels heavier than needed after the first tests, collapse it into direct parsing + dispatch.

## TDD Rules For This Plan

Apply this exact cycle to every step below:

1. Write one small failing test.
2. Run only that test and verify it fails for the expected reason.
3. Write the minimum production code needed to pass it.
4. Run the focused test again.
5. Run the full test suite.
6. Refactor only while all tests remain green.

Never build the CLI first and add tests afterward.

## Implementation Guardrails

- Keep output deterministic enough for tests.
- Prefer pure helpers for parsing and rendering where possible.
- Avoid reflection, dynamic dispatch maps, or plugin-like command systems.
- Keep methods small and responsibilities narrow.
- Use guard clauses for invalid input.
- If command parsing grows awkward, extract only the smallest useful parsing helper.

### Standard Commands

- Run one test class: `./gradlew test --tests terminal.buffer.TerminalBufferCliTest`
- Run one named test: `./gradlew test --tests terminal.buffer.TerminalBufferCliTest.<testName>`
- Run full suite: `./gradlew test`
- Run the CLI: `./gradlew run`

### Task 1: Make The Project Runnable As A CLI

**Files:**
- Modify: `build.gradle.kts`
- Create: `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt`

**Step 1: Write the failing test**

Create `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt` with a first formatting-oriented test:

```kotlin
package terminal.buffer

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class TerminalBufferCliTest {
    @Test
    fun render_help_includes_core_commands() {
        val help = renderHelp()

        assertTrue(help.contains("write <text>"))
        assertTrue(help.contains("insert <text>"))
        assertTrue(help.contains("show"))
        assertTrue(help.contains("quit"))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests terminal.buffer.TerminalBufferCliTest.render_help_includes_core_commands`

Expected: FAIL because CLI helpers do not exist.

**Step 3: Write minimal implementation**

- Apply the Gradle `application` plugin in `build.gradle.kts`
- Set `mainClass` to `terminal.buffer.TerminalBufferCliKt`
- Add the smallest help-rendering helper needed by the test

**Step 4: Run test to verify it passes**

Run the named test, then `./gradlew test`

**Step 5: Commit**

```bash
git add build.gradle.kts src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt src/main/kotlin/terminal/buffer/TerminalBufferCli.kt
git commit -m "build: configure runnable terminal buffer cli"
```

### Task 2: Add Snapshot Rendering For Interactive Inspection

**Files:**
- Create: `src/main/kotlin/terminal/buffer/TerminalBufferCli.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt`

**Step 1: Write the failing tests**

Add one test at a time:

```kotlin
@Test
fun render_snapshot_includes_screen_history_cursor_and_attributes()

@Test
fun render_snapshot_formats_empty_buffer_readably()
```

Suggested first test:

```kotlin
@Test
fun render_snapshot_includes_screen_history_cursor_and_attributes() {
    val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

    val snapshot = renderSnapshot(buffer)

    assertTrue(snapshot.contains("Screen:"))
    assertTrue(snapshot.contains("History:"))
    assertTrue(snapshot.contains("Cursor: (0, 0)"))
    assertTrue(snapshot.contains("Attributes:"))
}
```

**Step 2: Run each test to verify it fails**

Run each named test individually.

**Step 3: Write minimal implementation**

Implement `renderSnapshot(buffer: TerminalBuffer): String` and any tiny attribute-formatting helper required.

**Step 4: Run tests to verify they pass**

Run each named test, then `./gradlew test`

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBufferCli.kt src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt
git commit -m "feat: add terminal buffer cli snapshot rendering"
```

### Task 3: Add Parsing For Basic Non-Mutating Commands

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBufferCli.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt`

**Step 1: Write the failing tests**

Add one test per command:

```kotlin
@Test
fun execute_help_writes_help_text_and_continues()

@Test
fun execute_show_writes_full_snapshot_and_continues()

@Test
fun execute_quit_returns_false_to_end_session()
```

Use an in-memory output target like `StringBuilder`.

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Implement a tiny `TerminalBufferCli` runner with:

- owned `TerminalBuffer`
- `execute(commandLine: String): Boolean`
- direct dispatch for `help`, `show`, and `quit`

**Step 4: Run tests to verify they pass**

Run focused tests, then `./gradlew test`

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBufferCli.kt src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt
git commit -m "feat: add terminal buffer cli core loop"
```

### Task 4: Add Cursor And Inspection Commands

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBufferCli.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt`

**Step 1: Write the failing tests**

Add one test at a time for:

```kotlin
@Test
fun execute_cursor_prints_current_position()

@Test
fun execute_set_cursor_moves_cursor()

@Test
fun execute_move_right_updates_cursor()

@Test
fun execute_screen_prints_visible_content_only()

@Test
fun execute_history_prints_scrollback_plus_screen_content()

@Test
fun execute_attrs_prints_current_attributes()
```

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Implement:

- `cursor`
- `set-cursor <column> <row>`
- `move <direction> <count>`
- `screen`
- `history`
- `attrs`

Keep parsing explicit. Use clear input validation and error text for malformed commands.

**Step 4: Run tests to verify they pass**

Run focused tests, then `./gradlew test`

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBufferCli.kt src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt
git commit -m "feat: add terminal buffer cli inspection commands"
```

### Task 5: Add Editing Commands

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBufferCli.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt`

**Step 1: Write the failing tests**

Add one test per command:

```kotlin
@Test
fun execute_write_updates_screen_content()

@Test
fun execute_insert_updates_screen_content()

@Test
fun execute_fill_with_character_updates_current_row()

@Test
fun execute_fill_empty_clears_current_row()

@Test
fun execute_append_line_scrolls_screen()

@Test
fun execute_clear_screen_resets_visible_rows_only()

@Test
fun execute_clear_all_resets_screen_and_history()
```

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Implement:

- `write <text>`
- `insert <text>`
- `fill <char|empty>`
- `append-line`
- `clear-screen`
- `clear-all`

Treat everything after `write ` and `insert ` as raw text.

**Step 4: Run tests to verify they pass**

Run focused tests, then `./gradlew test`

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBufferCli.kt src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt
git commit -m "feat: add terminal buffer cli editing commands"
```

### Task 6: Add Attribute Commands

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBufferCli.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt`

**Step 1: Write the failing tests**

Add one test at a time for:

```kotlin
@Test
fun execute_set_attrs_updates_current_attributes()

@Test
fun execute_set_attrs_accepts_default_colors_and_no_styles()

@Test
fun execute_set_attrs_rejects_unknown_colors_or_styles()
```

Suggested command syntax:

```text
set-attrs green default bold underline
set-attrs default default
```

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Implement parsing for color names and style names. Keep mapping local and explicit.

**Step 4: Run tests to verify they pass**

Run focused tests, then `./gradlew test`

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBufferCli.kt src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt
git commit -m "feat: add terminal buffer cli attribute commands"
```

### Task 7: Add Error Handling And Reset Command

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBufferCli.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt`

**Step 1: Write the failing tests**

Add one test at a time for:

```kotlin
@Test
fun execute_unknown_command_writes_helpful_error()

@Test
fun execute_missing_arguments_writes_helpful_error()

@Test
fun execute_reset_reinitializes_buffer_with_original_dimensions()
```

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Add guard clauses and readable messages. Implement `reset` by rebuilding the buffer with the original configuration.

**Step 4: Run tests to verify they pass**

Run focused tests, then `./gradlew test`

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBufferCli.kt src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt
git commit -m "feat: harden terminal buffer cli error handling"
```

### Task 8: Wire The Interactive Read-Eval-Print Loop

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBufferCli.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt`

**Step 1: Write the failing tests**

Add one test at a time for:

```kotlin
@Test
fun run_prints_help_on_startup()

@Test
fun run_processes_multiple_commands_until_quit()
```

Use a `StringReader`-backed input and `StringBuilder` output.

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Implement `run()` with:

- startup help text
- prompt output, if you want one simple prompt like `buffer> `
- per-line execution
- loop termination on `quit` or `exit`

Keep the loop small and readable.

**Step 4: Run tests to verify they pass**

Run focused tests, then `./gradlew test`

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBufferCli.kt src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt
git commit -m "feat: add interactive terminal buffer cli"
```

### Task 9: Update README For Interactive Usage

**Files:**
- Modify: `README.md`

**Step 1: Verify current behavior before editing docs**

Run:

- `./gradlew test`
- `./gradlew run`

**Step 2: Write minimal implementation**

Update `README.md` so it:

- describes the project as library-first plus interactive CLI
- shows `./gradlew run`
- includes a few example CLI commands
- updates the architecture diagram if useful
- explains that tests remain the main executable behavior specification

Suggested example snippet:

```text
help
show
write hello world
set-cursor 0 1
fill =
history
quit
```

**Step 3: Run verification to confirm docs are accurate**

Re-run:

- `./gradlew test`
- `./gradlew run`

**Step 4: Commit**

```bash
git add README.md
git commit -m "docs: update readme for interactive cli"
```

## Suggested Commit Rhythm

Keep build changes, CLI features, hardening, and docs in separate Conventional Commits.

Suggested commit messages:

- `build: configure runnable terminal buffer cli`
- `feat: add terminal buffer cli snapshot rendering`
- `feat: add terminal buffer cli core loop`
- `feat: add terminal buffer cli inspection commands`
- `feat: add terminal buffer cli editing commands`
- `feat: add terminal buffer cli attribute commands`
- `feat: harden terminal buffer cli error handling`
- `feat: add interactive terminal buffer cli`
- `docs: update readme for interactive cli`

## Final Verification Checklist

- `./gradlew test` passes.
- `./gradlew run` starts an interactive CLI and exits cleanly with `quit`.
- The CLI allows a reviewer to manually exercise buffer operations.
- Output remains readable and deterministic enough for tests.
- The implementation stays small and avoids unnecessary abstractions.
- `README.md` reflects the CLI accurately.
- Git history remains incremental and uses Conventional Commits.
