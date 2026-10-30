# v1.1 PSD fix: Step 4 results and gate decision

**Branch:** `v1.1-psd-fix`, created from `investigate/v1.1-stoi` at `61ec650cd4bf676f217c69b57889c226f9a30e50`.
**Scope:** Steps 0–4 only. Step 5 was not performed because G4 failed.

## Implementation tested

The Kotlin pipeline now uses a VAD-independent, Martin-style minimum-statistics tracker behind `NoisePsd.estimate()`. I chose minimum statistics over MCRA because the existing pipeline consumes a batch, file-level PSD and the regression was VAD false-negative speech contaminating a VAD-gated average; a rolling minimum rejects short speech peaks without using VAD decisions. The window is 1.5 seconds (150 frames at 16 kHz / 10 ms hop), periodogram smoothing is 0.80, minimum bias compensation is 2.20×, and startup scaling ramps from 0.75× to 1.00× over 1.0 second. The legacy VAD-gated implementation remains available through `NoisePsd.useLegacyEstimatorForAbTests` or the explicit `legacy=true` parameter.

Spectral limits are named constants: over-subtraction factor max 1.00, subtraction gain floor 0.30, Wiener gain floor 0.30. Routing, decision thresholds, defaults and ONNX behavior were not edited.

## Baseline and after

STOI confidence intervals use 10,000 deterministic bootstrap resamples paired by file. Paired improvement CI is the after-minus-baseline change in per-file (output minus noisy) STOI delta.

| Set | n | Noisy STOI | Baseline STOI | After STOI | Baseline ΔSTOI (95% CI) | After ΔSTOI (95% CI) | Paired STOI improvement (95% CI) | Noisy ESTOI | Baseline ESTOI | After ESTOI | Baseline → after ΔESTOI | PSD/oracle median (base → after) | PSD/oracle mean (base → after) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Original | 14 | 0.9262 | 0.8883 | 0.9318 | -0.0379 [-0.0442, -0.0310] | +0.0056 [+0.0010, +0.0103] | +0.0435 [+0.0398, +0.0469] | 0.7788 | 0.7403 | 0.8201 | -0.0385 → +0.0414 | 8.48× → 1.10× | 12.33× → 1.32× |
| Padded | 12 | 0.9547 | 0.9476 | 0.9570 | -0.0072 [-0.0115, -0.0030] | +0.0023 [-0.0003, +0.0047] | +0.0095 [+0.0056, +0.0138] | 0.8357 | 0.8542 | 0.8653 | +0.0185 → +0.0296 | 2.78× → 0.89× | 2.62× → 0.88× |

Per-SNR Classical results, where output change compares new output with baseline output:

| Set | SNR | n | Baseline ΔSTOI | After ΔSTOI | Output change | Baseline ΔESTOI | After ΔESTOI |
|---|---:|---:|---:|---:|---:|---:|---:|
| Original | +5 dB | 5 | -0.0307 | +0.0142 | +0.0449 | -0.0119 | +0.0588 |
| Original | +10 dB | 6 | -0.0362 | +0.0045 | +0.0407 | -0.0347 | +0.0415 |
| Original | +15 dB | 3 | -0.0533 | -0.0064 | +0.0468 | -0.0903 | +0.0119 |
| Padded | +10 dB | 6 | -0.0095 | +0.0051 | +0.0146 | +0.0171 | +0.0364 |
| Padded | +15 dB | 6 | -0.0049 | -0.0006 | +0.0043 | +0.0200 | +0.0228 |

Oracle-PSD comparison on original Classical files was rerun with the same spectral caps/floors: mean new-estimator STOI 0.9318; mean oracle-PSD STOI 0.9328; new-minus-oracle -0.0010.

## Gates

- **G1 PASS:** original median PSD/oracle ratio is 1.10×; limit 2.5×.
- **G2 PASS:** output-minus-noisy STOI is +0.00560 original (minimum allowed -0.010) and +0.00226 padded (minimum allowed -0.005).
- **G3 PASS:** all +10/+15 dB buckets improved over baseline; the smallest average change is +0.00432 STOI (padded +15 dB). No bucket regressed by 0.015.
- **G4 FAIL:** all-off PCM identity passed on all 26 files; the minimum SI-SDR was 71.60 dB (limit 60 dB). However, the full JVM suite completed 61 tests with 4 failures and 3 skipped. Failures: `StftBoundaryTest.matchesPythonOnSpeechAndOriginalSampleOneCase` (sample 0 expected 0.32590008, got 0.33008468); `ClassicalStagesAblationTest.exportClassicalFactorial` (sample 0 expected -0.012407453, got -0.011247406); `PipelineSnapshotTest.capture` (expected/actual snapshot hashes differ); `V11OriginalClassicalTest.exportClassicalFactorial` (sample 0 expected -0.024594223, got -0.023996735). The three new tracker tests passed in the targeted run; the full-suite failures control G4.
- **G5 PASS:** after-minus-noisy ESTOI improved over baseline by +0.07984 original and +0.01105 padded.

Since G4 failed, Step 5 was stopped: no APK builds, README/PROGRESS updates, merge, release tag or push of `v1.1-psd-fix`.

## Evidence CSVs

- [Baseline original](v11c_baseline_original.csv) and [baseline padded](v11c_baseline_padded.csv)
- [After original](v11c_after_original.csv) and [after padded](v11c_after_padded.csv)
- [Before/after summary and bootstrap CIs](v11c_comparison_summary.csv)
- [SNR buckets](v11c_snr_buckets.csv), [PCM identity per file](v11c_identity_check.csv), [oracle comparison per file](v11c_oracle_comparison.csv), [gate results](v11c_gates.csv)

PESQ and DNSMOS were not scored in this task; the metrics reported here are STOI and ESTOI.
