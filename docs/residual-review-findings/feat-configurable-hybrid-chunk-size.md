# Residual Review Findings

Residual actionable findings from the ce-code-review run (20260829-192033-361b1dd3) on PR #710. Not applied in the PR because each needs follow-up work beyond a mechanical fix.

## Residual Review Findings

- [P3] python/opendataloader-pdf/src/opendataloader_pdf/convert_generated.py:149 — Python and Node wrappers silently mask the empty-string blank rejection: the dedicated `--hybrid-chunk-size ""` error is unreachable through the wrapper entry points (truthiness drops the empty string). Filed: [Python/Node wrappers silently drop empty-string hybrid_chunk_size #711](https://github.com/opendataloader-project/opendataloader-pdf/issues/711)
- [P2] verification/ci-verify.py:87 — ci-verify option-coverage gate exempts `--hybrid-chunk-size` without ever exercising it; the gate can stay green while the JVM silently ignores the flag. Already tracked: [test: assert hybrid CLI flags appear in the built JAR --help #709](https://github.com/opendataloader-project/opendataloader-pdf/issues/709)
- [P3] java/opendataloader-pdf-core/.../processors/HybridDocumentProcessor.java:658 — configured chunk-size wiring into the production chunking loop has no direct test; the loop is verified only through a test-side mirror. Already tracked: [test: drive the production hybrid chunking loop with a configured chunk size #706](https://github.com/opendataloader-project/opendataloader-pdf/issues/706)
- [P3] node/opendataloader-pdf/src/convert-options.generated.ts:305 — hybridChunkSize forwarding branches untested despite the existing convert-options.test.ts harness. Already tracked: [test: cover hybridChunkSize forwarding in Node bindings #707](https://github.com/opendataloader-project/opendataloader-pdf/issues/707)
- [P3] java/opendataloader-pdf-core/.../hybrid/HybridConfig.java:39 — default '50' duplicated across four sources with no sync test asserting equality. Already tracked: [test: add sync check for hybrid-chunk-size default across bindings #708](https://github.com/opendataloader-project/opendataloader-pdf/issues/708)

Report-only (not actionable tickets): per-chunk full-PDF re-upload cost scales linearly as the chunk size shrinks (documented in the flag help; pre-existing client semantics with default timeout 0); configured chunk sizes above the former 50-page cap can reintroduce the #352 backend hang (intentional, documented, opt-in); the `--hybrid-chunk-size` parse block intentionally diverges from `--hybrid-timeout` (blank rejection + cause preservation per plan KD3); `--hybrid-chunk-size` invalid value with no positional input exits 0 via the pre-existing help early-return in CLIMain.java:103.

## Run Context

- Run: ce-code-review 20260829-192033-361b1dd3
- Plan: docs/plans/2026-08-29-1859-fix-pr710-coderabbit-findings-plan.md
- Branch: feat/configurable-hybrid-chunk-size
- Artifact: /tmp/compound-engineering-501/ce-code-review/20260829-192033-361b1dd3
