package com.guadian.ondevice.llm

/**
 * What page inspection saw, in the form the LLM's prompt is built
 * from. Mirrors `PageObservation` in ml/guadian_ml/llm/contract/prompt.py; the training
 * data is built from the same fields.
 */
data class PageObservation(
    val url: String,
    val loaded: Boolean,
    val finalUrl: String? = null,
    val status: Int? = null,
    val redirects: Int = 0,
    val title: String = "",
    /** Visible text, first 2,500 characters. */
    val text: String = "",
    /** Length of the full visible text, in characters. */
    val textChars: Int = 0,
    val forms: List<FormObservation> = emptyList(),
    val apkLinks: List<String> = emptyList(),
)

data class FormObservation(
    /** Sorted subset of `account`, `card`, `password`, `phone`, `rrn` the fields request. */
    val asks: List<String>,
    /** The form submits to another site. */
    val externalAction: Boolean,
)
