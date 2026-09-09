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
