# Docling Speed Optimization Plan

## 1. Background

### Current Problem
- **DoclingClient** (docling-serve HTTP API): ~2 seconds per page
- **docling SDK direct call**: ~0.5 seconds per document (user-reported)
- HTTP overhead negates the speed benefits of hybrid mode

### Goal
Implement alternative approaches to efficiently call the docling SDK, then compare benchmark speeds

---

## 2. Experiment Phase (Phase 0)

### Purpose
Validate the speed improvement hypothesis before full implementation

### Experiment Targets
| Approach | Description |
|----------|-------------|
| baseline | Current docling-serve (reference) |
| fastapi | Optimized FastAPI server |
| subprocess | Direct Python subprocess call |

### Success Criteria
| Approach | Threshold | Condition |
|----------|-----------|-----------|
| fastapi | **< 0.8 sec/doc** (average) | Based on 200 documents |
| subprocess | **< 1.0 sec/doc** (average) | Based on 200 documents |

### Failure Conditions
- If fastapi approach exceeds 0.8 sec/doc: **Discard entire plan**
- If only subprocess fails: Exclude that approach only

### Experiment Environment
- Benchmark PDFs: `tests/benchmark/pdfs/` (200 files)
- Settings: `do_ocr=true`, `do_table_structure=true`
- Measurement: `total_time / document_count`

### Experiment Scripts
```
scripts/experiments/
├── docling_baseline_bench.py     # docling-serve speed measurement
├── docling_fastapi_bench.py      # FastAPI server + client test
├── docling_subprocess_bench.py   # subprocess approach test
└── docling_speed_report.py       # Results comparison report
```

### Experiment Execution
```bash
# 1. baseline (requires docling-serve running)
python scripts/experiments/docling_baseline_bench.py

# 2. fastapi (server auto-starts)
python scripts/experiments/docling_fastapi_bench.py

# 3. subprocess
python scripts/experiments/docling_subprocess_bench.py

# 4. compare results
python scripts/experiments/docling_speed_report.py
```

### Results Recording
```
docs/hybrid/experiments/
└── speed-experiment-YYYY-MM-DD.md
```

---

## 3. Implementation Tasks (After Phase 0 Success)

### Task 1: Python Scripts

#### Task 1.1: docling_subprocess_worker.py
| Item | Details |
|------|---------|
| File | `scripts/docling_subprocess_worker.py` |
| Prerequisites | docling package installed |
| Description | stdin JSON → stdout JSON conversion |
| Success Criteria | Single PDF conversion succeeds, JSON output parseable |
| Test | `echo '{"pdf_path":"/path/to.pdf"}' \| python scripts/docling_subprocess_worker.py` |

#### Task 1.2: docling_fast_server.py
| Item | Details |
|------|---------|
| File | `scripts/docling_fast_server.py` |
| Prerequisites | fastapi, uvicorn, docling installed |
| Description | POST /convert endpoint, DocumentConverter singleton |
| Success Criteria | curl PDF upload returns markdown response |
| Test | `curl -F "file=@test.pdf" http://localhost:5002/convert` |

### Task 2: Java Client Implementation

#### Task 2.1: DoclingSubprocessClient.java
| Item | Details |
|------|---------|
| File | `java/.../hybrid/DoclingSubprocessClient.java` |
| Prerequisites | Task 1.1 complete |
| Description | ProcessBuilder executes Python, stdin/stdout JSON |
| Success Criteria | Implements HybridClient interface, single PDF conversion succeeds |
| Test | `DoclingSubprocessClientTest.java` unit tests pass |

#### Task 2.2: DoclingFastServerClient.java
| Item | Details |
|------|---------|
| File | `java/.../hybrid/DoclingFastServerClient.java` |
| Prerequisites | Task 1.2 complete |
| Description | OkHttp calls FastAPI server |
| Success Criteria | Implements HybridClient interface, single PDF conversion succeeds |
| Test | `DoclingFastServerClientTest.java` unit tests pass |

#### Task 2.3: HybridClientFactory Modification
| Item | Details |
|------|---------|
| File | `java/.../hybrid/HybridClientFactory.java` |
| Prerequisites | Task 2.1, 2.2 complete |
| Description | Register `docling-subprocess`, `docling-fast` backends |
| Success Criteria | `HybridClientFactory.getOrCreate("docling-fast", config)` works |
| Test | Extend `HybridClientFactoryTest.java` |

### Task 3: Benchmark Integration

#### Task 3.1: Add pdf_parser Modules
| Item | Details |
|------|---------|
| Files | `tests/benchmark/src/pdf_parser_opendataloader_hybrid_subprocess.py` |
|       | `tests/benchmark/src/pdf_parser_opendataloader_hybrid_fast.py` |
| Prerequisites | Task 2.3 complete, JAR built |
| Success Criteria | Benchmark runs with `--hybrid docling-subprocess` option |

#### Task 3.2: Modify engine_registry.py
| Item | Details |
|------|---------|
| File | `tests/benchmark/src/engine_registry.py` |
| Description | Register new engines |
| Success Criteria | New engines queryable from ENGINE_DISPATCH |

#### Task 3.3: Add run.py CLI Options
| Item | Details |
|------|---------|
| File | `tests/benchmark/run.py` |
| Description | Extend `--hybrid` choices |
| Success Criteria | `./scripts/bench.sh --hybrid docling-fast` runs |

### Task 4: Final Validation

#### Task 4.1: Full Benchmark Execution
| Item | Details |
|------|---------|
| Prerequisites | Task 3 complete |
| Execution | Benchmark 200 documents with all 3 approaches |
| Success Criteria | elapsed_per_doc comparison shows meaningful improvement |

#### Task 4.2: Results Documentation
| Item | Details |
|------|---------|
| File | `docs/hybrid/docling-speed-optimization-results.md` |
| Content | Speed comparison table, recommended approach, usage guide |

---

## 4. Task Workflow

```
Phase 0: Experiment
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                    ┌─────────────────┐
                    │ baseline measure │
                    └────────┬────────┘
                             │
              ┌──────────────┴──────────────┐
              ▼                              ▼
    ┌─────────────────┐           ┌─────────────────┐
    │ fastapi test    │           │ subprocess test │
    └────────┬────────┘           └────────┬────────┘
              │                              │
              └──────────────┬──────────────┘
                             ▼
                    ┌─────────────────┐
                    │ compare results │
                    │ < 0.8 sec/doc?  │
                    └────────┬────────┘
                             │
              ┌──────────────┴──────────────┐
              ▼                              ▼
         [SUCCESS]                       [FAILURE]
       Proceed to                      Discard plan
        Phase 1

Phase 1~4: Implementation (parallelizable)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    Task 1.1 ─────────────────► Task 2.1 ─┐
    (subprocess worker)        (Java client) │
                                            │
    Task 1.2 ─────────────────► Task 2.2 ─┼─► Task 2.3 ─► Task 3 ─► Task 4
    (fastapi server)           (Java client) │   (Factory)   (Bench)  (Validate)
                                            │
              ◄──── parallelizable ────►    │
```

### Parallelizable Tasks
| Group | Tasks | Notes |
|-------|-------|-------|
| Phase 0 | fastapi test, subprocess test | After baseline measurement |
| Phase 1 | Task 1.1, Task 1.2 | Independent |
| Phase 2 | Task 2.1, Task 2.2 | Depend on Task 1.1, 1.2 respectively |
| Phase 3 | Task 3.1, 3.2, 3.3 | After Task 2.3 complete |

### Dependencies
```
Task 1.1 → Task 2.1 ─┐
                      ├─► Task 2.3 → Task 3.* → Task 4.*
Task 1.2 → Task 2.2 ─┘
```

---

## 5. File List

### New Files
| File | Phase | Description |
|------|-------|-------------|
| `scripts/experiments/docling_baseline_bench.py` | 0 | Baseline measurement |
| `scripts/experiments/docling_fastapi_bench.py` | 0 | FastAPI experiment |
| `scripts/experiments/docling_subprocess_bench.py` | 0 | Subprocess experiment |
| `scripts/experiments/docling_speed_report.py` | 0 | Results report |
| `scripts/docling_subprocess_worker.py` | 1 | Subprocess worker |
| `scripts/docling_fast_server.py` | 1 | FastAPI server |
| `java/.../hybrid/DoclingSubprocessClient.java` | 2 | Java client |
| `java/.../hybrid/DoclingFastServerClient.java` | 2 | Java client |
| `tests/.../pdf_parser_opendataloader_hybrid_subprocess.py` | 3 | Benchmark parser |
| `tests/.../pdf_parser_opendataloader_hybrid_fast.py` | 3 | Benchmark parser |

### Modified Files
| File | Phase | Changes |
|------|-------|---------|
| `java/.../hybrid/HybridClientFactory.java` | 2 | Register new backends |
| `tests/benchmark/src/engine_registry.py` | 3 | Register engines |
| `tests/benchmark/run.py` | 3 | CLI options |

---

## 6. Risks and Mitigations

| Risk | Probability | Mitigation |
|------|-------------|------------|
| FastAPI speed below threshold | Medium | Discard plan, explore other approaches |
| subprocess overhead | Medium | Consider process pooling |
| docling SDK version compatibility | Low | Pin version, test |
| Memory exhaustion | Low | Adjust batch size |

---

## 7. Checklist

### Phase 0 Completion Criteria
- [ ] Baseline speed measurement complete
- [ ] FastAPI experiment: < 0.8 sec/doc
- [ ] subprocess experiment: < 1.0 sec/doc
- [ ] Experiment results documented

### Overall Completion Criteria
- [ ] All Tasks complete
- [ ] Benchmark runs successfully with all 3 approaches
- [ ] Speed improvement confirmed (vs baseline)
- [ ] Results documented
