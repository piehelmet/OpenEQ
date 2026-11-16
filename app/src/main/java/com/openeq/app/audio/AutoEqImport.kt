package com.openeq.app.audio

import java.util.UUID
import kotlin.math.abs

object AutoEqImport {
    fun parse(text: String, fallbackName: String = "Imported"): EqPreset? {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.isEmpty()) return null
        return parseParametric(lines, fallbackName) ?: parseGraphic(lines, fallbackName)
    }

    private fun parseParametric(lines: List<String>, name: String): EqPreset? {
        if (!lines.any { it.startsWith("Filter", ignoreCase = true) && it.contains("Fc", ignoreCase = true) }) return null
        var preamp = 0f
        val bands = mutableListOf<EqBand>()
        for (line in lines) {
            if (line.startsWith("Preamp", ignoreCase = true)) {
                preamp = Regex("([-+]?\\d+(?:\\.\\d+)?)").find(line)?.groupValues?.get(1)?.toFloatOrNull() ?: preamp
                continue
            }
            if (!line.startsWith("Filter", ignoreCase = true)) continue
            val enabled = !line.contains(" OFF ", ignoreCase = true) && !line.contains(": OFF", ignoreCase = true)
            val type = when {
                Regex("\\bLSC\\b", RegexOption.IGNORE_CASE).containsMatchIn(line) -> FilterType.LOW_SHELF
                Regex("\\bHSC\\b", RegexOption.IGNORE_CASE).containsMatchIn(line) -> FilterType.HIGH_SHELF
                else -> FilterType.PEAK
            }
            val fc = Regex("Fc\\s+([\\d.]+)\\s*Hz", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)?.toFloatOrNull()
            val gain = Regex("Gain\\s+([-+]?\\d+(?:\\.\\d+)?)\\s*dB", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            val q = Regex("\\bQ\\s+([\\d.]+)", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)?.toFloatOrNull() ?: 1.41f
            if (fc != null) bands += EqBand(frequencyHz = fc, gainDb = gain, q = q, type = type, enabled = enabled)
        }
        if (bands.isEmpty()) return null
        return EqPreset(id = "import-${UUID.randomUUID()}", name = name, preampDb = preamp, bands = bands)
    }

    // GraphicEQ: 20 6.0; 21 5.8; ...  (freq gain pairs)
    private fun parseGraphic(lines: List<String>, name: String): EqPreset? {
        val joined = lines.joinToString(" ")
        val body = joined.substringAfter(":", joined)
        val pairs = Regex("([\\d.]+)\\s+([-+]?\\d+(?:\\.\\d+)?)").findAll(body).mapNotNull {
            val f = it.groupValues[1].toFloatOrNull() ?: return@mapNotNull null
            val g = it.groupValues[2].toFloatOrNull() ?: return@mapNotNull null
            f to g
        }.toList()
        if (pairs.size < 3) return null
        val bands = pairs.filter { it.first in 20f..20000f }.map { (f, g) ->
            EqBand(frequencyHz = f, gainDb = g, q = 1.41f, type = FilterType.PEAK)
        }
        if (bands.isEmpty()) return null
        return EqPreset(id = "import-${UUID.randomUUID()}", name = name, bands = bands)
    }
}
