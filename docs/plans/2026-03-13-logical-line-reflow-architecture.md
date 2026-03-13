# Logical Line Reflow Architecture Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the current row-reconstruction reflow approach with a true logical-line source of truth that drives wrapping, resize, editing, cursor mapping, and scrollback cleanly.

**Architecture:** Make logical lines the only authoritative text model inside `TerminalBuffer`. Introduce explicit projection and cursor-mapping layers so visible rows are derived from logical lines, and screen coordinates are translated to logical positions through dedicated mappers rather than ad-hoc buffer logic. This intentionally discards backward compatibility in the internal architecture to achieve a clean separation between content, projection, cursor mapping, and CLI rendering.

**Tech Stack:** Kotlin, Gradle, JUnit 5

---

## Design target

This plan is not an incremental patch on top of the current reflow branch. It is a replacement architecture.

Target model:

- `TerminalBuffer` orchestrates logical content, current attributes, viewport size, and scrollback policy, but delegates projection and cursor translation to dedicated helpers.
- `LogicalLine` is the source of truth for user-visible text and attributes.
- `VisualRow` is a derived projection only.
- `ViewportProjector` turns logical lines into visible rows and history rows.
- `CursorMapper` translates between screen coordinates and logical cursor positions.
- `ScreenLine` remains a cell renderer, not a persistence model.
- Scrollback stores logical lines or stable logical-line slices, never frozen visual rows.
- Cursor is tracked in logical space and only translated to screen coordinates when queried.

Do not preserve transitional row metadata if a direct logical-line design can replace it.

## Internal model to introduce

Use a clean model like this:

```kotlin
internal data class StyledGrapheme(
    val text: String,
    val displayWidth: Int,
    val attributes: CellAttributes,
)

internal class LogicalLine(
    private val graphemes: MutableList<StyledGrapheme> = mutableListOf(),
) {
    fun graphemeCount(): Int
    fun graphemeAt(index: Int): StyledGrapheme
    fun insert(index: Int, value: List<StyledGrapheme>)
    fun delete(index: Int, count: Int): List<StyledGrapheme>
    fun replace(start: Int, count: Int, value: List<StyledGrapheme>)
    fun append(value: List<StyledGrapheme>)
    fun toDisplayText(): String
}

internal data class LogicalCursor(
    val lineIndex: Int,
    val graphemeIndex: Int,
    val graphemeColumnOffset: Int = 0,
)

internal data class VisualRow(
    val logicalLineIndex: Int,
    val startGraphemeIndex: Int,
    val graphemeCount: Int,
    val displayWidth: Int,
    val screenLine: ScreenLine,
)

internal data class ViewportProjection(
    val scrollbackRows: List<VisualRow>,
    val visibleRows: List<VisualRow>,
)

internal object ViewportProjector {
    fun project(
        logicalLines: List<LogicalLine>,
        width: Int,
        height: Int,
        maxScrollbackLines: Int,
    ): ViewportProjection
}

internal object CursorMapper {
    fun logicalToScreen(
        cursor: LogicalCursor,
        projection: ViewportProjection,
    ): Pair<Int, Int>

    fun screenToLogical(
        row: Int,
        column: Int,
        projection: ViewportProjection,
        logicalLines: List<LogicalLine>,
    ): LogicalCursor
}
```

Notes:

- `graphemeColumnOffset` exists for correctness if the cursor must sit after a wide grapheme or at row boundaries.
- `ViewportProjection` is recalculated from logical lines whenever content or width changes.
- All `getScreen*`, `getHistory*`, and CLI rendering methods should read from `ViewportProjection`, never rebuild rows independently.
- `TerminalBuffer` should call `ViewportProjector` and `CursorMapper`, not reimplement their logic inline.
- `ScreenLine` should not know about history or scrollback.

## Behavioral decisions

These rules should become explicit and tested:

- Width changes rewrap from logical lines, not from current visual rows.
- Height changes change the visible window, not the stored text.
- Delete and backspace operate on logical grapheme units, even when the screen shows wrapped rows.
- Insert and overwrite operate on logical positions and then rerender.
- Scrollback is based on logical content history and viewport projection, not row mutation leftovers.
- Wide graphemes remain indivisible across all operations.
- ANSI `show` rendering reads only from the projected visible rows.

## Clean architecture boundaries

Keep these boundaries strict during implementation:

- **Content layer**
  - `LogicalLine`, `StyledGrapheme`, `LogicalCursor`
  - owns text, attributes, and logical positions only
  - knows nothing about screen rows, CLI, ANSI, or history rendering

- **Projection layer**
  - `VisualRow`, `ViewportProjection`, `ViewportProjector`
  - turns logical content into wrapped visible/history rows
  - knows width, height, and scrollback window rules
  - does not mutate logical content

- **Cursor mapping layer**
  - `CursorMapper`
  - translates between screen coordinates and logical positions
  - never edits text and never renders cells

- **Rendering layer**
  - `ScreenLine`, `AnsiSnapshotRenderer`
  - turns projected graphemes into cells or ANSI output
  - does not own editor semantics

- **Orchestration layer**
  - `TerminalBuffer`, `TerminalBufferCli`
  - coordinates mutations, projection rebuilds, and public API
  - should stay thin and avoid holding duplicated logic from lower layers

## Files to touch

- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Modify: `src/main/kotlin/terminal/buffer/ScreenLine.kt`
- Create: `src/main/kotlin/terminal/buffer/LogicalLine.kt`
- Create: `src/main/kotlin/terminal/buffer/LogicalCursor.kt`
- Create: `src/main/kotlin/terminal/buffer/StyledGrapheme.kt`
- Create: `src/main/kotlin/terminal/buffer/VisualRow.kt`
- Create: `src/main/kotlin/terminal/buffer/ViewportProjection.kt`
- Create: `src/main/kotlin/terminal/buffer/ViewportProjector.kt`
- Create: `src/main/kotlin/terminal/buffer/CursorMapper.kt`
- Modify: `src/main/kotlin/terminal/buffer/AnsiSnapshotRenderer.kt`
- Modify: `src/main/kotlin/terminal/buffer/TerminalBufferCli.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferCliBehaviorTest.kt`
- Modify: `README.md`

## TDD rules for this plan

For every task below:

1. Add one failing test.
2. Run that focused test and verify the failure is correct.
3. Implement the minimum code to pass it.
4. Re-run the focused test.
5. Run the full suite.
6. Refactor while green only.
7. Commit before moving to the next task.

Do not write the new model first and tests later.

### Standard commands

- Single class: `./gradlew test --tests terminal.buffer.TerminalBufferTest`
- Multiple behavior classes: `./gradlew test --tests terminal.buffer.TerminalBufferBehaviorTest --tests terminal.buffer.TerminalBufferCliBehaviorTest`
- Full suite: `./gradlew test`
- Manual CLI check: `./gradlew --console=plain -q run`

### Task 1: Lock down the desired logical reflow behavior

**Files:**
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt`

**Step 1: Write the failing test**

Add one core reflow test:

```kotlin
@Test
fun resize_width_growth_reflows_wrapped_content_from_logical_lines() {
    val buffer = TerminalBuffer(width = 4, height = 3, maxScrollbackLines = 5)

    buffer.writeText("abcdef")
    buffer.resize(newWidth = 6, newHeight = 3)

    assertEquals("abcdef", buffer.getScreenLine(0))
    assertEquals("      ", buffer.getScreenLine(1))
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests terminal.buffer.TerminalBufferBehaviorTest`

Expected: FAIL if the current implementation still reconstructs from visible rows or preserves old wraps incorrectly.

**Step 3: Add one more failing test**

```kotlin
@Test
fun delete_reflows_following_content_across_visual_row_boundaries() {
    val buffer = TerminalBuffer(width = 4, height = 3, maxScrollbackLines = 5)

    buffer.writeText("abcdef")
    buffer.setCursorPosition(column = 1, row = 0)
    buffer.deleteCharacters(1)

    assertEquals("acde", buffer.getScreenLine(0))
    assertEquals("f   ", buffer.getScreenLine(1))
}
```

**Step 4: Run focused tests again**

Run: `./gradlew test --tests terminal.buffer.TerminalBufferBehaviorTest`

Expected: FAIL in the new tests.

**Step 5: Commit**

```bash
git add src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt
git commit -m "test: define logical-line reflow behavior"
```

### Task 2: Add the core logical content model

**Files:**
- Create: `src/main/kotlin/terminal/buffer/StyledGrapheme.kt`
- Create: `src/main/kotlin/terminal/buffer/LogicalLine.kt`
- Create: `src/main/kotlin/terminal/buffer/LogicalCursor.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun logical_line_replace_and_delete_work_on_grapheme_units() {
    val line = LogicalLine()
    val a = StyledGrapheme("a", 1, CellAttributes())
    val wide = StyledGrapheme("界", 2, CellAttributes())

    line.append(listOf(a, wide))
    line.replace(start = 0, count = 1, value = listOf(wide))

    assertEquals(2, line.graphemeCount())
    assertEquals("界界", line.toDisplayText())

    val deleted = line.delete(1, 1)
    assertEquals(listOf(wide), deleted)
    assertEquals("界", line.toDisplayText())
}
```

**Step 2: Run test to verify it fails**

Run the named test.

Expected: FAIL because the new types or methods do not exist yet.

**Step 3: Write minimal implementation**

Implement only:

- `StyledGrapheme`
- `LogicalLine.append(...)`
- `LogicalLine.replace(...)`
- `LogicalLine.delete(...)`
- `LogicalLine.graphemeCount()`
- `LogicalLine.toDisplayText()`
- `LogicalCursor`

**Step 4: Run the focused test**

Run the named test, then `./gradlew test`

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/StyledGrapheme.kt src/main/kotlin/terminal/buffer/LogicalLine.kt src/main/kotlin/terminal/buffer/LogicalCursor.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "refactor: add logical content primitives"
```

### Task 3: Add projection objects and wrap rendering

**Files:**
- Create: `src/main/kotlin/terminal/buffer/VisualRow.kt`
- Create: `src/main/kotlin/terminal/buffer/ViewportProjection.kt`
- Create: `src/main/kotlin/terminal/buffer/ViewportProjector.kt`
- Modify: `src/main/kotlin/terminal/buffer/ScreenLine.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun logical_line_wrap_produces_visual_rows_without_splitting_wide_graphemes() {
    val line = LogicalLine()
    line.append(
        listOf(
            StyledGrapheme("a", 1, CellAttributes()),
            StyledGrapheme("界", 2, CellAttributes()),
            StyledGrapheme("b", 1, CellAttributes()),
        ),
    )

    val rows = projectLogicalLine(line = line, logicalLineIndex = 0, width = 3)

    assertEquals("a界", rows[0].screenLine.toDisplayText())
    assertEquals("b  ", rows[1].screenLine.toDisplayText())
}
```

**Step 2: Run test to verify it fails**

Expected: FAIL because there is no projection logic yet.

**Step 3: Write minimal implementation**

Add a single projection helper that:

- consumes one `LogicalLine`
- emits one or more `VisualRow`
- packs whole graphemes only
- uses `ScreenLine` only as a render target

The projection logic should live in `ViewportProjector`, not in `TerminalBuffer`.

Keep the helper private/internal and test through whichever surface is smallest and cleanest.

**Step 4: Run focused test and full suite**

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/VisualRow.kt src/main/kotlin/terminal/buffer/ViewportProjection.kt src/main/kotlin/terminal/buffer/ViewportProjector.kt src/main/kotlin/terminal/buffer/ScreenLine.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "refactor: project logical lines into visual rows"
```

### Task 4: Rebuild `TerminalBuffer` around logical lines

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun screen_content_is_derived_from_logical_lines_not_stored_rows() {
    val buffer = TerminalBuffer(width = 4, height = 3, maxScrollbackLines = 5)

    buffer.writeText("abcdef")

    assertEquals("abcd", buffer.getScreenLine(0))
    assertEquals("ef  ", buffer.getScreenLine(1))
}
```

**Step 2: Run it to verify failure**

Expected: FAIL once you begin replacing row-first storage.

**Step 3: Write minimal implementation**

Refactor `TerminalBuffer` so that it stores:

- `logicalLines: MutableList<LogicalLine>`
- `currentAttributes`
- `cursor: LogicalCursor`
- `width`, `height`
- any viewport offset needed for history/screen projection

Then implement:

- `getScreenLine()`
- `getScreenCell()`
- `getScreenContent()`

strictly from a newly projected `ViewportProjection`.

Do not let `TerminalBuffer` contain wrapping logic; call `ViewportProjector.project(...)` instead.

**Step 4: Run focused tests and full suite**

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "refactor: store buffer state as logical lines"
```

### Task 5: Move cursor mapping fully into logical space

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Create: `src/main/kotlin/terminal/buffer/CursorMapper.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun resize_keeps_cursor_attached_to_the_same_logical_position_after_reflow() {
    val buffer = TerminalBuffer(width = 4, height = 3, maxScrollbackLines = 5)

    buffer.writeText("abcdef")
    buffer.setCursorPosition(column = 2, row = 1)
    buffer.resize(newWidth = 6, newHeight = 3)

    assertEquals(0, buffer.getCursorRow())
    assertEquals(5, buffer.getCursorColumn())
}
```

**Step 2: Run it to verify failure**

**Step 3: Write minimal implementation**

Add two dedicated mapping helpers in `CursorMapper`:

- `screenToLogicalCursor(...)`
- `logicalToScreenCursor(...)`

and make all cursor APIs use them.

**Step 4: Run focused tests and full suite**

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/main/kotlin/terminal/buffer/CursorMapper.kt src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt
git commit -m "refactor: map cursor through logical content"
```

### Task 6: Rebuild write and insert on logical content

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun insert_text_mutates_logical_content_then_reprojects_wrapping() {
    val buffer = TerminalBuffer(width = 4, height = 3, maxScrollbackLines = 5)

    buffer.writeText("abcdef")
    buffer.setCursorPosition(column = 2, row = 0)
    buffer.insertText("XY")

    assertEquals("abXY", buffer.getScreenLine(0))
    assertEquals("cdef", buffer.getScreenLine(1))
}
```

**Step 2: Run test to verify failure**

**Step 3: Write minimal implementation**

Write and insert should mutate `LogicalLine` data first, then rebuild projection.

**Step 4: Run focused tests and full suite**

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt
git commit -m "feat: edit logical content through write and insert"
```

### Task 7: Rebuild delete and backspace on logical content

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun backspace_deletes_the_previous_logical_grapheme_even_across_visual_row_boundaries() {
    val buffer = TerminalBuffer(width = 4, height = 3, maxScrollbackLines = 5)

    buffer.writeText("abcdef")
    buffer.setCursorPosition(column = 0, row = 1)
    buffer.backspace()

    assertEquals("abce", buffer.getScreenLine(0))
    assertEquals("f   ", buffer.getScreenLine(1))
}
```

**Step 2: Run test to verify failure**

**Step 3: Write minimal implementation**

Delete and backspace should remove grapheme units from `LogicalLine`, not mutate rendered rows.

**Step 4: Run focused tests and full suite**

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "feat: edit logical content through delete and backspace"
```

### Task 8: Rebuild resize and scrollback on logical content

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing tests**

Add one at a time:

```kotlin
@Test
fun resize_width_growth_merges_previously_wrapped_rows_from_logical_content()

@Test
fun resize_width_shrink_rewraps_from_logical_content_without_losing_graphemes_that_still_fit()

@Test
fun history_is_projected_from_logical_content_after_reflow_resize()
```

Suggested history test:

```kotlin
@Test
fun history_is_projected_from_logical_content_after_reflow_resize() {
    val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

    buffer.writeText("abcdefghi")
    buffer.resize(newWidth = 6, newHeight = 2)

    assertEquals("abcdef\nghi   ", buffer.getHistoryContent())
}
```

**Step 2: Run them to verify failure**

**Step 3: Write minimal implementation**

Resize should only change projection inputs and cursor mapping, not mutate stored logical text.

Scrollback should derive from logical content + viewport selection, not from preserved visual rows.

The viewport/history logic should live in `ViewportProjector`, not be rebuilt ad hoc inside `TerminalBuffer`.

**Step 4: Run focused tests and full suite**

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "feat: reflow resize and history from logical content"
```

### Task 9: Realign CLI and ANSI rendering to the new projection

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/AnsiSnapshotRenderer.kt`
- Modify: `src/main/kotlin/terminal/buffer/TerminalBufferCli.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferCliBehaviorTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun show_renders_ansi_styled_content_after_logical_reflow_resize() {
    val rendered = runCommands(
        "set-attrs red blue bold",
        "write abcdef",
        "resize 6 3",
        "show",
    )

    assertTrue(rendered.contains("abcdef"))
    assertTrue(rendered.contains("\u001B[31;44;1m"))
}
```

**Step 2: Run it to verify failure**

**Step 3: Write minimal implementation**

Make CLI rendering depend only on the current visible projection.

**Step 4: Run focused tests and full suite**

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/AnsiSnapshotRenderer.kt src/main/kotlin/terminal/buffer/TerminalBufferCli.kt src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt src/test/kotlin/terminal/buffer/TerminalBufferCliBehaviorTest.kt
git commit -m "test: keep cli rendering aligned with logical projection"
```

### Task 10: Remove transitional architecture and document the final model

**Files:**
- Modify: `README.md`
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Modify: `src/main/kotlin/terminal/buffer/ScreenLine.kt`

**Step 1: Write one final behavior-doc test if needed**

Only if the final reflow rule is not already clearly named in tests.

**Step 2: Remove obsolete internals**

Delete any leftover row-first helpers, temporary wrap metadata, or reconstruction code that exists only because of the old model.

At the end, `TerminalBuffer` should read like orchestration code, not like a projection engine.

**Step 3: Update README**

Document:

- logical lines as source of truth
- projected visual rows
- resize reflow behavior
- remaining limitations

**Step 4: Run full suite**

Run: `./gradlew test`

Expected: PASS

**Step 5: Commit**

```bash
git add README.md src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/main/kotlin/terminal/buffer/ScreenLine.kt src/main/kotlin/terminal/buffer/LogicalLine.kt src/main/kotlin/terminal/buffer/LogicalCursor.kt src/main/kotlin/terminal/buffer/StyledGrapheme.kt src/main/kotlin/terminal/buffer/VisualRow.kt src/main/kotlin/terminal/buffer/ViewportProjection.kt
git commit -m "docs: describe logical-line buffer architecture"
```

## Final verification checklist

- Logical lines are the only text source of truth
- Visible rows are projected from logical lines only
- Width changes reflow content without relying on previous visual rows
- Height changes do not mutate logical content
- Delete/backspace/write/insert all mutate logical content first
- Cursor survives reflow by logical position, not visual accident
- Scrollback/history derives from logical content and viewport rules
- Wide graphemes stay intact through all operations
- CLI `show` still renders ANSI styles from the projected visible rows
- `TerminalBuffer` does not contain duplicated wrapping or cursor-mapping logic that belongs in projector/mapper helpers
- `./gradlew test` passes cleanly
