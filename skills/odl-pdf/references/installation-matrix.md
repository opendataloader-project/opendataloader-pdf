# Installation Matrix

This guide helps you choose the right installation method for your environment.

## Decision Tree

```
Do you have Python available?
├── Yes
│   ├── Do you need LangChain integration?
│   │   └── Yes → pip install langchain-opendataloader-pdf
│   ├── Do you need hybrid server capability?
│   │   └── Yes → pip install "opendataloader-pdf[hybrid]"
│   └── Otherwise → pip install opendataloader-pdf  (simplest)
├── Node.js only (no Python)?
│   └── npm install @opendataloader/pdf
├── Java project (Maven/Gradle)?
│   └── Add Maven dependency (see below)
└── Unsure?
    └── pip install opendataloader-pdf  (simplest, works on all platforms)
```

## Prerequisites

**Java 11 or higher is required for all installation methods.** All methods spawn a JVM internally to perform PDF processing.

If Java is missing when you run the tool, you will see:

> Java 11 or higher is required. Please install a JDK for your environment.

Install a JDK appropriate for your OS before proceeding. Verify with:

```
java -version
```

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

Minimum runtime requirements are declared in each package's manifest. Consult
the manifest for the authoritative current floor — the wrappers and build tools
enforce it at install time:

| Method | Runtime requirement (source of truth) | CLI Included |
|---|---|---|
| pip (all variants) | `python/opendataloader-pdf/pyproject.toml` → `requires-python` | Yes |
| pip langchain | above, plus the LangChain floor declared by `langchain-opendataloader-pdf` | Yes |
| npm | `node/opendataloader-pdf/package.json` → `engines.node` | Yes |
| Maven | `java/pom.xml` → `maven.compiler.source` | No (library only) |

`pip` / `npm` / `mvn` each validate against the manifest's declared floor and
fail with a clear error if the environment is below it.

All methods additionally require **Java 11 or higher** at runtime; the pip and
npm wrappers spawn a JVM internally. See Critical Gotcha 1 in `SKILL.md`.

## Post-Install Verification

After installing via pip or npm, confirm the CLI is working:

```
opendataloader-pdf --version
```

A successful output shows the installed version number. If the command is not found, ensure your package manager's bin directory is on your `PATH`.

For Maven, verify the dependency resolves by running a build (`mvn compile`) and checking that no classpath errors are reported.
