#!/usr/bin/env python3
"""
Docling Page Range Benchmark

페이지 범위별 변환 성능 비교:
- 25%, 50%, 75%, 100% 페이지 시나리오
- 각 시나리오별 최적 청크 크기 탐색

워밍업 후 여러 번 실행하여 평균 측정
결과는 JSON으로 저장
"""

import json
import time
import random
from pathlib import Path
from dataclasses import dataclass, asdict
from datetime import datetime

from docling.document_converter import DocumentConverter, PdfFormatOption
from docling.datamodel.base_models import InputFormat
from docling.datamodel.pipeline_options import PdfPipelineOptions


WARMUP_RUNS = 1
MEASURE_RUNS = 3
RANDOM_SEED = 42


@dataclass
class BenchmarkResult:
    name: str
    avg_time: float
    std_time: float
    times: list[float]
    chunk_size: int
    num_chunks: int


def get_project_root() -> Path:
    """프로젝트 루트 디렉토리 반환"""
    return Path(__file__).parent.parent.parent


def create_converter() -> DocumentConverter:
    """DocumentConverter 인스턴스 생성"""
    pipeline_options = PdfPipelineOptions()
    return DocumentConverter(
        format_options={
            InputFormat.PDF: PdfFormatOption(pipeline_options=pipeline_options)
        }
    )


def convert_with_page_range(
    converter: DocumentConverter, pdf_path: Path, start: int, end: int
) -> float:
    """지정된 페이지 범위로 변환하고 소요 시간 반환"""
    start_time = time.perf_counter()
    converter.convert(pdf_path, page_range=(start, end))
    return time.perf_counter() - start_time


def pages_to_ranges(pages: list[int]) -> list[tuple[int, int]]:
    """페이지 리스트를 연속 범위로 변환"""
    if not pages:
        return []

    pages = sorted(pages)
    ranges = []
    start = pages[0]
    end = pages[0]

    for p in pages[1:]:
        if p == end + 1:
            end = p
        else:
            ranges.append((start, end))
            start = p
            end = p
    ranges.append((start, end))
    return ranges


def run_benchmark_for_ranges(
    pdf_path: Path,
    ranges: list[tuple[int, int]],
    name: str,
) -> BenchmarkResult:
    """주어진 범위들에 대해 벤치마크 실행"""

    def run_once():
        converter = create_converter()
        total_time = 0.0
        for start, end in ranges:
            total_time += convert_with_page_range(converter, pdf_path, start, end)
        return total_time

    # 워밍업
    for _ in range(WARMUP_RUNS):
        run_once()

    # 측정
    times = []
    for _ in range(MEASURE_RUNS):
        times.append(run_once())

    avg_time = sum(times) / len(times)
    std_time = (sum((t - avg_time) ** 2 for t in times) / len(times)) ** 0.5

    return BenchmarkResult(
        name=name,
        avg_time=avg_time,
        std_time=std_time,
        times=times,
        chunk_size=0,
        num_chunks=len(ranges),
    )


def get_chunks_for_pages(
    target_pages: list[int], chunk_size: int, total_pages: int
) -> list[tuple[int, int]]:
    """타겟 페이지들을 청크 크기로 그룹화"""
    chunks = []
    for page in target_pages:
        chunk_start = ((page - 1) // chunk_size) * chunk_size + 1
        chunk_end = min(chunk_start + chunk_size - 1, total_pages)
        if (chunk_start, chunk_end) not in chunks:
            chunks.append((chunk_start, chunk_end))
    return chunks


def run_scenario_benchmark(
    pdf_path: Path,
    total_pages: int,
    target_pages: list[int],
    chunk_sizes: list[int],
    scenario_name: str,
) -> dict:
    """단일 시나리오 벤치마크 실행"""
    print(f"\n{'='*60}")
    print(f"Scenario: {scenario_name}")
    print(f"{'='*60}")
    print(f"Target pages ({len(target_pages)}): {target_pages}")
    print()

    results = []
    scenario_data = {
        "scenario": scenario_name,
        "total_pages": total_pages,
        "target_pages": target_pages,
        "target_page_count": len(target_pages),
        "percentage": round(len(target_pages) / total_pages * 100, 1),
        "results": [],
    }

    # 1. 연속 범위 최적화
    optimized_ranges = pages_to_ranges(target_pages)
    print(f"[1] Optimized ranges: {optimized_ranges} ({len(optimized_ranges)} ranges)")

    opt_result = run_benchmark_for_ranges(pdf_path, optimized_ranges, "Optimized ranges")
    results.append(opt_result)
    print(f"    Avg: {opt_result.avg_time:.2f}s (±{opt_result.std_time:.2f}s)")

    scenario_data["results"].append({
        "method": "optimized_ranges",
        "ranges": optimized_ranges,
        "num_chunks": len(optimized_ranges),
        "avg_time": round(opt_result.avg_time, 3),
        "std_time": round(opt_result.std_time, 3),
        "times": [round(t, 3) for t in opt_result.times],
        "overhead_pct": 0.0,
    })

    # 2. 각 청크 크기별 테스트
    for chunk_size in chunk_sizes:
        chunks = get_chunks_for_pages(target_pages, chunk_size, total_pages)
        print(f"[{len(results) + 1}] {chunk_size} page(s)/chunk ({len(chunks)} chunks)")

        result = run_benchmark_for_ranges(pdf_path, chunks, f"{chunk_size} page(s)/chunk")
        result.chunk_size = chunk_size
        results.append(result)

        overhead_pct = ((result.avg_time - opt_result.avg_time) / opt_result.avg_time) * 100
        print(f"    Avg: {result.avg_time:.2f}s (±{result.std_time:.2f}s) [{overhead_pct:+.1f}%]")

        scenario_data["results"].append({
            "method": f"chunk_{chunk_size}",
            "chunk_size": chunk_size,
            "chunks": chunks,
            "num_chunks": len(chunks),
            "avg_time": round(result.avg_time, 3),
            "std_time": round(result.std_time, 3),
            "times": [round(t, 3) for t in result.times],
            "overhead_pct": round(overhead_pct, 1),
        })

    # Best 찾기
    best_result = min(results, key=lambda r: r.avg_time)
    scenario_data["best_method"] = best_result.name
    scenario_data["best_time"] = round(best_result.avg_time, 3)

    print()
    print(f"  >> Best: {best_result.name} ({best_result.avg_time:.2f}s)")

    return scenario_data


def main():
    project_root = get_project_root()
    pdf_path = project_root / "samples" / "pdf" / "1901.03003.pdf"

    if not pdf_path.exists():
        print(f"Error: PDF not found at {pdf_path}")
        return 1

    total_pages = 15
    chunk_sizes = [1, 2, 3, 5]
    percentages = [25, 50, 75, 100]

    print("=" * 60)
    print("Docling Page Range Benchmark - Multi Scenario")
    print("=" * 60)
    print(f"PDF: {pdf_path.name} ({total_pages} pages)")
    print(f"Warmup: {WARMUP_RUNS} run(s), Measure: {MEASURE_RUNS} run(s)")
    print(f"Chunk sizes: {chunk_sizes}")
    print(f"Scenarios: {percentages}%")

    random.seed(RANDOM_SEED)

    report = {
        "metadata": {
            "pdf_file": pdf_path.name,
            "total_pages": total_pages,
            "warmup_runs": WARMUP_RUNS,
            "measure_runs": MEASURE_RUNS,
            "chunk_sizes": chunk_sizes,
            "random_seed": RANDOM_SEED,
            "timestamp": datetime.now().isoformat(),
        },
        "scenarios": [],
        "summary": {},
    }

    for pct in percentages:
        num_pages = max(1, total_pages * pct // 100)

        if pct == 100:
            target_pages = list(range(1, total_pages + 1))
        else:
            target_pages = sorted(random.sample(range(1, total_pages + 1), num_pages))

        scenario_data = run_scenario_benchmark(
            pdf_path,
            total_pages,
            target_pages,
            chunk_sizes,
            f"{pct}% pages",
        )
        report["scenarios"].append(scenario_data)

    # Summary 생성
    print("\n" + "=" * 60)
    print("SUMMARY")
    print("=" * 60)
    print(f"{'Scenario':<15} {'Best Method':<20} {'Time':>8} {'Chunks':>8}")
    print("-" * 60)

    for scenario in report["scenarios"]:
        best = min(scenario["results"], key=lambda r: r["avg_time"])
        print(f"{scenario['scenario']:<15} {best['method']:<20} {best['avg_time']:>7.2f}s {best['num_chunks']:>7}")
        report["summary"][scenario["scenario"]] = {
            "best_method": best["method"],
            "best_time": best["avg_time"],
            "best_chunks": best["num_chunks"],
        }

    # JSON 저장
    output_path = project_root / "tests" / "docling_chunking_strategy" / "docling_benchmark_report.json"
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)

    print()
    print(f"Report saved to: {output_path}")

    return 0


if __name__ == "__main__":
    exit(main())
