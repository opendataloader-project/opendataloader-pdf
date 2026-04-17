## Summary
- Fixed `StackOverflowError` risk in `LevelProcessor` by adding recursion safety across nested list/table traversal.
- Added cycle detection for table recursion paths to prevent infinite mutual recursion.
- Hardened bulleted label regex matching by using precompiled `Pattern` instances instead of repeated `String.matches()`.
- Added regression tests for deeply nested and cyclic table scenarios plus basic bulleted-label behavior.

## Root Cause
- `LevelProcessor.setLevels()` and `setLevelForTable()` recursively called each other without depth/cycle guards.
- Deeply nested table content (or self-referential/cyclic table references) could recurse until stack exhaustion.
- `BulletedParagraphUtils.isLabeledLine()` recompiled regex patterns on each call via `String.matches()`, adding avoidable overhead.

## Solution
- Introduced `MAX_RECURSION_DEPTH` in `LevelProcessor`.
- Threaded `depth` through recursive `setLevels(...)`/`setLevelForTable(...)` calls and stop descent when depth exceeds the max.
- Added identity-based table path tracking (`Set<TableBorder>`) to detect recursion cycles and skip recursive descent when revisiting the same table in the current path.
- Moved `isDocTitleSet` reset into `detectLevels(...)` `finally` block to guarantee state cleanup even on early return/error.
- In `BulletedParagraphUtils`, added precompiled bullet regex map and switched matching to `pattern.matcher(value).matches()`.

## Why this approach
- Minimal and focused: preserves current behavior for normal documents while preventing runaway recursion.
- Depth guard handles extremely deep but acyclic nesting safely.
- Identity-based cycle detection handles malformed/cyclic structures safely.
- Precompiled regex reduces repeated pattern compilation without changing matching rules.

## Tests
- Added `LevelProcessorTest#testDetectLevelsForDeeplyNestedTables`.
- Added `LevelProcessorTest#testDetectLevelsForCyclicTables`.
- Added `BulletedParagraphUtilsTest` basic behavior checks for labeled/unlabeled lines and regex retrieval.
- Attempted targeted run:
  - `mvn -pl opendataloader-pdf-core -Dtest=LevelProcessorTest,BulletedParagraphUtilsTest test`
  - Blocked by external dependency resolution (`org.verapdf:*` metadata unavailable in this environment).

## Backward compatibility
- No public API changes.
- Existing traversal behavior is preserved for normal nesting levels and non-cyclic table structures.

## Risks and mitigations
- Risk: very deep valid nesting beyond the configured max will stop descending.
  - Mitigation: depth threshold is explicit (`MAX_RECURSION_DEPTH`) and warnings are logged for observability.
- Risk: cycle detection could skip pathological recursive references.
  - Mitigation: this is intentional to prevent non-terminating recursion.

## Performance impact
- Positive/neutral:
  - Prevents pathological recursion blowups.
  - Precompiled regexes reduce repeated compile cost in bulleted label checks.

## Checklist
- [x] Reproduced and analyzed recursion call chain in `LevelProcessor`
- [x] Added recursion depth protection
- [x] Added table cycle protection
- [x] Hardened bulleted regex matching with precompiled patterns
- [x] Added regression tests for deep/cyclic table recursion
- [x] Ran targeted tests (blocked by external dependency resolution in environment)
