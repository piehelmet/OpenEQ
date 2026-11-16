package com.openeq.app.audio

import android.media.audiofx.DynamicsProcessing
import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * Audio effect engine built on DynamicsProcessing.
 * Note: DynamicsProcessing exposes cutoff frequency and gain per band but not biquad Q/filter type.
 * We store full parametric data (freq/gain/Q/type) for UI, curve, and future/custom DSP engines,
 * and map available parameters into the active effect.
 */
class EqEngine {
    private var dp: DynamicsProcessing? = null
    private var sessionId: Int = 0
    private var activeBandCount: Int = -1
    var enabled: Boolean = true
        private set

    var preset: EqPreset = DefaultPresets.flat10
        private set

    fun attach(audioSessionId: Int = 0) {
        sessionId = audioSessionId
        rebuild(preset)
    }

    fun setEnabled(on: Boolean) {
        enabled = on
        dp?.enabled = on
    }

    fun applyPreset(newPreset: EqPreset) {
        preset = newPreset
        if (dp == null || newPreset.bands.size != activeBandCount) {
            rebuild(newPreset)
        } else {
            applyToCurrent(newPreset)
        }
    }

    private fun rebuild(p: EqPreset) {
        releaseFx()
        val count = p.bands.size.coerceAtLeast(1)
        try {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                /* preEqInUse = */ true,
                /* preEqBandCount = */ count,
                /* mbcInUse = */ true,
                /* mbcBandCount = */ 1,
                /* postEqInUse = */ false,
                /* postEqBandCount = */ 0,
                /* limiterInUse = */ true
            ).build()
            dp = DynamicsProcessing(0, sessionId, config).apply { this.enabled = this@EqEngine.enabled }
            activeBandCount = count
            applyToCurrent(p)
        } catch (t: Throwable) {
            Log.e("EqEngine", "Failed to create DynamicsProcessing", t)
            dp = null
            activeBandCount = -1
        }
    }

    private fun applyToCurrent(p: EqPreset) {
        val fx = dp ?: return
        try {
            val preEq = fx.getPreEqByChannelIndex(0)
            val n = min(p.bands.size, preEq.bandCount)
            for (i in 0 until n) {
                val band = p.bands[i]
                val gain = if (band.enabled) clamp(band.gainDb + p.preampDb, -24f, 24f) else 0f
                val eqBand = preEq.getBand(i)
                eqBand.isEnabled = band.enabled
                eqBand.cutoffFrequency = band.frequencyHz.coerceIn(20f, 20000f)
                eqBand.gain = gain
                preEq.setBand(i, eqBand)
            }
            fx.setPreEqByChannelIndex(0, preEq)
            fx.setPreEqAllChannelsTo(preEq)

            val limiter = fx.getLimiterByChannelIndex(0)
            limiter.isEnabled = p.limiterEnabled
            limiter.linkGroup = 0
            limiter.attackTime = 1f
            limiter.releaseTime = 60f
            limiter.ratio = 10f
            limiter.threshold = -1f
            limiter.postGain = 0f
            fx.setLimiterByChannelIndex(0, limiter)
            fx.setLimiterAllChannelsTo(limiter)
        } catch (t: Throwable) {
            Log.e("EqEngine", "Failed to apply preset", t)
        }
    }

    fun updateBand(index: Int, gainDb: Float) {
        if (index !in preset.bands.indices) return
        val bands = preset.bands.toMutableList()
        bands[index] = bands[index].copy(gainDb = gainDb)
        applyPreset(preset.copy(bands = bands))
    }

    fun release() {
        releaseFx()
        activeBandCount = -1
    }

    private fun releaseFx() {
        try { dp?.release() } catch (_: Throwable) {}
        dp = null
    }

    private fun clamp(v: Float, lo: Float, hi: Float): Float = max(lo, min(hi, v))
}
