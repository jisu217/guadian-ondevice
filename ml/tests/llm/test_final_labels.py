import json
import unittest

from guadian_ml.llm.contract.prompt import Brands
from guadian_ml.llm.dataset.final_labels import apply_guide_checks, apply_policy

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

    def test_personal_info_without_a_personal_field_is_low(self):
        self.assertEqual(self.label("https://clinic.example.com/", [], "MEDIUM", "personal_info"),
                         ("LOW", "none", "policy:personal_info_unsupported"))

    def test_password_form_on_a_brand_platform_stays_low(self):
        login = [form(["text:id", "password:pw"], ["password"])]
        self.assertEqual(self.label("https://blog.naver.com/someone", login, "LOW", "none"),
                         ("LOW", "none", "teacher"))


class OfficialPolicyTest(unittest.TestCase):
    def label(self, url, type_="investment"):
        return apply_policy(case(url, []), {"risk": "MEDIUM", "type": type_}, BRANDS)[:3]

    def test_brand_site_is_low(self):
        self.assertEqual(self.label("https://www.naver.com/"), ("LOW", "none", "policy:official"))
        self.assertEqual(self.label("https://shop.kt.com/consult", "urgency"), ("LOW", "none", "policy:official"))

    def test_unlisted_host_stays_medium(self):
        self.assertEqual(self.label("https://www.meritzevent.com/"), ("MEDIUM", "investment", "teacher"))

    def test_user_content_on_a_brand_platform_stays_medium(self):
        self.assertEqual(self.label("https://m.blog.naver.com/someone/123", "contentfarm"),
                         ("MEDIUM", "contentfarm", "teacher"))


class GuideChecksTest(unittest.TestCase):
    def label(self, url, risk, type_, forms=()):
        return apply_guide_checks(case(url, list(forms)), {"risk": risk, "type": type_}, BRANDS)[:3]

    def test_teacher_label_is_kept(self):
        newsletter = [form(["email:email"], ["email"])]
        self.assertEqual(self.label("https://shop.example.com/", "LOW", "none", newsletter), ("LOW", "none", "teacher"))
        contact = [form(["text:name", "tel:phone"], ["name", "phone"])]
        self.assertEqual(self.label("https://clinic.example.com/", "MEDIUM", "personal_info", contact),
                         ("MEDIUM", "personal_info", "teacher"))

    def test_personal_field_off_the_brand_site_is_medium(self):
        contact = [form(["text:name", "tel:phone"], ["name", "phone"])]
        self.assertEqual(self.label("https://clinic.example.com/", "LOW", "none", contact),
                         ("MEDIUM", "personal_info", "policy:personal_info"))
        login = [form(["text:id", "password:pw"], ["password"])]
        self.assertEqual(self.label("https://shop.example.com/", "LOW", "none", login), ("LOW", "none", "teacher"))

    def test_personal_info_without_ground_is_low(self):
        self.assertEqual(self.label("https://clinic.example.com/", "MEDIUM", "personal_info"),
                         ("LOW", "none", "policy:personal_info"))

    def test_error_page_off_the_brand_site_is_unverifiable(self):
        broken = case("https://shop.example.com/", [])
        broken["input"]["tool_outputs"][0]["output"] = json.dumps({"final_url": "https://shop.example.com/", "status": 404,
                                                                    "chain": [], "text": "Not Found", "text_chars": 9})
        self.assertEqual(apply_guide_checks(broken, {"risk": "LOW", "type": "none"}, BRANDS)[:3],
                         ("MEDIUM", "unverifiable", "policy:unverifiable"))

    def test_card_field_off_the_brand_site_is_high(self):
        pay = [form(["text:cardno"], ["card"])]
        self.assertEqual(self.label("https://pay.example.com/", "MEDIUM", "payment", pay),
                         ("HIGH", "credentials", "policy:fields"))

    def test_apk_link_by_site_identity(self):
        def apk_case(url):
            c = case(url, [])
            c["input"]["tool_outputs"][1]["output"] = json.dumps({"title": "", "forms": [], "apk_links": [url + "app.apk"]})
            return c
        self.assertEqual(apply_guide_checks(apk_case("https://app.example.com/"), {"risk": "MEDIUM", "type": "apk"}, BRANDS)[:3],
                         ("HIGH", "apk", "policy:apk"))
        self.assertEqual(apply_guide_checks(apk_case("https://www.naver.com/"), {"risk": "LOW", "type": "none"}, BRANDS)[:3],
                         ("MEDIUM", "apk", "policy:official"))

    def test_high_of_another_type_is_medium(self):
        self.assertEqual(self.label("https://shop.example.com/", "HIGH", "payment"),
                         ("MEDIUM", "payment", "policy:high_type"))

    def test_brand_site_is_low(self):
        self.assertEqual(self.label("https://www.naver.com/", "MEDIUM", "investment"), ("LOW", "none", "policy:official"))

    def test_host_rule_raises(self):
        self.assertEqual(self.label("https://roblox.com.mu/", "LOW", "none")[::2], ("HIGH", "policy:host"))


if __name__ == "__main__":
    unittest.main()
