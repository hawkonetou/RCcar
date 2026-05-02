package com.hotwheels.command.bluetooth

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class CarConnectionTest {

    private val out = ByteArrayOutputStream()
    private var clockNanos = 0L
    private val clock: () -> Long = { clockNanos }
    private fun advance(ms: Long) { clockNanos += ms * 1_000_000L }

    private lateinit var conn: CarConnection

    @After
    fun tearDown() {
        if (::conn.isInitialized) conn.stop()
    }

    private fun newConnection() = CarConnection(
        outputStream = out,
        clockNanos = clock,
        sleeperNanos = { /* no-op for tests; we drive the loop manually */ }
    ).also { conn = it }

    @Test
    fun `tick writes ASCII value with newline`() {
        val c = newConnection()
        c.setTargetValue(-56)
        c.tickForTest()
        assertEquals("-56\n", out.toString(Charsets.US_ASCII))
    }

    @Test
    fun `throttle prevents two writes within 2ms`() {
        val c = newConnection()
        c.setTargetValue(10)
        c.tickForTest()           // first send
        advance(1)                // 1 ms later
        c.setTargetValue(20)
        c.tickForTest()           // should be throttled
        assertEquals("10\n", out.toString(Charsets.US_ASCII))
        advance(2)                // total 3 ms since last send
        c.tickForTest()
        assertEquals("10\n20\n", out.toString(Charsets.US_ASCII))
    }

    @Test
    fun `heartbeat resends value after 50ms with no change`() {
        val c = newConnection()
        c.setTargetValue(0)
        c.tickForTest()
        advance(49)
        c.tickForTest()
        assertEquals("0\n", out.toString(Charsets.US_ASCII))
        advance(2)                // total 51 ms
        c.tickForTest()
        assertEquals("0\n0\n", out.toString(Charsets.US_ASCII))
    }

    @Test
    fun `setTargetValue clamps to -100 100`() {
        val c = newConnection()
        c.setTargetValue(250)
        c.tickForTest()
        c.setTargetValue(-300)
        advance(3)
        c.tickForTest()
        assertEquals("100\n-100\n", out.toString(Charsets.US_ASCII))
    }

    @Test
    fun `IOException surfaces to caller`() {
        val throwingStream = object : java.io.OutputStream() {
            override fun write(b: Int) { throw java.io.IOException("boom") }
        }
        var caught: Throwable? = null
        val c = CarConnection(
            outputStream = throwingStream,
            clockNanos = { 0L },
            sleeperNanos = {}
        )
        c.onFailure { caught = it }
        c.setTargetValue(42)
        try { c.tickForTest() } catch (e: Exception) { caught = e }
        assertEquals("boom", caught?.message)
        c.stop()
    }

    @Test
    fun `stop writes 0 then flushes`() {
        val c = newConnection()
        c.setTargetValue(75)
        c.tickForTest()
        out.reset()
        c.stop()
        // Au stop on coupe les deux canaux : throttle puis direction.
        assertEquals("0\nM2:0\n", out.toString(Charsets.US_ASCII))
    }

    @Test
    fun `parses well-formed BAT line into BatteryState`() {
        val c = newConnection()
        c.parseLineForTest("BAT:374,82")
        assertEquals(BatteryState(centivolts = 374, percent = 82), c.battery.value)
    }

    @Test
    fun `clamps battery percent to 0-100 range`() {
        val c = newConnection()
        c.parseLineForTest("BAT:420,150")
        assertEquals(100, c.battery.value?.percent)
        c.parseLineForTest("BAT:280,-5")
        assertEquals(0, c.battery.value?.percent)
    }

    @Test
    fun `ignores malformed BAT lines`() {
        val c = newConnection()
        c.parseLineForTest("BAT:abc,82")
        c.parseLineForTest("BAT:374")
        c.parseLineForTest("HELLO")
        c.parseLineForTest("")
        assertEquals(null, c.battery.value)
    }

    @Test
    fun `tolerates whitespace and trailing CR around BAT payload`() {
        val c = newConnection()
        c.parseLineForTest("  BAT: 374 , 82 \r")
        assertEquals(BatteryState(centivolts = 374, percent = 82), c.battery.value)
    }
}
