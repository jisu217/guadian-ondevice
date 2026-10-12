package com.guadian.ondevice.llm

import org.json.JSONObject

/** A PageObservation from prompt.PageObservation.to_json, as the fixtures write it. */
internal fun observationFromJson(o: JSONObject) = PageObservation(
    url = o.getString("url"),
    loaded = o.getBoolean("loaded"),
    finalUrl = o.optString("final_url").takeUnless { o.isNull("final_url") },
    status = if (o.isNull("status")) null else o.getInt("status"),
    redirects = o.getInt("redirects"),
    title = o.getString("title"),
    text = o.getString("text"),
    textChars = o.getInt("text_chars"),
    forms = o.getJSONArray("forms").let { forms ->
        List(forms.length()) { i ->
            val f = forms.getJSONObject(i)
            val asks = f.getJSONArray("asks")
            FormObservation(List(asks.length()) { asks.getString(it) }, f.getBoolean("external_action"))
        }
    },
    apkLinks = o.getJSONArray("apk_links").let { links -> List(links.length()) { links.getString(it) } },
)
