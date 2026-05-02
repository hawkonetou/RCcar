package com.hotwheels.command.bluetooth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport

class CarConnection(
    private val outputStream: OutputStream,
    private val inputStream: InputStream? = null,
    private val clockNanos: () -> Long = { System.nanoTime() },
    private val sleeperNanos: (Long) -> Unit = { LockSupport.parkNanos(it) }
) {
    private val target = AtomicInteger(0)
    private var lastSent: Int = Int.MIN_VALUE
    private var lastSentNanos: Long = Long.MIN_VALUE
    @Volatile private var running = false
    private var senderThread: Thread? = null
    private var readerThread: Thread? = null

    @Volatile private var failureListener: ((Throwable) -> Unit)? = null

    private val _battery = MutableStateFlow<BatteryState?>(null)
    val battery: StateFlow<BatteryState?> = _battery.asStateFlow()

    fun setTargetValue(value: Int) {
        val clamped = value.coerceIn(SppConstants.MIN_VALUE, SppConstants.MAX_VALUE)
        target.set(clamped)
    }

    fun start() {
        if (running) return
        running = true
        senderThread = Thread({ runSender() }, "CarConnection-Sender").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
            start()
        }
        inputStream?.let { input ->
            readerThread = Thread({ runReader(input) }, "CarConnection-Reader").apply {
                isDaemon = true
                start()
            }
        }
    }

    fun stop() {
        running = false
        senderThread?.interrupt()
        senderThread = null
        readerThread = null
        runCatching {
            outputStream.write("0\n".toByteArray(Charsets.US_ASCII))
            outputStream.flush()
        }
    }

    fun onFailure(listener: (Throwable) -> Unit) { failureListener = listener }

    /** Visible for tests — execute one iteration of the sender loop. */
    internal fun tickForTest() {
        tickOnce()
    }

    /** Visible for tests — feed a single inbound line into the parser. */
    internal fun parseLineForTest(line: String) {
        parseLine(line)
    }

    private fun runSender() {
        while (running) {
            try {
                tickOnce()
            } catch (_: InterruptedException) {
                return
            } catch (e: Exception) {
                failureListener?.invoke(e)
                return
            }
            sleeperNanos(SppConstants.PARK_NANOS)
        }
    }

    private fun runReader(input: InputStream) {
        val reader = BufferedReader(InputStreamReader(input, Charsets.US_ASCII))
        try {
            while (running) {
                val line = reader.readLine() ?: return
                parseLine(line)
            }
        } catch (_: Exception) {
            // Socket closed or interrupted — sender's failure path already covers reconnection.
        }
    }

    private fun parseLine(line: String) {
        val trimmed = line.trim()
        if (!trimmed.startsWith("BAT:")) return
        val payload = trimmed.substring(4)
        val parts = payload.split(",")
        if (parts.size != 2) return
        val cv = parts[0].trim().toIntOrNull() ?: return
        val pct = parts[1].trim().toIntOrNull() ?: return
        _battery.value = BatteryState(centivolts = cv, percent = pct.coerceIn(0, 100))
    }

    private fun tickOnce() {
        val now = clockNanos()
        val current = target.get()
        val firstSend = lastSentNanos == Long.MIN_VALUE
        val sinceLastMs = if (firstSend) Long.MAX_VALUE else (now - lastSentNanos) / 1_000_000L
        val changed = current != lastSent
        val heartbeatDue = sinceLastMs >= SppConstants.HEARTBEAT_MS
        val throttleOk = firstSend || sinceLastMs >= SppConstants.THROTTLE_MIN_MS
        if ((changed || heartbeatDue || firstSend) && throttleOk) {
            outputStream.write("$current\n".toByteArray(Charsets.US_ASCII))
            outputStream.flush()
            lastSent = current
            lastSentNanos = now
        }
    }
}
