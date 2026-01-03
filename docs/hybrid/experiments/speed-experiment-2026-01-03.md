# Docling Speed Experiment Results

**Date**: 2026-01-03 14:31:43

## Summary

| Approach | Description | Avg (s/doc) | Target | Status | Speedup |
|----------|-------------|-------------|--------|--------|---------|
| baseline | docling-serve HTTP | 2.283 | - | - | - |
| fastapi | FastAPI + SDK singleton | 0.685 | 0.8 | PASS | 3.3x |
| subprocess | Persistent subprocess | 0.661 | 1.0 | PASS | 3.5x |

## Decision

**Phase 0 PASSED** - FastAPI approach meets the < 0.8s threshold.

Proceed to Phase 1 implementation:

- [ ] Task 1.1: docling_subprocess_worker.py
- [ ] Task 1.2: docling_fast_server.py
- [ ] Task 2.1: DoclingSubprocessClient.java
- [ ] Task 2.2: DoclingFastServerClient.java
- [ ] Task 2.3: HybridClientFactory modification
- [ ] Task 3: Benchmark integration
- [ ] Task 4: Final validation

Subprocess approach also passed - both approaches available for implementation.

## Detailed Statistics

### Baseline

- **Description**: docling-serve HTTP API
- **Timestamp**: 2026-01-03 14:23:41
- **Total documents**: 200
- **Successful**: 200
- **Failed**: 0
- **Total elapsed**: 456.6s
- **Average per doc**: 2.2825s
- **Min**: 2.0045s
- **Max**: 8.0182s

### Fastapi

- **Description**: FastAPI server with docling SDK singleton
- **Timestamp**: 2026-01-03 14:27:18
- **Total documents**: 200
- **Successful**: 200
- **Failed**: 0
- **Total elapsed**: 137.1s
- **Average per doc**: 0.6855s
- **Min**: 0.1912s
- **Max**: 4.2420s

### Subprocess

- **Description**: Persistent Python subprocess with docling SDK
- **Timestamp**: 2026-01-03 14:30:50
- **Total documents**: 200
- **Successful**: 200
- **Failed**: 0
- **Total elapsed**: 132.4s
- **Average per doc**: 0.6612s
- **Min**: 0.1908s
- **Max**: 4.2498s
