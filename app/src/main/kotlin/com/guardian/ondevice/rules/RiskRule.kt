package com.guardian.ondevice.rules

import com.guardian.ondevice.judge.Verdict

interface RiskRule {
    fun evaluate(url: String): Verdict?
}

