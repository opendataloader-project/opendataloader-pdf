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

## Benchmark

### Running

```bash
./scripts/bench.sh                      # Full benchmark
./scripts/bench.sh --doc-id 01030...    # Specific document only
./scripts/bench.sh --check-regression   # Include regression test (for CI)
```

### Evaluation Metrics

- **NID**: Reading order accuracy (Normalized Indel Distance)
- **TEDS**: Table structure accuracy (Tree Edit Distance Similarity)
- **MHS**: Heading structure accuracy (Markdown Heading Similarity)
- **Table Detection F1**: Table presence detection accuracy

### Claude Commands

- `/bench` - Run benchmark and analyze results
- `/bench-debug <doc_id>` - Analyze failure causes for a specific document

### Regression Testing

Benchmark runs automatically before PR merge.
CI fails if scores fall below thresholds in `tests/benchmark/thresholds.json`.

### Benchmark Data

- `tests/benchmark/pdfs/` - 200 test PDFs (Git LFS)
- `tests/benchmark/ground-truth/` - Ground-truth markdown
- `tests/benchmark/prediction/` - Prediction results
