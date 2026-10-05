"""Write fixtures/llm: the cases the Kotlin parity tests in core/llm and core/judge read.

Each fixture pairs inputs with what this package's contract produces for them, so
the Kotlin ports must reproduce the Python behavior exactly. Inputs are synthetic:
the committed files hold no collected page text.

    prompt_cases.jsonl        observation -> prompt           (PromptBuilderParityTest)
    page_signal_cases.json    DOM readings -> form asks, external action, text, and
                              page_inspection.js results -> observation (PageSignalsParityTest)
    answer_cases.jsonl        reply -> reading and grounding  (AnswerReaderParityTest)
    verdict_cases.json        facts -> verdict table decision (core/judge VerdictTableParityTest)
    chat_cases.json           prompt -> model input via the real tokenizer (ChatFormatParityTest;
                              rewritten only when data/llm/cache/tokenizer is present)

Usage:
    python -m guadian_ml.llm.contract.fixtures
"""

from __future__ import annotations

import dataclasses
import json
import random
import sys

from guadian_ml import paths
from guadian_ml.llm.contract import answer, prompt, verdict_table


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
        prompt.PageObservation(url="https://lead.example.com/", loaded=True, final_url="https://lead.example.com/",
                        status=200, title="자료 받기", text="자료를 받으려면 입력하세요", text_chars=400,
                        forms=(prompt.FormObservation(("search",), False),
                               prompt.FormObservation(("email", "name", "phone"), False))),
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
        prompt.PageObservation(url="https://phish.example.com/h/google.html", loaded=True,
                        final_url="blob:https://phish.example.com/6fb6-1f8c#", status=200, redirects=1,
                        title="Sign in - Google Accounts", text="Sign in - Google Accounts", text_chars=25),
        prompt.PageObservation(url="https://token.xyz/", loaded=True, final_url="https://token.xyz/", status=200,
                        title="TokenPocket wallet", text="Download TokenPocket and imToken", text_chars=300),
    ]


def page_signal_cases() -> dict:
    """Cases for core/llm PageSignals.kt, computed with the original rules and the added field kinds."""
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
        # field kinds added after the original collector: first 12 fields, labels cut to 30
        [{"type": "text", "name": "EMAIL_addr"}, {"type": "text", "name": "user_name"}],
        [{"type": "text", "name": "username"}, {"type": "text", "name": "nickname"}],
        [{"type": "text", "name": "firstName"}, {"type": "text", "placeholder": "성명"}],
        [{"type": "search", "name": "x"}, {"type": "text", "name": "q"}, {"type": "text", "name": "query1"}],
        [{"type": "text", "name": "umobile"}, {"type": "text", "placeholder": "핸드폰 번호"}],
        [{"type": "text", "name": "seq"}, {"type": "text", "name": "faq"}],
        [{"type": "text", "name": "a" * 26, "id": "email"}],
        [{"type": "text", "placeholder": "\U0001F600" * 29 + "email"}],
        [{"type": "text", "name": f"f{i}", "placeholder": "이메일" if i == 12 else ""} for i in range(13)],
        [{"type": "text", "name": "\u212Aname"}, {"type": "text", "name": "\u017Fearch"}],
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

    def field(type_, shown=True, **attrs):
        return {"type": type_, "name": None, "id": None, "placeholder": None, "autocomplete": None,
                **attrs, "shown": shown}

    inspections = [
        ("https://board.example.com/", {
            "title": "  게시판  ", "text": "  레저\n체험  ",
            "forms": [{"action": None, "fields": [field("password", False, name="secret_pw_input"),
                                                  field("button", False)]},
                      {"action": None, "fields": []},
                      {"action": "https://board.example.com/s", "fields": [field("text", name="q")]}]}),
        ("https://lead.example.com/", {
            "title": "T" * 250, "text": "상담 신청",
            "forms": [{"action": "https://crm.other.net/post", "fields": [
                field("text", name="user_name"), field("tel", name="phone"),
                field("email", False, name="email"), field("hidden", False, name="token")]}]}),
        ("https://apk.example.com/", {
            "title": "", "text": "",
            "forms": [{"action": None, "fields": [{"type": "text", "name": "id"}, {"type": "password", "name": "pw"}]}],
            "apkAnchors": ["https://apk.example.com/a.apk", "https://apk.example.com/" + "x" * 130 + ".apk"],
            "apkInHtml": ["https://apk.example.com/a.apk", "https://b.example.com/b.apk", "https://c.example.com/c.apk",
                          "https://d.example.com/d.apk", "https://e.example.com/e.apk"]}),
        ("https://forms.example.com/", {
            "title": "많은 폼", "text": "\U0001F600" * 2600,
            "forms": [{"action": None, "fields": [field("text", name=f"f{i}")]} for i in range(8)]}),
    ]
    return {
        "forms": [{"fields": f, "asks": prompt.form_asks(f)} for f in field_sets],
        "actions": [{"final_url": u, "action": a, "external": prompt.external_action(u, a)} for u, a in actions],
        "texts": [{"raw": t, "collapsed": prompt.collapse_text(t)} for t in texts],
        "inspections": [{"url": u, "final_url": u, "status": 200, "redirects": 1, "inspection": data,
                         "observation": prompt.observation_from_inspection(u, u, 200, 1, data).to_json()}
                        for u, data in inspections],
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
    cases = [(r, prompt) for r in replies]
    # A personal_info answer is kept only when a field or a worded form gives it ground.
    pi = '{"risk": "MEDIUM", "type": "personal_info", "evidence": ""}'
    base = ["주소: https://clinic.example.com/consult", "페이지: 상태 200, 본문 900자", "제목: 상담 안내"]
    for extra in (
        [],                                                     # no form at all -> LOW
        ["입력 칸: search"],                                      # search box only -> LOW
        ["입력 칸: email, search"],                               # email alone is not personal -> LOW
        ["입력 칸: 일반"],                                         # unnamed fields, no wording -> LOW
        ["입력 칸: name, phone"],                                 # kept
        ["입력 칸: password"],                                    # kept
        ["입력 칸: 일반 (외부로 전송)"],                             # posting elsewhere is not ground -> LOW
        ["브랜드 언급: 국민은행(공식 kbstar.com) / 실제 clinic.example.com"],   # a brand mention is not ground -> LOW
        ["주소 흉내: host x embeds kbstar.com"],                   # nor is host imitation -> LOW
        ["본문: 빠른 상담 신청 연락처를 남겨 주세요"],                  # wording without a form -> LOW
        ["입력 칸: 일반", "본문: 빠른 상담 신청 연락처를 남겨 주세요"],    # form with contact wording -> kept
        ["입력 칸: 일반, email (외부로 전송)", "본문: 개인정보 수집 및 이용에 동의합니다"],   # form with consent -> kept
        ["입력 칸: email", "본문: 개인정보 수집 및 이용에 동의합니다"],   # an email box alone is not that form -> LOW
        ["입력 칸: search", "본문: 개인정보 수집 및 이용에 동의합니다"],  # nor is a search box -> LOW
        ["입력 칸: 일반", "본문: 개인정보처리방침 보기"],                # 개인정보 without 수집/동의 -> LOW
    ):
        cases.append((pi, "\n".join(base + extra)))
    cases.append(('{"risk": "HIGH", "type": "personal_info", "evidence": "제목: 상담 안내"}', "\n".join(base)))
    cases.append(('{"risk": "LOW", "type": "personal_info", "evidence": ""}', "\n".join(base)))
    return [{"reply": r, "prompt": p, **answer.read(r, p)} for r, p in cases]


def verdict_cases() -> list[dict]:
    """Facts with the table's decisions: the cases the design names, then a fixed random sample."""
    vt = verdict_table
    none = dict(apk_link=False, sensitive_field=False, personal_field=False)
    quiet = dict(claim="none", sign_in=False, payment=False, investment=False, reward_pressure=False, content_farm=False)
    named = [
        vt.Facts(),                                                                  # nothing known yet
        vt.Facts(blocklisted=True),                                                  # final at gate A
        vt.Facts(host_imitation="certain"),                                          # final at gate A
        vt.Facts(host_imitation="suspicious"),
        vt.Facts(opened=True, official=True, **none),                                # official landing: final at gate B
        vt.Facts(opened=True, official=True, apk_link=True, sensitive_field=False, personal_field=False),
        vt.Facts(opened=True, official=False, apk_link=True, sensitive_field=False, personal_field=False),
        vt.Facts(opened=True, official=False, apk_link=False, sensitive_field=True, personal_field=False),
        vt.Facts(opened=True, official=False, apk_link=False, sensitive_field=False, personal_field=True),
        vt.Facts(opened=False, official=False, **none),
        vt.Facts(opened=True, official=False, **none, **quiet),
        vt.Facts(opened=True, official=False, **none, **{**quiet, "claim": "listed_other"}),
        vt.Facts(opened=True, official=False, **none, **{**quiet, "claim": "unlisted"}),
        vt.Facts(opened=True, official=False, **none, **{**quiet, "claim": "unlisted", "sign_in": True}),
        vt.Facts(opened=True, official=False, **none, **{**quiet, "sign_in": True}),
        vt.Facts(opened=True, official=False, **none, **{**quiet, "payment": True, "content_farm": True}),
        vt.Facts(host_imitation="suspicious", opened=True, official=False, **none, **quiet),
        vt.Facts(host_imitation="suspicious", opened=True, official=False, **none, **{**quiet, "sign_in": True}),
    ]
    rng = random.Random(20261005)
    fields = [f.name for f in dataclasses.fields(vt.Facts)]
    def pick(name):
        if name == "blocklisted":
            return rng.random() < 0.05
        if name == "host_imitation":
            return rng.choice(vt.HOST_IMITATIONS)
        if rng.random() < 0.3:
            return None
        return rng.choice(vt.CLAIMS) if name == "claim" else rng.random() < 0.4
    sampled = [vt.Facts(**{name: pick(name) for name in fields}) for _ in range(200)]
    table = vt.VerdictTable.load()
    return [{"facts": f.to_json(), "decision": table.decide(f).to_json()} for f in named + sampled]


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
    _write_json("verdict_cases.json", verdict_cases())
    chat = chat_cases(brands)
    if chat is not None:
        _write_json("chat_cases.json", chat)
    print("wrote", paths.LLM_FIXTURES, "" if chat is not None else "(chat_cases.json kept: no local tokenizer)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
