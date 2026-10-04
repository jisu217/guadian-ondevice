"""Tests for the GPU-free parts of notebooks/train_llm.ipynb: example building and scoring.

Runs the notebook's CONFIG, EXAMPLES, and SCORING cells against a local copy of the
Qwen3 tokenizer (ml/data/llm/tokenizer). Skipped when that copy is absent.
"""

import json
import importlib.util
import os
import unittest

from guadian_ml import paths
from guadian_ml.llm.training import notebook as nb

TOKENIZER_DIR = str(paths.TOKENIZER)


def example(risk, type_="none", user="주소: https://a.example/\n차단 목록: 없음"):
    answer = json.dumps({"risk": risk, "type": type_, "evidence": ""}, ensure_ascii=False)
    return {"case_id": "t", "label_source": "teacher", "messages": [
        {"role": "system", "content": "S"}, {"role": "user", "content": user},
        {"role": "assistant", "content": answer}]}


class ScoringTest(unittest.TestCase):
    def setUp(self):
        self.ns = {}
        exec(nb.CONFIG, self.ns)
        exec(nb.SCORING, self.ns)

    def test_parse_answer_takes_first_json_object(self):
        parse = self.ns["parse_answer"]
        self.assertEqual(parse('{"risk": "HIGH", "type": "apk", "evidence": "x"} trailing')["risk"], "HIGH")
        self.assertIsNone(parse('{"risk": "중간"}'))
        self.assertIsNone(parse("no json here"))

    def test_score_counts_unparseable_as_medium_and_missed_risk(self):
        examples = [example("LOW"), example("MEDIUM"), example("HIGH"), example("HIGH")]
        replies = ['{"risk": "LOW"}', "garbage", '{"risk": "LOW"}', '{"risk": "HIGH"}']
        report = self.ns["score"](examples, replies)
        self.assertEqual(report["unparseable"], 1)
        self.assertEqual(report["accuracy"], 3 / 4)       # garbage -> MEDIUM, which is correct
        self.assertEqual(report["missed_risk"], 1)        # HIGH answered LOW
        self.assertEqual(report["recall_HIGH"], 1 / 2)


@unittest.skipUnless(os.path.exists(os.path.join(TOKENIZER_DIR, "tokenizer_config.json"))
                     and importlib.util.find_spec("transformers"), "needs the local tokenizer and model[llm]")
class ExampleBuildingTest(unittest.TestCase):
    def setUp(self):
        from transformers import AutoTokenizer
        self.ns = {"data": {"train": [example("HIGH", "apk")], "validation": [example("LOW")], "test": []}}
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
        self.assertEqual(decoded, '{"risk": "HIGH", "type": "apk", "evidence": ""}<|im_end|>\n')

    def test_prompt_matches_inference_format(self):
        prompt = self.ns["prompt_text"](example("LOW")["messages"])
        self.assertTrue(prompt.endswith("<|im_start|>assistant\n<think>\n\n</think>\n\n"))


if __name__ == "__main__":
    unittest.main()
