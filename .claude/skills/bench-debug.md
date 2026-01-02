# /bench-debug <doc_id>

특정 문서의 파싱 결과를 ground-truth와 상세 비교하여 실패 원인을 분석합니다.

## 사용법

```
/bench-debug 01030000000189
```

## 실행 절차

1. 해당 문서만 벤치마크 실행
   ```bash
   ./scripts/bench.sh --doc-id <doc_id>
   ```

2. 파일 비교
   - Ground-truth: `tests/benchmark/ground-truth/markdown/<doc_id>.md`
   - Prediction: `tests/benchmark/prediction/opendataloader/markdown/<doc_id>.md`
   - 원본 PDF: `tests/benchmark/pdfs/<doc_id>.pdf`

3. 차이점 분석
   - 텍스트 누락/추가 위치
   - 테이블 구조 차이 (TEDS 점수 원인)
   - 헤딩 레벨 불일치 (MHS 점수 원인)
   - 읽기 순서 오류 (NID 점수 원인)

4. 실패 원인 추정
   - 어떤 PDF 요소에서 문제가 발생했는지
   - Java 코어의 어떤 부분이 관련되는지

5. 개선 방향 제안
   - 수정이 필요한 Java 클래스/메서드
   - 예상되는 영향 범위

## 참고 파일

- `ground-truth/reference.json`: 각 문서의 요소별 정보 (카테고리, 좌표 등)
- `java/opendataloader-pdf-core/`: 핵심 파싱 로직

## 예시 출력

```
문서 01030000000189 분석 결과:

Overall: 0.2763 (최악 성능 문서 중 하나)

문제점:
1. 테이블 3개 중 2개 미인식 (TEDS: 0.15)
   - 테이블 경계 감지 실패
   - 관련 코드: TableDetector.java

2. 읽기 순서 오류 (NID: 0.45)
   - 2열 레이아웃 처리 실패
   - 관련 코드: ColumnDetector.java

권장 조치:
- TableDetector의 클러스터링 임계값 조정
- 다중 열 감지 로직 개선
```
