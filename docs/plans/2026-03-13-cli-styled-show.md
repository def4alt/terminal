# CLI Styled Show Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make the CLI render `show` output with visible per-cell terminal styling so foreground color, background color, bold, italic, and underline are observable in a real terminal.

**Architecture:** Keep `TerminalBuffer` unchanged as the source of truth and add ANSI-aware rendering only in the CLI layer. Introduce a small renderer that walks visible rows cell-by-cell, emits ANSI SGR sequences when attributes change, and resets styles safely at line boundaries so plain-text behavior stays available for tests and non-color contexts.

**Tech Stack:** Kotlin, Gradle, JUnit 5, ANSI escape sequences (no external libraries)

---

### Task 1: Define the CLI rendering contract

**Files:**
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferCliBehaviorTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt`

**Step 1: Write the failing test**

Add behavior tests that assert `show` includes ANSI styling for visible cells and still prints snapshot sections in order.

```kotlin
@Test
fun show_uses_ansi_sequences_for_colored_and_styled_cells() {
    val rendered = runCommands(
        "set-attrs red blue bold underline",
        "write A",
        "show",
    )

    assertTrue(rendered.contains("\u001B["))
    assertTrue(rendered.contains("A"))
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests terminal.buffer.TerminalBufferCliBehaviorTest`

Expected: FAIL because `renderSnapshot()` still uses plain `getScreenContent()` output.

**Step 3: Add narrower regression tests**

Add tests for:
- default attributes producing reset output rather than stale color bleed
- multiple adjacent cells with same attributes not spamming redundant SGR sequences
- style transitions between cells being observable in `show`
- wide graphemes still rendering once, without styling continuation cells separately

**Step 4: Re-run targeted tests**

Run: `./gradlew test --tests terminal.buffer.TerminalBufferCliTest --tests terminal.buffer.TerminalBufferCliBehaviorTest`

Expected: FAIL only in new `show` styling assertions.

**Step 5: Commit**

```bash
git add src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt src/test/kotlin/terminal/buffer/TerminalBufferCliBehaviorTest.kt
git commit -m "test: define ansi show rendering contract"
```

### Task 2: Add an ANSI snapshot renderer for visible rows

**Files:**
- Create: `src/main/kotlin/terminal/buffer/AnsiSnapshotRenderer.kt`
- Modify: `src/main/kotlin/terminal/buffer/TerminalBufferCli.kt`

**Step 1: Write the minimal renderer API**

Create a CLI-only renderer that can format one visible row and the whole snapshot.

```kotlin
internal object AnsiSnapshotRenderer {
    fun renderScreen(buffer: TerminalBuffer): String = TODO()
    fun renderSnapshot(buffer: TerminalBuffer): String = TODO()
}
```

**Step 2: Implement ANSI mapping**

Map `TerminalColor` and `TextStyle` to ANSI SGR codes:
- foreground: 30-37 and 90-97
- background: 40-47 and 100-107
- bold: 1
- italic: 3
- underline: 4
- reset: 0

Use one helper to build a full SGR sequence for a `CellAttributes` value.

**Step 3: Implement row rendering**

Walk visible cells left-to-right and:
- emit text only for `CellKind.GraphemeStart`
- emit blanks for `CellKind.Empty`
- skip `CellKind.Continuation`
- change ANSI state only when the effective attributes change
- emit a final reset at the end of each rendered row

**Step 4: Wire `show` to the renderer**

Update `renderSnapshot()` in `src/main/kotlin/terminal/buffer/TerminalBufferCli.kt` to use ANSI-rendered screen lines while leaving `history`, `screen`, `cursor`, and `attrs` plain unless intentionally expanded later.

**Step 5: Run targeted tests**

Run: `./gradlew test --tests terminal.buffer.TerminalBufferCliTest --tests terminal.buffer.TerminalBufferCliBehaviorTest`

Expected: PASS

**Step 6: Commit**

```bash
git add src/main/kotlin/terminal/buffer/AnsiSnapshotRenderer.kt src/main/kotlin/terminal/buffer/TerminalBufferCli.kt
git commit -m "feat: render cli snapshots with ansi styles"
```

### Task 3: Handle edge cases and fallbacks cleanly

**Files:**
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt`
- Modify: `src/test/kotlin/terminal/buffer/TerminalBufferCliBehaviorTest.kt`
- Modify: `src/main/kotlin/terminal/buffer/AnsiSnapshotRenderer.kt`

**Step 1: Write failing edge-case tests**

Add tests for:
- empty screen rendering with no leaked ANSI state
- mixed attributes across one row
- line endings resetting attributes before `History:` and `Cursor:` labels
- default-colored cells after styled cells not inheriting prior SGR state

**Step 2: Run test to verify failures**

Run: `./gradlew test --tests terminal.buffer.TerminalBufferCliTest --tests terminal.buffer.TerminalBufferCliBehaviorTest`

Expected: FAIL in newly added edge-case assertions.

**Step 3: Tighten renderer implementation**

Implement minimal fixes only:
- explicit reset before section labels if needed
- end-of-line reset even when the line ends styled
- avoid duplicate resets between adjacent blank/default segments

**Step 4: Re-run targeted tests**

Run: `./gradlew test --tests terminal.buffer.TerminalBufferCliTest --tests terminal.buffer.TerminalBufferCliBehaviorTest`

Expected: PASS

**Step 5: Commit**

```bash
git add src/main/kotlin/terminal/buffer/AnsiSnapshotRenderer.kt src/test/kotlin/terminal/buffer/TerminalBufferCliTest.kt src/test/kotlin/terminal/buffer/TerminalBufferCliBehaviorTest.kt
git commit -m "test: cover ansi snapshot edge cases"
```

### Task 4: Document terminal limitations and usage

**Files:**
- Modify: `README.md`

**Step 1: Update CLI documentation**

Document that:
- `show` uses ANSI colors and text styles in terminals that support them
- the project can render bold, italic, and underline, but not control actual font size
- plain text output still exists for `screen` and `history`

Suggested wording:

```md
- `show` renders the visible screen with ANSI colors and styles when the terminal supports them.
- Text styling is limited to terminal capabilities like color, bold, italic, and underline; font size is controlled by the terminal emulator, not this program.
```

**Step 2: Verify docs against behavior**

Run: `./gradlew test`

Expected: PASS

**Step 3: Commit**

```bash
git add README.md
git commit -m "docs: describe ansi cli rendering"
```

### Task 5: Final verification

**Files:**
- Modify: none

**Step 1: Run the full suite**

Run: `./gradlew test`

Expected: PASS

**Step 2: Manually verify in a real terminal**

Run:

```sh
./gradlew --console=plain -q run
```

Then enter:

```text
set-attrs red default bold
write hello
set-cursor 0 1
set-attrs bright_cyan blue underline
write world
show
```

Expected:
- `hello` rendered in red + bold
- `world` rendered in bright cyan on blue + underline
- section labels readable and not inheriting cell styling

**Step 3: Push when ready**

```bash
git push -u origin feat/cli-ansi-show
```
