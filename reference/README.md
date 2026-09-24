## Reference Files

이 폴더는 SKT FLY AI 부트캠프 GUADIAN 원본 프로젝트 기반 참조 자료를 보관한다.

현재 프로젝트의 실행 코드는 `android/`, `server/`, `model/`에 있으며, `reference/` 안의 파일은 원본 판정 구조와 정책을 분석하기 위한 자료다.

### Purpose

- 기존 GUADIAN의 위험 판정 기준과 흐름 확인
- 원격 판정, URL/사이트 추출, 위험 등급 문서 참고
- 온디바이스 우선 하이브리드 구조로 재설계할 때 비교 기준으로 사용

### Source Path Mapping

| 현재 경로 | GUADIAN 원본 경로 | 내용 |
| :-- | :-- | :-- |
| `reference/Judge.kt` | `app/src/main/java/com/flyai/adalert/Judge.kt` | 기존 Judge 인터페이스와 Verdict 구조 |
| `reference/HttpJudge.kt` | `app/src/main/java/com/flyai/adalert/HttpJudge.kt` | 기존 원격 판정 API 호출 구조 |
| `reference/WebNode.kt` | `app/src/main/java/com/flyai/adalert/WebNode.kt` | URL, host, site 추출 참고 |
| `reference/judge_agent.py` | `server/judge_agent.py` | 기존 서버 판정 에이전트 |
| `reference/docs/history.md` | `docs/history.md` | 기존 개발 이력 |
| `reference/docs/judge.md` | `docs/judge.md` | 기존 광고 판정 기준 |
| `reference/docs/risk-grade-rationale.md` | `docs/risk-grade-rationale.md` | 위험 등급 판정 근거 |
| `reference/docs/risk-grade-validation.md` | `docs/risk-grade-validation.md` | 위험 등급 검증 방식 |

### Notes

- 이 폴더의 코드는 현재 Gradle/Python 실행 경로에 연결되어 있지 않다.
- 현재 구현을 수정할 때는 `android/app/src/main/kotlin/`, `server/`, `model/` 쪽을 기준으로 본다.
- 원본 문서 내부의 상대 링크는 GUADIAN 원본 프로젝트 기준일 수 있다.

### External / Sibling Repository Sources (2026-09-24 유입)

GUADIAN 원본 앱 저장소가 아니라, 같은 부트캠프에서 나온 **별도 저장소**와 **외부 공개 데이터**에서 가져온 것들이다. 둘 다 git 저장소가 아닌 로컬 압축 해제본이라 커밋 해시 대신 SHA256으로 원본을 특정한다.

| 현재 경로 | 원본 위치 | 내용 | 사용처 |
| :-- | :-- | :-- | :-- |
| `reference/gold_251.jsonl` | 로컬 `guadian23-model-compare-main/gold/gold_251.jsonl` (SHA256 `4b6c184bb58abcb945de40aa7d12fd2212a48a60b2606475b058539bd876be17`, 251 lines) | Claude/Gemini/GPT-5 3사 판정 + 사람 검토로 확정한 실제 광고 도착지 251건 (`case_id`, `url`, `risk`, `type`, `source`, `why`) | `model/data/sample_urls.csv`에 248건 병합 (`reviewed_by=human-review` 31건, `reviewed_by=llm-consensus-claude-gemini-openai` 217건). 깨진 URL 2건(mojibake, host 파싱 불가)과 중복 1건 제외 |

병합에 쓴 매핑 로직은 코드로 남기지 않고 1회성 스크립트로 실행했다 — 재현 필요 시 `reference/gold_251.jsonl` + `model/data/sample_urls.csv`의 `created_at=2026-08-29`, `source=reference-derived` 행들을 대조하면 된다.

**참고만 하고 코드는 그대로 옮기지 않은 것:**

- 로컬 `guadian23-model-compare-main/domains.py:17-20`(`host_of`)와 `:23-33`(`site_of`) — `java.net.URI`가 국제화 도메인(예: `알뜰상조.com`)에서 `host`를 `null`로 반환하는 문제를 진단하는 데 참고했다. `android/app/src/main/kotlin/com/guardian/ondevice/model/UrlFeatureExtractor.kt`의 `parse()` 함수는 이 파일의 코드를 복사한 게 아니라 Python `urllib.parse.urlparse`와 동일하게 동작하도록 새로 짠 것이다.

**GUADIAN 계열이 아닌 외부 공개 데이터:**

| 현재 경로 | 원본 | 내용 |
| :-- | :-- | :-- |
| `model/data/official_domains.txt` | [Tranco](https://tranco-list.eu) `top-1m.csv.zip`, 2026-09-24 다운로드, rank 1–50000만 사용 | 실트래픽 기준 상위 도메인 5만 개 + `android/app/.../rules/OfficialDomains.kt`에 이미 있던 브랜드 도메인(coupang/naver/kakao/kb/shinhan) + 기존 27개 도메인의 합집합 |
