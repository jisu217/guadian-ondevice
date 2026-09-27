package com.guadian.ondevice.rules

import com.guadian.ondevice.judge.Verdict

interface RiskRule {
    fun evaluate(url: String): Verdict?
}

