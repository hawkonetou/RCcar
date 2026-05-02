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
import com.hotwheels.command.data.ThrottleLimitStore
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
fun DiagSheet(onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val entries by DiagLog.entries.collectAsStateWithLifecycle()
    val throttleLimit by ThrottleLimitStore.limit.collectAsStateWithLifecycle()
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
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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

            // ------- Journal
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
        }
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
