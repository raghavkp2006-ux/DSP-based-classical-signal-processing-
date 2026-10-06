# SignalChain Android progress

## Completed

- Created an isolated signalchain-android application folder from the supplied skeleton.
- Added WAV-only PCM16/PCM32 loading with mono fold, peak normalization, and minimum-length validation.
- Implemented frame parameters, periodic Hann framing, radix-2 FFT/IFFT, VAD, pre/de-emphasis, noise PSD, and segmental SNR.
- Implemented the deterministic rule-based analyzer/decision agent.
- Implemented the fused spectral-subtraction + adaptive Wiener pass, EQ, compressor, limiter, WAV writer, and audio playback.
- Wired the background pipeline and one-screen Compose UI with file picker, progress state, metrics, and playback controls.
- Added optional ONNX post-filter integration; missing/unavailable model errors fall back to DSP-only processing.

## Verification status

- The Gradle wrapper is included, but assembleDebug is blocked in this environment because no Android SDK is configured.
- The ONNX export and numerical parity check passed in the Python project with a maximum absolute difference of `8.94e-07`.
- The generated ONNX asset is included under `app/src/main/assets/models/`.

## Next

- Run the Gradle wrapper on a machine with JDK 17 and Android SDK.
- Run the required fixed-WAV numerical comparisons and correct any platform-specific discrepancies.

## Task 1 analyzer investigation (2026-10-06)

- Kotlin and Python `AudioAnalyzer` use the same frame energy, VAD-derived noise frames, global lower-15%-energy SNR, coefficient of variation, and `0.55` stationarity threshold. Three deterministic synthetic signals match the Python goldens within `0.02 dB` SNR, `0.0002` CV, and `0.0001` activity ratio.
- On the 40 noisy WAVs in `ablation_testset/`, Python global SNR is `-4.59 / 9.68 / 25.92 dB` min/median/max and CV is `0.0895 / 0.3221 / 2.6224`. The current decision rules route 18 Full adaptive, 14 Classical DSP, and 8 Light touch.
- The UI's segmental SNR and the decision's global SNR are not interchangeable: their absolute difference has a `6.11 dB` median and `11.98 dB` maximum on this set. This can make the displayed before-SNR disagree materially with the route input.
- No physical-phone analyzer logs were available, so the test set does not establish why the reported phone recordings always choose Full adaptive. Runtime logs now include all four analysis fields and the chosen mode, without paths. Thresholds remain unchanged pending device evidence.
- Empty noise is explicitly reported as `CV = +Infinity`, `stationaryNoise = false`; a unit test covers that behavior.
