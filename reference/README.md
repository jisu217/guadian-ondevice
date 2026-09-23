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
