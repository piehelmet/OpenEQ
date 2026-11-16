package com.openeq.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openeq.app.audio.*
import kotlin.math.*

class MainActivity : ComponentActivity() {
    private val engine = EqEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        engine.attach(0)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    EqScreen(engine)
                }
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
    var importText by remember { mutableStateOf("") }
    var importName by remember { mutableStateOf("AutoEQ Import") }
    var showImport by remember { mutableStateOf(false) }

    fun apply(p: EqPreset) {
        preset = p.copy(bands = p.bands.toList())
        engine.applyPreset(preset)
        selectedBand = selectedBand.coerceIn(0, (preset.bands.size - 1).coerceAtLeast(0))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("OpenEQ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (enabled) "On" else "Off", modifier = Modifier.padding(end = 8.dp))
            Switch(checked = enabled, onCheckedChange = { enabled = it; engine.setEnabled(it) })
        }

        Text("Response curve", style = MaterialTheme.typography.titleMedium)
        EqCurve(preset = preset, modifier = Modifier.fillMaxWidth().height(180.dp).background(Color(0xFF101418), RoundedCornerShape(12.dp)).padding(8.dp))

        Text("Presets", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presets, key = { it.id }) { p ->
                FilterChip(selected = preset.id == p.id, onClick = { apply(p) }, label = { Text(p.name) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showImport = !showImport }) { Text(if (showImport) "Hide import" else "Import AutoEQ") }
            OutlinedButton(onClick = {
                val b = preset.bands.toMutableList()
                b.add(EqBand(1000f, 0f, 1.41f, FilterType.PEAK, true))
                apply(preset.copy(id = "custom-${System.currentTimeMillis()}", name = preset.name, bands = b))
            }) { Text("Add band") }
            OutlinedButton(onClick = {
                if (preset.bands.size > 1) {
                    val b = preset.bands.toMutableList(); b.removeAt(selectedBand.coerceIn(b.indices))
                    apply(preset.copy(id = "custom-${System.currentTimeMillis()}", bands = b))
                }
            }) { Text("Remove") }
        }

        if (showImport) {
            OutlinedTextField(value = importName, onValueChange = { importName = it }, label = { Text("Preset name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = importText, onValueChange = { importText = it }, label = { Text("Paste AutoEQ ParametricEQ.txt or GraphicEQ text") }, modifier = Modifier.fillMaxWidth().height(140.dp))
            Button(onClick = {
                val imported = AutoEqImport.parse(importText, importName.ifBlank { "Imported" })
                if (imported != null) {
                    presets = (presets + imported).toMutableList()
                    apply(imported)
                    importText = ""
                    showImport = false
                }
            }) { Text("Import preset") }
            Text("Tip: Use open presets from AutoEQ (https://github.com/jaakkopasanen/AutoEq) — copy the contents of a headphone’s ParametricEQ.txt or GraphicEQ.txt.", style = MaterialTheme.typography.bodySmall)
        }

        Text("Bands (parametric)", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(preset.bands) { i, b ->
                FilterChip(selected = i == selectedBand, onClick = { selectedBand = i },
                    label = { Text("${b.frequencyHz.toInt()}Hz ${"%.1f".format(b.gainDb)}dB") })
            }
        }

        if (preset.bands.isNotEmpty()) {
            val idx = selectedBand.coerceIn(preset.bands.indices)
            val band = preset.bands[idx]
            fun updateBand(mut: (EqBand) -> EqBand) {
                val b = preset.bands.toMutableList(); b[idx] = mut(b[idx])
                apply(preset.copy(id = preset.id, bands = b))
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Band ${idx + 1}: ${band.type}", fontWeight = FontWeight.SemiBold)
                    Text("Frequency: ${band.frequencyHz.toInt()} Hz")
                    Slider(value = log10(band.frequencyHz.coerceIn(20f, 20000f)), onValueChange = {
                        val f = (10f.pow(it)).coerceIn(20f, 20000f); updateBand { b0 -> b0.copy(frequencyHz = f) }
                    }, valueRange = log10(20f)..log10(20000f))
                    Text("Gain: ${"%.1f".format(band.gainDb)} dB")
                    Slider(value = band.gainDb, onValueChange = { v -> updateBand { b0 -> b0.copy(gainDb = v) } }, valueRange = -12f..12f)
                    Text("Q: ${"%.2f".format(band.q)} (stored for curve/future DSP; DynamicsProcessing applies F/G)")
                    Slider(value = band.q, onValueChange = { v -> updateBand { b0 -> b0.copy(q = v) } }, valueRange = 0.3f..10f)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(FilterType.PEAK, FilterType.LOW_SHELF, FilterType.HIGH_SHELF).forEach { t ->
                            FilterChip(selected = band.type == t, onClick = { updateBand { b0 -> b0.copy(type = t) } }, label = { Text(t.name.replace('_', ' ')) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Enabled", modifier = Modifier.weight(1f))
                        Switch(checked = band.enabled, onCheckedChange = { v -> updateBand { b0 -> b0.copy(enabled = v) } })
                    }
                }
            }
        }

        Text("Preamp: ${"%.1f".format(preset.preampDb)} dB")
        Slider(value = preset.preampDb, onValueChange = { v -> apply(preset.copy(preampDb = v)) }, valueRange = -12f..12f)

        Text(
            "Engine: DynamicsProcessing maps frequency & gain (and preamp/limiter). Q and filter type are preserved for the response curve, preset portability, and future custom biquad DSP.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun EqCurve(preset: EqPreset, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val minF = 20f; val maxF = 20000f
        val dbRange = 12f
        fun xFor(f: Float): Float = ((ln(f / minF) / ln(maxF / minF)) * w)
        fun yFor(db: Float): Float = (h / 2f) - (db / dbRange) * (h / 2f)
        // grid
        val grid = Color(0xFF2A2F36)
        for (db in listOf(-12f, -6f, 0f, 6f, 12f)) drawLine(grid, Offset(0f, yFor(db)), Offset(w, yFor(db)), 1f)
        for (f in listOf(50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f)) drawLine(grid, Offset(xFor(f), 0f), Offset(xFor(f), h), 1f)
        drawLine(Color(0xFF4A5568), Offset(0f, yFor(0f)), Offset(w, yFor(0f)), 2f)
        // response
        val path = Path()
        val steps = 240
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val f = minF * (maxF / minF).pow(t)
            var db = preset.preampDb
            preset.bands.forEach { b -> if (b.enabled) db += bandResponseDb(f, b) }
            val x = t * w; val y = yFor(db.coerceIn(-18f, 18f))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color(0xFF64B5F6), style = Stroke(width = 3f))
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
