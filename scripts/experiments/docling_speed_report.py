#!/usr/bin/env python3
"""Generate speed comparison report for docling experiments.

Reads results from all experiment runs and generates a summary report.

Usage:
    python scripts/experiments/docling_speed_report.py
"""

import json
import sys
from datetime import datetime
from pathlib import Path

RESULTS_DIR = Path(__file__).parent.parent.parent / "docs" / "hybrid" / "experiments"
REPORT_FILE = RESULTS_DIR / f"speed-experiment-{datetime.now().strftime('%Y-%m-%d')}.md"


def load_results(filename: str) -> dict | None:
    """Load results from JSON file."""
    path = RESULTS_DIR / filename
    if not path.exists():
        return None
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def main():
    """Generate comparison report."""
    print("Loading experiment results...")

    baseline = load_results("baseline_results.json")
    fastapi = load_results("fastapi_results.json")
    subprocess = load_results("subprocess_results.json")

    if not any([baseline, fastapi, subprocess]):
        print("ERROR: No experiment results found", file=sys.stderr)
        sys.exit(1)

    # Print console summary
    print()
    print("=" * 70)
    print("DOCLING SPEED EXPERIMENT RESULTS")
    print("=" * 70)
    print()

    approaches = []
    if baseline:
        approaches.append(("baseline", "docling-serve HTTP", baseline))
    if fastapi:
        approaches.append(("fastapi", "FastAPI + SDK singleton", fastapi))
    if subprocess:
        approaches.append(("subprocess", "Persistent subprocess", subprocess))

    # Table header
    print(f"{'Approach':<15} {'Description':<25} {'Avg (s/doc)':<12} {'Target':<10} {'Status':<10} {'Speedup':<10}")
    print("-" * 70)

    baseline_time = baseline["statistics"]["elapsed_per_doc"] if baseline else None

    for name, desc, data in approaches:
        stats = data["statistics"]
        avg_time = stats["elapsed_per_doc"]

        threshold = data.get("threshold", {})
        target = threshold.get("target", "-")
        passed = threshold.get("passed", None)

        if passed is True:
            status = "PASS"
        elif passed is False:
            status = "FAIL"
        else:
            status = "-"

        # Calculate speedup vs baseline
        if baseline_time and name != "baseline":
            speedup = f"{baseline_time / avg_time:.1f}x"
        else:
            speedup = "-"

        print(f"{name:<15} {desc:<25} {avg_time:<12.3f} {str(target):<10} {status:<10} {speedup:<10}")

    print("-" * 70)
    print()

    # Decision summary
    print("DECISION SUMMARY:")
    print("-" * 40)

    fastapi_passed = fastapi and fastapi.get("threshold", {}).get("passed", False)
    subprocess_passed = subprocess and subprocess.get("threshold", {}).get("passed", False)

    if fastapi_passed:
        print("FastAPI approach:    APPROVED (proceed to Phase 1)")
    else:
        print("FastAPI approach:    REJECTED (plan discarded)")

    if subprocess_passed:
        print("Subprocess approach: APPROVED (proceed to Phase 1)")
    else:
        print("Subprocess approach: REJECTED (excluded from plan)")

    print()

    if fastapi_passed:
        print("OVERALL: Phase 0 PASSED - Proceed to implementation")
        print()

        # Recommendation
        if subprocess_passed:
            fastapi_time = fastapi["statistics"]["elapsed_per_doc"]
            subprocess_time = subprocess["statistics"]["elapsed_per_doc"]
            if subprocess_time < fastapi_time:
                print(f"RECOMMENDATION: subprocess approach is slightly faster ({subprocess_time:.3f}s vs {fastapi_time:.3f}s)")
                print("                However, FastAPI is more production-ready (health checks, easier deployment)")
            else:
                print(f"RECOMMENDATION: FastAPI approach is faster and more production-ready")
    else:
        print("OVERALL: Phase 0 FAILED - Plan should be discarded")

    print("=" * 70)

    # Generate markdown report
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)

    report = []
    report.append("# Docling Speed Experiment Results")
    report.append("")
    report.append(f"**Date**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    report.append("")

    report.append("## Summary")
    report.append("")
    report.append("| Approach | Description | Avg (s/doc) | Target | Status | Speedup |")
    report.append("|----------|-------------|-------------|--------|--------|---------|")

    for name, desc, data in approaches:
        stats = data["statistics"]
        avg_time = stats["elapsed_per_doc"]
        threshold = data.get("threshold", {})
        target = threshold.get("target", "-")
        passed = threshold.get("passed", None)

        if passed is True:
            status = "PASS"
        elif passed is False:
            status = "FAIL"
        else:
            status = "-"

        if baseline_time and name != "baseline":
            speedup = f"{baseline_time / avg_time:.1f}x"
        else:
            speedup = "-"

        report.append(f"| {name} | {desc} | {avg_time:.3f} | {target} | {status} | {speedup} |")

    report.append("")
    report.append("## Decision")
    report.append("")

    if fastapi_passed:
        report.append("**Phase 0 PASSED** - FastAPI approach meets the < 0.8s threshold.")
        report.append("")
        report.append("Proceed to Phase 1 implementation:")
        report.append("")
        report.append("- [ ] Task 1.1: docling_subprocess_worker.py")
        report.append("- [ ] Task 1.2: docling_fast_server.py")
        report.append("- [ ] Task 2.1: DoclingSubprocessClient.java")
        report.append("- [ ] Task 2.2: DoclingFastServerClient.java")
        report.append("- [ ] Task 2.3: HybridClientFactory modification")
        report.append("- [ ] Task 3: Benchmark integration")
        report.append("- [ ] Task 4: Final validation")

        if subprocess_passed:
            report.append("")
            report.append("Subprocess approach also passed - both approaches available for implementation.")
    else:
        report.append("**Phase 0 FAILED** - FastAPI approach exceeds 0.8s threshold.")
        report.append("")
        report.append("Plan should be discarded. Consider alternative approaches.")

    report.append("")
    report.append("## Detailed Statistics")
    report.append("")

    for name, desc, data in approaches:
        stats = data["statistics"]
        report.append(f"### {name.title()}")
        report.append("")
        report.append(f"- **Description**: {data['description']}")
        report.append(f"- **Timestamp**: {data['timestamp']}")
        report.append(f"- **Total documents**: {stats['total_documents']}")
        report.append(f"- **Successful**: {stats['successful']}")
        report.append(f"- **Failed**: {stats['failed']}")
        report.append(f"- **Total elapsed**: {stats['total_elapsed']:.1f}s")
        report.append(f"- **Average per doc**: {stats['elapsed_per_doc']:.4f}s")
        report.append(f"- **Min**: {stats['min_elapsed']:.4f}s")
        report.append(f"- **Max**: {stats['max_elapsed']:.4f}s")
        report.append("")

    # Write report
    with open(REPORT_FILE, "w", encoding="utf-8") as f:
        f.write("\n".join(report))

    print(f"\nReport saved to: {REPORT_FILE}")


if __name__ == "__main__":
    main()
