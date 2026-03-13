# Terminal Buffer Reflow Resize Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the current visual-row resize behavior with a clean logical-line architecture that can reflow wrapped content correctly when the terminal width changes.

**Architecture:** Stop treating the screen as the primary source of truth for wrapped text. Introduce a logical content layer that stores immutable grapheme runs per logical line, then derive visible screen rows from that model during writes, inserts, deletes, backspace, and resize. The screen becomes a rendered projection of logical content plus viewport rules, which makes reflow a normal rebuild step instead of a lossy row mutation.

**Tech Stack:** Kotlin, Gradle, JUnit 5

---

## Design direction

This plan intentionally breaks backward compatibility inside the implementation to get a cleaner architecture.

The current design stores visible rows directly and mutates them in place. That works for no-reflow resize, but it loses the information needed to reconstruct logical wrapped lines later. Proper reflow needs a higher-level model.

Target design:

- `TerminalBuffer` owns logical lines, cursor state, current attributes, and viewport size.
- A logical line stores grapheme runs with attributes, not pre-wrapped screen rows.
- Screen rows are rebuilt from logical lines whenever content changes or width changes.
- Scrollback is logical-line history, not frozen visual rows.
- Cursor tracks a logical position, then maps to visible row/column during rendering.

Do not try to preserve the old row-first internals. Replace them.

## New internal model

Introduce these internal concepts:

```kotlin
internal data class StyledGrapheme(
    val text: String,
    val displayWidth: Int,
    val attributes: CellAttributes,
)

internal class LogicalLine(
    private val graphemes: MutableList<StyledGrapheme> = mutableListOf(),
) {
    fun insert(index: Int, graphemes: List<StyledGrapheme>)
    fun delete(index: Int, count: Int): List<StyledGrapheme>
    fun replace(index: Int, graphemes: List<StyledGrapheme>)
    fun graphemeCount(): Int
    fun graphemeAt(index: Int): StyledGrapheme
    fun toDisplayText(): String
}

internal data class VisualRow(
    val logicalLineIndex: Int,
    val startGraphemeIndex: Int,
    val graphemeCount: Int,
    val cells: ScreenLine,
)

internal data class LogicalCursor(
    val lineIndex: Int,
    val graphemeIndex: Int,
)
```

Keep `ScreenLine` if it remains useful for rendering cells, but make it a pure render target instead of the source of truth.

## Behavioral rules

Reflow policy for the new model:

- Width increase:
  - recompute visual rows from logical lines
  - merge previously wrapped rows when their graphemes now fit together
- Width decrease:
  - recompute visual rows from logical lines
  - wrap by whole grapheme units only
  - never split wide graphemes or leave orphan continuation cells
- Height increase:
  - show more visual rows from the logical history if available
  - otherwise pad with blank visible rows at the bottom
- Height decrease:
  - reduce visible viewport height only
  - do not mutate logical content
- Cursor after resize:
  - stay attached to the same logical line + grapheme position
  - remap to the new visible row/column projection

Editing policy for the new model:

- `writeText` overwrites graphemes at the logical cursor position.
- `insertText` inserts graphemes into the logical line.
- `deleteCharacters` deletes graphemes from the logical cursor position.
- `backspace` deletes graphemes before the logical cursor position.
- Wrapping is always a rendering concern, not a storage concern.

## Files to touch

- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Modify: `src/main/kotlin/terminal/buffer/ScreenLine.kt`
- Create: `src/main/kotlin/terminal/buffer/LogicalLine.kt`
- Create: `src/main/kotlin/terminal/buffer/StyledGrapheme.kt`
- Create: `src/main/kotlin/terminal/buffer/VisualRow.kt`
- Modify: `src/main/kotlin/terminal/buffer/AnsiSnapshotRenderer.kt`
- Modify: `src/main/kotlin/terminal/buffer/TerminalBufferCli.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferCliBehaviorTest.kt`
- Modify: `README.md`

## TDD rules for this plan

For every step below, follow this exact cycle:

1. Add one small failing test.
2. Run only that test and confirm it fails for the expected reason.
3. Implement the minimum code to make it pass.
4. Re-run the focused test.
5. Run the full suite.
6. Refactor only while the suite stays green.

Do not pre-build the logical-line model before the tests force it.

### Standard commands

- One test class: `./gradlew test --tests terminal.buffer.TerminalBufferTest`
- One named test: `./gradlew test --tests terminal.buffer.TerminalBufferTest.<testName>`
- CLI behavior tests: `./gradlew test --tests terminal.buffer.TerminalBufferCliTest --tests terminal.buffer.TerminalBufferCliBehaviorTest`
- Full suite: `./gradlew test`
- Manual CLI run: `./gradlew --console=plain -q run`

### Task 1: Prove the desired reflow behavior first

**Files:**
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing test**

Add one behavior test that captures the core expectation of proper reflow.

```kotlin
@Test
fun resize_width_growth_reflows_previously_wrapped_text_back_together() {
    val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

    buffer.writeText("abcdef")

    assertEquals("abcd", buffer.getScreenLine(0))
    assertEquals("ef  ", buffer.getScreenLine(1))

    buffer.resize(newWidth = 6, newHeight = 2)

    assertEquals("abcdef", buffer.getScreenLine(0))
    assertEquals("      ", buffer.getScreenLine(1))
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests terminal.buffer.TerminalBufferBehaviorTest.resize_width_growth_reflows_previously_wrapped_text_back_together`

Expected: FAIL because current resize preserves visual rows instead of reflowing.

**Step 3: Add one more failing test**

Add a wide-grapheme reflow case:

```kotlin
@Test
fun resize_width_growth_reflows_without_splitting_wide_graphemes() {
    val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

    buffer.writeText("a界bc")
    buffer.resize(newWidth = 6, newHeight = 2)

    assertEquals("a界bc ", buffer.getScreenLine(0))
}
```

**Step 4: Run focused tests again**

Run: `./gradlew test --tests terminal.buffer.TerminalBufferBehaviorTest`

Expected: FAIL in the new reflow cases.

**Step 5: Commit**

```bash
git add src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "test: define resize reflow behavior"
```

### Task 2: Extract styled graphemes and logical lines

**Files:**
- Create: `src/main/kotlin/terminal/buffer/StyledGrapheme.kt`
- Create: `src/main/kotlin/terminal/buffer/LogicalLine.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing test**

Add a narrow test around the new logical-line abstraction before wiring it into the whole buffer.

```kotlin
@Test
fun logical_line_insert_and_delete_operate_on_grapheme_units() {
    val line = LogicalLine()
    val styled = StyledGrapheme("界", 2, CellAttributes())

    line.insert(0, listOf(styled))
    assertEquals(1, line.graphemeCount())

    val deleted = line.delete(0, 1)
    assertEquals(listOf(styled), deleted)
    assertEquals(0, line.graphemeCount())
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests terminal.buffer.TerminalBufferTest.logical_line_insert_and_delete_operate_on_grapheme_units`

Expected: FAIL because `LogicalLine` and `StyledGrapheme` do not exist yet.

**Step 3: Write minimal implementation**

Implement only:

- `StyledGrapheme`
- `LogicalLine.insert(...)`
- `LogicalLine.delete(...)`
- `LogicalLine.graphemeCount()`

Keep it independent from `TerminalBuffer` for now.

**Step 4: Run test to verify it passes**

Run focused test, then `./gradlew test`

**Step 5: Refactor**

If duplication appears between `Grapheme` and `StyledGrapheme`, keep both for now unless tests force a merge.

**Step 6: Commit**

```bash
git add src/main/kotlin/terminal/buffer/StyledGrapheme.kt src/main/kotlin/terminal/buffer/LogicalLine.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "refactor: add logical line content model"
```

### Task 3: Render visual rows from logical lines

**Files:**
- Create: `src/main/kotlin/terminal/buffer/VisualRow.kt`
- Modify: `src/main/kotlin/terminal/buffer/ScreenLine.kt`
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing test**

Add one rendering-focused test:

```kotlin
@Test
fun logical_lines_render_into_wrapped_visual_rows_by_width() {
    val buffer = TerminalBuffer(width = 4, height = 3, maxScrollbackLines = 5)

    buffer.writeText("abcdef")

    assertEquals("abcd", buffer.getScreenLine(0))
    assertEquals("ef  ", buffer.getScreenLine(1))
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests terminal.buffer.TerminalBufferTest.logical_lines_render_into_wrapped_visual_rows_by_width`

Expected: FAIL once you have begun switching `TerminalBuffer` away from row-first storage.

**Step 3: Write minimal implementation**

Refactor `TerminalBuffer` so that:

- content is stored in logical lines
- `getScreenLine()`, `getScreenCell()`, `getScreenContent()` render from logical lines
- wrapping uses width and whole grapheme boundaries only

Keep the first pass minimal:

- render only screen lines
- history and resize can still be wrong temporarily
- do not optimize caching yet

**Step 4: Run test to verify it passes**

Run focused test, then `./gradlew test`

**Step 5: Refactor**

Extract a private `renderViewport()` helper in `TerminalBuffer` if the mapping code starts repeating.

**Step 6: Commit**

```bash
git add src/main/kotlin/terminal/buffer/VisualRow.kt src/main/kotlin/terminal/buffer/ScreenLine.kt src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "refactor: render screen rows from logical lines"
```

### Task 4: Move cursor state to logical positions

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt`

**Step 1: Write the failing test**

Add one test proving the cursor survives reflow as logical content changes shape:

```kotlin
@Test
fun resize_keeps_cursor_attached_to_the_same_logical_content_after_reflow() {
    val buffer = TerminalBuffer(width = 4, height = 3, maxScrollbackLines = 5)

    buffer.writeText("abcdef")
    buffer.setCursorPosition(column = 2, row = 1)

    buffer.resize(newWidth = 6, newHeight = 3)

    assertEquals(2, buffer.getCursorColumn())
    assertEquals(0, buffer.getCursorRow())
}
```

**Step 2: Run test to verify it fails**

Run the named test.

Expected: FAIL because the current cursor is screen-cell based.

**Step 3: Write minimal implementation**

Introduce logical cursor tracking internally:

- store current position as logical line + grapheme index + optional cell offset within grapheme
- translate to visible row/column on reads
- make `setCursorPosition(...)` resolve screen coordinates back into logical coordinates

Keep the public API unchanged.

**Step 4: Run tests to verify they pass**

Run focused test, then `./gradlew test`

**Step 5: Refactor**

Extract mapping helpers:

- `screenToLogicalCursor(...)`
- `logicalToScreenCursor(...)`

**Step 6: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt
git commit -m "refactor: track cursor in logical content space"
```

### Task 5: Rebuild writes and inserts on top of logical lines

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt`

**Step 1: Write the failing tests**

Add one test at a time:

```kotlin
@Test
fun write_text_overwrites_logical_content_then_re-renders_wrapping()

@Test
fun insert_text_inserts_into_logical_content_then_re-renders_wrapping()
```

Suggested example:

```kotlin
@Test
fun insert_text_reflows_following_content_across_visual_rows() {
    val buffer = TerminalBuffer(width = 4, height = 3, maxScrollbackLines = 5)

    buffer.writeText("abcdef")
    buffer.setCursorPosition(column = 2, row = 0)
    buffer.insertText("XY")

    assertEquals("abXY", buffer.getScreenLine(0))
    assertEquals("cdef", buffer.getScreenLine(1))
}
```

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Implement write/insert only against `LogicalLine` content. After mutation, rerender viewport rows.

Do not keep any old row-shuffling code.

**Step 4: Run tests to verify they pass**

Run focused tests, then `./gradlew test`

**Step 5: Refactor**

Extract one mutation helper if both operations repeat position normalization.

**Step 6: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt
git commit -m "feat: edit logical lines through write and insert"
```

### Task 6: Rebuild delete and backspace on top of logical lines

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferCliBehaviorTest.kt`

**Step 1: Write the failing tests**

Add one test at a time:

```kotlin
@Test
fun delete_characters_reflows_following_logical_content_leftward()

@Test
fun backspace_reflows_following_logical_content_leftward()

@Test
fun delete_and_backspace_treat_wide_graphemes_as_single_units()
```

Example:

```kotlin
@Test
fun delete_characters_reflows_following_logical_content_leftward() {
    val buffer = TerminalBuffer(width = 4, height = 3, maxScrollbackLines = 5)

    buffer.writeText("abcdef")
    buffer.setCursorPosition(column = 1, row = 0)
    buffer.deleteCharacters(1)

    assertEquals("acde", buffer.getScreenLine(0))
    assertEquals("f   ", buffer.getScreenLine(1))
}
```

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Implement delete/backspace by deleting grapheme units from `LogicalLine`, not from rendered rows.

**Step 4: Run tests to verify they pass**

Run focused tests, then `./gradlew test`

**Step 5: Refactor**

Unify write/insert/delete/backspace around one logical mutation path if the code starts to drift.

**Step 6: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt src/test/kotlin/terminal/buffer/TerminalBufferCliBehaviorTest.kt
git commit -m "feat: edit logical lines through delete and backspace"
```

### Task 7: Implement true reflow resize

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt`

**Step 1: Write the failing tests**

Add one test at a time:

```kotlin
@Test
fun resize_width_growth_merges_previously_wrapped_rows()

@Test
fun resize_width_shrink_rewraps_from_logical_content()

@Test
fun resize_preserves_attributes_across_reflow()

@Test
fun resize_preserves_cursor_attachment_to_logical_content()
```

Suggested width-shrink test:

```kotlin
@Test
fun resize_width_shrink_rewraps_from_logical_content() {
    val buffer = TerminalBuffer(width = 6, height = 3, maxScrollbackLines = 5)

    buffer.writeText("abcdef")
    buffer.resize(newWidth = 3, newHeight = 3)

    assertEquals("abc", buffer.getScreenLine(0))
    assertEquals("def", buffer.getScreenLine(1))
}
```

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Implement `resize(...)` as:

- update width/height
- rerender viewport rows from logical lines
- recalculate cursor screen projection
- do not mutate logical text for width changes

For height changes, change only the visible projection and which logical rows are included in the viewport.

**Step 4: Run tests to verify they pass**

Run focused tests, then `./gradlew test`

**Step 5: Refactor**

Extract a single `rebuildViewport()` helper if resize and editing both need it.

**Step 6: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt
git commit -m "feat: reflow wrapped content on resize"
```

### Task 8: Rebuild scrollback as logical history

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing test**

Add a test that proves scrollback remains logical under reflow:

```kotlin
@Test
fun history_content_is_based_on_logical_lines_after_reflow_resize() {
    val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

    buffer.writeText("abcdefghi")
    buffer.resize(newWidth = 6, newHeight = 2)

    assertEquals("abcdef\nghi   ", buffer.getHistoryContent())
}
```

Adjust the exact expected value to the chosen logical-history rendering, but decide once and document it in tests.

**Step 2: Run test to verify it fails**

**Step 3: Write minimal implementation**

Store scrollback as logical lines or logical visual projections derived from logical lines, but keep one consistent source of truth. Prefer logical lines for cleanliness.

**Step 4: Run tests to verify they pass**

Run focused tests, then `./gradlew test`

**Step 5: Refactor**

If viewport selection and history rendering use different traversal rules, extract one shared projection routine.

**Step 6: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "refactor: model scrollback as logical history"
```

### Task 9: Align CLI and ANSI rendering with the new model

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/AnsiSnapshotRenderer.kt`
- Modify: `src/main/kotlin/terminal/buffer/TerminalBufferCli.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferCliBehaviorTest.kt`

**Step 1: Write the failing tests**

Add one test that proves `show` still renders correctly after reflow:

```kotlin
@Test
fun show_renders_ansi_styled_content_after_reflow_resize() {
    val rendered = runCommands(
        "set-attrs red blue bold",
        "write abcdef",
        "resize 6 2",
        "show",
    )

    assertTrue(rendered.contains("abcdef"))
    assertTrue(rendered.contains("\u001B[31;44;1m"))
}
```

**Step 2: Run test to verify it fails**

**Step 3: Write minimal implementation**

Update snapshot rendering only as needed so it reads the new visible projection correctly.

**Step 4: Run tests to verify they pass**

Run focused CLI tests, then `./gradlew test`

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/AnsiSnapshotRenderer.kt src/main/kotlin/terminal/buffer/TerminalBufferCli.kt src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt src/test/kotlin/terminal/buffer/TerminalBufferCliBehaviorTest.kt
git commit -m "test: keep cli rendering aligned with reflow model"
```

### Task 10: Final cleanup and documentation

**Files:**
- Modify: `README.md`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt`

**Step 1: Write one final failing doc-style test if needed**

Add one behavior-doc test that names the final resize rule plainly:

```kotlin
@Test
fun resize_reflows_wrapped_content_from_logical_lines() {
    val buffer = TerminalBuffer(width = 4, height = 2, maxScrollbackLines = 5)

    buffer.writeText("abcdef")
    buffer.resize(newWidth = 6, newHeight = 2)

    assertEquals("abcdef", buffer.getScreenLine(0))
}
```

**Step 2: Run it to verify it fails if still missing**

If already covered, skip adding another duplicate.

**Step 3: Update README**

Document:

- the logical-line model
- reflow-on-resize behavior
- the fact that width changes now reconstruct visual rows from logical content
- any remaining limitations around full Unicode correctness

**Step 4: Run full suite**

Run: `./gradlew test`

Expected: PASS

**Step 5: Refactor pass**

Before finishing, remove transitional helpers from the old row-first design. If a helper exists only to bridge the old design, delete it.

**Step 6: Commit**

```bash
git add README.md src/test/kotlin/terminal/buffer/TerminalBufferBehaviorTest.kt src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/main/kotlin/terminal/buffer/LogicalLine.kt src/main/kotlin/terminal/buffer/VisualRow.kt src/main/kotlin/terminal/buffer/StyledGrapheme.kt
git commit -m "docs: describe logical reflow resize model"
```

## Final verification checklist

- Width grow merges prior wraps when possible
- Width shrink rewraps from logical content, not from frozen rows
- Wide graphemes remain intact through writes, inserts, deletes, backspace, and resize
- Cursor remains attached to logical content during reflow
- Scrollback/history rules are explicit and tested
- CLI `show` still renders ANSI styling correctly after reflow
- No old visual-row-first resize helpers remain if they duplicate the new model
- `./gradlew test` passes cleanly
