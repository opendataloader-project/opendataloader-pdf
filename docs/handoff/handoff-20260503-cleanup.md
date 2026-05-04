# Handoff — Codebase Cleanup & Branch Consolidation

**Date:** 2026-05-03  
**From:** Plan D implementation session  
**To:** Next agent / developer

---

## Current State

All Plan A–D work is committed and merged into `main`. The codebase is functional and tested. This handoff covers the cleanup work needed before the next feature cycle.

### What's on main

```
9bffa39 chore(mcp): version bump to 0.2.0, add PyPI classifiers and keywords
b0ccd18 chore: Plan D complete — MCP scientific paper understanding v1
4fbd8f7 feat: add graph-json to format enum and regenerate Python/Node bindings
7807598 feat(java): wire GraphJsonWriter into DocumentProcessor for graph-json format
e43cabb refactor(java): null-safe id/page in GraphJsonWriter, expand test coverage
50c8b99 refactor(java): null-safe id/page in GraphJsonWriter, expand test coverage  ← duplicate message, different content (both committed by subagents)
c8dee09 fix(java): filter figures-only CaptionNodes, fix getPageCount() semantics
cac98ea feat(java): add GraphJsonWriter — enriched graph + triage sidecar
...
```

### Branches to clean up

| Branch | Status | Action |
|--------|--------|--------|
| `plan-c-mineru-fallback` | Active worktree at `.claude/worktrees/extraction-reliability-task1` | Remove worktree first, then delete branch |
| `worktree-agent-a6d790bb` | Active worktree at `.claude/worktrees/agent-a6d790bb` (locked) | Remove worktree first, then delete branch |

Both branches' work is already merged into `main`. They can be deleted once their worktrees are removed.

---

## Task 1 — Remove stale worktrees and branches

### Step 1: Check the worktrees

```bash
git worktree list
```

You should see three entries: `main`, `agent-a6d790bb`, and `extraction-reliability-task1`.

### Step 2: Remove the worktrees

```bash
# From the repo root
git worktree remove .claude/worktrees/extraction-reliability-task1 --force
git worktree remove .claude/worktrees/agent-a6d790bb --force
```

The `--force` flag is needed because `agent-a6d790bb` is marked locked.

### Step 3: Delete the stale branches

```bash
git branch -d plan-c-mineru-fallback
git branch -d worktree-agent-a6d790bb
```

If `-d` fails (branch not fully merged from git's perspective), use `-D`:

```bash
git branch -D plan-c-mineru-fallback
git branch -D worktree-agent-a6d790bb
```

### Step 4: Prune remote-tracking stale refs

```bash
git remote prune origin
```

### Step 5: Verify clean state

```bash
git worktree list      # should show only main
git branch -a          # plan-c and worktree-agent branches should be gone
git log --oneline -5   # confirm main is the live branch
```

---

## Task 2 — Code cleanup: stub documentation

The following items are implemented but intentionally stubbed. They are **not bugs** — they are planned extensions documented in `docs/superpowers/OVERVIEW.md` under "What's deferred (Plan E+)". No action needed unless starting Plan E.

| Item | Location | Status |
|------|----------|--------|
| `LlmEnrichmentPass` | `java/.../enrichment/LlmEnrichmentPass.java` | Wired but `NoOpLlmFallback` always returns empty. Stub by design. |
| `FormulaEnrichment` bridge | `java/.../enrichment/FormulaEnrichment.java` | `SemanticFormula.setNumber()` is never called by the enrichment pipeline. `GraphJsonWriter` reads from `EquationNode.getNumber()` directly — so equation numbers appear in `graph-json` output correctly. The IObject-layer `SemanticFormula.getNumber()` path is ready but unused. |
| MinerU remote service | `python/.../mineru.py` | Currently always local subprocess. Remote API support deferred. |

---

## Task 3 — Known cosmetic issues

### Duplicate commit messages

Commits `e43cabb` and `50c8b99` both say `"refactor(java): null-safe id/page in GraphJsonWriter, expand test coverage"` but contain different content (two separate fix passes by subagents). This is harmless — both are on `main` and tests pass. If you want a clean history, squash them:

```bash
# Interactive rebase to squash — only if you have NOT pushed to remote
git rebase -i HEAD~10
# mark e43cabb as 's' (squash) onto 50c8b99
```

**Do not squash if origin/main is already ahead** (i.e., if you pushed these commits).

### pyproject.toml version

The `python/opendataloader-pdf-mcp/pyproject.toml` now has `version = "0.2.0"`. This is committed. The companion package `python/opendataloader-pdf/pyproject.toml` still has its own version — they are versioned independently.

---

## Task 4 — Run the verification checklist before closing

```bash
# From java/opendataloader-pdf-core/
mvn test -q 2>&1 | tail -3

# From python/opendataloader-pdf-mcp/
python -m pytest tests/ -q 2>&1 | tail -3

# MCP stdio handshake
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"0"}}}' \
  | python -m opendataloader_pdf_mcp.server 2>/dev/null \
  | python3 -c "import sys,json; d=json.loads(sys.stdin.readline()); print('OK' if 'result' in d else 'FAIL')"
```

Expected: Java `BUILD SUCCESS`, Python `55 passed`, MCP `OK`.

---

## Reference docs written during Plan D

| Doc | Location | Purpose |
|-----|----------|---------|
| Design spec | `docs/superpowers/specs/2026-05-02-mcp-scientific-paper-v1-design.md` | Full architecture decisions for Plan D |
| Implementation plan | `docs/superpowers/plans/2026-05-02-mcp-scientific-paper-v1.md` | Task-by-task plan with test code |
| Plans A–D overview | `docs/superpowers/OVERVIEW.md` | Summary table, deferred work, architectural invariants |
| Local usage guide | `docs/guides/local-usage-and-publishing.md` | How to use and publish the MCP server |
| README | `python/opendataloader-pdf-mcp/README.md` | End-user documentation |
| CLAUDE.md | `CLAUDE.md` (repo root) | Architecture gotchas for AI agents |
