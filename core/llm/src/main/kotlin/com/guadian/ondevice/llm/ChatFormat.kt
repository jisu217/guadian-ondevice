package com.guadian.ondevice.llm

/**
 * The exact text the model is given: Qwen3's chat template with thinking turned
 * off, as the training notebook rendered it (`apply_chat_template(...,
 * add_generation_prompt=True, enable_thinking=False)`). The system line is the one
 * the training examples carry (model/llm/build_prompts.py SYSTEM).
 * Parity fixture: data/fixtures/llm_chat_cases.json.
 */
object ChatFormat {
    const val SYSTEM = "광고 도착 페이지의 위험도를 판정한다. JSON 한 줄로만 답한다."

    fun render(userMessage: String): String =
        "<|im_start|>system\n$SYSTEM<|im_end|>\n" +
            "<|im_start|>user\n$userMessage<|im_end|>\n" +
            "<|im_start|>assistant\n<think>\n\n</think>\n\n"
}
