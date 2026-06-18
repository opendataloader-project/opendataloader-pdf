# Agent Skills

opendataloader-pdf ships built-in agent skills that help AI coding assistants use this project effectively. Skills follow the [Agent Skills](https://agentskills.io) open format. This repository is packaged for Claude Code via [`.claude-plugin/marketplace.json`](../.claude-plugin/marketplace.json).

## Directory Structure

```
skills/
├── README.md                          ← You are here
└── odl-pdf/                           ← One skill per directory
    ├── SKILL.md                       ← Main skill file (loaded when activated)
    ├── references/                    ← Deep-dive docs (loaded on demand)
    │   ├── options-matrix.md
    │   ├── hybrid-guide.md
    │   ├── format-guide.md
    │   ├── installation-matrix.md
    │   ├── integration-examples.md
    │   └── eval-metrics.md
    ├── scripts/                       ← Executable helpers
    │   ├── detect-env.sh
    │   ├── hybrid-health.sh
    │   ├── quick-eval.py
    │   └── sync-skill-refs.py
    └── evals/                         ← Quality test cases
        └── evals.json
```

## How Skills Work

### Progressive Disclosure (3 Levels)

| Level | Content | When Loaded |
|-------|---------|-------------|
| **L1** | `description` field in SKILL.md frontmatter | Always visible to skill router |
| **L2** | SKILL.md body — persona, workflows, decision trees, gotchas | When skill is activated |
| **L3** | `references/*` files — detailed option matrices, guides, metrics | When the user enters that topic |

This design minimizes token usage. The AI agent only loads what it needs for the current task.

### Dual-Path Option Reference

Skills must work for **both** source-code users and pip-install users:

- **Built-in summaries** (`references/options-matrix.md`): Always available, even without source code
- **Dynamic reference** (`options.json`): Authoritative source when the source repo is available

SKILL.md instructs the AI: "If `options.json` exists in this project, it is the source of truth. Options in `options.json` not found in `options-matrix.md` are newly added."

## Creating a New Skill

### 1. Create the Directory

```
skills/my-skill/
├── SKILL.md
├── references/       (optional)
├── scripts/          (optional)
└── evals/            (optional)
```

### 2. Write SKILL.md

The SKILL.md file has two parts:

**Frontmatter** (YAML between `---` markers):

```yaml
---
name: my-skill
description: >
  One paragraph (~100 words) explaining what this skill does.
  Include trigger keywords so the skill router knows when to activate.
  Include "Do NOT use for:" to prevent false activations.
---
```

**Body** (Markdown):

- Define a persona (who the AI becomes when this skill is active)
- Define a workflow (numbered phases the AI follows)
- Include decision trees for common choices
- List critical gotchas the AI must always warn about
- Reference deeper docs with: "See `references/filename.md` for details"

### 3. Write Evals

Create `evals/evals.json` with test scenarios:

```json
{
  "version": "1.0",
  "skill": "my-skill",
  "evals": [
    {
      "id": "eval-001",
      "scenario": "Description of the user's situation",
      "user_input": "What the user says",
      "expected_recommendations": ["What the AI should recommend"],
      "must_mention": ["Required terms in the response"],
      "must_not_mention": ["Forbidden terms"]
    }
  ]
}
```

### 4. Register in marketplace.json

Add your skill to `.claude-plugin/marketplace.json`:

```json
{
  "plugins": [{
    "skills": ["./skills/odl-pdf", "./skills/my-skill"]
  }]
}
```

### 5. Test

Test by spawning an AI agent that knows nothing about the project, loading only your SKILL.md, and asking it the eval scenarios. All `must_mention` terms should appear; no `must_not_mention` terms should appear.

## Modifying the Existing Skill

### When CLI Options Change

1. Run `npm run sync` (regenerates `options.json`)
2. Update `skills/odl-pdf/references/options-matrix.md` — add the new option to the appropriate category
3. If the option has interaction rules, document them in the "Interaction Rules" section
4. CI (`skill-drift-check.yml`) will catch any mismatch you miss

### When Adding a New Hybrid Backend

1. Update `skills/odl-pdf/references/hybrid-guide.md` — add to the Backend Registry table
2. SKILL.md's decision tree says "check `options.json` for allowed hybrid values" — new backends are auto-discovered

### When Adding a New Output Format

1. Update `skills/odl-pdf/references/format-guide.md` — add to the format table with downstream use mapping
2. The format list in `options.json` is auto-discovered by the skill

## CI Integration

### Drift Check (`skill-drift-check.yml`)

Runs automatically when `options.json` changes. Compares option names in `options.json` against `options-matrix.md` and fails if they diverge.

Run manually:

```bash
python skills/odl-pdf/scripts/sync-skill-refs.py
```

## Writing Guidelines

- **Language**: English only (external open-source users)
- **No internal terminology**: No company names, team names, or internal tool references
- **Tone**: Senior engineer pair-programming — diagnose first, prescribe later
- **Java guidance**: Always mention Java 11+ requirement. Never recommend specific JDK distributions or download links.
- **Gotchas**: Only include gotchas that affect external users. Internal development gotchas belong in CLAUDE.md.

## References

- [Agent Skills](https://agentskills.io) — Open format spec for agent skills
- [`skills` CLI](https://skills.sh) — CLI that installs Agent Skills (`vercel-labs/skills`); used by the `npx skills add ...` command in the root README's install section
- [Claude Code Skills](https://docs.anthropic.com/en/docs/claude-code) — Claude Code skill documentation
- `.claude-plugin/marketplace.json` — Plugin registration for this project
- `CLAUDE.md` — Internal development notes (not for the skill)
- `CONTRIBUTING.md` — Contributor guidelines including skill maintenance
