import json
import unittest

from guadian_ml.llm.contract.prompt import Brands
from guadian_ml.llm.dataset.final_labels import apply_policy

BRANDS = Brands.load()


def case(url: str, forms: list[dict]) -> dict:
    fetch = {"final_url": url, "status": 200, "chain": [url], "text": "본문", "text_chars": 1000}
    signals = {"title": "", "forms": forms}
    return {"case_id": "case_test", "url": url, "input": {"tool_outputs": [
        {"tool": "fetch_page", "output": json.dumps(fetch)},
        {"tool": "page_signals", "output": json.dumps(signals)},
    ]}}


def form(fields: list[str], asks: list[str] = ()) -> dict:
    return {"fields": fields, "asks": list(asks), "external_action": False}


class FormPolicyTest(unittest.TestCase):
    def label(self, url, forms, risk, type_):
        return apply_policy(case(url, forms), {"risk": risk, "type": type_}, BRANDS)[:3]

    def test_password_form_on_unlisted_host_is_medium(self):
        login = [form(["text:username", "password:password"], ["password"])]
        self.assertEqual(self.label("https://shop.example.com/", login, "LOW", "none"),
                         ("MEDIUM", "personal_info", "policy:form"))

    def test_password_form_on_brand_host_stays_low(self):
        login = [form(["text:id", "password:pw"], ["password"])]
        self.assertEqual(self.label("https://nid.naver.com/login", login, "LOW", "none"),
                         ("LOW", "none", "teacher"))

    def test_phone_field_on_unlisted_host_is_medium(self):
        contact = [form(["text:user_name", "text:hphone"], ["phone"])]
        self.assertEqual(self.label("https://ad.example.com/", contact, "LOW", "none"),
                         ("MEDIUM", "personal_info", "policy:form"))

    def test_email_only_form_is_low(self):
        newsletter = [form(["email:emailAddress newsletter_email", "checkbox:agree"])]
        self.assertEqual(self.label("https://www.example.com/", newsletter, "MEDIUM", "personal_info"),
                         ("LOW", "none", "policy:email_only"))

    def test_email_with_name_stays_medium(self):
        lead = [form(["text:name 이름", "email:email"])]
        self.assertEqual(self.label("https://www.example.com/", lead, "MEDIUM", "personal_info"),
                         ("MEDIUM", "personal_info", "teacher"))


class InvestmentPolicyTest(unittest.TestCase):
    def label(self, url):
        return apply_policy(case(url, []), {"risk": "MEDIUM", "type": "investment"}, BRANDS)[:3]

    def test_investment_on_brand_host_is_low(self):
        self.assertEqual(self.label("https://www.naver.com/"), ("LOW", "none", "policy:official_investment"))

    def test_investment_on_unlisted_host_stays_medium(self):
        self.assertEqual(self.label("https://www.meritzevent.com/"), ("MEDIUM", "investment", "teacher"))


if __name__ == "__main__":
    unittest.main()
