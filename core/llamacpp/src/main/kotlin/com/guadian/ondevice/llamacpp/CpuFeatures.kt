package com.guadian.ondevice.llamacpp

import java.io.File

/**
 * The native library is compiled for ARMv8.2 with dot-product instructions
 * (CMakeLists.txt GGML_CPU_ARM_ARCH), which make 4-bit inference about four times
 * faster. Cores older than Cortex-A55/A75 lack them and would crash on the first
 * such instruction, so the library is only loaded where the kernel reports them.
 */
internal object CpuFeatures {
    val hasDotProduct: Boolean by lazy {
        runCatching {
            File("/proc/cpuinfo").useLines { lines ->
                lines.any { it.startsWith("Features") && "asimddp" in it.split(Regex("\\s+")) }
            }
        }.getOrDefault(false)
    }
}
