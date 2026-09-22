package com.guardian.ondevice.judge

interface Judge {
    fun judge(url: String, done: (Verdict) -> Unit)
}

