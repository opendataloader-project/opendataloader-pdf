# /improve-perf [target]

성능 개선을 위한 리서치-구현-검증 루프를 실행합니다.

## 사용법

```
/improve-perf           # 전체 지표 개선
/improve-perf teds      # 테이블 구조 점수 개선
/improve-perf nid       # 읽기 순서 점수 개선
/improve-perf mhs       # 헤딩 구조 점수 개선
/improve-perf table-detection  # 테이블 탐지 정확도 개선
```

## 실행 절차

### 1. 현재 상태 확인
`/bench` 실행하여 현재 점수 확인

### 2. 리서치
- 현재 벤치마크 결과 분석
- 최악 성능 문서들의 공통 패턴 식별
- 경쟁 엔진(docling 등)의 접근법 조사
- 필요시 학술 자료 참고

### 3. 개선 전략 수립
- 구체적인 구현 방향 제안
- 예상 효과 및 리스크 분석
- 사용자 확인 후 진행

### 4. 구현
- Java 코어 수정
- 단위 테스트 작성/수정

### 5. 검증
- `/bench` 재실행
- 결과 비교 및 분석
- `.claude/memory/perf-history.md`에 기록

### 6. 반복
- 목표 달성시까지 또는 사용자 중단시까지 반복
- 각 반복마다 결과 기록

## 참고 자료

### 벤치마크 데이터
- `tests/benchmark/prediction/opendataloader/evaluation.json`
- `tests/benchmark/ground-truth/`

### 코드베이스
- `java/opendataloader-pdf-core/` - 핵심 파싱 로직
- `java/opendataloader-pdf-cli/` - CLI 래퍼

### 경쟁 엔진 참고
- docling: transformer 기반 테이블 감지
- pymupdf4llm: PyMuPDF 기반 추출

## 성능 목표 (현재 vs 목표)

| 지표 | 현재 | 목표 | docling |
|------|------|------|---------|
| NID | 0.91 | 0.93 | 0.90 |
| TEDS | 0.49 | 0.70 | 0.89 |
| MHS | 0.65 | 0.75 | 0.80 |
