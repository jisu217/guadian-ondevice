package com.guadian.ondevice

import android.app.Application
import com.guadian.ondevice.rules.BlocklistFilter
import com.guadian.ondevice.url.DomainAllowlist
import com.guadian.ondevice.url.PublicSuffixList
import com.guadian.ondevice.verdict.VerdictTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Owns the single judge graph shared by JudgeActivity and ShieldAccessibilityService. */
class GuadianApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val judges: DefaultJudgeGraph by lazy { DefaultJudgeGraph(this) }

    override fun onCreate() {
        super.onCreate()
        /*
         * Load the large runtime resources off the main thread before the
         * first judgment needs them.
         */
        appScope.launch {
            DomainAllowlist.default
            BlocklistFilter.default
            PublicSuffixList.default
            VerdictTable.default
        }
    }
}
