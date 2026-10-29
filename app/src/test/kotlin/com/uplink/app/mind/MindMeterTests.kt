package com.uplink.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MindMeterTests {
    @Test
    fun splitsOnAndOffAveragesPerSurface() {
        val m = MindMeter()
        m.record(MindSurface.Phone, false, 1000)
        m.record(MindSurface.Phone, false, 3000)
        m.record(MindSurface.Phone, true, 1500)
        m.record(MindSurface.Chat, true, 99)
        assertEquals(2000.0, m.averageOff(MindSurface.Phone))
        assertEquals(1500.0, m.averageOn(MindSurface.Phone))
        assertEquals(-25.0, m.percentDiff(MindSurface.Phone))
        assertEquals(true to 1500, m.last(MindSurface.Phone))
        assertNull(m.percentDiff(MindSurface.Chat))
        assertNull(m.averageOff(MindSurface.Ssh))
    }

    @Test
    fun keepsOnlyLastTwentyPerState() {
        val m = MindMeter()
        for (i in 1..30) m.record(MindSurface.Ssh, true, i)
        assertEquals(20, m.countOn(MindSurface.Ssh))
        assertEquals((11..30).average(), m.averageOn(MindSurface.Ssh))
        assertEquals(0, m.countOff(MindSurface.Ssh))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val m = MindMeter()
        m.record(MindSurface.Flow, true, 10)
        m.record(MindSurface.Flow, false, 20)
        m.record(MindSurface.Flow, true, 30)
        val back = MindMeter()
        back.decode(m.encode())
        assertEquals(m.averageOn(MindSurface.Flow), back.averageOn(MindSurface.Flow))
        assertEquals(m.averageOff(MindSurface.Flow), back.averageOff(MindSurface.Flow))
        assertEquals(true to 30, back.last(MindSurface.Flow))
    }

    @Test
    fun sizeCountsSystemPlusAllMessages() {
        assertEquals(10 + 3 + 4 + 5, MindMeter.sizeOf("x".repeat(10), listOf("abc", "defg", "hijkl")))
    }
}
