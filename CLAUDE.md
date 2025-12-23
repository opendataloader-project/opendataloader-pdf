# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

OpenDataLoader PDF is a PDF parsing library for RAG pipelines. Converts PDFs to Markdown, JSON, HTML, and annotated PDF. Java core with Python and Node.js wrappers.

## Build Commands

### Java (Core)

```bash
# Run tests (local development)
./scripts/test-java.sh

# Build with release profile (CI/CD)
./scripts/build-java.sh

# Run a single test class
cd java && mvn test -Dtest=TextLineProcessorTest

# Run a single test method
cd java && mvn test -Dtest=TextLineProcessorTest#testMethodName
```

### Sync Generated Files

After changing CLI options in Java, sync to Python/Node.js:
```bash
npm run sync
```

This exports options from the Java CLI and regenerates `options.json`, `schema.json`, and language-specific bindings.

## Architecture

```
java/opendataloader-pdf-core/    # Core PDF processing engine
java/opendataloader-pdf-cli/     # CLI wrapper (shaded JAR)
python/opendataloader-pdf/       # Python wrapper (subprocess)
node/opendataloader-pdf/         # Node.js wrapper (TypeScript)
```

## Commit Message Format

```
<type>: <short summary>
```

Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`

## Documentation

The `content/docs/` directory contains documentation that is automatically synced to the homepage (opendataloader.org) on release.

## AI Issue Workflow

GitHub Actions automatically process issues: Triage → Analyze → Fix (see `ai-issue-*.yml`).

Manual triggers via issue comment: `@ai-issue analyze` or `@ai-issue fix` (CODEOWNERS only).

Use `/ai-issue` skill locally to process issues.
