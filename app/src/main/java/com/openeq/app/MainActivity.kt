package com.openeq.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState as rememberHScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openeq.app.audio.*
import kotlin.math.*

private val Accent = Color(0xFF7C9CFF)
private val Accent2 = Color(0xFF4DD0C8)
private val Bg = Color(0xFF0F1115)
private val Surface1 = Color(0xFF171A21)
private val Surface2 = Color(0xFF1E222B)
private val OnDim = Color(0xFF9AA3B2)

class MainActivity : ComponentActivity() {
    private val engine = EqEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        engine.attach(0)
        setContent {
            val scheme = darkColorScheme(
                primary = Accent, secondary = Accent2, background = Bg, surface = Surface1,
                onBackground = Color(0xFFE8ECF3), onSurface = Color(0xFFE8ECF3), surfaceVariant = Surface2
            )
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize(), color = Bg) { EqScreen(engine) }
            }
        }
    }

    override fun onDestroy() {
        engine.release()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqScreen(engine: EqEngine) {
    var preset by remember { mutableStateOf(engine.preset) }
    var enabled by remember { mutableStateOf(engine.enabled) }
    var presets by remember { mutableStateOf(DefaultPresets.all.toMutableList()) }
    var selectedBand by remember { mutableIntStateOf(0) }
    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importName by remember { mutableStateOf("AutoEQ Import") }

    fun apply(p: EqPreset) {
        preset = p.copy(bands = p.bands.toList())
        engine.applyPreset(preset)
        selectedBand = selectedBand.coerceIn(0, (preset.bands.size - 1).coerceAtLeast(0))
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            containerColor = Surface1,
            title = { Text("Import AutoEQ preset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = importName, onValueChange = { importName = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = importText, onValueChange = { importText = it }, label = { Text("Paste ParametricEQ.txt or GraphicEQ") }, modifier = Modifier.fillMaxWidth().height(160.dp))
                    Text("Source: github.com/jaakkopasanen/AutoEq (copy a headphone’s ParametricEQ.txt).", color = OnDim, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val imported = AutoEqImport.parse(importText, importName.ifBlank { "Imported" })
                    if (imported != null) {
                        presets = (presets + imported).toMutableList(); apply(imported); importText = ""; showImport = false
                    }
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg),
                title = { Text("OpenEQ", fontWeight = FontWeight.SemiBold) },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                        Text(if (enabled) "On" else "Bypass", color = if (enabled) Accent2 else OnDim, fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                        Switch(checked = enabled, onCheckedChange = { enabled = it; engine.setEnabled(it) })
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = Surface1), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Frequency response", color = OnDim, fontSize = 13.sp)
                        Text("${preset.bands.size} bands · pre ${fmtDb(preset.preampDb)} dB", color = OnDim, fontSize = 12.sp)
                    }
                    EqCurve(preset = preset, enabled = enabled, modifier = Modifier.fillMaxWidth().height(190.dp))
                }
            }

            SectionHeader("Presets")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { p ->
                    val sel = p.id == preset.id
                    FilterChip(
                        selected = sel, onClick = { apply(p) },
                        label = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent.copy(alpha = 0.22f), selectedLabelColor = Accent)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { showImport = true }, label = { Text("Import AutoEQ") })
                AssistChip(onClick = {
                    val b = preset.bands.toMutableList(); b.add(EqBand(1000f, 0f, 1.41f, FilterType.PEAK, true))
                    apply(preset.copy(id = "custom-${System.currentTimeMillis()}", bands = b))
                }, label = { Text("+ Band") })
                AssistChip(onClick = {
                    if (preset.bands.size > 1) { val b = preset.bands.toMutableList(); b.removeAt(selectedBand.coerceIn(b.indices)); apply(preset.copy(id = "custom-${System.currentTimeMillis()}", bands = b)) }
                }, label = { Text("Remove") })
            }

            SectionHeader("Bands")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                preset.bands.forEachIndexed { i, b ->
                    val sel = i == selectedBand
                    ElevatedFilterChip(
                        selected = sel, onClick = { selectedBand = i },
                        label = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatFreq(b.frequencyHz), fontSize = 12.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                            Text("${fmtDb(b.gainDb)} dB", fontSize = 11.sp, color = if (b.gainDb >= 0) Accent2 else Color(0xFFFF8A80))
                        } },
                        colors = FilterChipDefaults.elevatedFilterChipColors(selectedContainerColor = Surface2, containerColor = Surface1)
                    )
                }
            }

            if (preset.bands.isNotEmpty()) {
                val idx = selectedBand.coerceIn(preset.bands.indices)
                val band = preset.bands[idx]
                fun updateBand(mut: (EqBand) -> EqBand) {
                    val b = preset.bands.toMutableList(); b[idx] = mut(b[idx]); apply(preset.copy(bands = b))
                }
                Card(colors = CardDefaults.cardColors(containerColor = Surface1), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Band ${idx + 1}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(if (band.enabled) "Enabled" else "Disabled", color = OnDim, fontSize = 12.sp)
                            Spacer(Modifier.width(6.dp))
                            Switch(checked = band.enabled, onCheckedChange = { v -> updateBand { it.copy(enabled = v) } })
                        }
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            val types = listOf(FilterType.PEAK to "Peak", FilterType.LOW_SHELF to "Low shelf", FilterType.HIGH_SHELF to "High shelf")
                            types.forEachIndexed { ti, (t, label) ->
                                SegmentedButton(selected = band.type == t, onClick = { updateBand { it.copy(type = t) } },
                                    shape = SegmentedButtonDefaults.itemShape(ti, types.size), label = { Text(label, fontSize = 12.sp, maxLines = 1) })
                            }
                        }
                        LabeledSlider("Frequency", formatFreq(band.frequencyHz), log10(band.frequencyHz.coerceIn(20f, 20000f)), log10(20f)..log10(20000f)) {
                            updateBand { b0 -> b0.copy(frequencyHz = 10f.pow(it).coerceIn(20f, 20000f)) }
                        }
                        LabeledSlider("Gain", "${fmtDb(band.gainDb)} dB", band.gainDb, -12f..12f) { v -> updateBand { it.copy(gainDb = v) } }
                        LabeledSlider("Q (width)", String.format("%.2f", band.q), band.q, 0.3f..10f) { v -> updateBand { it.copy(q = v) } }
                        Text("Q & filter shape are visualized and stored for AutoEQ compatibility/future DSP; current engine applies frequency and gain.", color = OnDim, fontSize = 12.sp)
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Surface1), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LabeledSlider("Preamp", "${fmtDb(preset.preampDb)} dB", preset.preampDb, -12f..12f) { v -> apply(preset.copy(preampDb = v)) }
                    Text("Limiter enabled for headroom protection.", color = OnDim, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable private fun SectionHeader(t: String) { Text(t, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnDim) }

@Composable
private fun LabeledSlider(label: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, on: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp); Text(value, fontSize = 14.sp, color = Accent, fontWeight = FontWeight.Medium)
        }
        Slider(value = v, onValueChange = on, valueRange = range)
    }
}

private fun fmtDb(v: Float): String = (if (v >= 0) "+" else "") + String.format("%.1f", v)
private fun formatFreq(hz: Float): String = if (hz >= 1000f) String.format("%.1f kHz", hz / 1000f) else "${hz.roundToInt()} Hz"

@Composable
fun EqCurve(preset: EqPreset, enabled: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF0B0D12))) {
        val w = size.width; val h = size.height
        val padL = 28f; val padB = 18f; val padT = 10f; val padR = 10f
        val gw = w - padL - padR; val gh = h - padT - padB
        val minF = 20f; val maxF = 20000f; val dbRange = 12f
        fun xFor(f: Float): Float = padL + ((ln(f / minF) / ln(maxF / minF)) * gw).toFloat()
        fun yFor(db: Float): Float = padT + (gh / 2f) - (db / dbRange) * (gh / 2f)
        val grid = Color(0xFF222733)
        for (db in listOf(-12f, -6f, 0f, 6f, 12f)) drawLine(grid, Offset(padL, yFor(db)), Offset(padL + gw, yFor(db)), 1f)
        for (f in listOf(50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f)) drawLine(grid, Offset(xFor(f), padT), Offset(xFor(f), padT + gh), 1f)
        drawLine(Color(0xFF3A4252), Offset(padL, yFor(0f)), Offset(padL + gw, yFor(0f)), 2f)

        val path = Path(); val fill = Path()
        val steps = 260
        fun responseAt(f: Float): Float {
            var db = preset.preampDb
            preset.bands.forEach { b -> if (b.enabled) db += bandResponseDb(f, b) }
            return db
        }
        val y0 = yFor(responseAt(minF).coerceIn(-18f, 18f))
        path.moveTo(xFor(minF), y0); fill.moveTo(xFor(minF), padT + gh); fill.lineTo(xFor(minF), y0)
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            val f = minF * (maxF / minF).pow(t)
            val x = xFor(f); val y = yFor(responseAt(f).coerceIn(-18f, 18f))
            path.lineTo(x, y); fill.lineTo(x, y)
        }
        fill.lineTo(xFor(maxF), padT + gh); fill.close()
        val lineC = if (enabled) Accent else Color(0xFF556070)
        drawPath(fill, Brush.verticalGradient(listOf(lineC.copy(alpha = 0.25f), Color.Transparent), startY = padT, endY = padT + gh), style = Fill)
        drawPath(path, lineC, style = Stroke(width = 3.5f))
        // band markers
        preset.bands.forEach { b ->
            val x = xFor(b.frequencyHz.coerceIn(minF, maxF)); val y = yFor((preset.preampDb + b.gainDb).coerceIn(-12f, 12f))
            drawCircle(if (b.enabled) Accent2 else Color(0xFF555D6B), radius = 5f, center = Offset(x, y))
        }
    }
}

// Approximate RBJ-style peaking/shelf magnitude response for visualization
fun bandResponseDb(freq: Float, band: EqBand): Float {
    val f0 = band.frequencyHz.coerceIn(20f, 20000f)
    val g = band.gainDb
    val q = band.q.coerceIn(0.1f, 20f)
    return when (band.type) {
        FilterType.PEAK -> {
            val ratio = freq / f0
            val bw = ratio - 1f / ratio
            val x = q * bw
            val mag2 = (1f + (g / 12f) * (g / 12f) * x * x) / (1f + x * x)
            (10f * log10(mag2.toDouble().coerceAtLeast(1e-9))).toFloat() * sign(g)
        }
        FilterType.LOW_SHELF -> {
            val t = (log2((freq / f0).toDouble())).toFloat()
            val s = (g / 2f) * (1f - (t / sqrt(t * t + (1f / q).toDouble()).toFloat()).coerceIn(-1f, 1f))
            g - s.coerceIn(min(g, 0f), max(g, 0f))
        }
        FilterType.HIGH_SHELF -> {
            val t = (log2((f0 / freq).toDouble())).toFloat()
            val s = (g / 2f) * (1f - (t / sqrt(t * t + (1f / q).toDouble()).toFloat()).coerceIn(-1f, 1f))
            g - s.coerceIn(min(g, 0f), max(g, 0f))
        }
    }
}
