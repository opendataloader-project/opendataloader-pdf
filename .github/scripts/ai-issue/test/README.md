# AI Issue Test Runner

AI 이슈 처리 시스템의 테스트를 실행하는 스크립트입니다.

## 사전 요구사항

- `jq` 설치 필요
- `ANTHROPIC_API_KEY` 환경변수 설정 (실제 테스트 실행 시)

## 기본 사용법

```bash
# 테스트 디렉토리로 이동
cd .github/scripts/ai-issue/test

# 실행 권한 부여
chmod +x run-tests.sh

# 모든 테스트 실행
./run-tests.sh
```

## 옵션

| 옵션 | 설명 |
|------|------|
| `--stage=1` | Stage 1 (Triage) 테스트만 실행 |
| `--stage=2` | Stage 2 (Analyze) 테스트만 실행 |
| `--stage=all` | 모든 스테이지 테스트 실행 (기본값) |
| `--case=<name>` | 특정 케이스만 실행 (파일명 패턴 매칭) |
| `--dry-run` | API 호출 없이 프롬프트만 확인 |
| `--verbose` | 상세 출력 (입력/응답 표시) |

## 사용 예시

### 1. API 키 없이 프롬프트 확인 (Dry Run)

```bash
# 모든 테스트의 프롬프트 미리보기
./run-tests.sh --dry-run

# Stage 1만 dry-run
./run-tests.sh --stage=1 --dry-run
```

### 2. Stage별 테스트 실행

```bash
# Stage 1 (Triage) 테스트만 실행
./run-tests.sh --stage=1

# Stage 2 (Analyze) 테스트만 실행
./run-tests.sh --stage=2
```

### 3. 특정 케이스만 테스트

```bash
# 'duplicate' 패턴이 포함된 케이스만 실행
./run-tests.sh --case=duplicate

# Stage 1에서 'valid' 케이스만 실행
./run-tests.sh --stage=1 --case=valid
```

### 4. 디버깅용 상세 출력

```bash
# 입력과 응답을 모두 표시
./run-tests.sh --verbose

# 특정 케이스를 상세하게 테스트
./run-tests.sh --case=bug --verbose
```

### 5. 조합 사용

```bash
# Stage 1의 특정 케이스를 dry-run으로 확인
./run-tests.sh --stage=1 --case=spam --dry-run --verbose
```

## 테스트 케이스 위치

- Stage 1 케이스: `cases/stage1/*.json`
- Stage 2 케이스: `cases/stage2/*.json`
- 픽스처 파일: `fixtures/`

## 종료 코드

- `0`: 모든 테스트 통과
- `N`: N개의 테스트 실패
