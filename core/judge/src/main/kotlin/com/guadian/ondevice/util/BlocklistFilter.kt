package com.guadian.ondevice.util

import java.io.DataInputStream
import java.io.InputStream

/**
 * The phishing/malware blocklist in `model/runtime/blocklist.bloom`, built by
 * model/training/build_blocklist_filter.py from the ut1 lists the original
 * GUADIAN server used (CC BY-NC-SA 3.0, non-commercial).
 *
 * A Bloom filter keeps ~268k domains in ~1 MB. It is sized for a one-in-a-million
 * false-positive rate, so a hit is treated as certain; a miss is always exact.
 * The bit scheme must match the Python builder: double hashing over 64-bit
 * FNV-1a of the ASCII domain, `h2 = splitmix64(h1) | 1`, `bit_i = (h1 + i*h2) mod m`.
 */
class BlocklistFilter(private val bits: ByteArray, private val m: Long, private val k: Int) {

    fun mightContain(domain: String): Boolean {
        val h1 = fnv1a64(domain.lowercase())
        val h2 = splitmix64(h1) or 1uL
        return (0 until k).all { i ->
            val bit = ((h1 + i.toULong() * h2) % m.toULong()).toLong()
            bits[(bit ushr 3).toInt()].toInt() and (1 shl (bit and 7).toInt()) != 0
        }
    }

    companion object {
        private val MAGIC = "GBL1".toByteArray(Charsets.US_ASCII)
        private const val FNV_OFFSET = 0xCBF29CE484222325uL
        private const val FNV_PRIME = 0x100000001B3uL

        val default: BlocklistFilter by lazy {
            val stream = BlocklistFilter::class.java.getResourceAsStream("/blocklist.bloom")
                ?: error("blocklist.bloom resource is missing")
            stream.use(::read)
        }

        fun read(input: InputStream): BlocklistFilter {
            val data = DataInputStream(input.buffered())
            val magic = ByteArray(4).also(data::readFully)
            require(magic.contentEquals(MAGIC)) { "not a GBL1 blocklist filter" }
            val version = data.readUnsignedByte()
            require(version == 1) { "unsupported blocklist filter version $version" }
            val k = data.readUnsignedByte()
            data.readUnsignedShort()   // reserved
            val m = data.readInt().toLong() and 0xFFFFFFFFL
            data.readInt()             // entry count, informational
            val bits = ByteArray(((m + 7) / 8).toInt()).also(data::readFully)
            return BlocklistFilter(bits, m, k)
        }

        internal fun fnv1a64(text: String): ULong {
            var h = FNV_OFFSET
            for (byte in text.toByteArray(Charsets.US_ASCII)) {
                h = (h xor (byte.toULong() and 0xFFuL)) * FNV_PRIME
            }
            return h
        }

        private fun splitmix64(seed: ULong): ULong {
            var x = seed + 0x9E3779B97F4A7C15uL
            x = (x xor (x shr 30)) * 0xBF58476D1CE4E5B9uL
            x = (x xor (x shr 27)) * 0x94D049BB133111EBuL
            return x xor (x shr 31)
        }
    }
}
