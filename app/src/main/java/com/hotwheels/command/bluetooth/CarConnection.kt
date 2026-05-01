package com.hotwheels.command.bluetooth

import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport

class CarConnection(
    private val outputStream: OutputStream,
    private val clockNanos: () -> Long = { System.nanoTime() },
    private val sleeperNanos: (Long) -> Unit = { LockSupport.parkNanos(it) }
) {
    private val target = AtomicInteger(0)
    private var lastSent: Int = Int.MIN_VALUE
    private var lastSentNanos: Long = Long.MIN_VALUE
    @Volatile private var running = false
    private var thread: Thread? = null

    @Volatile private var failureListener: ((Throwable) -> Unit)? = null

    fun setTargetValue(value: Int) {
        val clamped = value.coerceIn(SppConstants.MIN_VALUE, SppConstants.MAX_VALUE)
        target.set(clamped)
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread({ runLoop() }, "CarConnection-Sender").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
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

    private fun runLoop() {
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
