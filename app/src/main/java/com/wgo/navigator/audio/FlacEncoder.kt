package com.wgo.navigator.audio

import android.media.MediaCodec
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * An audio encoder fed while recording (P10, docs/design "Compressed upload"): [start], then [feed] PCM in order, then
 * [finish] for the whole stream in memory, or null when anything failed (the caller sends the WAV instead).
 * [release] frees it on every path. Faked in tests.
 */
interface StreamEncoder {
    fun start(): Boolean
    fun feed(pcm: ShortArray, from: Int, to: Int): Boolean
    fun finish(): ByteArray?
    fun release()
}

/**
 * FLAC through the platform `MediaCodec` encoder (`c2.android.flac.encoder` on the Galaxy Watch5 Pro): lossless, so
 * the transcript is the WAV's (P10 STEP 0), about 2.5x smaller. The stream is the codec's "fLaC" header block followed
 * by its frames, concatenated in memory: no muxer, no file.
 */
class FlacEncoder(private val sampleRate: Int = 16_000) : StreamEncoder {
    private var codec: MediaCodec? = null
    private val out = ByteArrayOutputStream(64 * 1024)
    private val info = MediaCodec.BufferInfo()
    private var ptsUs = 0L
    private var failed = false
    private var ended = false

    override fun start(): Boolean = try {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_FLAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, COMPRESSION_LEVEL)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, CHUNK_SAMPLES * 2)
        }
        val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_FLAC)
        codec = c
        c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        c.start()
        true
    } catch (e: Exception) {
        failed = true
        false
    }

    override fun feed(pcm: ShortArray, from: Int, to: Int): Boolean {
        val c = codec ?: return false
        if (failed) return false
        try {
            var pos = from
            val deadline = System.nanoTime() + FEED_BUDGET_NS
            while (pos < to) {
                val index = c.dequeueInputBuffer(INPUT_WAIT_US)
                if (index < 0) {
                    drain(c, 0)
                    if (System.nanoTime() > deadline) return fail() // a stuck codec never stalls the capture
                    continue
                }
                val buffer = c.getInputBuffer(index) ?: return fail()
                buffer.clear()
                val n = minOf(to - pos, buffer.capacity() / 2, CHUNK_SAMPLES)
                buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm, pos, n)
                c.queueInputBuffer(index, 0, n * 2, ptsUs, 0)
                ptsUs += n * 1_000_000L / sampleRate
                pos += n
                drain(c, 0)
            }
            return true
        } catch (e: Exception) {
            return fail()
        }
    }

    override fun finish(): ByteArray? {
        val c = codec ?: return null
        if (failed) return null
        return try {
            var index = -1
            val deadline = System.nanoTime() + FINISH_BUDGET_NS
            while (index < 0 && System.nanoTime() < deadline) {
                index = c.dequeueInputBuffer(INPUT_WAIT_US)
                if (index < 0) drain(c, 0)
            }
            if (index < 0) return null
            c.queueInputBuffer(index, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            while (!ended && System.nanoTime() < deadline) drain(c, OUTPUT_WAIT_US)
            if (!ended || out.size() < 4) null else out.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    override fun release() {
        val c = codec ?: return
        codec = null
        try {
            c.stop()
        } catch (e: Exception) {
            // Not started or already stopped.
        }
        c.release()
    }

    /** Collects every ready output buffer (the header block and the frames, in order). */
    private fun drain(c: MediaCodec, waitUs: Long) {
        while (true) {
            val index = c.dequeueOutputBuffer(info, waitUs)
            if (index < 0) return
            val buffer: ByteBuffer? = c.getOutputBuffer(index)
            if (buffer != null && info.size > 0) {
                buffer.position(info.offset)
                buffer.limit(info.offset + info.size)
                val bytes = ByteArray(info.size)
                buffer.get(bytes)
                out.write(bytes)
            }
            c.releaseOutputBuffer(index, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                ended = true
                return
            }
        }
    }

    private fun fail(): Boolean {
        failed = true
        return false
    }

    private companion object {
        const val COMPRESSION_LEVEL = 5
        const val CHUNK_SAMPLES = 1_600 // 100 ms
        const val INPUT_WAIT_US = 2_000L
        const val OUTPUT_WAIT_US = 10_000L
        const val FINISH_BUDGET_NS = 800_000_000L
        const val FEED_BUDGET_NS = 300_000_000L
    }
}
