# SignalChain v1.1 PSD tracker — verification and ship evidence

## Step 1: reference suites

| Commit | Tests run | Failed | Skipped | Failures |
|---|---:|---:|---:|---|
| `f68e100` (v1.0.0) | 30 | 0 | 0 | None |
| `61ec650` (investigate) | 58 | 9 | 3 | ClassicalStagesAblationTest, CleanReferenceExportTest, ForcedCleanRouteTest, HangoverSensitivityExportTest, PreFixVadExportTest, V11BCounterfactualTest, V11BFactorialTest, V11DownstreamAblationTest, V11OriginalClassicalTest |

The investigate worktree failures were local-fixture/environment failures: missing ignored synthetic test data or unavailable clean-source audio. The four target tests were absent at v1.0.0. At `61ec650`, StftBoundaryTest passed, PipelineSnapshotTest passed, and the two classical export tests failed while opening local input files.

## Step 2: root causes and verdicts

`git diff 61ec650..HEAD --stat` is in the final branch diff. Production files changed are `NoisePsd.kt`, `SpectralSubtraction.kt`, and `PipelineCore.kt`; there are **no STFT, ISTFT, framing, or windowing source-file changes**.

| Test | v1.0.0 / 61ec650 | Path exercised | Verdict and resolution |
|---|---|---|---|
| StftBoundaryTest | Absent / passed | Calls `SpectralSubtraction.apply()` directly with a fixed PSD; no PSD estimator, no pipeline. | **B — unintended.** Sample 0 changed from expected 0.32590008 to 0.33008468 because the new cap/floor was enabled by the direct-call default. At the first sample, overlap-add has only edge-frame coverage, so a gain change shifts the normalized edge sample. The API now defaults to old limits; PipelineCore explicitly opts into the new limits when the new tracker is selected. No golden was changed. |
| ClassicalStagesAblationTest | Absent / failed on missing local fixture | Estimates PSD and compares a test-side spectral reconstruction with PipelineCore; exercises estimator and cap/floor. | **A — intentional C3 behavior.** The old helper used pre-v1.1 alpha/floor equations. It now mirrors the selected production constants. The previous mismatch was -0.012407453 vs -0.011247406 at sample 0; no golden was changed. |
| PipelineSnapshotTest | Absent / passed | Calls full PipelineCore on Light, Full/ONNX, and Classical inputs; PSD and cap/floor apply on processed routes. | **A — intentional new default.** Default C3 hashes are updated for the two changed outputs: Full/noisy `6e7675bd…953239` → `29b581f2…bad8b`; Classical `410b3e02…535f6` → `ca4e14fb…fefa76`. The new PSD estimate and limits alter those complete-pipeline outputs. |
| V11OriginalClassicalTest | Absent / failed on missing local fixture | Estimates PSD, then compares the test-side Classical stages against PipelineCore; estimator and cap/floor. | **A — intentional C3 behavior.** The helper used the previous stage equations. It now mirrors the new limits. Previous mismatch: -0.024594223 vs -0.023996735 at sample 0; no golden was changed. |

The legacy-flag snapshot test passes against all three `61ec650` hashes: steady `ac51fecdbf586873b3f08b405ced95c879390d57d607b78a5500a712ac92b03c`, Full/noisy `6e7675bdb73fc47d0f9e59d1620405b597e6fba584a03f35562bb039ad953239`, Classical `410b3e02ef9b9d0e55ccb84ef7d45c4b136d2fce31fda1130177d8384be535f6`. New cap/floor limits only apply when the new estimator is selected.

## Step 5: C0–C3 Classical ablation

STOI and SI-SDR intervals are 10,000-resample paired-by-file bootstrap 95% percentile CIs. Speech-band SI-SDR uses a 300–3400 Hz bandpass. Nonspeech attenuation is the pooled median of framewise output/noisy power ratios on clean-reference frames below −45 dBFS (25 ms frames, 10 ms hop); negative dB means reduced nonspeech energy.

| Set | Config | ΔSTOI (95% CI) | ΔESTOI | Full SI-SDR improvement dB (95% CI) | Speech-band SI-SDR improvement dB (95% CI) | Median nonspeech attenuation dB |
|---|---|---:|---:|---:|---:|---:|
| Original (n=14) | C0 legacy + old | -0.0379 [-0.0443, -0.0313] | -0.0385 | -1.964 [-4.474, +0.374] | +0.651 [-0.611, +1.915] | -5.75 |
| Original (n=14) | C1 new + old | +0.0052 [+0.0003, +0.0103] | +0.0432 | +0.534 [-2.269, +3.228] | +3.092 [+1.858, +4.345] | -5.45 |
| Original (n=14) | C2 legacy + new | -0.0306 [-0.0365, -0.0247] | -0.0296 | -1.853 [-4.364, +0.498] | +0.889 [-0.317, +2.072] | -4.99 |
| Original (n=14) | C3 new + new | +0.0056 [+0.0010, +0.0103] | +0.0414 | +0.326 [-2.412, +2.949] | +2.976 [+1.849, +4.131] | -4.79 |
| Padded (n=12) | C0 legacy + old | -0.0072 [-0.0116, -0.0030] | +0.0185 | +2.506 [+0.466, +4.413] | +4.852 [+4.007, +5.735] | -5.02 |
| Padded (n=12) | C1 new + old | +0.0023 [-0.0004, +0.0050] | +0.0316 | +2.446 [+0.483, +4.244] | +4.501 [+3.582, +5.474] | -4.39 |
| Padded (n=12) | C2 legacy + new | -0.0054 [-0.0088, -0.0022] | +0.0187 | +2.147 [+0.204, +3.932] | +4.486 [+3.689, +5.322] | -4.20 |
| Padded (n=12) | C3 new + new | +0.0023 [-0.0004, +0.0046] | +0.0296 | +2.057 [+0.254, +3.771] | +4.079 [+3.144, +5.056] | -3.76 |

## Step 6: complete agent C3

| Set | Route | n | Noisy STOI | Output STOI | ΔSTOI | Noisy ESTOI | Output ESTOI | ΔESTOI |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Original | Light touch | 8 | 0.9809 | 0.9809 | -0.0000 | 0.9357 | 0.9356 | -0.0000 |
| Original | Classical DSP | 14 | 0.9262 | 0.9318 | +0.0056 | 0.7788 | 0.8201 | +0.0414 |
| Original | Full adaptive / ONNX | 18 | 0.8237 | 0.8326 | +0.0089 | 0.6347 | 0.6704 | +0.0357 |
| Original | Overall (n=40) | 40 | 0.8910 | 0.8970 | +0.0060 | 0.7453 | 0.7759 | +0.0305 |
| Padded | Light touch | 7 | 0.9999 | 0.9999 | -0.0000 | 0.9989 | 0.9989 | -0.0000 |
| Padded | Classical DSP | 12 | 0.9547 | 0.9570 | +0.0023 | 0.8357 | 0.8653 | +0.0296 |
| Padded | Full adaptive / ONNX | 21 | 0.8227 | 0.8384 | +0.0157 | 0.6159 | 0.6607 | +0.0448 |
| Padded | Overall (n=40) | 40 | 0.8933 | 0.9022 | +0.0089 | 0.7489 | 0.7813 | +0.0324 |

## Gates

- G1 PASS: C3 original median PSD/oracle ratio 1.10× (limit 2.5×).
- G2 PASS: C3 ΔSTOI +0.00560 original (limit −0.010), +0.00226 padded (limit −0.005).
- G3 PASS: the +10/+15 dB changes versus baseline are +0.0407/+0.0468 original and +0.0146/+0.0043 padded; no bucket regresses by 0.015.
- G4 PASS: full JVM suite 64 tests, 0 failures, 3 skipped; legacy flag matches old hashes; all-off PCM SI-SDR minimum 71.60 dB across 26 files (limit 60 dB). Skips are opt-in tests requiring `SIGNALCHAIN_CLARITY_WAV` or `SIGNALCHAIN_ABLATION_METADATA` local inputs.
- G5 PASS: after-minus-noisy ESTOI improved versus C0 by +0.07984 original and +0.01105 padded.
- G6 PASS for eligible files: original has five Classical files at SNR ≤5 dB; C3 mean full-band SI-SDR improvement is +4.138 dB versus C0 +2.190 dB (paired C3−C0 +1.948 dB). Padded has zero Classical files at SNR ≤5 dB, so no padded file is eligible for this gate.
- G7 PASS: complete-agent overall ΔSTOI is +0.00597 original and +0.00892 padded (minimum −0.01).

Debug and release APK builds succeeded; `minifyReleaseWithR8` ran and release minification is enabled. README and PROGRESS now record the measurements. The evaluation remains small (40 mixtures per set), and PESQ/DNSMOS were not measured.
