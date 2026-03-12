# Grapheme Display Mitigation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix incorrect display of emoji and other multi-codepoint graphemes by moving the terminal buffer from codepoint-based writing to grapheme-cluster-aware writing and rendering.

**Architecture:** Keep the existing `CellKind.GraphemeStart` and `CellKind.Continuation` model, but change the segmentation, width calculation, and rendering layers so one user-visible grapheme is treated as one logical unit. The buffer should own cluster-aware writing and cursor semantics, while text reconstruction should stop leaking continuation cells as fake spaces. The CLI should remain a thin consumer of the buffer and simply render buffer output correctly.

**Tech Stack:** Kotlin, Gradle, JUnit 5

**Status:** Done

---

## Problem Summary

The current implementation is close structurally, but it is still wrong for real-world emoji and other Unicode grapheme clusters because:

- input is split by Unicode code point instead of grapheme cluster
- width is guessed per code point instead of per grapheme cluster
- `lineToString()` renders `Continuation` cells as visible spaces, which distorts displayed text
- modifier sequences like `👍🏻`, ZWJ emoji sequences, flags, and combined marks can be broken across cells
- cursor and insertion logic currently assume one code point is one logical write unit

That is why a visually incorrect result like `👍🏻 😘  hhei` can appear with strange spacing and broken emoji grouping.

## Repository Deliverables For This Phase

The finished repository should additionally contain:

- A grapheme-cluster-aware buffer write path
- Tests covering emoji modifiers, ZWJ sequences, combining marks, wide CJK characters, and mixed ASCII + emoji content
- Updated rendering semantics so continuation cells do not create fake spaces in reconstructed line text
- Updated README notes describing the current Unicode support level and remaining limitations
- Incremental Conventional Commits separating model work, behavior work, and documentation

## Architecture Principles

- Keep the `CellKind` model; do not add a second competing width model.
- Treat grapheme segmentation as a dedicated responsibility rather than sprinkling Unicode logic across buffer methods.
- Keep rendering logic side-effect free and separate from mutation logic.
- Prefer one small grapheme utility layer over many ad hoc Unicode helpers.
- Let tests define the display semantics before changing internals.
- Avoid fake “visual padding” behavior in `lineToString()`; text reconstruction should reflect user-visible graphemes, not occupied cells.

## Scope And Design Decisions

- Use grapheme clusters as the unit of write and insert operations.
- Store each visible grapheme in one `GraphemeStart(text, displayWidth)` cell and follow it with `Continuation` cells as needed.
- Render `Continuation` cells as empty in reconstructed text rather than as spaces.
- Keep the buffer grid fixed-width in cells; wide graphemes still consume 2 cells.
- For string reconstruction APIs, return user-visible grapheme text joined in logical order, and use blanks only for truly empty cells.
- Start with support for:
  - ASCII and BMP letters
  - CJK wide characters
  - combining marks
  - emoji modifiers
  - ZWJ emoji sequences
  - regional-indicator flag pairs
- If full Unicode grapheme-breaking is too large to hand-roll cleanly, implement a pragmatic cluster segmenter that supports the tested categories first and document the remaining gaps clearly.

## Files To Touch

- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Modify: `src/main/kotlin/terminal/buffer/Cell.kt`
- Modify: `src/main/kotlin/terminal/buffer/CellKind.kt`
- Create: `src/main/kotlin/terminal/buffer/Grapheme.kt`
- Create: `src/main/kotlin/terminal/buffer/GraphemeSegmenter.kt`
- Create: `src/main/kotlin/terminal/buffer/GraphemeWidth.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt`
- Modify: `README.md`

## Target Design

Use a small internal grapheme abstraction:

```kotlin
package terminal.buffer

data class Grapheme(
    val text: String,
    val displayWidth: Int,
)
```

Segment text into `List<Grapheme>` before writing or inserting.

Recommended helpers:

```kotlin
internal fun segmentGraphemes(text: String): List<Grapheme>
internal fun measureDisplayWidth(grapheme: String): Int
internal fun Cell.visibleTextOrBlank(): String
```

The exact file structure can vary, but keep segmentation, width measurement, and buffer mutation separate.

## TDD Rules For This Plan

Apply this exact cycle to every step below:

1. Write one small failing test.
2. Run only that test and verify it fails for the expected reason.
3. Write the minimum production code needed to pass it.
4. Run the focused test again.
5. Run the full test suite.
6. Refactor only while all tests remain green.

Never rewrite multiple Unicode behaviors at once without locking each one in with a failing test first.

### Standard Commands

- Run one test class: `./gradlew test --tests terminal.buffer.TerminalBufferTest`
- Run one named test: `./gradlew test --tests terminal.buffer.TerminalBufferTest.<testName>`
- Run full suite: `./gradlew test`
- Run CLI interactively: `./gradlew --console=plain -q run`

### Task 1: Fix Rendering Of Continuation Cells

**Files:**
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`

**Step 1: Write the failing test**

Add a test proving that a wide grapheme does not render with a fake extra space in the visible line text:

```kotlin
@Test
fun get_screen_line_does_not_render_continuation_cells_as_visible_spaces() {
    val buffer = TerminalBuffer(width = 6, height = 2, maxScrollbackLines = 5)

    buffer.writeText("a界b")

    assertEquals("a界b  ", buffer.getScreenLine(0))
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests terminal.buffer.TerminalBufferTest.get_screen_line_does_not_render_continuation_cells_as_visible_spaces`

Expected: FAIL because the current renderer inserts an extra space for `Continuation`.

**Step 3: Write minimal implementation**

Change line reconstruction so:

- `Empty` contributes a blank cell placeholder
- `GraphemeStart` contributes its text
- `Continuation` contributes nothing

Do this carefully so line width representation remains predictable.

**Step 4: Run test to verify it passes**

Run the named test, then `./gradlew test`

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "fix: stop rendering continuation cells as spaces"
```

### Task 2: Introduce Grapheme Segmentation Abstraction

**Files:**
- Create: `src/main/kotlin/terminal/buffer/Grapheme.kt`
- Create: `src/main/kotlin/terminal/buffer/GraphemeSegmenter.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing tests**

Add one test at a time for the segmenter:

```kotlin
@Test
fun segmenter_keeps_ascii_as_single_codepoint_graphemes()

@Test
fun segmenter_keeps_combining_mark_sequence_as_one_grapheme()

@Test
fun segmenter_keeps_emoji_modifier_sequence_as_one_grapheme()

@Test
fun segmenter_keeps_zwj_emoji_sequence_as_one_grapheme()

@Test
fun segmenter_keeps_flag_sequence_as_one_grapheme()
```

Suggested examples:

- `"a"` → `"a"`
- `"e\u0301"` → one grapheme
- `"👍🏻"` → one grapheme
- `"👨‍👩‍👧‍👦"` → one grapheme
- `"🇵🇱"` → one grapheme

**Step 2: Run each test to verify it fails**

Run each named test individually.

**Step 3: Write minimal implementation**

Implement a `segmentGraphemes(text: String): List<String>` or `List<Grapheme>` helper.

Recommended initial rules:

- base code point starts a new grapheme
- combining marks attach to previous grapheme
- emoji modifier code points attach to previous grapheme
- ZWJ joins adjacent emoji into one grapheme cluster
- regional indicators pair into one flag grapheme

Do not attempt every Unicode boundary rule on the first pass; cover the tested categories clearly.

**Step 4: Run tests to verify they pass**

Run focused tests, then `./gradlew test`

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/Grapheme.kt src/main/kotlin/terminal/buffer/GraphemeSegmenter.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "feat: add grapheme cluster segmentation"
```

### Task 3: Move Width Measurement To Grapheme Level

**Files:**
- Create: `src/main/kotlin/terminal/buffer/GraphemeWidth.kt`
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing tests**

Add one test at a time for grapheme display width:

```kotlin
@Test
fun wide_cjk_grapheme_has_display_width_two()

@Test
fun emoji_modifier_sequence_has_display_width_two()

@Test
fun combining_mark_sequence_has_display_width_one()

@Test
fun flag_sequence_has_display_width_two()
```

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Implement `measureDisplayWidth(grapheme: String): Int` and use it when building `CellKind.GraphemeStart`.

Suggested width rules:

- combining-mark clusters remain width 1 unless the base grapheme is wide
- most emoji clusters should be width 2
- flags are width 2
- CJK ideographs are width 2
- ASCII and Latin-with-combining-marks are width 1

**Step 4: Run tests to verify they pass**

Run focused tests, then `./gradlew test`

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/GraphemeWidth.kt src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "feat: measure display width per grapheme"
```

### Task 4: Rewrite Buffer Writing To Use Grapheme Clusters

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing tests**

Add one test at a time:

```kotlin
@Test
fun write_text_stores_emoji_modifier_sequence_in_one_grapheme_start()

@Test
fun write_text_stores_zwj_sequence_in_one_grapheme_start()

@Test
fun write_text_stores_combining_mark_sequence_in_one_grapheme_start()

@Test
fun cursor_advances_by_grapheme_display_width_not_codepoint_count()
```

Suggested first assertion:

```kotlin
val cell = buffer.getScreenCell(0, 0)
assertEquals(CellKind.GraphemeStart("👍🏻", 2), cell.kind)
assertEquals(CellKind.Continuation, buffer.getScreenCell(1, 0).kind)
```

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Replace codepoint-based `toGraphemes()` with the new grapheme segmenter and width measurer.

Ensure:

- one grapheme cluster writes as one logical unit
- width 2 clusters reserve a continuation cell
- width 1 clusters do not create continuation cells
- if a width 2 grapheme would start in the last column, wrap before writing

**Step 4: Run tests to verify they pass**

Run focused tests, then `./gradlew test`

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "feat: write grapheme clusters as logical units"
```

### Task 5: Rewrite Insert Semantics For Grapheme Integrity

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing tests**

Add one test at a time:

```kotlin
@Test
fun insert_text_keeps_emoji_modifier_sequence_together()

@Test
fun insert_text_keeps_zwj_sequence_together()

@Test
fun insert_before_wide_grapheme_does_not_split_existing_cluster()
```

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Ensure insert operates on grapheme-generated cells and does not split a cluster across partial operations.

If the current flat cell insertion approach becomes too fragile, refactor insert to operate on a temporary grapheme-aware stream of cells rather than per-cell carry state.

**Step 4: Run tests to verify they pass**

Run focused tests, then `./gradlew test`

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "feat: preserve grapheme integrity during insert"
```

### Task 6: Tighten Cursor And Clearing Semantics Around Continuations

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBuffer.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferTest.kt`

**Step 1: Write the failing tests**

Add one test at a time:

```kotlin
@Test
fun move_cursor_right_skips_continuation_cells_for_emoji_clusters()

@Test
fun set_cursor_position_normalizes_from_continuation_to_grapheme_start()

@Test
fun overwrite_on_continuation_clears_the_whole_grapheme()
```

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Keep `normalizeCursor()` and grapheme clearing logic, but make sure they work for multi-codepoint grapheme text and not just single wide code points.

**Step 4: Run tests to verify they pass**

Run focused tests, then `./gradlew test`

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBuffer.kt src/test/kotlin/terminal/buffer/TerminalBufferTest.kt
git commit -m "fix: normalize cursor across grapheme continuation cells"
```

### Task 7: Update CLI Snapshot And Manual Validation Tests

**Files:**
- Modify: `src/main/kotlin/terminal/buffer/TerminalBufferCli.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt`

**Step 1: Write the failing tests**

Add one test at a time:

```kotlin
@Test
fun render_snapshot_displays_emoji_clusters_without_fake_internal_spacing()

@Test
fun cli_write_and_show_preserve_emoji_modifier_sequence_visually()
```

**Step 2: Run each test to verify it fails**

**Step 3: Write minimal implementation**

Adjust CLI formatting only if buffer text reconstruction changes require it.

The CLI should remain thin; avoid solving Unicode problems in the CLI itself.

**Step 4: Run tests to verify they pass**

Run focused tests, then `./gradlew test`

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/TerminalBufferCli.kt src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt
git commit -m "test: cover cli rendering for grapheme clusters"
```

### Task 8: Update README With Unicode Support Notes

**Files:**
- Modify: `README.md`

**Step 1: Verify actual behavior first**

Run:

- `./gradlew test`
- `./gradlew --console=plain -q run`

**Step 2: Write minimal implementation**

Update `README.md` so it describes:

- `CellKind.GraphemeStart` / `Continuation`
- grapheme-cluster-aware writing
- the current Unicode support categories covered by tests
- remaining limitations, if any

**Step 3: Run verification to confirm docs are accurate**

Re-run `./gradlew test`

**Step 4: Commit**

```bash
git add README.md
git commit -m "docs: document grapheme cluster display handling"
```

## Suggested Commit Rhythm

Suggested Conventional Commit sequence:

- `fix: stop rendering continuation cells as spaces`
- `feat: add grapheme cluster segmentation`
- `feat: measure display width per grapheme`
- `feat: write grapheme clusters as logical units`
- `feat: preserve grapheme integrity during insert`
- `fix: normalize cursor across grapheme continuation cells`
- `test: cover cli rendering for grapheme clusters`
- `docs: document grapheme cluster display handling`

## Final Verification Checklist

- `./gradlew test` passes.
- Emoji modifier sequences render as one visible grapheme.
- ZWJ emoji sequences render as one visible grapheme.
- Combining-mark sequences remain attached to their base grapheme.
- Flag sequences render as single logical graphemes.
- Continuation cells do not create fake spaces in visible string reconstruction.
- Cursor movement never lands on continuation cells.
- Insert and overwrite preserve grapheme integrity.
- CLI output reflects the corrected buffer display.
