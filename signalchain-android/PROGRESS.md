# SignalChain Android progress

## Done

- Added Python/Kotlin analyzer parity reporting and JVM tests for golden synthetic signals and empty-noise handling.
- Removed repeated VAD energy sorting and ZCR averaging from the per-frame classifier; 10 minutes of 16 kHz audio completes in about 0.60 seconds on this JVM.
- Added seeded routing coverage for all three policies, in-memory ONNX model constructors, a Python-generated ONNX inference golden, non-16 kHz output-length coverage, and an ML fallback test.
- Disabled Android backups, limited FileProvider access to `cache/exports/`, added managed cache cleanup at startup, on unreferenced input replacement, and on history clear, and added a pre-decode 10-minute duration check. Microphone capture stops at 10 minutes.
- Added release smoke-test instructions in `RELEASE_SMOKE_TEST.md`.

## Verified (with SHAs)

All listed commits are local on `main` and have **not** been pushed to `origin/main`.

- `e7817cd` — Analyzer Kotlin/Python parity test, runtime analysis log, and explicit empty-noise behavior.
- `6a07619` — VAD output-preservation tests and linear-time frame classification.
- `a99c183` — Three decision branches and JVM ONNX inference/fallback coverage.
- `4a047ba` — Audio cache, FileProvider, duration, and logging privacy fixes.
- `3efa8ee` — R8 release verification adjustment and owner smoke-test checklist.
- Python parity run: 40 noisy test files. Global SNR min/median/max `-4.59 / 9.68 / 25.92 dB`; CV `0.0895 / 0.3221 / 2.6224`; routes: 18 Full adaptive, 14 Classical DSP, 8 Light touch.
- Three Python-generated analyzer goldens match Kotlin within `0.02 dB` SNR, `0.0002` CV, and `0.0001` activity ratio. `emptyNoiseHasExplicitNonStationaryResult` asserts `CV = +Infinity` and non-stationary.
- `optimizedVadPreservesLegacySpeechArraysForThreeSignals` compares all boolean arrays to the pre-optimization implementation. `tenMinuteVadFinishesUnderOneSecond` passed in about `0.60 s`.
- `seededSyntheticAudioReachesAllThreePolicies` verifies the mode and ML flag for each route. `modelByteConstructorMatchesPythonGoldenAndReturnsFiniteMask` compares 257 ONNX outputs within `1e-4` and checks output lengths at 16 kHz and 12 kHz. `failingModelLoadUsesDspOnlyAudioAndShowsWarning` checks retained DSP samples and a non-null warning.
- The required `clean test assembleDebug` command passed after the changes: 23 JVM tests, 0 failures. The release build passed with R8 class minification enabled and debug-signing fallback. The APK contains `assets/models/spectral_mask_denoiser.onnx` (46,586 bytes). The Task 1 debug-analysis message is absent from the minified DEX.
- The device smoke test has not been run; it is for the owner to perform.

### Task 1 root-cause finding

Kotlin and Python `AudioAnalyzer` use the same VAD-derived noise frames, global lower-15%-energy SNR, CV, and `0.55` stationarity threshold. The test set does **not** reproduce “always Full adaptive”: it routes 18 of 40 files there. The confirmed inconsistency is that the UI’s segmental SNR and the decision’s global SNR differ by a median `6.11 dB` and up to `11.98 dB`; they can describe the same recording very differently. Across noise categories, the brown-hum group has median CV `1.95` and 0/10 stationary labels, while bursty, tonal, and white groups have median CVs around `0.26–0.31` and 10/10 stationary labels each. This points to analyzer inputs and noise-frame labels as route drivers, but no physical-phone logs were available to prove why those recordings selected Full adaptive. No thresholds or DSP decision rules were changed.

## Open issues

- The owner still needs to run the release APK on a physical phone and check Full adaptive/ML execution, output playback, and sharing.
- The physical-phone “always Full adaptive” report is not fully explained without the new device log values.
- The two SNR estimators remain intentionally unchanged; a median `6.11 dB` absolute difference is documented for owner review.
- No release keystore is configured, so the APK is signed with the debug key.
- The automatic approval review rejected the requested push to shared `origin/main`; the changes remain in local commits.

## Next

- Run the checklist in `RELEASE_SMOKE_TEST.md` on the phone and share the logged analyzer values for a clip that routes unexpectedly.
- Review whether the UI’s before-SNR should display the same global SNR that feeds `DecisionAgent`; make any policy change only after owner review.
- Review and approve the local commit series for pushing to `origin/main`.
