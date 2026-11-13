package com.wgo.navigator.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavEncoderTest {
    @Test fun headerDescribes16kMonoPcm16() {
        val wav = WavEncoder.encode(ShortArray(16_000), sampleRate = 16_000)
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals(36 + 32_000, b.getInt(4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals("fmt ", String(wav, 12, 4))
        assertEquals(16, b.getInt(16))
        assertEquals(1, b.getShort(20).toInt()) // PCM
        assertEquals(1, b.getShort(22).toInt()) // mono
        assertEquals(16_000, b.getInt(24))
        assertEquals(32_000, b.getInt(28)) // byte rate
        assertEquals(2, b.getShort(32).toInt()) // block align
        assertEquals(16, b.getShort(34).toInt()) // bits
        assertEquals("data", String(wav, 36, 4))
        assertEquals(32_000, b.getInt(40))
        assertEquals(44 + 32_000, wav.size)
    }

    @Test fun samplesAreLittleEndianAndTheRangeIsRespected() {
        val pcm = shortArrayOf(1, 0x1234, -2, Short.MAX_VALUE, Short.MIN_VALUE)
        val wav = WavEncoder.encode(pcm, from = 1, to = 4)
        assertEquals(44 + 6, wav.size)
        assertArrayEquals(
            byteArrayOf(0x34, 0x12, 0xFE.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F),
            wav.copyOfRange(44, 50)
        )
    }

    @Test fun tenSecondsFitTheSizeBudget() {
        assertEquals(320_044, WavEncoder.encode(ShortArray(160_000)).size)
    }
}
