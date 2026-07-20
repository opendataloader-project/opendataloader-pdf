# Installation Matrix

This guide helps you choose the right installation method for your environment.

## Decision Tree

Decide by **what you are integrating with first**, then by whether you need the
hybrid/OCR server, then by runtime availability — not by which runtime happens to
be installed (a Java project with Python present should still use the Java path).

```
What are you calling ODL from?
├── Java (Maven/Gradle project)  → add the Maven dependency (see below)
├── Node.js                      → npm install @opendataloader/pdf
├── LangChain                    → pip install langchain-opendataloader-pdf
│                                   (also install "opendataloader-pdf[hybrid]" if you need hybrid/OCR)
├── Python (direct use)          → pip install opendataloader-pdf
│                                   (use "opendataloader-pdf[hybrid]" if you need hybrid/OCR)
└── Unsure / just the CLI        → pip install opendataloader-pdf  (simplest; needs Python 3.10+)
```

All paths require **Java 11+** (see Prerequisites); the Node and Python paths also
need their own runtime floor (Node 20.19+, Python 3.10+).

## Prerequisites

**Java 11 or higher is required for all installation methods.** The Python and Node wrappers and the CLI spawn a JVM internally to perform PDF processing; a Java consumer runs the library **inside its own JVM**. Either way a compatible Java runtime is needed. The authoritative current floor is `maven.compiler.source` in `java/pom.xml`; this document is updated when that bumps.

If Java is missing or too old when you run the tool, the symptom depends on the cause:
- **Java not on PATH** → `Error: 'java' command not found. Please ensure Java is installed and in your system's PATH.`
- **Java present but too old** → the run fails (`Error running opendataloader-pdf CLI` / `Return code: 1`) with an `UnsupportedClassVersionError` in the captured output (a JVM ran, but the bytecode is newer than it).

Install a JDK 11+ appropriate for your OS before proceeding. Verify with:

```
java -version
```

**Language-binding runtime floors** are *declared* in each package's manifest. How strictly each package manager enforces them differs — a declaration is not the same as a hard install refusal:

- pip: `requires-python >= 3.10` (per `python/opendataloader-pdf/pyproject.toml`) — pip **refuses** to install on an older Python.
- npm: `engines.node >= 20.19` (per `node/opendataloader-pdf/package.json`) — advisory by default (a **warning**); only blocks with `engine-strict`.
- Maven/Gradle: the artifact is compiled to Java 11 bytecode (`maven.compiler.source=11`); dependency resolution does not gate on your runtime JVM, so a too-old JVM surfaces at build/run time, not as an install refusal.

Java itself is a runtime requirement of the built JAR, so on the wrapper/CLI paths it fails at use time rather than install time — which is why the upfront `java -version` verification above is explicitly called out.

## Quick Start Commands

### pip (Python)

```bash
# Minimal install
pip install opendataloader-pdf

# With hybrid server capability
pip install "opendataloader-pdf[hybrid]"

# LangChain integration
pip install langchain-opendataloader-pdf
```

The `opendataloader-pdf` CLI command is included automatically with the pip install.

### npm (Node.js)

```bash
npm install @opendataloader/pdf
```

The `opendataloader-pdf` CLI command is included automatically with the npm install.

### Maven (Java)

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>org.opendataloader</groupId>
    <artifactId>opendataloader-pdf-core</artifactId>
    <version>LATEST</version>
</dependency>
```

Replace `LATEST` with the specific version you want to pin. Check the [releases page](https://github.com/opendataloader-project/opendataloader-pdf/releases) for available versions.

### Gradle (Java/Kotlin)

Add to your `build.gradle` (Groovy DSL):

```groovy
dependencies {
    implementation 'org.opendataloader:opendataloader-pdf-core:LATEST'
}
```

Or `build.gradle.kts` (Kotlin DSL):

```kotlin
dependencies {
    implementation("org.opendataloader:opendataloader-pdf-core:LATEST")
}
```

Pin `LATEST` to a specific released version from the [releases page](https://github.com/opendataloader-project/opendataloader-pdf/releases).

## Version Compatibility

Minimum runtime requirements are *declared* in each package's manifest. Consult
the manifest for the authoritative current floor:

| Method | Runtime requirement (source of truth) | CLI Included |
|---|---|---|
| pip (all variants) | `python/opendataloader-pdf/pyproject.toml` → `requires-python` | Yes |
| pip langchain | above, plus the LangChain floor declared by `langchain-opendataloader-pdf` | Yes |
| npm | `node/opendataloader-pdf/package.json` → `engines.node` | Yes |
| Maven | `java/pom.xml` → `maven.compiler.source` | No (library only) |

**Enforcement differs by package manager** (a declared floor is not the same as a
hard install refusal): pip **rejects** an incompatible Python version at install;
npm normally only **warns** unless strict engine enforcement (`engine-strict`) is
on; Maven/Gradle may resolve the dependency successfully and surface an incompatible
Java runtime at build or execution time rather than blocking install.

All methods additionally require **Java 11 or higher** at runtime (current
floor declared in `java/pom.xml` `maven.compiler.source`); the pip and npm
wrappers spawn a JVM internally. See Critical Gotcha 1 in `SKILL.md`.

## Post-Install Verification

After installing via pip or npm, confirm the CLI resolves on your `PATH`:

```
opendataloader-pdf --help
```

This prints usage. If the command is not found, ensure your package manager's bin directory is on your `PATH`. **There is no `--version` flag** — to check the installed version, use `pip show opendataloader-pdf` (Python) or `npm ls @opendataloader/pdf` (Node.js).

For Maven, verify the dependency resolves by running a build (`mvn compile`) and checking that no classpath errors are reported.
