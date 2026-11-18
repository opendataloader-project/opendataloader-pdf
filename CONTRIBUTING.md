# Contributing to This Project

Thank you for your interest in contributing!  
We welcome contributions from everyone. This document outlines the guidelines for how to contribute effectively and
respectfully.

---

## 📌 Types of Contributions We Welcome

We appreciate various kinds of contributions, including but not limited to:

- 🛠️ **Code contributions** (bug fixes, performance improvements, new features)
- 🐞 **Bug reports**
- 💡 **Feature suggestions**
- ❓ **Questions and discussions**
- 📚 **Improving documentation**

---

## ❓ How to Ask Questions

If you have questions:

1. Check the [README](./README.md), the
   existing [issues](https://github.com/opendataloader-project/opendataloader-pdf/issues), and
   the [discussions](https://github.com/opendataloader-project/opendataloader-pdf/discussions) first.
2. If your question has no answer, ask it
   in [Discussions Q&A](https://github.com/opendataloader-project/opendataloader-pdf/discussions/categories/q-a).

---

## 🐛 How to Report Bugs

Use the **Bug report** issue form. We reproduce each bug before we fix it, so the form asks for:

- What happened, and what you expected
- How you run it (CLI, Python, Node.js, Java, or the MCP server), and the version
- The exact command or code, with all options
- The document, or a description of it if you cannot share it
- Your environment (OS, `java -version`)

Do not attach a document that contains private or confidential data.

To report a security issue, see [SECURITY.md](.github/SECURITY.md). Do not open a public issue for it.

---

## 💡 How to Suggest a Feature

To suggest a new feature:

1. Search the existing issues and discussions to avoid duplicates.
2. Post your idea
   in [Discussions Ideas](https://github.com/opendataloader-project/opendataloader-pdf/discussions/categories/ideas).
   Describe the problem first, then your idea and the alternatives.
3. A new option, a new default, a new output field, or a new dependency needs a decision by the maintainers. Wait for
   that decision before you send a pull request.

---

## 🔧 How to Contribute Code

### Step-by-Step Process

1. **Fork** the repository.
2. **Clone** your fork:

   ```bash
   git clone https://github.com/your-username/opendataloader-pdf.git
   cd opendataloader-pdf
   ```

3. **Create a feature branch:**

   ```bash
   git checkout -b my-feature
   ```

4. **Build** the project:

   **Prerequisites:** Java 17+ (the code targets Java 11, the tests need Java 17, and CI uses Java 21), Maven, Python 3.11+, uv, Node.js 24 (current active LTS), pnpm via `corepack enable pnpm`

   Node 24 and pnpm 11.21.0 are what CI builds against. Enabling Corepack once
   picks the pnpm version up from the `packageManager` field, so there is no
   global install and no version to remember. Node must be >=22.13 — pnpm 11
   refuses to install on anything older.
   See the [Development Workflow guide](https://opendataloader.org/docs/development-workflow) for OS-specific install instructions.

   ```bash
   # Build Java packages
   npm run build-java

   # If you changed CLI options in Java, sync bindings (regenerates options.json, Python/Node.js wrappers)
   npm run sync
   ```

   > **Important**: If you modified any CLI options in Java, you **must** run `npm run sync` before committing. This regenerates `options.json` and all Python/Node.js bindings. Forgetting this silently breaks the wrappers.

5. Make your changes, **test** them, and commit them:

   ```bash
   # Build and test all packages, as CI does
   ./scripts/build-all.sh

   # Or one package: each script builds the package and runs its tests
   ./scripts/build-java.sh
   ./scripts/build-python.sh   # installs the hybrid extra (several GB)
   ./scripts/build-node.sh
   ```
6. **Push** your branch:

   ```bash
   git push origin my-feature
   ```

7. **Open a Pull Request** (PR) against the `main` branch, and fill in the pull request template.
8. Respond to review comments and update your PR as needed (see below).

### After You Open a Pull Request

- CodeRabbit reviews the pull request automatically. Answer each of its comments: change the code, or reply why not.
- If the pull request changes `java/`, `python/`, `node/`, `samples/`, `verification/`, or the build and benchmark
  scripts, CI runs the tests and the benchmark. The `paths` filter in `.github/workflows/test-benchmark.yml` has the
  full list. A change outside it (for example, `scripts/test-node.sh` or only the documentation) does not start them.
- A maintainer reviews the pull request.
- If you are not a maintainer, a bot reminds you when the CLA is not signed after 3 days, or when a CodeRabbit
  comment has no answer after 7 days. After 2 reminders, the pull request gets the `stale` label.
- If a maintainer requests changes and you do not answer, a reminder comes after 30 days, and the `stale` label
  30 days after that. A maintainer can close a pull request that has the `stale` label.

---

## 🧹 Coding Style & Guidelines

- Follow existing code conventions and `.editorconfig`. Each new Java file starts with the header
  in `LICENSE_TEMPLATE/license.txt`.
- CI does not run a linter or a formatter. For Python, use `ruff` and `black` with the settings
  in `python/opendataloader-pdf/pyproject.toml`. For Node.js, run `pnpm lint` in `node/opendataloader-pdf`.
- Write unit tests for any new or changed logic.
- On the same pull requests, CI runs the benchmark (`./scripts/bench.sh`), and fails if the scores drop below the
  thresholds. To run it locally, build the Java package first (`./scripts/build-java.sh`): `bench.sh` uses the JAR
  that is already in `java/opendataloader-pdf-cli/target`. It also downloads the benchmark repository.
- Keep your changes minimal and focused.
- Do not add MDX files. `content/docs/reference/` is generated at release time
  and is not tracked.

## 🔁 Compatibility of Released Options

Users run the CLI and the wrappers from scripts. A removed option or value breaks these scripts on update.

- Do not remove an option, an option value, or a public API that a release has shipped.
- To replace one, keep the old one working, leave it out of the help and the docs, and print a deprecation warning that names the replacement. Do not promise a removal date or version in the warning.
- Remove one only for a reason that outweighs compatibility: a security problem, a wrong or harmful output, or a change that it blocks. Code cleanup, a better name, or a shorter option list is not such a reason. Give the reason in the pull request and in the release notes.

## ✅ Commit Message Guidelines

Use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/#summary):

```
<type>(<scope>): <short summary>
```

For example: `fix(core): look for the next row below the lowest line of a split row`.

- Common types: `feat`, `fix`, `perf`, `refactor`, `test`, `docs`, `chore`, `ci`.
- Use the same format for the pull request title. We merge by squash or by rebase, and a squash merge uses the title
  as the commit message on `main`.

## 🤖 Agent Skill Maintenance

This repo ships an AI-agent skill under `skills/odl-pdf/`. It is a **version-independent procedure** — it reads the installed CLI's own `--help` at runtime and bakes no option name, value, or default into its prose, so renaming a flag or flipping a default does **not** require touching the skill.

What still needs manual review when you change the CLI:

- **Silent-failure behavior** (e.g. an enrichment that is skipped unless the whole document is routed to the backend; structured output that does not stream to stdout). If you add, remove, or change such behavior, update the hazard principles and the release-review checklist in `skills/odl-pdf-maintenance/MAINTAINING.md`.
- The **version-coupling lint** (`skills/odl-pdf-maintenance/sync-skill-refs.py`, run in CI) fails the build if a version number or an option name is ever baked into the skill. If it fails, fix the skill text — do not add to its allowlist.

The `skills/odl-pdf-maintenance/` directory is developer-only and is **not** part of the installable skill.

---

## 📝 CLA / DCO Requirements

- **CLA (Contributor License Agreement):** on your first pull request, a bot asks you to sign the CLA. We can merge
  your pull request only after you sign it.
- **DCO (Developer Certificate of Origin):** to sign the DCO, add `Signed-off-by` to your commit message:

```
git commit -s -m "your message"
```

Make sure your Git config contains your real name and email.

Thank you again for helping us improve this project! 🙌
If you have any questions, ask them
in [Discussions Q&A](https://github.com/opendataloader-project/opendataloader-pdf/discussions/categories/q-a).
