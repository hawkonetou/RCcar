package com.hotwheels.command.bluetooth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport

class CarConnection(
    private val outputStream: OutputStream,
    private val inputStream: InputStream? = null,
    private val clockNanos: () -> Long = { System.nanoTime() },
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val sleeperNanos: (Long) -> Unit = { LockSupport.parkNanos(it) }
) {
    private val target = AtomicInteger(0)
    private var lastSent: Int = Int.MIN_VALUE
    private var lastSentNanos: Long = Long.MIN_VALUE
    private val steering = AtomicInteger(0)
    private var lastSteeringSent: Int = Int.MIN_VALUE
    private var lastSteeringSentNanos: Long = Long.MIN_VALUE
    private var lastPingNanos: Long = Long.MIN_VALUE
    @Volatile private var running = false
    private var senderThread: Thread? = null
    private var readerThread: Thread? = null

    @Volatile private var failureListener: ((Throwable) -> Unit)? = null

    private val _battery = MutableStateFlow<BatteryState?>(null)
    val battery: StateFlow<BatteryState?> = _battery.asStateFlow()

    /** ms epoch de la derniere trame inbound (BAT ou PONG). 0 = jamais. */
    private val _lastRxMillis = AtomicLong(0L)
    private val _linkFreshMs = MutableStateFlow(Long.MAX_VALUE)
    val linkFreshMs: StateFlow<Long> = _linkFreshMs.asStateFlow()

    fun setTargetValue(value: Int) {
        val clamped = value.coerceIn(SppConstants.MIN_VALUE, SppConstants.MAX_VALUE)
        target.set(clamped)
    }

    fun setSteeringValue(value: Int) {
        val clamped = value.coerceIn(SppConstants.MIN_VALUE, SppConstants.MAX_VALUE)
        steering.set(clamped)
    }

    fun start() {
        if (running) return
        running = true
        _lastRxMillis.set(nowMillis())
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
            outputStream.write("M2:0\n".toByteArray(Charsets.US_ASCII))
            outputStream.flush()
        }
    }

    fun onFailure(listener: (Throwable) -> Unit) { failureListener = listener }

    /** ms ecoulees depuis la derniere trame recue. Long.MAX_VALUE si jamais. */
    fun freshness(): Long {
        val last = _lastRxMillis.get()
        if (last == 0L) return Long.MAX_VALUE
        return nowMillis() - last
    }

    /** Visible for tests. */
    internal fun tickForTest() = tickOnce()
    internal fun parseLineForTest(line: String) = parseLine(line)

    private fun runSender() {
        while (running) {
            try {
                tickOnce()
                tickSteeringOnce()
                pingIfDue()
                refreshLinkFreshness()
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
            // Socket closed or interrupted — sender's failure path covers reconnection.
        }
    }

    private fun parseLine(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        _lastRxMillis.set(nowMillis())
        when {
            trimmed.startsWith("BAT:") -> {
                // Formats acceptes :
                //   BAT:cv,pct                       (firmware v0.3 — legacy)
                //   BAT:cv,pct,raw,pinMv,vbatMv      (firmware v0.4+ — diagnostic enrichi)
                val payload = trimmed.substring(4)
                val parts = payload.split(",")
                if (parts.size != 2 && parts.size != 5) return
                val cv = parts[0].trim().toIntOrNull() ?: return
                val pct = parts[1].trim().toIntOrNull() ?: return
                val raw = if (parts.size == 5) parts[2].trim().toIntOrNull() else null
                val pinMv = if (parts.size == 5) parts[3].trim().toIntOrNull() else null
                val vbatMv = if (parts.size == 5) parts[4].trim().toIntOrNull() else null
                _battery.value = BatteryState(
                    centivolts = cv,
                    percent = pct.coerceIn(0, 100),
                    rawAdc = raw,
                    pinMv = pinMv,
                    vbatMv = vbatMv
                )
            }
            trimmed == "PONG" -> { /* freshness deja mise a jour ci-dessus */ }
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

    private fun tickSteeringOnce() {
        val now = clockNanos()
        val current = steering.get()
        val firstSend = lastSteeringSentNanos == Long.MIN_VALUE
        val sinceLastMs = if (firstSend) Long.MAX_VALUE else (now - lastSteeringSentNanos) / 1_000_000L
        val changed = current != lastSteeringSent
        val heartbeatDue = sinceLastMs >= SppConstants.HEARTBEAT_MS
        val throttleOk = firstSend || sinceLastMs >= SppConstants.THROTTLE_MIN_MS
        if ((changed || heartbeatDue || firstSend) && throttleOk) {
            outputStream.write("M2:$current\n".toByteArray(Charsets.US_ASCII))
            outputStream.flush()
            lastSteeringSent = current
            lastSteeringSentNanos = now
        }
    }

    private fun pingIfDue() {
        val now = clockNanos()
        val sinceLastPingMs = if (lastPingNanos == Long.MIN_VALUE) Long.MAX_VALUE
        else (now - lastPingNanos) / 1_000_000L
        if (sinceLastPingMs >= SppConstants.PING_INTERVAL_MS) {
            outputStream.write("PING\n".toByteArray(Charsets.US_ASCII))
            outputStream.flush()
            lastPingNanos = now
        }
    }

    private fun refreshLinkFreshness() {
        _linkFreshMs.value = freshness()
    }
}
