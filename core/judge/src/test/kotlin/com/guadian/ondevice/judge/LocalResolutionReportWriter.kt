package com.guadian.ondevice.judge

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File
import kotlin.test.Test

/**
 * Not a test: writes model/evaluation/local-resolution-report.md. It is
 * skipped by `./gradlew test`; `./gradlew :core:judge:writeLocalResolutionReport`
 * sets the variable below and runs only this class.
 */
@EnabledIfEnvironmentVariable(named = "GUADIAN_WRITE_LOCAL_RESOLUTION_REPORT", matches = "1")
class LocalResolutionReportWriter {
    @Test
    fun writeReport() {
        File(LocalResolution.REPORT_PATH).writeText(LocalResolution.render(LocalResolution.measureAll()))
    }
}
