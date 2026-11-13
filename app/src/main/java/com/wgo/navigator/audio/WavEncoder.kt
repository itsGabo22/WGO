package com.wgo.navigator.audio

/** A RIFF/WAVE file (PCM16, mono) built in memory. The audio is never written to disk. */
object WavEncoder {
    const val HEADER_BYTES = 44

    /** Encodes samples [from, to) of [pcm] at [sampleRate] Hz. */
    fun encode(pcm: ShortArray, from: Int = 0, to: Int = pcm.size, sampleRate: Int = 16_000): ByteArray {
        require(from in 0..to && to <= pcm.size) { "bad range" }
        val dataBytes = (to - from) * 2
        val out = ByteArray(HEADER_BYTES + dataBytes)
        var p = 0
        fun ascii(s: String) { for (c in s) out[p++] = c.code.toByte() }
        fun int32(v: Int) { out[p++] = v.toByte(); out[p++] = (v shr 8).toByte(); out[p++] = (v shr 16).toByte(); out[p++] = (v shr 24).toByte() }
        fun int16(v: Int) { out[p++] = v.toByte(); out[p++] = (v shr 8).toByte() }
        ascii("RIFF"); int32(36 + dataBytes); ascii("WAVE")
        ascii("fmt "); int32(16); int16(1); int16(1); int32(sampleRate); int32(sampleRate * 2); int16(2); int16(16)
        ascii("data"); int32(dataBytes)
        for (i in from until to) {
            val v = pcm[i].toInt()
            out[p++] = v.toByte()
            out[p++] = (v shr 8).toByte()
        }
        return out
    }
}
