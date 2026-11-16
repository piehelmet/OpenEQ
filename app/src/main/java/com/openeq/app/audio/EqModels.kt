package com.openeq.app.audio

enum class FilterType { PEAK, LOW_SHELF, HIGH_SHELF }

data class EqBand(
    val frequencyHz: Float,
    val gainDb: Float,
    val q: Float = 1.41f,
    val type: FilterType = FilterType.PEAK,
    val enabled: Boolean = true,
)

data class EqPreset(
    val id: String,
    val name: String,
    val preampDb: Float = 0f,
    val bands: List<EqBand>,
    val bassBoostDb: Float = 0f,
    val virtualizerDb: Float = 0f,
    val loudnessDb: Float = 0f,
    val limiterEnabled: Boolean = true,
)

object DefaultPresets {
    val flat10 = EqPreset(
        id = "flat",
        name = "Flat",
        bands = listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f).map {
            EqBand(frequencyHz = it, gainDb = 0f)
        }
    )

    val bass = flat10.copy(
        id = "bass",
        name = "Bass Boost",
        bands = flat10.bands.map {
            when (it.frequencyHz) {
                31f -> it.copy(gainDb = 6f, type = FilterType.LOW_SHELF)
                62f -> it.copy(gainDb = 4.5f)
                125f -> it.copy(gainDb = 3f)
                250f -> it.copy(gainDb = 1.5f)
                else -> it
            }
        },
        bassBoostDb = 3f,
    )

    val vocal = flat10.copy(
        id = "vocal",
        name = "Vocal Clarity",
        bands = flat10.bands.map {
            when (it.frequencyHz) {
                250f -> it.copy(gainDb = -1.5f)
                500f -> it.copy(gainDb = -1f)
                1000f -> it.copy(gainDb = 1.5f)
                2000f -> it.copy(gainDb = 3f)
                4000f -> it.copy(gainDb = 3.5f)
                8000f -> it.copy(gainDb = 1.5f)
                else -> it
            }
        }
    )

    val parametricStarter = EqPreset(
        id = "parametric",
        name = "Parametric (5-band)",
        preampDb = -2f,
        bands = listOf(
            EqBand(60f, 3f, 0.7f, FilterType.LOW_SHELF),
            EqBand(250f, -2f, 1.2f, FilterType.PEAK),
            EqBand(1000f, 0f, 1.0f, FilterType.PEAK),
            EqBand(3500f, 2.5f, 1.4f, FilterType.PEAK),
            EqBand(10000f, 2f, 0.8f, FilterType.HIGH_SHELF),
        )
    )

    val all = listOf(flat10, bass, vocal, parametricStarter)
}
