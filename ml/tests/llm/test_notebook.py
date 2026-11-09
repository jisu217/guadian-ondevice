"""Tests for the GPU-free parts of notebooks/train_llm.ipynb: example building and scoring.

Runs the notebook's CONFIG, EXAMPLES, and SCORING cells against a local copy of the
Qwen3 tokenizer (ml/data/llm/cache/tokenizer). Skipped when that copy is absent.
"""

import json
import importlib.util
import os
import unittest

from guadian_ml import paths
from guadian_ml.llm.training import notebook as nb

TOKENIZER_DIR = str(paths.TOKENIZER)


QUIET = {"claims": None, "evidence": "", "sign_in": False, "payment": False, "investment": False,
         "reward_pressure": False, "content_farm": False}


def example(risk="LOW", user="주소: https://a.example/", **answers):
    answer = json.dumps({**QUIET, **answers}, ensure_ascii=False)
    return {"case_id": "t", "label_source": "teacher", "risk": risk, "facts": {}, "messages": [
        {"role": "system", "content": "S"}, {"role": "user", "content": user},
        {"role": "assistant", "content": answer}]}


def reply(**answers):
    return json.dumps({**QUIET, **answers}, ensure_ascii=False)


class ScoringTest(unittest.TestCase):
    def setUp(self):
        self.ns = {}
        exec(nb.CONFIG, self.ns)
        exec(nb.SCORING, self.ns)

    def test_parse_answer_needs_boolean_answers(self):
        parse = self.ns["parse_answer"]
        self.assertTrue(parse(reply(sign_in=True) + " trailing")["sign_in"])
        self.assertIsNone(parse('{"claims": null, "sign_in": "yes"}'))
        self.assertIsNone(parse("no json here"))

    def test_score_counts_unparseable_as_wrong(self):
        examples = [example(sign_in=True), example(), example(claims="KB", evidence="제목: KB")]
        replies = [reply(sign_in=True), "garbage", reply()]
        report = self.ns["score"](examples, replies)
        self.assertEqual(report["unparseable"], 1)
        self.assertEqual(report["sign_in_accuracy"], 2 / 3)    # garbage is wrong even where gold is no
        self.assertEqual(report["claims_recall"], 0.0)
        self.assertEqual(report["all_correct"], 1 / 3)

    def test_risk_report_counts_missed_risk(self):
        report = self.ns["risk_report"](["HIGH", "MEDIUM", "LOW"], ["LOW", "MEDIUM", "LOW"])
        self.assertEqual(report["missed_risk"], 1)
        self.assertEqual(report["accuracy"], 2 / 3)


@unittest.skipUnless(os.path.exists(os.path.join(TOKENIZER_DIR, "tokenizer_config.json"))
                     and importlib.util.find_spec("transformers"), "needs the local tokenizer and model[llm]")
class ExampleBuildingTest(unittest.TestCase):
    def setUp(self):
        from transformers import AutoTokenizer
        self.ns = {"data": {"train": [example("HIGH")], "validation": [example("LOW")], "test": []}}
        exec(nb.CONFIG, self.ns)
        self.ns["MODEL_ID"] = TOKENIZER_DIR
        self.ns["AutoTokenizer"] = AutoTokenizer
        exec(nb.EXAMPLES.replace("from transformers import AutoTokenizer\n", ""), self.ns)

    def test_only_training_high_cases_are_repeated(self):
        # one HIGH example, OVERSAMPLE["HIGH"] copies
        self.assertEqual(len(self.ns["features"]["train"]), self.ns["OVERSAMPLE"]["HIGH"])
        self.assertEqual(len(self.ns["features"]["validation"]), 1)

    def test_loss_covers_only_the_answer(self):
        tokenizer = self.ns["tokenizer"]
        features = self.ns["features"]["train"][0]
        answer_ids = [t for t, l in zip(features["input_ids"], features["labels"]) if l != -100]
        decoded = tokenizer.decode(answer_ids)
        self.assertEqual(decoded, reply() + "<|im_end|>\n")

    def test_prompt_matches_inference_format(self):
        prompt = self.ns["prompt_text"](example("LOW")["messages"])
        self.assertTrue(prompt.endswith("<|im_start|>assistant\n<think>\n\n</think>\n\n"))


if __name__ == "__main__":
    unittest.main()
