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

### 실행

```bash
./scripts/bench.sh                      # 전체 벤치마크
./scripts/bench.sh --doc-id 01030...    # 특정 문서만
./scripts/bench.sh --check-regression   # 회귀 테스트 포함 (CI용)
```

### 평가 지표

- **NID**: 읽기 순서 정확도 (Normalized Indel Distance)
- **TEDS**: 테이블 구조 정확도 (Tree Edit Distance Similarity)
- **MHS**: 헤딩 구조 정확도 (Markdown Heading Similarity)
- **Table Detection F1**: 테이블 유무 탐지 정확도

### Claude 명령

- `/bench` - 벤치마크 실행 및 결과 분석
- `/bench-debug <doc_id>` - 특정 문서 실패 원인 분석
- `/improve-perf` - 성능 개선 리서치-구현-검증 루프

### 회귀 테스트

PR 머지 전 벤치마크가 자동 실행됩니다.
`tests/benchmark/thresholds.json` 기준 미달 시 CI 실패.

### 벤치마크 데이터

- `tests/benchmark/pdfs/` - 200개 테스트 PDF (Git LFS)
- `tests/benchmark/ground-truth/` - 정답 마크다운
- `tests/benchmark/prediction/` - 예측 결과
