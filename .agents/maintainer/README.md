# Maintainer files

This folder has the rules and tools of this repository for three kinds of maintenance work.

| Folder | Work | Start with |
|:--|:--|:--|
| [deps/](deps/rules.md) | Upgrade of dependencies | `deps/rules.md` |
| [issue/](issue/rules.md) | Fix of an issue: the harness that decides when a fix is done, its evals, and the rules | `issue/rules.md`, `issue/harness.md` |
| [review/](review/tools.md) | Review of a pull request: the checks of this repository, the path categories, the tools, and the sample rules | `review/profile.json`, `review/tools.md` |

Use these files only for that work, and only when a maintainer asks for it.

For a fix or a review, use the files of the `main` branch. A branch that changes this folder cannot judge itself.

## Evals

```bash
python3 -I -B .agents/maintainer/issue/evals/run.py
python3 -I -B .agents/maintainer/review/evals/run.py
```

If a tool here gives a wrong result, add an eval for that case first. Then change the tool.
