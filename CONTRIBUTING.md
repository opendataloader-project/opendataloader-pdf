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

1. Check the [README](./README.md) and
   existing [issues](https://github.com/opendataloader-project/opendataloader-pdf/issues) first.
2. If your question hasn't been addressed, open a new issue using the `Question` label.

---

## 🐛 How to Report Bugs

When reporting a bug, please include the following:

- A clear and descriptive title
- Steps to reproduce the issue
- Expected vs actual behavior
- Environment info (OS, version, etc.)
- Logs or screenshots if available

Use the **Bug Report** issue template when creating the issue.

---

## 💡 How to Suggest a Feature

To suggest a new feature:

1. Search existing issues to avoid duplicates.
2. If it's new, open a new issue using the **Feature Request** template.
3. Describe your idea, use cases, and possible alternatives.

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

   **Prerequisites:** Java 11+, Maven, Python 3.10+, uv, Node.js 20+, pnpm
   See the [Development Workflow guide](https://opendataloader.org/docs/development-workflow) for OS-specific install instructions.

   ```bash
   # Build Java packages
   npm run build-java

   # If you changed CLI options in Java, sync bindings (regenerates options.json, Python/Node.js wrappers)
   npm run sync
   ```

   > **Important**: If you modified any CLI options in Java, you **must** run `npm run sync` before committing. This regenerates `options.json` and all Python/Node.js bindings. Forgetting this silently breaks the wrappers.

5. Make your changes and commit them.
6. **Push** your branch:

   ```bash
   git push origin my-feature
   ```

7. **Open a Pull Request** (PR) against the `main` branch.
8. Respond to review comments and update your PR as needed.

---

## 🧹 Coding Style & Guidelines

- Follow existing code conventions.
- Run linters/formatters before committing.
- Write unit tests for any new or changed logic.
- Run `./scripts/bench.sh` before submitting a PR — CI will fail if benchmark scores drop below thresholds.
- Keep your changes minimal and focused.

## ✅ Commit Message Guidelines

Use the following format:

```
<type> <short summary>
```

### Common types:

- Add: New feature
- Fix: Bug fix
- Update: Code update

## 🤖 Agent Skill Maintenance

This repo ships an AI-agent skill under `skills/odl-pdf/`. It is a **version-independent procedure** — it reads the installed CLI's own `--help` at runtime and bakes no option name, value, or default into its prose, so renaming a flag or flipping a default does **not** require touching the skill.

What still needs manual review when you change the CLI:

- **Silent-failure behavior** (e.g. an enrichment that is skipped unless the whole document is routed to the backend; structured output that does not stream to stdout). If you add, remove, or change such behavior, update the hazard principles and the release-review checklist in `skills/odl-pdf-maintenance/MAINTAINING.md`.
- The **version-coupling lint** (`skills/odl-pdf-maintenance/sync-skill-refs.py`, run in CI) fails the build if a version number or an option name is ever baked into the skill. If it fails, fix the skill text — do not add to its allowlist.

The `skills/odl-pdf-maintenance/` directory is developer-only and is **not** part of the installable skill.

---

## 📝 CLA / DCO Requirements

Depending on your contribution, we may ask you to sign:

- CLA – Contributor License Agreement
- DCO – Developer Certificate of Origin

To sign the DCO, add `Signed-off-by` to your commit message:

```
git commit -s -m "your message"
```

Make sure your Git config contains your real name and email.

Thank you again for helping us improve this project! 🙌
If you have any questions, open an issue or join the discussion.
