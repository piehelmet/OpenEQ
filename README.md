# OpenEQ (WIP)

Native Android (Kotlin, Jetpack Compose) equalizer app inspired by feature-rich system equalizers, built as an original personal/learning project (not a clone of any proprietary app).

## Current features
- Min SDK 28 / Target SDK 35
- `DynamicsProcessing`-based audio engine (frequency-resolution variant) with preamp and limiter
- **Parametric EQ editing:** per-band frequency, gain, Q, filter type (Peak/Low Shelf/High Shelf), enable/disable, add/remove bands
- **Response curve UI:** real-time approximate magnitude response visualization (includes F/G/Q/type and preamp)
- **Presets:** starter presets (Flat, Bass Boost, Vocal Clarity, 5-band Parametric) + in-session imported presets
- **AutoEQ import:** paste `ParametricEQ.txt` or `GraphicEQ.txt` content to create a preset
- Foreground service scaffold for persistent processing

> Engine note: `DynamicsProcessing` natively applies band frequency and gain (plus preamp/limiter). Q and filter type are stored for the curve, preset portability/import-export, and can drive a future custom biquad DSP path for full parametric accuracy.

## Open-source presets that work well (AutoEQ)

The best practical source is **[AutoEQ](https://github.com/jaakkopasanen/AutoEq)** (open-source headphone/IEM correction EQ project). It publishes generated EQ settings for thousands of headphones based on measurements from sources like **oratory1990**, **Crinacle**, **Rtings**, **Headphone.com**, and others (see the AutoEQ repo/results and each source’s terms).

**How to use with OpenEQ:**
1. In the [AutoEQ results index](https://github.com/jaakkopasanen/AutoEq/tree/master/results), find your headphone (pick a source/target you trust, e.g. *oratory1990* or *Crinacle*).
2. Open that headphone’s folder and copy the contents of **`ParametricEQ.txt`** (preferred for parametric bands: includes Preamp, Fc, Gain, Q, and filter type like PK/LSC/HSC) or **`GraphicEQ.txt`**.
3. In OpenEQ, tap **Import AutoEQ**, paste the text, name it, and import.

These presets are corrective (aim to neutralize a headphone’s response toward a target) and usually translate well as a starting point; you can fine-tune bands/Q in the parametric editor to taste.

## Planned / roadmap (feasible targets)
- **Sound quality / DSP:** custom biquad engine for true Q/filter-type processing, refined MBC/compressor/expander, limiter tuning, optional convolver/IR
- **EQ:** more bands, L/R or M/S options (where feasible), live spectrum analyzer
- **Effects:** bass boost, virtualizer, loudness, reverb, stereo balance/crossfeed
- **Routing/control:** robust output/session attachment, per-app/device profiles, auto-enable rules
- **Presets:** persistent storage, file import/export, backup/restore
- **UX:** visualizations, quick settings tile/widgets, notification controls, themes

System-wide behavior and some effects depend on Android version/OEM and audio session access.

## Build
Requires Android SDK (platform 35, build-tools 35+) and JDK 17. Open `OpenEQ/` in Android Studio and sync/build, or use a local Gradle installation/wrapper if added.

```bash
# if you have a Gradle wrapper installed:
./gradlew :app:assembleDebug
```
