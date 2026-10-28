# Python vs Kotlin stage comparison

Three Classical DSP conditions and the previously flagged bursty +15 dB Light touch case were processed with Python legacy EQ and Kotlin legacy EQ. Float WAV intermediates are locally generated; `stage_comparison.csv` records max absolute error, RMS error, and error relative to reference RMS. Python stage logs were captured with `run_pipeline` and are committed separately.

| Condition | Python speech frames | Kotlin speech frames | VAD mismatch / 298 | Pre-emphasis max error |
|---|---:|---:|---:|---:|
| white +5 dB | 18 | 236 | 218 | 8.93e-8 |
| white +10 dB | 103 | 237 | 134 | 9.15e-8 |
| tonal +5 dB | 37 | 236 | 199 | 8.21e-8 |
| bursty +15 dB (Light touch) | 95 | 95 | 0 | 8.65e-8 |

For the three Classical files the first divergence clearly beyond float rounding is VAD. Each routes with a five-frame speech hangover: Python uses NumPy slice updates, whereas Kotlin updates sequentially in ascending order, propagating earlier true flags forward within the same pass. Independently simulating Kotlin's loop from Python's zero-hangover VAD exactly reproduced the Kotlin VAD labels in all four files; this identifies the hangover update order as the cause of this observed VAD mismatch. Noise-PSD RMS differences for the three Classical examples are 0.3757, 0.1773 and 0.2678 respectively.

For bursty +15 dB, VAD matches exactly and PSD differs by RMS 9.60e-9; spectral output differs by RMS 5.55e-8 (max 1.08e-5), and normalized output by RMS 1.19e-8 (max 4.09e-7). There is no demonstrated large upstream divergence for that example; the first point beyond float noise is not established by these measurements. Its previously observed musical-kurtosis ratio difference remains a proxy-level observation.

Python's `spectral_subtract_wiener` applies a smoothed adaptive Wiener gain with a 0.25 floor after optional subtraction. Kotlin's `SpectralSubtraction.apply` includes the same gain formula and floor; it is incorporated into that stage rather than named as a separate stage.

No algorithm fix was applied. The measured hangover behavior warrants a targeted parity correction and regression tests, but any resulting signal-quality effects need evaluation before changing the shipped DSP.

Reproduce: set SIGNALCHAIN_ABLATION_METADATA to the absolute metadata path, run `gradlew.bat testDebugUnitTest --tests *StageComparisonExportTest*`, then `python compare_pipeline_stages.py`. Stage WAVs remain local and ignored.
