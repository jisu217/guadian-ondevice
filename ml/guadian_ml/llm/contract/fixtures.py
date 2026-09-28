"""Write fixtures/llm: the cases core/llm's Kotlin parity tests read.

Each fixture pairs inputs with what this package's contract produces for them, so
the Kotlin ports must reproduce the Python behavior exactly. Inputs are synthetic:
the committed files hold no collected page text.

    prompt_cases.jsonl        observation -> prompt           (PromptBuilderParityTest)
    page_signal_cases.json    DOM readings -> form asks, external action, text (PageSignalsParityTest)
    answer_cases.jsonl        reply -> reading and grounding  (AnswerReaderParityTest)
    chat_cases.json           prompt -> model input via the real tokenizer (ChatFormatParityTest;
                              rewritten only when data/llm/tokenizer is present)

Usage:
    python -m guadian_ml.llm.contract.fixtures
"""

from __future__ import annotations

import json
import sys

from guadian_ml import paths
from guadian_ml.llm.contract import answer, prompt


def observations() -> list[prompt.PageObservation]:
    """Synthetic observations that exercise every line and limit of the format.

    Synthetic so the committed fixture holds no collected page text.
    """
    long_text = "안내 " * 400
    return [
        prompt.PageObservation(url="https://unreachable.example/path", loaded=False),
        prompt.PageObservation(url="https://roblox.com.mu/games/1", loaded=False),
        prompt.PageObservation(url="https://shop.example.com/sale", loaded=True, final_url="https://shop.example.com/sale",
                        status=200, title="여름 세일", text=long_text.strip(), text_chars=4000),
        prompt.PageObservation(url="https://ad.example.net/click?id=1", loaded=True,
                        final_url="https://landing.example.org/", status=200, redirects=2,
                        title="Landing", text="상품 소개 페이지입니다.", text_chars=900),
        prompt.PageObservation(url="https://m.kbstar.com/quics", loaded=True, final_url="https://m.kbstar.com/quics",
                        status=200, title="", text="", text_chars=12),
        prompt.PageObservation(url="https://gone.example/", loaded=True, final_url="https://gone.example/",
                        status=404, title="Not Found", text="Not Found", text_chars=9),
        prompt.PageObservation(url="https://kb-star-bank.top/login", loaded=True, final_url="https://kb-star-bank.top/login",
                        status=200, title="KB국민은행 본인 인증", text="KB 국민은행 고객님 본인 인증이 필요합니다.",
                        text_chars=300,
                        forms=(prompt.FormObservation(("password", "rrn"), True),)),
        prompt.PageObservation(url="https://www.roblox.ly/users/1/profile", loaded=True,
                        final_url="https://www.roblox.ly/users/1/profile", status=200,
                        title="Player - Roblox", text="Player - Roblox Log in to Roblox", text_chars=250),
        prompt.PageObservation(url="https://form.example.com/", loaded=True, final_url="https://form.example.com/",
                        status=200, title="Newsletter", text="이메일을 입력하세요", text_chars=400,
                        forms=(prompt.FormObservation((), False), prompt.FormObservation(("phone",), False))),
        prompt.PageObservation(url="https://apk.example.com/", loaded=True, final_url="https://apk.example.com/",
                        status=200, title="Download", text="앱 설치", text_chars=300,
                        apk_links=("https://apk.example.com/" + "x" * 120 + "/app.apk",)),
        prompt.PageObservation(url="https://알뜰상조.com/?utm=1", loaded=True, final_url="https://xn--ig2bl5pwvd7zd.com/?utm=1",
                        status=200, title="상조", text="상조 서비스", text_chars=500),
        prompt.PageObservation(url="https://long.example.com/" + "a" * 250, loaded=True,
                        final_url="https://long.example.com/", status=200, title="T" * 120, text="내용", text_chars=500),
        prompt.PageObservation(url="http://www.xfinityuserverification.weebly.com/", loaded=True,
                        final_url="http://www.xfinityuserverification.weebly.com/", status=200,
                        title="Xfinity Sign In", text="Xfinity account verification", text_chars=240),
        prompt.PageObservation(url="https://m.naver.com/", loaded=True, final_url="https://m.naver.com/", status=200,
                        title="NAVER", text="네이버 메인 네이버페이", text_chars=5000),
        prompt.PageObservation(url="https://many.example/", loaded=True, final_url="https://many.example/", status=200,
                        title="제휴", text="네이버 카카오 구글 애플 삼성전자 쿠팡 roblox paypal", text_chars=600),
        prompt.PageObservation(url="https://tiny.cc/abc", loaded=True, final_url="https://www.roblox.com.bi/users/2",
                        status=200, redirects=1, title="Roblox", text="Roblox", text_chars=180),
        prompt.PageObservation(url="https://short.example/f", loaded=True, final_url="https://m.facebook.com/login",
                        status=200, redirects=3, title="Facebook", text="Facebook 로그인", text_chars=40),
        prompt.PageObservation(url="https://token.xyz/", loaded=True, final_url="https://token.xyz/", status=200,
                        title="TokenPocket wallet", text="Download TokenPocket and imToken", text_chars=300),
    ]


def page_signal_cases() -> dict:
    """Cases for core/llm PageSignals.kt, computed with the original rules."""
    field_sets = [
        [{"type": "password", "name": "pw"}],
        [{"type": "text", "name": "jumin", "placeholder": "주민등록번호"}],
        [{"type": "text", "placeholder": "주민 등록 번호 앞자리"}],
        [{"type": "text", "id": "RRN"}],
        [{"type": "text", "name": "cardNo", "placeholder": "카드 번호"}, {"type": "text", "name": "cvc"}],
        [{"type": "tel", "name": "x"}],
        [{"type": "text", "placeholder": "휴대폰 번호"}],
        [{"type": "text", "name": "hotel"}],
        [{"type": "text", "name": "tel_no"}],
        [{"type": "text", "placeholder": "계좌번호"}, {"type": "text", "autocomplete": "account-number"}],
        [{"type": "select", "name": "bank"}, {"type": "textarea", "name": "memo"}],
        [{"type": "email", "name": "email", "placeholder": "이메일"}],
        [{"type": "text", "name": "Resident_ID"}],
        [{"type": "text", "name": "tel번호"}],
        [{"type": "text", "name": "rrn_no"}],
        [{"type": "text", "placeholder": "주민\u3000등록번호"}],
        [{"type": "text", "name": "id-rrn"}],
        [{"type": "text", "name": f"f{i}", "placeholder": "phone" if i == 20 else ""} for i in range(21)],
    ]
    actions = [
        ("https://shop.example.com/a", None),
        ("https://shop.example.com/a", "https://shop.example.com/submit"),
        ("https://shop.example.com/a", "https://pay.example.com/submit"),
        ("https://www.shop.co.kr/a", "https://login.shop.co.kr/post"),
        ("https://www.shop.co.kr/a", "https://evil.co.kr/post"),
        ("https://kb-star.top/login", "https://collect.attacker.net/p.php"),
        ("https://a.b.example.org/", "https://example.org/x"),
    ]
    texts = ["  광고\n\n  안내\t\t입니다  ", "a b　c", "", "   ", "line1\r\nline2"]
    return {
        "forms": [{"fields": f, "asks": prompt.form_asks(f)} for f in field_sets],
        "actions": [{"final_url": u, "action": a, "external": prompt.external_action(u, a)} for u, a in actions],
        "texts": [{"raw": t, "collapsed": prompt.collapse_text(t)} for t in texts],
    }


def answer_cases() -> list[dict]:
    prompt = "\n".join([
        "주소: https://kb-star-bank.top/login",
        "페이지: 상태 200, 본문 300자",
        "브랜드 언급: 국민은행(공식 kbstar.com) / 실제 kb-star-bank.top",
        "입력 칸: password, rrn (외부로 전송)",
    ])
    replies = [
        '{"risk": "HIGH", "type": "impersonation", "evidence": "브랜드 언급: 국민은행(공식 kbstar.com) / 실제 kb-star-bank.top"}',
        '{"risk": "HIGH", "type": "credentials", "evidence": "  입력 칸: password, rrn (외부로 전송)  "}',
        '{"risk": "HIGH", "type": "credentials", "evidence": ""}',
        '{"risk": "HIGH", "type": "credentials", "evidence": "본문: 없는 문장"}',
        '{"risk": "HIGH", "type": "urgency", "evidence": "입력 칸: password, rrn (외부로 전송)"}',
        '{"risk": "MEDIUM", "type": "personal_info", "evidence": ""}',
        '{"risk": "LOW", "type": "none", "evidence": ""}',
        '```json\n{"risk": "MEDIUM", "type": "payment", "evidence": "form {x}"}\n``` trailing',
        'noise {not json} then {"risk": "LOW", "type": "none"}',
        '{"risk": "medium", "type": "none"}',
        '{"risk": 2, "type": "none"}',
        '{"risk": "LOW", "type": "phishing"}',
        '{"risk": "UNKNOWN", "type": "none"}',
        '{"risk": "HIGH", "type": "apk", "evidence": "cut off',
        '{"위험도": "중"}',
        "",
        "no json at all",
    ]
    return [{"reply": r, "prompt": prompt, **answer.read(r, prompt)} for r in replies]


def chat_cases(brands: prompt.Brands) -> dict | None:
    """Prompts rendered by the training tokenizer's chat template, or None without a local tokenizer."""
    if not (paths.TOKENIZER / "tokenizer_config.json").exists():
        return None
    from transformers import AutoTokenizer
    tokenizer = AutoTokenizer.from_pretrained(str(paths.TOKENIZER))
    rendered = []
    for obs in observations()[:3]:
        user = prompt.user_message(obs, brands)
        text = tokenizer.apply_chat_template(
            [{"role": "system", "content": prompt.SYSTEM}, {"role": "user", "content": user}],
            tokenize=False, add_generation_prompt=True, enable_thinking=False)
        rendered.append({"user": user, "text": text})
    return {"system": prompt.SYSTEM, "rendered": rendered}


def _write_jsonl(name: str, rows: list[dict]) -> None:
    with open(paths.LLM_FIXTURES / name, "w", encoding="utf-8") as f:
        f.writelines(json.dumps(row, ensure_ascii=False) + "\n" for row in rows)


def _write_json(name: str, value: dict) -> None:
    with open(paths.LLM_FIXTURES / name, "w", encoding="utf-8") as f:
        json.dump(value, f, ensure_ascii=False, indent=1)
        f.write("\n")


def main() -> int:
    brands = prompt.Brands.load()
    paths.LLM_FIXTURES.mkdir(parents=True, exist_ok=True)
    _write_jsonl("prompt_cases.jsonl", [{"observation": obs.to_json(), "prompt": prompt.user_message(obs, brands)}
                                        for obs in observations()])
    _write_json("page_signal_cases.json", page_signal_cases())
    _write_jsonl("answer_cases.jsonl", answer_cases())
    chat = chat_cases(brands)
    if chat is not None:
        _write_json("chat_cases.json", chat)
    print("wrote", paths.LLM_FIXTURES, "" if chat is not None else "(chat_cases.json kept: no local tokenizer)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
