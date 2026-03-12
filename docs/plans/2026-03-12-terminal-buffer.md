# Terminal Buffer Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a Kotlin `TerminalBuffer` library with screen + scrollback storage, cursor control, text editing operations, and content access APIs, using strict TDD and Red/Green/Refactor for every behavior.

**Architecture:** The implementation uses an in-memory terminal model with immutable cell values and mutable buffer state. The buffer owns a fixed-size visible screen, a bounded scrollback queue, current drawing attributes, and a clamped cursor. Public behavior is driven from tests first, starting from construction and read APIs, then overwrite/insert editing, then scrolling and clear behavior.

**Tech Stack:** Kotlin, Gradle, JUnit 5

---

## Repository Deliverables

The finished repository should contain:

- Source code for the terminal buffer implementation under Kotlin-standard source roots such as `src/main/kotlin` and `src/test/kotlin`
- A working Gradle build that compiles successfully
- Unit tests under `src/test/kotlin` with:
  - comprehensive coverage of the required behavior
  - explicit edge-case and boundary-condition coverage
  - test names and assertions that document expected behavior clearly
- Git history that shows incremental development through TDD with:
  - small, reviewable commits
  - clear, descriptive Conventional Commit messages
  - separate commits for new behavior vs refactoring whenever practical

## Architecture Principles

- Keep the design simple and purposeful; do not overcomplicate the model or introduce abstractions before tests justify them.
- Prefer a small number of clear domain types with obvious responsibilities over clever generic helpers.
- Refactor toward readability and maintainability, not abstraction for its own sake.
- Keep public APIs explicit and easy to test.
- Let tests drive architecture decisions so the final structure is clear without becoming over-engineered.
- Follow clean code principles throughout: clear naming, small focused methods, minimal duplication, and straightforward control flow.
- Favor readability over cleverness, and remove temporary duplication during refactor steps once tests are green.
- Keep classes cohesive and responsibilities narrow so the codebase stays easy to extend and review.

## Scope And Design Decisions

- Use Kotlin and Gradle because `flake.nix` already provisions `kotlin`, `gradle`, and `jdk`.
- Use zero-based coordinates: `(column, row)`.
- Clamp cursor movement to visible screen bounds only.
- Represent the screen as `height` mutable lines, each containing `width` cells.
- Represent scrollback as a bounded FIFO list of lines; when capacity is exceeded, evict the oldest line.
- Use immutable `Cell` values containing:
  - optional character
  - foreground color
  - background color
  - style flags
- Use current attributes for all cursor-based edit operations.
- `writeText` means overwrite existing cells starting at the cursor, advancing left-to-right and continuing onto following rows as needed.
- `insertText` means insert cells at the cursor, shift existing content right, and wrap overflow across later screen rows; overflowing bottom rows scroll into scrollback.
- `fillLine` applies to the cursor's current visible row and fills the entire row with either a character or blank cells using current attributes.
- `insertEmptyLineAtBottom` appends a blank row to the visible screen and scrolls the top visible row into scrollback.
- `clearScreen` clears only visible rows and resets cursor to origin.
- `clearScreenAndScrollback` clears visible rows, clears history, and resets cursor + current attributes.
- Expose accessors for both visible screen rows and combined scrollback+screen rows.
- Defer bonus work (`resize`, wide characters) until the full core suite is green.

## Target Project Layout

Use standard Kotlin/JVM Gradle layout and package-to-directory alignment.

```text
build.gradle.kts
settings.gradle.kts
src/main/kotlin/terminal/buffer/TerminalBuffer.kt
src/main/kotlin/terminal/buffer/Cell.kt
src/main/kotlin/terminal/buffer/CellAttributes.kt
src/main/kotlin/terminal/buffer/TerminalColor.kt
src/main/kotlin/terminal/buffer/TextStyle.kt
src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
```

## Public API Sketch

The exact names can evolve during refactoring, but tests should drive an API close to this:

```kotlin
package terminal.buffer

enum class TerminalColor {
    DEFAULT,
    BLACK, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE,
    BRIGHT_BLACK, BRIGHT_RED, BRIGHT_GREEN, BRIGHT_YELLOW,
    BRIGHT_BLUE, BRIGHT_MAGENTA, BRIGHT_CYAN, BRIGHT_WHITE
}

enum class TextStyle {
    BOLD,
    ITALIC,
    UNDERLINE
}

data class CellAttributes(
    val foreground: TerminalColor = TerminalColor.DEFAULT,
    val background: TerminalColor = TerminalColor.DEFAULT,
    val styles: Set<TextStyle> = emptySet()
)

data class Cell(
    val character: Char? = null,
    val attributes: CellAttributes = CellAttributes()
)

class TerminalBuffer(
    val width: Int,
    val height: Int,
    val maxScrollbackLines: Int
) {
    fun setCurrentAttributes(attributes: CellAttributes)
    fun getCurrentAttributes(): CellAttributes

    fun getCursorColumn(): Int
    fun getCursorRow(): Int
    fun setCursorPosition(column: Int, row: Int)
    fun moveCursorUp(count: Int = 1)
    fun moveCursorDown(count: Int = 1)
    fun moveCursorLeft(count: Int = 1)
    fun moveCursorRight(count: Int = 1)

    fun writeText(text: String)
    fun insertText(text: String)
    fun fillLine(character: Char?)
    fun insertEmptyLineAtBottom()
    fun clearScreen()
    fun clearScreenAndScrollback()

    fun getScreenCell(column: Int, row: Int): Cell
    fun getHistoryCell(column: Int, row: Int): Cell
    fun getScreenLine(row: Int): String
    fun getHistoryLine(row: Int): String
    fun getScreenContent(): String
    fun getHistoryContent(): String
}
```

## TDD Rules For This Plan

Apply this exact cycle to every step below:

1. Write one small failing test.
2. Run only that test and verify it fails for the expected reason.
3. Write the minimum production code needed to pass it.
4. Run the focused test again.
5. Run the full test suite.
6. Refactor only while all tests remain green.

Never batch multiple new behaviors into one test. Never write production code before seeing a failing test.

## Implementation Guardrails

- Keep the production structure small: prefer `Cell`, `CellAttributes`, and `TerminalBuffer` as the main domain types unless tests prove another type is necessary.
- Add helpers only when they remove real duplication or clarify intent; do not introduce strategy objects, factories, or inheritance unless the tests force that complexity.
- Keep methods focused and short. If a method starts mixing cursor logic, cell creation, and scrollback mutation, split it during the refactor step.
- Prefer private helper functions inside `TerminalBuffer` before creating new files or new public types.
- Use descriptive Kotlin names and Kotlin idioms, but avoid clever abstractions that hide the buffer behavior.
- Separate behavior work from cleanup work in git history: green commits for behavior, refactor commits for structural cleanup.

### Standard Commands

- Run one test class: `./gradlew test --tests terminal.buffer.TerminalBufferTest`
- Run one named test: `./gradlew test --tests terminal.buffer.TerminalBufferTest.<testName>`
- Run full suite: `./gradlew test`

## Task 1: Bootstrap Kotlin And Gradle

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing test**

Create a smoke test in `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`:

```kotlin
package terminal.buffer

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TerminalBufferTest {
    @Test
    fun placeholder() {
        assertEquals(1, 1)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests terminal.buffer.TerminalBufferTest.placeholder`

Expected: build fails because Gradle project files do not exist yet.

**Step 3: Write minimal implementation**

Create `settings.gradle.kts`:

```kotlin
rootProject.name = "terminal-buffer"
```

Create `build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.1.10"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.0")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests terminal.buffer.TerminalBufferTest.placeholder`

Expected: PASS

**Step 5: Refactor**

Keep the bootstrap simple: only the minimum Gradle config required to compile and run tests. Keep the smoke test only until a real construction test replaces it in Task 2.

## Task 2: Create Core Value Types

**Files:**
- Create: `src/main/kotlin/terminal/buffer/TerminalColor.kt`
- Create: `src/main/kotlin/terminal/buffer/TextStyle.kt`
- Create: `src/main/kotlin/terminal/buffer/CellAttributes.kt`
- Create: `src/main/kotlin/terminal/buffer/Cell.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing test**

Replace the placeholder test with:

```kotlin
@Test
fun cell_defaults_to_blank_character_and_default_attributes() {
    val cell = Cell()

    assertEquals(null, cell.character)
    assertEquals(TerminalColor.DEFAULT, cell.attributes.foreground)
    assertEquals(TerminalColor.DEFAULT, cell.attributes.background)
    assertEquals(emptySet(), cell.attributes.styles)
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests terminal.buffer.TerminalBufferTest.cell_defaults_to_blank_character_and_default_attributes`

Expected: FAIL because `Cell`, `CellAttributes`, and enums do not exist.

**Step 3: Write minimal implementation**

Add the four production files with immutable data classes and enums. Do not add helper objects or utility files yet.

**Step 4: Run test to verify it passes**

Run the named test, then `./gradlew test`

Expected: PASS

**Step 5: Refactor**

If helpful, add a reusable `DEFAULT_ATTRIBUTES` constant only after the test is green. Keep the value types tiny and self-explanatory.

## Task 3: Construct An Empty Buffer

**Files:**
- Create: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun new_buffer_has_blank_screen_origin_cursor_and_empty_scrollback() {
    val buffer = TerminalBuffer(width = 4, height = 3, maxScrollbackLines = 5)

    assertEquals(0, buffer.getCursorColumn())
    assertEquals(0, buffer.getCursorRow())
    assertEquals("    ", buffer.getScreenLine(0))
    assertEquals("    ", buffer.getScreenLine(1))
    assertEquals("    ", buffer.getScreenLine(2))
    assertEquals("    \n    \n    ", buffer.getScreenContent())
    assertEquals("    \n    \n    ", buffer.getHistoryContent())
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests terminal.buffer.TerminalBufferTest.new_buffer_has_blank_screen_origin_cursor_and_empty_scrollback`

Expected: FAIL because `TerminalBuffer` does not exist.

**Step 3: Write minimal implementation**

Implement constructor state, blank screen initialization, cursor origin, and read methods returning blank content. Keep the buffer storage direct and readable rather than abstract.

**Step 4: Run test to verify it passes**

Run the named test, then `./gradlew test`

**Step 5: Refactor**

Extract `blankCell()` and `blankLine()` helpers if duplicated, but keep them private and local to the buffer implementation.

## Task 4: Cursor Positioning And Clamping

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing tests, one at a time**

Test sequence:

```kotlin
@Test
fun set_cursor_position_updates_cursor_when_inside_bounds()

@Test
fun set_cursor_position_clamps_when_outside_bounds()

@Test
fun move_cursor_methods_respect_screen_edges()
```

Use a separate Red/Green/Refactor cycle for each test.

**Step 2: Run each new test to verify it fails**

Run each named test individually.

Expected: FAIL because the behavior is not implemented yet.

**Step 3: Write minimal implementation**

Add `setCursorPosition`, `moveCursorUp`, `moveCursorDown`, `moveCursorLeft`, `moveCursorRight`, and shared clamping. Keep cursor rules in one place so movement stays easy to reason about.

**Step 4: Run tests to verify they pass**

Run each named test, then `./gradlew test`

**Step 5: Refactor**

Extract a single `clampCursor()` helper and avoid duplicating bound checks across cursor methods.

## Task 5: Current Attribute State

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing tests, one at a time**

```kotlin
@Test
fun current_attributes_default_to_terminal_defaults()

@Test
fun set_current_attributes_changes_attributes_for_future_edits()
```

The second test should not assert any writing behavior yet; it should only verify `getCurrentAttributes()`.

**Step 2: Run test to verify it fails**

Run each test individually.

**Step 3: Write minimal implementation**

Add `currentAttributes` state plus getter/setter. Keep attribute storage separate from cell mutation logic.

**Step 4: Run test to verify it passes**

Run focused tests, then full suite.

**Step 5: Refactor**

No refactor unless duplication appears.

## Task 6: Overwrite Text Writing

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing tests, one at a time**

Test order:

```kotlin
@Test
fun write_text_overwrites_cells_from_cursor_and_advances_cursor()

@Test
fun write_text_uses_current_attributes_for_written_cells_only()

@Test
fun write_text_continues_on_next_screen_row_when_reaching_line_end()

@Test
fun write_text_at_bottom_row_scrolls_content_into_scrollback_when_needed()
```

**Step 2: Run each test to verify it fails**

Expected: FAIL for missing `writeText` behavior.

**Step 3: Write minimal implementation**

Implement overwrite semantics only:
- write char at current cursor
- snapshot current attributes into each written cell
- advance cursor
- wrap to next row after last column
- when advancing beyond last row, scroll top visible row into scrollback and keep cursor on last visible row

Keep the implementation procedural and explicit first; do not try to unify overwrite and insert logic yet.

**Step 4: Run tests to verify they pass**

Run each named test, then full suite.

**Step 5: Refactor**

Extract helpers like `putCell`, `advanceCursorForWrite`, and `scrollUpOneLine` only if they reduce duplication and keep one responsibility per method.

## Task 7: Fill A Visible Line

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing tests, one at a time**

```kotlin
@Test
fun fill_line_replaces_current_row_with_repeated_character_using_current_attributes()

@Test
fun fill_line_with_null_clears_current_row_to_blank_cells()
```

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Fill the entire current row with cells built from current attributes. Reuse existing cell-creation logic if it improves clarity without hiding behavior.

**Step 4: Run tests to verify they pass**

Run focused tests, then full suite.

**Step 5: Refactor**

Reuse a row helper if useful, but avoid creating general-purpose utilities for a single call site.

## Task 8: Insert Text With Shifting And Wrapping

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing tests, one at a time**

Test order:

```kotlin
@Test
fun insert_text_shifts_existing_cells_right_from_cursor_position()

@Test
fun insert_text_wraps_overflow_onto_following_visible_rows()

@Test
fun insert_text_overflow_at_screen_bottom_pushes_top_rows_into_scrollback()

@Test
fun insert_text_moves_cursor_to_position_after_inserted_text()
```

Suggested setup for the first test:

```kotlin
val buffer = TerminalBuffer(5, 2, 5)
buffer.writeText("abc")
buffer.setCursorPosition(1, 0)
buffer.insertText("Z")

assertEquals("aZbc ", buffer.getScreenLine(0))
```

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Implement insertion by flowing line content through a temporary flat stream of cells from the cursor position forward, then writing it back across visible lines and scrolling if overflow escapes the bottom.

Keep insertion isolated from overwrite behavior. If shared code emerges, extract only the shared low-level operations, not a large generic editing engine.

**Step 4: Run tests to verify they pass**

Run each named test, then full suite.

**Step 5: Refactor**

Extract an insertion helper that keeps overwrite and insert logic separate and preserves clear responsibilities.

## Task 9: Append Empty Line At Bottom And Enforce Scrollback Limit

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing tests, one at a time**

```kotlin
@Test
fun insert_empty_line_at_bottom_scrolls_top_visible_line_into_scrollback()

@Test
fun scrollback_is_trimmed_to_maximum_size()
```

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Implement `insertEmptyLineAtBottom` and centralize scrollback eviction. Keep scrollback trimming in one obvious place.

**Step 4: Run tests to verify they pass**

Run focused tests, then full suite.

**Step 5: Refactor**

Reuse the same scroll helper already introduced for bottom-row writes.

## Task 10: Clear Operations

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing tests, one at a time**

```kotlin
@Test
fun clear_screen_resets_visible_content_but_keeps_scrollback()

@Test
fun clear_screen_and_scrollback_resets_all_content_cursor_and_attributes()
```

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Implement both clearing methods and reset state explicitly. Prefer straightforward reset logic over trying to reuse every branch prematurely.

**Step 4: Run tests to verify they pass**

Run focused tests, then full suite.

**Step 5: Refactor**

Extract `resetScreen()` if needed, but keep the difference between `clearScreen` and `clearScreenAndScrollback` obvious in code.

## Task 11: Fine-Grained Content Access

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing tests, one at a time**

```kotlin
@Test
fun get_screen_cell_returns_character_and_attributes_for_visible_position()

@Test
fun get_history_cell_returns_character_and_attributes_for_combined_history_position()

@Test
fun get_screen_line_returns_visible_row_as_plain_string()

@Test
fun get_history_line_returns_combined_row_as_plain_string()

@Test
fun get_screen_content_returns_visible_rows_joined_by_newlines()

@Test
fun get_history_content_returns_scrollback_then_screen_joined_by_newlines()
```

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Add the accessor methods and row lookup logic. Keep read paths side-effect free and separate from edit paths.

**Step 4: Run tests to verify they pass**

Run focused tests, then full suite.

**Step 5: Refactor**

Extract shared lookup helpers for screen and combined-history addressing only if the result remains easier to read than duplicated conditionals.

## Task 12: Edge Cases And API Hardening

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing tests, one at a time**

Add these behaviors as separate tests:

```kotlin
@Test
fun write_text_with_empty_string_does_not_change_content_or_cursor()

@Test
fun insert_text_with_empty_string_does_not_change_content_or_cursor()

@Test
fun move_cursor_by_zero_does_not_change_position()

@Test
fun bottom_overflow_discards_oldest_scrollback_line_when_at_capacity()

@Test
fun fill_line_preserves_other_rows()
```

If the production API should reject invalid constructor input, add one more test:

```kotlin
@Test
fun constructor_rejects_non_positive_dimensions()
```

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Fix only the specific failing behavior each time. Prefer guard clauses and small targeted changes over broad rewrites.

**Step 4: Run tests to verify they pass**

Run each named test, then full suite.

**Step 5: Refactor**

Improve naming, eliminate duplication, and keep internals small, cohesive, and readable. If a helper or field no longer earns its place, remove it.

## Optional Bonus Task 13: Wide Characters

Do this only after the entire core suite is green.

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Modify: `src/main/kotlin/terminal/buffer/Cell.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Suggested behavior choice:**
- Treat width-2 characters as occupying a leading cell plus a continuation marker cell.
- Prevent cursor placement on continuation cells.
- Overwrites touching either half replace the full wide character.

Add tests first for write, read, and cursor movement semantics before any implementation.

## Optional Bonus Task 14: Resize

Do this only after the core suite is stable.

**Suggested behavior choice:**
- Preserve as much visible content as possible.
- When reducing height, move trimmed top visible rows into scrollback.
- When increasing height, pull from scrollback only if that behavior is explicitly desired; otherwise add blank rows.

Again, lock the behavior in tests before implementation.

## Suggested Commit Rhythm

Create a commit after each completed task or after every 2-3 tightly related green tasks. Keep feature additions separate from pure refactors whenever practical. Use Conventional Commits throughout.

Suggested commit messages:

- `build: initialize Kotlin Gradle project`
- `feat: add terminal cell and attribute models`
- `feat: add terminal buffer cursor state`
- `feat: implement overwrite text writing`
- `feat: implement insert and scrollback behavior`
- `feat: add clear and content access operations`
- `test: cover terminal buffer edge cases`
- `refactor: simplify terminal buffer scrolling helpers`

## Final Verification Checklist

- Repository contains source code, a compiling Gradle build, and executable tests.
- Every public method has at least one failing test written first.
- Every new test was observed failing before implementation.
- Full suite passes with `./gradlew test`.
- Tests cover normal behavior, edge cases, and boundary conditions.
- Test names and assertions are readable enough to document expected behavior.
- The implementation stays simple, with clear responsibilities and no unnecessary abstractions.
- Clean code principles are visible in naming, method size, cohesion, and control flow.
- Scrollback never exceeds configured capacity.
- Cursor never leaves visible screen bounds.
- Old cells retain their original attributes after later attribute changes.
- Screen-only reads and scrollback+screen reads are both covered.
- Git history shows incremental progress with clear Conventional Commit messages.
- Refactoring commits are separated from feature commits whenever practical.
- Bonus features are not started until the core suite is green.
