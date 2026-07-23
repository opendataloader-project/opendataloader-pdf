# Installation & prerequisites — procedure

How to install ODL for your integration and satisfy its prerequisites. The exact
runtime **version floors** are not baked here — they change between releases; read
them from the installed package's manifest and its `--help` (SKILL.md
"Source-of-truth rule"). This file is the durable *procedure*; the numbers are the
package's to state.

## ToC
- Choose the install by what you integrate from
- Prerequisites — a Java runtime (all paths)
- Prerequisites — language-binding runtime floors
- Install commands (pip · venv · npm · Maven/Gradle)
- Post-install verification

## Choose the install by what you integrate from

Decide by what you are calling ODL from (and whether you need the backend/OCR
server), not by which runtime happens to already be present — a Java project with
Python installed should still use the Java path.

```
What are you calling ODL from?
├── Java (Maven/Gradle)     → add the Maven/Gradle dependency (below)
├── Node.js                 → install the npm package
├── LangChain / LlamaIndex  → install the framework's ODL loader package
│                             (add the backend extras too if you need OCR/hybrid)
├── Python (direct)         → install the pip package
│                             (use the backend extras if you need OCR/hybrid)
└── Just the CLI            → install the pip package (simplest)
```

The pip and npm packages include the `opendataloader-pdf` CLI automatically; the
Maven/Gradle artifact is a library only.

## Prerequisites — a Java runtime (all paths)

Every path needs a Java runtime: the pip/npm wrappers and the CLI spawn a JVM
internally, and a Java consumer runs the library inside its own JVM. **Do not
assume a specific Java version** — the required floor is declared by the package
(for the Java artifact, its build's compiler target; the wrappers need whatever JVM
the bundled bytecode was compiled for). Read the requirement from the
package/manifest rather than hard-coding a number.

Verify a Java runtime is present and note its version:

```bash
java -version
```

If Java is missing or too old, the failure differs by cause:

- **Not on PATH** → the tool reports that the `java` command was not found.
- **Present but too old** → the run fails with a message that the compiled
  class-file version is newer than the running JVM (a JVM started, but the bytecode
  is newer than it supports). The fix is a newer JDK, not a tool option — no mode or
  OCR flag bypasses it.

Install a JDK meeting the package's declared floor for your OS before proceeding.

## Prerequisites — language-binding runtime floors

Each wrapper declares its own minimum runtime in its manifest, and **enforcement
differs by package manager** (a declared floor is not the same as a hard install
refusal). Read the floor from the manifest; expect:

- **pip** — declares a minimum Python (`requires-python` in the Python package
  manifest) and **refuses** to install on an older Python.
- **npm** — declares a minimum Node (`engines.node` in the Node package manifest);
  advisory by default (a warning), only blocking under strict engine enforcement.
- **Maven/Gradle** — the artifact is compiled to a target Java version; dependency
  resolution does not gate on your runtime JVM, so a too-old JVM surfaces at
  build/run time, not as an install refusal.

Because Java is a runtime (not install-time) requirement on the wrapper/CLI paths,
it fails at use time — which is why the upfront `java -version` check matters.

## Install commands

### pip (Python)

```bash
pip install opendataloader-pdf                    # minimal (includes the CLI)
pip install "opendataloader-pdf[hybrid]"          # adds the OCR/hybrid backend server
```

Install the framework loader package separately if you integrate via
LangChain/LlamaIndex.

### Virtual environments (Python) — recommended

Install into the **environment you will run from**: the CLI shim lands in that
env's `bin/` (`Scripts\` on Windows) and is on PATH only while the env is active.
Run the install *and* every later ODL command in the same activated env, and make
sure the JVM is visible there too. `scripts/detect-env.sh` reports the active env
and flags an externally-managed base interpreter.

venv:

```bash
python3 -m venv .venv
. .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install opendataloader-pdf
```

conda:

```bash
# 3.XX = a Python version opendataloader-pdf supports (see Prerequisites)
conda create -n odl "python=3.XX" && conda activate odl
pip install opendataloader-pdf
```

**Externally-managed environment (PEP 668):** an OS-managed system Python refuses a
bare `pip install`. Use a venv/conda env as above, or — for a CLI-only need —
install it with `pipx` (it manages an isolated env and puts the CLI on PATH).
Prefer these over overriding the system-package protection.

### npm (Node.js)

```bash
npm install @opendataloader/pdf
```

Includes the `opendataloader-pdf` CLI automatically.

### Maven / Gradle (Java)

Add the dependency and pin it to a released version (check the project's releases
page); the artifact is a library (no CLI).

```xml
<dependency>
    <groupId>org.opendataloader</groupId>
    <artifactId>opendataloader-pdf-core</artifactId>
    <version>LATEST</version>
</dependency>
```

```groovy
dependencies {
    implementation 'org.opendataloader:opendataloader-pdf-core:LATEST'
}
```

Replace `LATEST` with the specific version you want to pin (Kotlin DSL:
`implementation("org.opendataloader:opendataloader-pdf-core:LATEST")`).

## Post-install verification

Confirm the CLI resolves on PATH (this also prints the option surface you will
read):

```bash
opendataloader-pdf --help
```

If it is not found, ensure your package manager's bin directory is on PATH. To
check the *installed version*, ask the package manager (`pip show
opendataloader-pdf`, or `npm ls @opendataloader/pdf`) — there is no version flag on
the CLI itself. For Maven, verify the dependency resolves with a build and check
for classpath errors.

---

**Cross-references:** SKILL.md "Source-of-truth rule", "Where the human decides"
(prerequisites are the user's to install); `hybrid-guide.md` (the backend extras);
`integration-examples.md` (per-language code); `scripts/detect-env.sh`.
