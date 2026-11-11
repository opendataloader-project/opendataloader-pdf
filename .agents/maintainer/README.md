# Maintainer files

This folder has the rules and tools of this repository for three kinds of maintenance work. The tools of the maintainers read these files from the `main` branch. A change in this folder has an effect only after it is on `main`.

| Folder | Work | Start with |
|:--|:--|:--|
| [deps/](deps/rules.md) | Upgrade of dependencies | `deps/rules.md` |
| [issue/](issue/rules.md) | Fix of an issue: the harness that decides when a fix is done, its evals, and the rules | `issue/rules.md`, `issue/harness.md` |
| [review/](review/tools.md) | Review of a pull request: the checks of this repository, the path categories, the tools, and the sample rules | `review/profile.json`, `review/tools.md` |

Use these files only for that work, and only when a maintainer asks for it.

## Evals

```bash
python3 -I -B .agents/maintainer/issue/evals/run.py
python3 -I -B .agents/maintainer/review/evals/run.py --engine <folder of the review tools>
```

The second command needs the generic review tools of the maintainers (`--engine`). Those tools are not in this repository.

If a tool here gives a wrong result, add an eval for that case first. Then change the tool.
