# Project Status

> MVP implementation status and remaining work.

## Completed

- [x] Local Verdict Store — `LocalRiskStore`
- [x] Rule Engine — `RuleBasedJudge`, `rules/`
- [x] On-device Model (MVP) — `OnDeviceModelJudge`
- [x] Local RAG (MVP) — `LocalRagJudge`
- [x] Remote Fallback — `server/`
- [x] Hybrid Flow — `HybridJudge`
- [x] Server Tests — `server/test_main.py`, `server/test_judge_service.py`
- [x] Local Pipeline Tests — `HybridJudgeTest`
- [x] Reference Docs — `reference/README.md`
- [x] Azure Prep Docs — `server/README.md`

## Remaining

- [ ] **Android UI/Shield** — `Verdict`를 실제 경고/차단 UI에 매핑
- [ ] **Azure deployment** — `server/` FastAPI 앱 배포
- [ ] **Dataset expansion** — 검증된 URL 샘플 확장과 라벨 품질 개선
- [ ] **Model runtime upgrade** — Kotlin 내장 centroid 값을 assets JSON, TFLite, ONNX로 전환
- [ ] **Device testing** — 실제 Android 기기에서 end-to-end 확인

## Verification

```bash
./gradlew test
cd server
../.venv/bin/python -m unittest discover .
```

최근 확인:

```text
Gradle Kotlin tests: BUILD SUCCESSFUL
Python server tests: 7 passed
git diff --check: clean
```
