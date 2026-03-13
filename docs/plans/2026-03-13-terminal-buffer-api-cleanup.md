# Terminal Buffer API Cleanup Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Simplify `TerminalBuffer` into a cleaner spec-aligned API with fewer overlapping internal types, a single logical source of truth, and clearer content-access operations.

**Architecture:** Keep the public surface required by the spec, but aggressively simplify internals so `TerminalBuffer` stores logical content only, projects visible rows through one projector, and maps cursor positions through one cursor model. Remove transitional row-sync state and redundant helper types so the implementation becomes easier to reason about, test, and evolve.

**Tech Stack:** Kotlin, Gradle, JUnit 5

---

## Cleanup target

Public API should stay aligned to the assignment, but can be renamed and simplified as long as it still supports:

- configurable width, height, scrollback
- current attributes
- cursor get/set/move
- write, insert, fill
- insert empty line at bottom, clear screen, clear screen+scrollback
- character / attributes / line / full screen / full history access
- wide graphemes and resize

Internal simplification goals:

- `TerminalBuffer` owns one source of truth for content
- one cursor model only
- one projection path only
- one blank representation in logical content
- remove duplicated row-first and logical-first state

## Simplified design target

- `TerminalBuffer`
  - stores `logicalLines: MutableList<LogicalLine>`
  - stores `cursor: LogicalCursor`
  - stores `currentAttributes`, `width`, `height`, `maxScrollbackLines`
  - does not store both `screen` and `logicalScreenLines`
- `LogicalLine`
  - stores `StyledGrapheme` units only
  - exposes small mutation API: `insert`, `replace`, `delete`, `append`
- `ViewportProjector`
  - builds visible rows and history rows from logical lines
- `CursorMapper`
  - handles screen <-> logical cursor conversion
- `ScreenLine`
  - render artifact only
- remove if possible:
  - `BufferRow`
  - private `LogicalCursorPosition`
  - dual row/logical sync helpers
  - duplicated blank-preserving conversion helpers that can be folded into one clearer rule

## Public API cleanup target

Keep the spec-required behavior, but simplify naming where possible.

Recommended public API:

```kotlin
class TerminalBuffer(
    width: Int,
    height: Int,
    maxScrollbackLines: Int,
)

fun currentAttributes(): CellAttributes
fun setCurrentAttributes(attributes: CellAttributes)

fun cursorColumn(): Int
fun cursorRow(): Int
fun setCursorPosition(column: Int, row: Int)
fun moveCursorUp(count: Int = 1)
fun moveCursorDown(count: Int = 1)
fun moveCursorLeft(count: Int = 1)
fun moveCursorRight(count: Int = 1)

fun write(text: String)
fun insert(text: String)
fun fillLine(character: Char?)
fun insertEmptyLineAtBottom()
fun clearScreen()
fun clearScreenAndScrollback()

fun screenCellAt(column: Int, row: Int): Cell
fun historyCellAt(column: Int, row: Int): Cell
fun screenCharacterAt(column: Int, row: Int): String?
fun historyCharacterAt(column: Int, row: Int): String?
fun screenAttributesAt(column: Int, row: Int): CellAttributes
fun historyAttributesAt(column: Int, row: Int): CellAttributes
fun screenLineAt(row: Int): String
fun historyLineAt(row: Int): String
fun screenText(): String
fun historyText(): String

fun resize(newWidth: Int, newHeight: Int)
```

It is acceptable to keep compatibility shims temporarily during the refactor, but remove them by the end unless tests or CLI still require them.

## Files to touch

- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Modify: `src/main/kotlin/terminal/buffer/LogicalLine.kt`
- Modify: `src/main/kotlin/terminal/buffer/LogicalCursor.kt`
- Modify: `src/main/kotlin/terminal/buffer/ScreenLine.kt`
- Modify: `src/main/kotlin/terminal/buffer/ViewportProjector.kt`
- Modify: `src/main/kotlin/terminal/buffer/CursorMapper.kt`
- Delete: `src/main/kotlin/terminal/buffer/BufferRow.kt` (if fully obsolete)
- Modify: `src/main/kotlin/terminal/buffer/AnsiSnapshotRenderer.kt`
- Modify: `src/main/kotlin/terminal/buffer/TerminalBufferCli.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferCliBehaviorTest.kt`
- Modify: `README.md`

## TDD rules for this cleanup

For each task:

1. Write one failing test.
2. Run that test and verify the failure is for the expected reason.
3. Implement the minimum code to make it pass.
4. Re-run the focused test.
5. Run the full suite.
6. Refactor while green only.
7. Commit before moving to the next task.

## Standard commands

- Focused buffer tests: `./gradlew test --tests terminal.buffer.TerminalBufferTest --tests terminal.buffer.TerminalBufferBehaviorTest`
- Focused CLI tests: `./gradlew test --tests terminal.buffer.TerminalBufferCliTest --tests terminal.buffer.TerminalBufferCliBehaviorTest`
- Full suite: `./gradlew test`

### Task 1: Lock the simplified public API shape with tests

**Files:**
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing test**

Add tests that document the simplified method names while preserving spec behavior.

```kotlin
@Test
fun simplified_screen_access_api_returns_character_attributes_and_line_text() {
    val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

    buffer.write("ab")

    assertEquals("a", buffer.screenCharacterAt(0, 0))
    assertEquals(CellAttributes(), buffer.screenAttributesAt(0, 0))
    assertEquals("ab  ", buffer.screenLineAt(0))
    assertEquals("ab  \n    ", buffer.screenText())
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests terminal.buffer.TerminalBufferTest`

Expected: FAIL because the new simplified API does not exist yet.

**Step 3: Write minimal implementation**

Add forwarding methods only. Do not rename internals yet.

**Step 4: Run tests**

Run focused tests, then full suite.

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "refactor: add simplified terminal buffer api"
```

### Task 2: Collapse to one cursor model

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Modify: `src/main/kotlin/terminal/buffer/LogicalCursor.kt`
- Modify: `src/main/kotlin/terminal/buffer/CursorMapper.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun cursor_position_is_preserved_through_resize_using_one_logical_model() {
    val buffer = TerminalBuffer(width = 4, height = 3, maxScrollbackLines = 5)

    buffer.write("abcdef")
    buffer.setCursorPosition(column = 2, row = 1)
    buffer.resize(newWidth = 6, newHeight = 3)

    assertEquals(0, buffer.cursorColumn())
    assertEquals(1, buffer.cursorRow())
}
```

**Step 2: Run test to verify failure**

Expected: FAIL once the old private cursor representation is removed.

**Step 3: Write minimal implementation**

- remove `LogicalCursorPosition`
- keep one `LogicalCursor`
- make all cursor translation go through `CursorMapper`

**Step 4: Run tests**

Run focused tests, then full suite.

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/main/kotlin/terminal/buffer/LogicalCursor.kt src/main/kotlin/terminal/buffer/CursorMapper.kt src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt
git commit -m "refactor: use one logical cursor model"
```

### Task 3: Remove duplicated row state from `TerminalBuffer`

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Delete: `src/main/kotlin/terminal/buffer/BufferRow.kt`
- Modify: `src/main/kotlin/terminal/buffer/ViewportProjector.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun history_and_screen_reads_still_work_after_internal_row_state_removal() {
    val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

    buffer.write("abcdefghi")

    assertEquals("abcd", buffer.historyLineAt(0))
    assertEquals("efgh", buffer.historyLineAt(1))
    assertEquals("i   ", buffer.historyLineAt(2))
}
```

**Step 2: Run test to verify failure**

Expected: FAIL while removing `screen`/`scrollback` row storage.

**Step 3: Write minimal implementation**

- remove stored `screen` / `scrollback` row lists from `TerminalBuffer`
- keep only logical content lists
- make projector the only row producer
- delete `BufferRow` if no longer needed

**Step 4: Run tests**

Run focused tests, then full suite.

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/main/kotlin/terminal/buffer/ViewportProjector.kt src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt
git rm src/main/kotlin/terminal/buffer/BufferRow.kt
git commit -m "refactor: remove duplicated row storage"
```

### Task 4: Simplify blank handling to one logical rule

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/LogicalLine.kt`
- Modify: `src/main/kotlin/terminal/buffer/ScreenLine.kt`
- Modify: `src/main/kotlin/terminal/buffer/StyledGrapheme.kt`
- Modify: `src/main/kotlin/terminal/buffer/ViewportProjector.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun blank_cells_have_one_clear_logical_representation() {
    val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

    buffer.fillLine(null)

    assertEquals(null, buffer.screenCharacterAt(0, 0))
    assertEquals("    ", buffer.screenLineAt(0))
}
```

**Step 2: Run test to verify failure**

**Step 3: Write minimal implementation**

- choose one blank representation in logical content
- remove the need for both “preserve blanks” and “trimmed” special cases if possible
- keep render-time blank expansion in one place only

**Step 4: Run tests**

Run focused tests, then full suite.

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/LogicalLine.kt src/main/kotlin/terminal/buffer/ScreenLine.kt src/main/kotlin/terminal/buffer/StyledGrapheme.kt src/main/kotlin/terminal/buffer/ViewportProjector.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "refactor: simplify blank cell handling"
```

### Task 5: Route all mutation paths through logical content

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing tests**

Add one at a time for `write`, `insert`, `fill`, `delete`, `backspace`, `insertEmptyLineAtBottom`, `clearScreen`, `clearScreenAndScrollback`, and `resize`.

Example:

```kotlin
@Test
fun insert_mutates_logical_content_then_projects_wrapped_rows() {
    val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

    buffer.write("abcdef")
    buffer.setCursorPosition(2, 0)
    buffer.insert("XY")

    assertEquals("abXY", buffer.screenLineAt(0))
    assertEquals("cdef", buffer.screenLineAt(1))
}
```

**Step 2: Run tests to verify failure**

**Step 3: Write minimal implementation**

Make every editing operation mutate logical content first. Projection should only happen when reading or rebuilding cursor/view state.

**Step 4: Run tests**

Run focused tests, then full suite.

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "feat: mutate terminal state through logical content"
```

### Task 6: Simplify CLI and docs to the cleaned API

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBufferCli.kt`
- Modify: `src/main/kotlin/terminal/buffer/AnsiSnapshotRenderer.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferCliBehaviorTest.kt`
- Modify: `README.md`

**Step 1: Write the failing test**

Add one CLI test that still proves visible behavior after the API rename/cleanup.

```kotlin
@Test
fun show_and_screen_commands_still_render_after_terminal_buffer_api_cleanup() {
    val rendered = runCommands("write hello", "show", "screen")
    assertTrue(rendered.contains("hello"))
}
```

**Step 2: Run test to verify failure**

**Step 3: Write minimal implementation**

- update CLI to call the simplified buffer API
- trim README language to the cleaned architecture
- remove mentions of transitional types

**Step 4: Run tests**

Run CLI tests, then full suite.

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBufferCli.kt src/main/kotlin/terminal/buffer/AnsiSnapshotRenderer.kt src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt src/test/kotlin/terminal/buffer/TerminalBufferCliBehaviorTest.kt README.md
git commit -m "docs: align cli and readme with simplified buffer api"
```

### Task 7: Final cleanup and removal pass

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/*.kt`
- Modify: `src/test/kotlin/terminal/buffer/*.kt`

**Step 1: Remove obsolete API and helper code**

Delete:

- compatibility shims no longer needed
- unused helper methods
- overlapping conversion functions
- stale test helpers tied to removed internals

**Step 2: Run full suite**

Run: `./gradlew test`

Expected: PASS

**Step 3: Commit**

```bash
git add src/main/kotlin/terminal/buffer src/test/kotlin/terminal/buffer
git commit -m "refactor: remove transitional buffer architecture"
```

## Final verification checklist

- public API still satisfies the assignment requirements
- one cursor model only
- one source of truth for content
- one projection path for screen/history rows
- blank handling has one clear logical rule
- no transitional row/logical sync architecture remains
- CLI still works
- `./gradlew test` passes cleanly
