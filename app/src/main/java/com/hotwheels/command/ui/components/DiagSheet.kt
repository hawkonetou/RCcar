package com.hotwheels.command.ui.components

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.hotwheels.command.data.SessionStatsStore
import com.hotwheels.command.data.SteeringEnabledStore
import com.hotwheels.command.data.ThrottleLimitStore
import com.hotwheels.command.data.TuningStore
import com.hotwheels.command.data.VbatHistory
import com.hotwheels.command.ui.theme.LocalPalette
import com.hotwheels.command.ui.theme.MonoFamily
import com.hotwheels.command.util.DiagLog
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagSheet(onDismiss: () -> Unit, battery: com.hotwheels.command.bluetooth.BatteryState? = null) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val entries by DiagLog.entries.collectAsStateWithLifecycle()
    val throttleLimit by ThrottleLimitStore.limit.collectAsStateWithLifecycle()
    val steeringEnabled by SteeringEnabledStore.enabled.collectAsStateWithLifecycle()
    val trimSteering by TuningStore.trimSteering.collectAsStateWithLifecycle()
    val expoThrottle by TuningStore.expoThrottle.collectAsStateWithLifecycle()
    val expoSteering by TuningStore.expoSteering.collectAsStateWithLifecycle()
    val invertThrottle by TuningStore.invertThrottle.collectAsStateWithLifecycle()
    val invertSteering by TuningStore.invertSteering.collectAsStateWithLifecycle()
    val brakeOnRelease by TuningStore.brakeOnRelease.collectAsStateWithLifecycle()
    val motorTimeMs by SessionStatsStore.motorTimeMs.collectAsStateWithLifecycle()
    val distanceArb by SessionStatsStore.distanceArb.collectAsStateWithLifecycle()
    val vbatSamples by VbatHistory.samples.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.scrollToItem(entries.size - 1)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.bg,
        contentColor = palette.accent,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ------- Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "▸ JOURNAL DIAG (${entries.size})",
                    color = palette.accent,
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    letterSpacing = 2.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { exportCsv(context, entries) },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.surface, contentColor = palette.accent)
                    ) { Text("EXPORT CSV", fontSize = 12.sp, fontFamily = MonoFamily, letterSpacing = 1.5.sp) }
                    Button(
                        onClick = { DiagLog.clear() },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.surface, contentColor = palette.accent)
                    ) { Text("EFFACER", fontSize = 12.sp, fontFamily = MonoFamily, letterSpacing = 1.5.sp) }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.surface, contentColor = palette.accent)
                    ) { Text("FERMER", fontSize = 12.sp, fontFamily = MonoFamily, letterSpacing = 1.5.sp) }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ------- Limiteur de gaz
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "▸ LIMITEUR GAZ",
                    color = palette.textMuted,
                    fontFamily = MonoFamily,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Medium
                )
                ThrottleLimitStore.ALLOWED.forEach { v ->
                    val active = v == throttleLimit
                    Box(
                        modifier = Modifier
                            .border(
                                if (active) 2.dp else 1.dp,
                                if (active) palette.accent else palette.accentDim35
                            )
                            .background(if (active) palette.panel else palette.surface)
                            .clickable { ThrottleLimitStore.set(context, v) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "$v %",
                            color = if (active) palette.accent else palette.textMuted,
                            fontFamily = MonoFamily,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            letterSpacing = 1.5.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ------- Option direction (M2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "▸ DIRECTION (M2)",
                    color = palette.textMuted,
                    fontFamily = MonoFamily,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Medium
                )
                listOf(false to "OFF", true to "ON").forEach { (v, label) ->
                    val active = v == steeringEnabled
                    Box(
                        modifier = Modifier
                            .border(
                                if (active) 2.dp else 1.dp,
                                if (active) palette.accent else palette.accentDim35
                            )
                            .background(if (active) palette.panel else palette.surface)
                            .clickable { SteeringEnabledStore.set(context, v) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (active) palette.accent else palette.textMuted,
                            fontFamily = MonoFamily,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            letterSpacing = 1.5.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ------- Diagnostic batterie (firmware v0.4+ requis pour raw/pinMv/vbatMv)
            BatteryDiagPanel(battery)

            Spacer(Modifier.height(10.dp))

            // ------- Mini graph Vbat 60s
            VbatSparkline(samples = vbatSamples)

            Spacer(Modifier.height(10.dp))

            // ------- Stats de session
            SessionStatsPanel(
                motorTimeMs = motorTimeMs,
                distanceArb = distanceArb,
                onReset = { SessionStatsStore.reset(context) }
            )

            Spacer(Modifier.height(10.dp))

            // ------- Tuning : trim direction
            TrimSlider(
                label = "▸ TRIM DIRECTION",
                value = trimSteering,
                min = -20,
                max = 20,
                onChange = { TuningStore.setTrimSteering(context, it) },
                hint = "decalage permanent applique au M2 (-20..+20)"
            )

            Spacer(Modifier.height(8.dp))

            // ------- Tuning : expo throttle / steering
            TrimSlider(
                label = "▸ EXPO THROTTLE",
                value = expoThrottle,
                min = 0,
                max = 100,
                onChange = { TuningStore.setExpoThrottle(context, it) },
                hint = "0 = lineaire, 100 = expo pur (plus de finesse a basse vitesse)"
            )

            Spacer(Modifier.height(8.dp))

            TrimSlider(
                label = "▸ EXPO DIRECTION",
                value = expoSteering,
                min = 0,
                max = 100,
                onChange = { TuningStore.setExpoSteering(context, it) },
                hint = "0 = lineaire, 100 = expo pur"
            )

            Spacer(Modifier.height(10.dp))

            // ------- Toggles : inversion + brake on release
            ToggleRow("INV THROTTLE", invertThrottle) { TuningStore.setInvertThrottle(context, it) }
            ToggleRow("INV DIRECTION", invertSteering) { TuningStore.setInvertSteering(context, it) }
            ToggleRow("FREIN AU RELÂCHÉ", brakeOnRelease) { TuningStore.setBrakeOnRelease(context, it) }

            Spacer(Modifier.height(10.dp))

            // ------- Journal (hauteur fixe car embedded dans un scroll vertical)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .border(1.dp, palette.accentDim35)
                    .background(palette.surface)
                    .padding(8.dp)
            ) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(entries) { entry ->
                        Text(
                            text = entry,
                            color = palette.textPrimary,
                            fontFamily = MonoFamily,
                            fontSize = 12.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun BatteryDiagPanel(battery: com.hotwheels.command.bluetooth.BatteryState?) {
    val palette = LocalPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.accentDim35)
            .background(palette.surface)
            .padding(10.dp)
    ) {
        Text(
            text = "▸ DIAG BATTERIE",
            color = palette.textMuted,
            fontFamily = MonoFamily,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))
        if (battery == null) {
            Text(
                text = "▸ aucune trame BAT recue",
                color = palette.textSubtle,
                fontFamily = MonoFamily,
                fontSize = 12.sp
            )
            return
        }
        val plausible = battery.plausible
        val statusColor = if (plausible) palette.lime else palette.magenta
        val statusLabel = if (plausible) "PLAUSIBLE" else "ANORMALE (cv < 250)"
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DiagKv("CV (EMA)", "${battery.centivolts}")
            DiagKv("V (EMA)", "%.2f V".format(battery.volts))
            DiagKv("PCT", "${battery.percent} %")
            DiagKv("ÉTAT", statusLabel, valueColor = statusColor)
        }
        Spacer(Modifier.height(6.dp))
        // Champs ESP32 v0.4+ — null avec firmware legacy.
        if (battery.rawAdc != null || battery.pinMv != null || battery.vbatMv != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DiagKv("RAW ADC", battery.rawAdc?.toString() ?: "--", hint = "0..4095")
                DiagKv("PIN", battery.pinMv?.let { "$it mV" } ?: "--", hint = "Vbat / 2 attendu")
                DiagKv("VBAT", battery.vbatMv?.let { "$it mV" } ?: "--", hint = "apres pont")
            }
            Spacer(Modifier.height(6.dp))
            val raw = battery.rawAdc ?: -1
            val pin = battery.pinMv ?: -1
            val hint = when {
                raw in 0..50 || pin in 0..80 ->
                    "▸ pin pratiquement à 0 V → cablage flottant ou GPIO faux"
                pin in 1500..2200 ->
                    "▸ pin proche de Vbat/2 attendu → mesure OK"
                pin > 3000 ->
                    "▸ pin > 3 V → diviseur absent ou Vbat trop haute pour ADC"
                else -> "▸ valeurs intermediaires — cherche un faux contact"
            }
            Text(
                text = hint,
                color = palette.textMuted,
                fontFamily = MonoFamily,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            )
        } else {
            Text(
                text = "▸ firmware ESP32 v0.3 detecte (BAT:cv,pct legacy) — flasher v0.4+ pour les champs RAW/PIN/VBAT",
                color = palette.textSubtle,
                fontFamily = MonoFamily,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun DiagKv(
    label: String,
    value: String,
    hint: String? = null,
    valueColor: androidx.compose.ui.graphics.Color? = null
) {
    val palette = LocalPalette.current
    Column {
        Text(
            text = label,
            color = palette.textSubtle,
            fontFamily = MonoFamily,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp
        )
        Text(
            text = value,
            color = valueColor ?: palette.accent,
            fontFamily = MonoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 1.sp
        )
        if (hint != null) {
            Text(
                text = hint,
                color = palette.textSubtle,
                fontFamily = MonoFamily,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun TrimSlider(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    hint: String? = null
) {
    val palette = LocalPalette.current
    var widthPx by remember { mutableStateOf(1f) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = palette.textMuted,
                fontFamily = MonoFamily,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier
                    .border(1.dp, palette.accentDim35)
                    .background(palette.surface)
                    .clickable { onChange((value - 1).coerceAtLeast(min)) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                ) { Text("−", color = palette.accent, fontFamily = MonoFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                Text(
                    text = value.toString(),
                    color = palette.accent,
                    fontFamily = MonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
                Box(modifier = Modifier
                    .border(1.dp, palette.accentDim35)
                    .background(palette.surface)
                    .clickable { onChange((value + 1).coerceAtMost(max)) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                ) { Text("+", color = palette.accent, fontFamily = MonoFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                Box(modifier = Modifier
                    .border(1.dp, palette.accentDim35)
                    .background(palette.surface)
                    .clickable {
                        val mid = if (min < 0) 0 else min
                        onChange(mid)
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                ) { Text("0", color = palette.textMuted, fontFamily = MonoFamily, fontSize = 12.sp) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .border(1.dp, palette.accentDim35)
                .background(palette.surface)
                .pointerInput(min, max) {
                    detectDragGestures(
                        onDrag = { change, _ ->
                            val ratio = (change.position.x / widthPx).coerceIn(0f, 1f)
                            val v = (min + ratio * (max - min)).toInt()
                            onChange(v)
                            change.consume()
                        }
                    )
                }
        ) {
            widthPx = size.width
            val ratio = (value - min).toFloat() / (max - min).toFloat()
            // graduations milieu
            val midX = if (min < 0) (-min).toFloat() / (max - min).toFloat() * size.width else size.width / 2f
            drawLine(palette.accentDim50, Offset(midX, 0f), Offset(midX, size.height), 1f)
            // curseur
            val x = ratio * size.width
            drawLine(palette.accent, Offset(x, 0f), Offset(x, size.height), 3f)
        }
        if (hint != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = hint,
                color = palette.textSubtle,
                fontFamily = MonoFamily,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "▸ $label",
            color = palette.textMuted,
            fontFamily = MonoFamily,
            fontSize = 12.sp,
            letterSpacing = 2.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(false to "OFF", true to "ON").forEach { (v, t) ->
                val active = v == value
                Box(
                    modifier = Modifier
                        .border(if (active) 2.dp else 1.dp, if (active) palette.accent else palette.accentDim35)
                        .background(if (active) palette.panel else palette.surface)
                        .clickable { onChange(v) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = t,
                        color = if (active) palette.accent else palette.textMuted,
                        fontFamily = MonoFamily,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun VbatSparkline(samples: List<Int>) {
    val palette = LocalPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.accentDim35)
            .background(palette.surface)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "▸ VBAT 60S",
                color = palette.textMuted,
                fontFamily = MonoFamily,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium
            )
            if (samples.isNotEmpty()) {
                val min = samples.min()
                val max = samples.max()
                Text(
                    text = "min %.2f / max %.2f V".format(min / 100f, max / 100f),
                    color = palette.textSubtle,
                    fontFamily = MonoFamily,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(60.dp)) {
            if (samples.size < 2) return@Canvas
            // Echelle Y : 280 (Li-ion vide) -> 425 (full+marge)
            val yMin = 280
            val yMax = 425
            val w = size.width
            val h = size.height
            val step = w / (VbatHistory.CAPACITY - 1)
            // ligne de seuil 20% (vbat ~= 320)
            val warnY = h * (1f - (320 - yMin).toFloat() / (yMax - yMin).toFloat())
            drawLine(palette.stateError.copy(alpha = 0.4f), Offset(0f, warnY), Offset(w, warnY), 1f)
            // courbe
            for (i in 1 until samples.size) {
                val cv1 = samples[i - 1].coerceIn(yMin, yMax)
                val cv2 = samples[i].coerceIn(yMin, yMax)
                val y1 = h * (1f - (cv1 - yMin).toFloat() / (yMax - yMin).toFloat())
                val y2 = h * (1f - (cv2 - yMin).toFloat() / (yMax - yMin).toFloat())
                val x1 = (VbatHistory.CAPACITY - samples.size + i - 1) * step
                val x2 = (VbatHistory.CAPACITY - samples.size + i) * step
                drawLine(palette.accent, Offset(x1, y1), Offset(x2, y2), 2f)
            }
        }
        @Suppress("UNUSED_EXPRESSION") IntSize.Zero
    }
}

@Composable
private fun SessionStatsPanel(motorTimeMs: Long, distanceArb: Long, onReset: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.accentDim35)
            .background(palette.surface)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "▸ STATS CUMUL",
                color = palette.textMuted,
                fontFamily = MonoFamily,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium
            )
            Box(
                modifier = Modifier
                    .border(1.dp, palette.accentDim35)
                    .clickable { onReset() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("RESET", color = palette.accent, fontFamily = MonoFamily, fontSize = 11.sp, letterSpacing = 1.5.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            DiagKv("MOTEUR", formatDuration(motorTimeMs), hint = "temps moteur cumule")
            DiagKv("DISTANCE", "$distanceArb u", hint = "integrale |throttle|·dt")
        }
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return when {
        h > 0 -> "%dh %02dm %02ds".format(h, m, sec)
        m > 0 -> "%dm %02ds".format(m, sec)
        else -> "%ds".format(sec)
    }
}

private fun exportCsv(context: Context, entries: List<String>) {
    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val filename = "hotwheels_diag_$ts.csv"
    val csv = buildString {
        appendLine("timestamp,tag,message")
        entries.forEach { line ->
            // entry format : "HH:mm:ss.SSS TAG msg"
            val parts = line.split(" ", limit = 3)
            val tsField = parts.getOrNull(0).orEmpty()
            val tag = parts.getOrNull(1).orEmpty()
            val msg = parts.getOrNull(2).orEmpty().replace("\"", "\"\"")
            appendLine("$tsField,$tag,\"$msg\"")
        }
    }
    val written = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        writeViaMediaStore(context, filename, csv)
    } else {
        writeViaLegacyDownloads(filename, csv)
    }
    val msg = if (written) "Exporté : $filename" else "Échec export CSV"
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    DiagLog.log("EXPORT", "$msg (${entries.size} entrées)")
}

private fun writeViaMediaStore(context: Context, filename: String, csv: String): Boolean {
    return runCatching {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return@runCatching false
        resolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
        true
    }.getOrDefault(false)
}

@Suppress("DEPRECATION")
private fun writeViaLegacyDownloads(filename: String, csv: String): Boolean {
    return runCatching {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        FileOutputStream(File(dir, filename)).use { it.write(csv.toByteArray(Charsets.UTF_8)) }
        true
    }.getOrDefault(false)
}
