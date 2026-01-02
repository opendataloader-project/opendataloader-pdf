# /bench

Java 빌드 후 전체 벤치마크를 실행하고 결과를 분석합니다.

## 실행 절차

1. `./scripts/bench.sh` 실행
2. `tests/benchmark/prediction/opendataloader/evaluation.json` 읽기
3. 결과 요약 출력:
   - NID (읽기 순서)
   - TEDS (테이블 구조)
   - MHS (헤딩 구조)
   - Table Detection F1/Precision/Recall
4. `.claude/memory/perf-history.md`의 이전 결과와 비교하여 변화량 표시
5. 회귀 발생 시 경고
6. 결과를 `.claude/memory/perf-history.md`에 자동 기록

## 자동 기록 형식

```markdown
### YYYY-MM-DD HH:MM - [commit hash 또는 "uncommitted"]
| 지표 | 값 | 변화 |
|------|-----|------|
| NID | 0.91 | - |
| TEDS | 0.52 | +0.03 |
| MHS | 0.65 | - |
| Table Detection F1 | 0.82 | +0.02 |

**변경 사항**: (git diff --stat 요약)
```

## 옵션

- `/bench --doc-id 01030000000189` - 특정 문서만 실행
- `/bench --no-record` - 히스토리 기록 생략

## 주의사항

- Java 빌드가 실패하면 벤치마크가 실행되지 않습니다
- 첫 실행 시 uv sync로 Python 의존성을 설치합니다
