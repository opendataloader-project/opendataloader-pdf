# AI / OCR backend guide

When to reach for an AI/OCR backend, how to set one up, how to confirm it is
actually reachable, and the two hazards the backend path hides. This file is
durable procedure; the exact option names, backend names, and values come from the
installed `--help` (SKILL.md "Source-of-truth rule"), not from here.

## ToC
- When to reach for a backend
- Choosing a backend, neutrally
- Setup procedure
- Probe for reachability (don't assume)
- Routing: per-page vs. whole-document
- HAZARD — enrichment needs whole-document routing
- HAZARD — OCR quality depends on setting the document language
- Troubleshooting

## When to reach for a backend

Default to the local path. Reach for a backend only when a *verified* local result
falls short and the content calls for it:

- scanned or image-only pages (OCR needed to get text at all);
- complex tables the local heuristics miss;
- mathematical formulas needing structured (e.g. LaTeX) extraction;
- charts/figures needing generated descriptions;
- documents whose language needs language-specific OCR.

Local-first is not only about speed: the backend sends the PDF to a separate
service (a privacy boundary) and is an extra moving part. Escalate to it only after
a verified local run is insufficient (SKILL.md "Representative workflow", step 6).

## Choosing a backend, neutrally

The installed help lists the available backends. Prefer the neutral, open, local
option unless the user specifically needs another; do not steer a user to a vendor
backend unasked. `--help` does not rank backends by accuracy and neither does this
guide — choose by the control you need (see the OCR-language hazard below), not by
reputation.

## Setup procedure (two processes)

A backend needs a server process and a client that calls it.

1. **Install the backend extras** for your package (the install adds the server
   entry point) — see `installation-matrix.md`.
2. **Start the server, bound to loopback.** It is unauthenticated, so bind it to
   `127.0.0.1` (not `0.0.0.0`) for local use; expose it on a network only behind a
   firewall / reverse-proxy auth and with explicit user consent (SKILL.md "Where
   the human decides"). Confirm the server's own option surface from **its own**
   `--help` — the server is a separate package, so its options are not in the
   client's help.
3. **Point the client at the server.** From the client `--help`, find the option
   that selects the backend, the option that routes pages, and the option that sets
   the server address.
4. **Probe reachability** before trusting a run (next section).

**Remote setup:** run the server on a capable (e.g. GPU) host bound to an explicit
private address, point the client's server-address option at it, and treat the
network hop as the same privacy/security boundary — there is no built-in auth.

## Probe for reachability (don't assume)

A backend option can be *listed* and *accepted* while no server is actually
answering — and if a fallback is in effect, the run then completes on the local
path with a clean exit and none of the backend's OCR/enrichment (hazard A.2 in
`option-interactions.md`). So confirm the endpoint answers before the run:

```bash
# bundled helper: reports reachable / stopped / error for a backend endpoint
# scripts/ resolves against this skill's directory, not your CWD
bash scripts/hybrid-health.sh
```

Reachability confirms only that the endpoint answers — **not** that the OCR engine
or enrichment model is healthy. That is what the post-run VERIFY checks (SKILL.md
"VERIFY").

## Routing: per-page vs. whole-document

Backends typically offer a per-page triage mode (simple pages stay local, complex
pages go to the backend) and a whole-document mode (every page to the backend).
Triage is cheaper for mixed documents; whole-document is needed when every page
must get the backend's treatment — most importantly for enrichment (next), and for
a fully-scanned document that needs uniform OCR. Confirm the exact routing option
and its values in the installed `--help`, and measure throughput on your own
corpus rather than relying on fixed figures.

## HAZARD — enrichment needs whole-document routing

Enrichment features (formula extraction, figure descriptions) run only on pages
that reach the backend. Under per-page triage, pages judged simple stay local and
their enrichment is **silently skipped** — no error, clean exit. To enrich the
whole document, route the whole document to the backend, then VERIFY the enriched
content is actually present. (This is SKILL.md's enrichment-silently-skipped
hazard; see `option-interactions.md` §A.1.)

## HAZARD — OCR quality depends on setting the document language

OCR accuracy depends on telling the engine what language(s) the document is in. If
the language is not set, the engine falls back to its own default language set,
which may not include the document's language — so text comes back wrong or empty,
with no error. **The language-code system is engine-specific**: different OCR
engines expect different code spellings for the same language, and the codes are
*not* interchangeable. Confirm both the language option **and** the exact codes it
expects from the backend's own `--help` for the engine you selected — do not carry
codes over from another engine or from memory. Note that not every backend path
exposes language control at all; where it is not exposed you cannot set it, and OCR
runs with the backend's built-in behavior.

## Troubleshooting

- **Endpoint unreachable** ("connection refused" and the like): the server is not
  running, or the client's server-address option points at the wrong host/port.
  Confirm the server started cleanly and that the address matches on both sides;
  re-probe with `scripts/hybrid-health.sh`.
- **Requests time out:** the backend is slower than the configured timeout. Raise
  it within a bounded limit via the client's timeout option (from `--help`) and
  diagnose backend CPU/GPU load and connectivity; disable the timeout only if you
  control cancellation yourself and accept that a stuck request can hang
  indefinitely.
- **Enrichment missing from output:** the whole-document routing hazard above — the
  most common silent failure. Route the whole document and re-VERIFY.
- **Complex tables still weak under triage:** triage may have classified them
  simple; route the whole document (SKILL.md "DIAGNOSE by symptom" → weak quality).

---

**Cross-references:** SKILL.md "Source-of-truth rule", "VERIFY", "DIAGNOSE by
symptom", "Where the human decides"; `option-interactions.md` (interaction
principles); `installation-matrix.md` (installing the backend extras);
`scripts/hybrid-health.sh`.
