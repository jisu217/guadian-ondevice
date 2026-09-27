import json
import unittest

import prompt_format
from prompt_format import Brands, FormObservation, PageObservation, user_message


class PromptFormatTest(unittest.TestCase):
    def test_committed_brand_data_matches_its_sources(self):
        with open(prompt_format.BRANDS_PATH, encoding="utf-8") as f:
            committed = json.load(f)
        self.assertEqual(committed, json.loads(json.dumps(prompt_format.export_brands(), ensure_ascii=False)),
                         "run: python model/llm/prompt_format.py --export-brands")

    def test_unreachable_page_has_no_page_lines(self):
        prompt = user_message(PageObservation(url="https://down.example/", loaded=False), Brands.load())
        self.assertEqual(prompt, "주소: https://down.example/\n페이지: 열리지 않음")

    def test_brand_mention_needs_the_brand_on_a_foreign_host(self):
        brands = Brands.load()
        own = PageObservation(url="https://m.naver.com/", loaded=True, final_url="https://m.naver.com/",
                              status=200, title="네이버", text="네이버페이", text_chars=500)
        foreign = PageObservation(url="https://naver-pay.top/", loaded=True, final_url="https://naver-pay.top/",
                                  status=200, title="네이버페이 로그인", text="네이버페이", text_chars=500,
                                  forms=(FormObservation(("password",), True),))
        self.assertNotIn("브랜드 언급", user_message(own, brands))
        self.assertIn("브랜드 언급: 네이버(공식 naver.com) / 실제 naver-pay.top", user_message(foreign, brands))
        self.assertIn("입력 칸: password (외부로 전송)", user_message(foreign, brands))


if __name__ == "__main__":
    unittest.main()
