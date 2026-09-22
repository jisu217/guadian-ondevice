# GUADIAN On-device Judge

![Status](https://img.shields.io/badge/status-in%20progress-D4A017?style=flat-square&logo=githubactions&logoColor=white)
![Android](https://img.shields.io/badge/Android-module-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-judge%20module-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Hybrid](https://img.shields.io/badge/strategy-on--device--first%20hybrid-4B5563?style=flat-square&logo=databricks&logoColor=white)

> **문서 출처:** GUADIAN은 SKT FLY AI 부트캠프에서 개발한 악성 광고 기반 디지털 피해 예방 서비스이며, 본 저장소는 기존 GUADIAN 프로젝트의 판정 관련 코드와 문서를 참고해 온디바이스 우선 하이브리드 구조로 재설계하는 연구용 프로젝트다.

GUADIAN On-device Judge는 광고 기반 위험 흐름을 기기 내부에서 먼저 감지하고, 로컬 판단이 불확실한 경우에만 원격 fallback을 사용하는 위험 판정 구조를 설계한다.

---

## Project Direction

이 프로젝트는 모든 판정을 원격 API에 맡기거나, 반대로 모든 판정을 기기 내부에서만 처리하는 구조를 목표로 하지 않는다.

핵심 방향은 **Local Verdict Store, Rule Engine, On-device Model/Local RAG를 우선 사용하고 Remote Fallback을 보조 계층으로 두는 온디바이스 우선 하이브리드 방식**이다.

| 원칙 | 설명 |
| :-- | :-- |
| Local-first | 저장된 판정 결과와 명확한 위험 신호를 기기 내부에서 우선 처리 |
| Low latency | 네트워크 요청 없이 반환 가능한 케이스의 응답 시간 단축 |
| Privacy-aware | 원격으로 전송되는 URL과 도착지 정보의 범위 최소화 |
| Explainable | 위험 사유와 사용자 권장 행동을 함께 반환 |
| Fallback-aware | 로컬 판정이 불확실한 경우에만 remote fallback 사용 |

---

## Judgment Flow

```text
URL Input
  ↓
Local Verdict Store
  ↓ miss
Rule Engine
  ↓ uncertain
On-device Model / Local RAG
  ↓ low confidence
Remote Fallback
  ↓
Verdict
```

| 단계 | 핵심 |
| :-- | :-- |
| Local Verdict Store | Cache |
| Rule Engine | Heuristics |
| On-device Model / Local RAG | Reasoning |
| Remote Fallback | Escalation |

---

## Repository Structure

```text
guadian-ondevice/
  gradlew
  gradlew.bat
  settings.gradle.kts
  build.gradle.kts
  gradle/wrapper/

  android/app/src/main/kotlin/com/guardian/ondevice/
    judge/
      Judge.kt
      Verdict.kt
      HybridJudge.kt
      RuleBasedJudge.kt
      RemoteJudge.kt

    rules/
      RiskRule.kt
      ApkRule.kt
      BrandImpersonationRule.kt
      SuspiciousUrlRule.kt
      OfficialDomains.kt

    store/
      LocalRiskStore.kt

    model/
      OnDeviceModelJudge.kt

    rag/
      LocalRagJudge.kt
      LocalKnowledgeRetriever.kt

  docs/
    architecture.md
    rule-policy.md

  android/app/src/test/kotlin/
    com/guardian/ondevice/judge/
      HybridJudgeTest.kt
      RemoteJudgeTest.kt
      RuleBasedJudgeTest.kt
    com/guardian/ondevice/store/
      LocalRiskStoreTest.kt
    com/guardian/ondevice/rag/
      LocalKnowledgeRetrieverTest.kt
      LocalRagJudgeTest.kt

  server/
    main.py
    judge_service.py
    requirements.txt
    .env.example

  reference/
    Judge.kt
    HttpJudge.kt
    WebNode.kt
    judge_agent.py
    docs/
      history.md
      judge.md
      risk-grade-rationale.md
      risk-grade-validation.md
```

---

## Documentation

| 문서 | 내용 |
| :-- | :-- |
| [`docs/architecture.md`](./docs/architecture.md) | 온디바이스 우선 하이브리드 판정 계층, 흐름, fallback 규칙 |
| [`docs/rule-policy.md`](./docs/rule-policy.md) | 룰 기반 판정 출력, 카테고리, 설계 기준 |

## Test

```bash
./gradlew test
python3 -m unittest discover server
```

## Remote Fallback Server

```bash
cd server
cp .env.example .env
pip install -r requirements.txt
uvicorn main:app --reload
```

## References

`reference/` 디렉터리는 온디바이스 판정 구조를 설계하기 위한 참고 자료를 포함한다.

| 파일 | 용도 |
| :-- | :-- |
| `reference/Judge.kt` | Risk, Verdict, Judge 인터페이스 참고 |
| `reference/HttpJudge.kt` | 원격 판정 API 호출 구조 참고 |
| `reference/WebNode.kt` | URL, host, site 추출 로직 참고 |
| `reference/judge_agent.py` | 브랜드/공식 도메인, 위험 신호 참고 |
| `reference/docs/history.md` | 기존 개발 이력 참고 |
| `reference/docs/judge.md` | 판정 기준 문서 |
| `reference/docs/risk-grade-rationale.md` | 위험 등급 근거와 정책 기준 |
| `reference/docs/risk-grade-validation.md` | 위험 등급 검증 방식 참고 |
