# odl-pdf-maintenance — not part of the installed skill

This folder is the **maintenance kit** for the `odl-pdf` Agent Skill. The skill users
actually install is the sibling [`../odl-pdf/`](../odl-pdf/) — copy only that. Nothing here
is read by the agent at runtime, and end users do not need any of it.

| File | Purpose |
|------|---------|
| `sync-skill-refs.py` | The version-coupling lint (a tripwire) over the skill's agent-facing prose, run by `.github/workflows/skill-drift-check.yml`. Fails on a baked version or ODL option name, or a missing source-of-truth concept — the mechanical floor under the "defer syntax to the installed `--help`" rule. |
| `evals/evals.json` | Decision-correctness eval scenarios + frozen scoring contract; run on demand by a behavioral eval runner. |
| `MAINTAINING.md` | What CI guards, what a human must re-check on an ODL release, and how to change the skill safely. |

It lives inside the ODL repo (not the installed skill) so that changing a CLI option trips
the drift-check on the same PR — shift-left. See `MAINTAINING.md` to develop or update the skill.
