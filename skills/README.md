# Agent Skills

This directory holds **Agent Skills** — packaged instructions (in the
[agentskills.io](https://agentskills.io) open format: `SKILL.md` + `references/` +
`scripts/`) that let an AI coding assistant use this project correctly without
prior knowledge.

Each skill is a self-contained folder. `SKILL.md` is what the **agent** reads; the
folder's `README.md` explains the skill for **humans** (what it does, how to enable it).

## Available skills

| Skill | What it does |
|-------|--------------|
| [`odl-pdf/`](odl-pdf/README.md) | A durable procedure for using opendataloader-pdf correctly: read the installed tool's own `--help` at runtime to build the minimal command for the user's goal, **verify the result** (a zero exit does not mean success), and diagnose the silent failures the tool does not report. |

## Enabling a skill

Copy the skill folder into your agent's skills location (for Claude Code:
`~/.claude/skills/<name>/` for user scope, or a project's `.claude/skills/`), or
install it via a plugin/marketplace that bundles it. See each skill's own
`README.md` for specifics.
