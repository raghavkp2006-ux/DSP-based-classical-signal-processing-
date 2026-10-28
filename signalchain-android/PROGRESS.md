# SignalChain Android Progress

Branch: `main`

## Completed (P0 & P1 Release v1.0.0)

- **P0-1 Filter Corrections (`Eq.kt`)**: Fixed corner frequency calculation (evaluating normalized cutoff in cycles/sample rather than treating Nyquist fraction as cycles/sample), tap count scaling (`oddTaps` for sample rates like 44.1/48 kHz), bandpass center gain normalization matching `scipy.signal.firwin(scale=True)`, and low-pass filter guard. Added `scripts/make_eq_golden.py`, `eq_golden.json`, and `EqTest` verifying golden parity (< 1e-3 max abs error), 16 kHz frequency response (±0.5 dB), and 48 kHz consistency (±1.0 dB).
- **P0-2 Anti-Aliasing Resampler (`OnnxDenoiser.kt`)**: Replaced linear decimation with windowed-sinc anti-aliasing interpolation/decimation filter with Hann taper. Verified > 60 dB attenuation on out-of-band tones, < 0.01 RMS on 10 kHz tone, and unity passband fidelity.
- **P0-3 32-bit Integer PCM Decoding (`WavLoader.kt`)**: Corrected WAV decoding to branch on `(fmt, bits)` so 32-bit integer PCM (`fmt=1, bits=32`) is properly scaled by `2^31` rather than misread as IEEE float. Added unit tests for 16-bit, 32-bit int, and 32-bit float WAVs.
- **P0-4 Stage Diagnostic Dumps (`PipelineStageDumpTest.kt`)**: Automated pipeline stage dump test producing sequential WAV files (`00_input.wav` to `06_normalized.wav`) for listening diagnostics.
- **P1 Release Hardening**:
  - Configured release keystore (`signalchain-release.keystore`) and configured `app/build.gradle.kts` to throw `GradleException` if release signing credentials are missing.
  - Bumped version to `1.0.0` (versionCode `3`).
  - Added custom adaptive launcher icons (`ic_launcher`, `ic_launcher_round`, foreground vector, background color).
  - Removed unused `READ_MEDIA_AUDIO` permission from `AndroidManifest.xml` (using Storage Access Framework / `OpenDocument`).
  - Added `android:configChanges` to `MainActivity` to prevent mid-processing activity recreation on rotation.
  - Clarified UI metrics with "Segmental SNR (VAD-based)" label in `ResultsScreen`.
  - Allocated 2048 MB heap to Gradle JVM args to ensure R8 minification completes reliably.

## Verification Status

- `./gradlew test` passes 100% on both debug and release variants.
- `./gradlew assembleDebug` builds successfully.
- `./gradlew assembleRelease` builds with R8 shrinking, obfuscation, and release keystore signing.
- Verified signature via `apksigner verify --print-certs app-release.apk` (SHA-256 fingerprint verified).

## Prior Commit History (Integrated on `origin/main`)

- `e7817cd` — Analyzer Kotlin/Python parity test, runtime analysis log, and explicit empty-noise behavior.
- `6a07619` — VAD output-preservation tests and linear-time frame classification.
- `a99c183` — Three decision branches and JVM ONNX inference/fallback coverage.
- `4a047ba` — Audio cache, FileProvider, duration, and logging privacy fixes.
- `3efa8ee` — R8 release verification adjustment and owner smoke-test checklist.
- `f8af4cf` — STT options spike (`STT_SPIKE.md`).

## Finish-up run 2026-10-28

- Phase 0 baseline: clean main matched origin/main at f68e1000c9e2bdc87fb07b04256886b55d976e11 after fetch. `gradlew.bat clean test` succeeded: debug 30/30 and release 30/30, zero failures/errors/skips (XML reports under app/build/test-results/testDebugUnitTest and testReleaseUnitTest).
- Runtime: Temurin JDK 17.0.20.1; Python 3.12.10; numpy 2.5.3, scipy 1.18.1, torch 2.14.0, onnx 1.23.0. Installed missing pystoi 0.4.1 and soundfile 0.14.0.
- Signing prerequisites: local.properties, all four RELEASE_* keys, and referenced keystore exist; secrets were not printed.
- The 40-row metadata existed but all 80 clean/noisy references were missing locally. Regenerated with `python make_ablation_testset.py --data C:\Users\ragha\Downloads\dev-clean\LibriSpeech\dev-clean --output ablation_testset_regen`; all 40 file_id/noise_type/snr_db tuples matched. Copied regenerated clean/noisy directories into ablation_testset without changing tracked metadata. WAVs remain ignored.
- Phases 1–7 are not completed by this baseline entry. Device verification: PENDING OWNER DEVICE TEST.

## EQ correctness follow-up — 2026-10-28

- Added delay-compensated EQ for the app while retaining the legacy reference. Independent convolution max error 2.3841858e-7 at fixed 1e-5 tolerance; full-band response and 18 loud-input cases pass. Original golden tests unchanged.
- `gradlew.bat test assembleRelease`: 34/34 debug and 34/34 release with the optional cafe excerpt test enabled. Raw evidence and reproducible collection scripts are committed; private recordings are not.
- Signed APK installed successfully on the connected OnePlus 13R and cold launch succeeded; installed APK SHA-256 matches the tested build. Device listening and processing of the updated build remain pending. Intelligibility gain and echo removal: not measured.
- See RELEASE_NOTES_EQ_ALIGNMENT.md for reproduction commands, limits, headroom values and signature/hash evidence. This follow-up does not complete phases 1–7 of the original finish-up request.

## Full-recording memory fix — 2026-10-28

- Diagnosed phone crashes as OutOfMemoryError at the 256 MiB heap limit. Removed boxed per-sample energy allocations and bounded ONNX working spectra with context-preserving chunks; original whole-clip method retained for parity checks.
- 36/36 debug and 36/36 release tests passed under a 256 MiB JVM heap, including the complete 60.7-second cafe input. Chunked/whole ML maximum difference 0.0 in 16/44.1/48 kHz parity tests; no tolerance changes.
- Signed replacement installed; the actual original MP3 decoded and completed enhancement on the OnePlus 13R, Full adaptive, same process alive. Device processing is now checked for this file. Post-run Java heap allocation ~25 MB is a snapshot, not a peak measurement.
- See RELEASE_NOTES_MEMORY_FIX.md. Echo removal and intelligibility gain remain unmeasured; broader release smoke checklist and original finish-up phases remain incomplete.

- Phase 1A pre-refactor snapshot: current aligned-EQ and bounded-ML inline chain captured for Light touch and Full adaptive. Debug/release targeted tests each passed 1/1 on 2026-10-28; ONNX fallback warning is asserted absent. Extraction and the rest of Phase 1 remain outstanding.
- Phase 1A extraction: PipelineCore preserves the pre-extraction SHA-256 snapshots in debug and release; agent parity, stage dumps and diagnostics remain outstanding.
- Classical DSP snapshot added: debug/release targeted tests passed; analyzer asserts stationary noise and SNR 5–15 dB. Phase 1B investigation found Python eq_gain=0.5 and Kotlin eqGain=0.6 for Classical DSP; parity is not yet green.
- Classical DSP EQ override 0.6 -> 0.5 matches Python: snapshot old 71cccba12f30fbc47eb917237af600a79d119c5c9a19d6ee3b1c39a7ae626693; new e4642a25efec66eb481a43f27843345987042ecdcff816d367a4ad2918c2fbbd. On 32000 float samples: max absolute difference 0.01702040433883667; RMS difference 0.0002855638636517363 (-70.8859350142186 dBFS).
