# Terminal Buffer Resize Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a clean, grapheme-safe `resize(newWidth, newHeight)` operation to `TerminalBuffer` that preserves visible content predictably without attempting complex terminal reflow.

**Architecture:** Keep resize entirely in the buffer layer and refactor the raw line storage into a small internal line abstraction first. Width changes should rebuild each visible row from whole graphemes, while height changes should move visible rows to or from the screen without introducing wrap metadata or paragraph reflow. The resize policy is intentionally visual and deterministic: preserve what fits, discard what does not, and never split a grapheme.

**Tech Stack:** Kotlin, Gradle, JUnit 5

---

## Repository Deliverables For This Phase

The finished repository should additionally contain:

- A `resize(newWidth, newHeight)` operation on `TerminalBuffer`
- Tests covering width grow/shrink, height grow/shrink, grapheme safety, attribute preservation, scrollback behavior, and cursor clamping
- Internal code that uses a clearer line-level abstraction instead of raw mutable cell lists for resize-sensitive operations
- Incremental Conventional Commits separating refactor, feature behavior, and documentation

## Architecture Principles

- Keep resize in `TerminalBuffer`, not the CLI.
- Do not implement paragraph or wrap reflow.
- Treat one grapheme as the smallest resizable visible unit.
- Never allow resize to split a grapheme into partial cells.
- Prefer one small internal line abstraction over scattered list-manipulation helpers.
- Keep resize behavior deterministic and easy to reason about from tests.

## Resize Policy

Use this exact policy:

- Width increase:
  - preserve all visible graphemes on each row
  - pad the rest of the row with empty cells
- Width decrease:
  - preserve graphemes from left to right while they still fully fit
  - drop any grapheme that would be cut by the new width
  - never keep orphaned `Continuation` cells
- Height increase:
  - append blank rows to the bottom of the visible screen
- Height decrease:
  - move trimmed top visible rows into scrollback
  - enforce scrollback max size
- Cursor after resize:
  - clamp to new bounds
  - if clamped onto a `Continuation`, normalize left to the owning `GraphemeStart`

This is intentionally a visual-row resize, not a logical-line reflow.

## Files To Touch

- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Create: `src/main/kotlin/terminal/buffer/ScreenLine.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`
- Modify: `src/main/kotlin/terminal/buffer/TerminalBufferCli.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt`
- Modify: `README.md`

## Recommended Internal Design

Introduce a small internal line abstraction:

```kotlin
package terminal.buffer

internal class ScreenLine(
    private val cells: MutableList<Cell>,
) {
    fun cellAt(column: Int): Cell
    fun replace(column: Int, cell: Cell)
    fun toCells(): MutableList<Cell>
    fun toDisplayText(): String
    fun visibleGraphemes(): List<GraphemeRun>
    fun resizeWidth(newWidth: Int): ScreenLine
}

internal data class GraphemeRun(
    val text: String,
    val attributes: CellAttributes,
    val displayWidth: Int,
)
```

This is internal only. The public API does not need to expose `ScreenLine` unless the refactor later justifies it.

## TDD Rules For This Plan

Apply this exact cycle to every step below:

1. Write one small failing test.
2. Run only that test and verify it fails for the expected reason.
3. Write the minimum production code needed to pass it.
4. Run the focused test again.
5. Run the full test suite.
6. Refactor only while all tests remain green.

Never write resize logic first and add tests later.

### Standard Commands

- Run one test class: `./gradlew test --tests terminal.buffer.TerminalBufferTest`
- Run one named test: `./gradlew test --tests terminal.buffer.TerminalBufferTest.<testName>`
- Run full suite: `./gradlew test`
- Run CLI interactively: `./gradlew --console=plain -q run`

### Task 1: Extract A Small Internal Line Abstraction

**Files:**
- Create: `src/main/kotlin/terminal/buffer/ScreenLine.kt`
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing test**

Add one narrow behavior test that proves existing visible line output still works after the refactor:

```kotlin
@Test
fun get_screen_line_preserves_existing_visible_text_after_internal_line_refactor() {
    val buffer = TerminalBuffer(width = 6, height = 2, maxScrollbackLines = 5)

    buffer.writeText("a界b")

    assertEquals("a界b  ", buffer.getScreenLine(0))
}
```

**Step 2: Run test to verify it fails**

Run the named test after first introducing the new type but before fully wiring it in, or start by writing the test and confirming it protects the upcoming refactor.

**Step 3: Write minimal implementation**

Refactor raw `MutableList<Cell>` rows into `ScreenLine` internally while keeping public behavior the same.

Minimum responsibilities for `ScreenLine`:

- hold cells
- render line display text
- expose safe cell access
- build blank lines

**Step 4: Run test to verify it passes**

Run the named test, then `./gradlew test`

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/ScreenLine.kt src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "refactor: extract internal screen line model"
```

### Task 2: Add Width Expansion Behavior

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/ScreenLine.kt`
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing tests**

Add one test at a time:

```kotlin
@Test
fun resize_width_grow_preserves_existing_graphemes_and_pads_right_side()

@Test
fun resize_width_grow_preserves_attributes_of_surviving_graphemes()
```

Suggested first test:

```kotlin
@Test
fun resize_width_grow_preserves_existing_graphemes_and_pads_right_side() {
    val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

    buffer.writeText("a界")
    buffer.resize(newWidth = 6, newHeight = 2)

    assertEquals("a界   ", buffer.getScreenLine(0))
}
```

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Implement width growth by rebuilding each visible line into a wider `ScreenLine` and padding with empty cells.

**Step 4: Run tests to verify they pass**

Run focused tests, then `./gradlew test`

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/ScreenLine.kt src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "feat: support width expansion for terminal buffer"
```

### Task 3: Add Width Shrink Behavior Without Splitting Graphemes

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/ScreenLine.kt`
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing tests**

Add one test at a time:

```kotlin
@Test
fun resize_width_shrink_keeps_only_whole_graphemes_that_fit()

@Test
fun resize_width_shrink_drops_wide_grapheme_that_no_longer_fits()

@Test
fun resize_width_shrink_never_leaves_continuation_cells_visible()
```

Suggested example:

```kotlin
@Test
fun resize_width_shrink_keeps_only_whole_graphemes_that_fit() {
    val buffer = TerminalBuffer(width = 6, height = 2, maxScrollbackLines = 5)

    buffer.writeText("a界b")
    buffer.resize(newWidth = 3, newHeight = 2)

    assertEquals("a界", buffer.getScreenLine(0))
}
```

And a drop case:

```kotlin
@Test
fun resize_width_shrink_drops_wide_grapheme_that_no_longer_fits() {
    val buffer = TerminalBuffer(width = 6, height = 2, maxScrollbackLines = 5)

    buffer.writeText("ab界")
    buffer.resize(newWidth = 3, newHeight = 2)

    assertEquals("ab ", buffer.getScreenLine(0))
}
```

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Implement `ScreenLine.resizeWidth(newWidth)` by extracting grapheme runs and rebuilding from left to right while space remains.

Do not slice raw cells.

**Step 4: Run tests to verify they pass**

Run focused tests, then `./gradlew test`

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/ScreenLine.kt src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "feat: shrink terminal buffer width safely"
```

### Task 4: Add Height Growth And Shrink Behavior

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing tests**

Add one test at a time:

```kotlin
@Test
fun resize_height_grow_appends_blank_rows_at_bottom()

@Test
fun resize_height_shrink_moves_trimmed_top_rows_into_scrollback()

@Test
fun resize_height_shrink_respects_scrollback_capacity()
```

Suggested shrink example:

```kotlin
@Test
fun resize_height_shrink_moves_trimmed_top_rows_into_scrollback() {
    val buffer = TerminalBuffer(width = 4, height = 3, maxScrollbackLines = 5)

    buffer.writeText("abcdefghijkl")
    buffer.resize(newWidth = 4, newHeight = 2)

    assertEquals("abcd\nefgh\nijkl", buffer.getHistoryContent())
}
```

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Implement height change by:

- growing: append blank visible rows
- shrinking: move trimmed top visible rows into scrollback, then trim scrollback if over capacity

**Step 4: Run tests to verify they pass**

Run focused tests, then `./gradlew test`

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "feat: resize terminal buffer height"
```

### Task 5: Clamp And Normalize Cursor After Resize

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Test: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing tests**

Add one test at a time:

```kotlin
@Test
fun resize_clamps_cursor_into_new_bounds()

@Test
fun resize_normalizes_cursor_off_continuation_cell()
```

Suggested example:

```kotlin
@Test
fun resize_normalizes_cursor_off_continuation_cell() {
    val buffer = TerminalBuffer(width = 6, height = 2, maxScrollbackLines = 5)

    buffer.writeText("a界b")
    buffer.setCursorPosition(column = 4, row = 0)
    buffer.resize(newWidth = 3, newHeight = 2)

    assertEquals(2, buffer.getCursorColumn())
}
```

Adjust the expected column based on the exact preserved visible content chosen by the width-shrink policy.

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

After resize:

- clamp the cursor
- if on `Continuation`, walk left to the owning `GraphemeStart`

**Step 4: Run tests to verify they pass**

Run focused tests, then `./gradlew test`

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "fix: normalize cursor after terminal buffer resize"
```

### Task 6: Expose Resize Through The CLI

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBufferCli.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt`

**Step 1: Write the failing tests**

Add one test at a time:

```kotlin
@Test
fun execute_resize_updates_screen_dimensions()

@Test
fun execute_resize_preserves_visible_content_with_current_policy()

@Test
fun execute_resize_rejects_invalid_arguments()
```

Recommended CLI command:

```text
resize <width> <height>
```

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Add `resize <width> <height>` to CLI dispatch and update help output.

Keep CLI logic thin: parse ints, call `buffer.resize(...)`, print errors for invalid input.

**Step 4: Run tests to verify they pass**

Run focused tests, then `./gradlew test`

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBufferCli.kt src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt
git commit -m "feat: add resize command to terminal buffer cli"
```

### Task 7: Update README For Resize Behavior

**Files:**
- Modify: `README.md`

**Step 1: Verify actual behavior first**

Run:

- `./gradlew test`
- `./gradlew --console=plain -q run`

**Step 2: Write minimal implementation**

Update `README.md` so it describes:

- that resize now exists
- the no-reflow resize policy
- that width shrink preserves whole graphemes only
- that height shrink moves trimmed top rows into scrollback
- the CLI `resize <width> <height>` command

**Step 3: Run verification to confirm docs are accurate**

Re-run `./gradlew test`

**Step 4: Commit**

```bash
git add README.md
git commit -m "docs: describe terminal buffer resize behavior"
```

## Suggested Commit Rhythm

Suggested Conventional Commit sequence:

- `refactor: extract internal screen line model`
- `feat: support width expansion for terminal buffer`
- `feat: shrink terminal buffer width safely`
- `feat: resize terminal buffer height`
- `fix: normalize cursor after terminal buffer resize`
- `feat: add resize command to terminal buffer cli`
- `docs: describe terminal buffer resize behavior`

## Final Verification Checklist

- `./gradlew test` passes.
- Width grow preserves graphemes and pads blanks.
- Width shrink keeps only full graphemes and never leaves continuation artifacts.
- Height shrink moves trimmed top visible rows into scrollback.
- Scrollback capacity is still enforced during resize.
- Cursor is clamped and normalized after every resize.
- CLI `resize <width> <height>` works and updates visible output.
- README describes the no-reflow resize policy accurately.
