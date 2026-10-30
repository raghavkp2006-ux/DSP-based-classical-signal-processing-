# STFT boundary fix: post-fix evaluation

The earlier reports are historical baselines. Before means bypass and fixed VAD were present but the enabled STFT boundary fix was absent; after adds the boundary fix. Python uses legacy EQ; Kotlin uses aligned EQ, the current production configuration. Feedback was disabled throughout. Each set contains 40 conditions from two clean clips. Full-file SI-SDR improvement is unshifted; interior improvement uses the ±256-sample local lag, crops 100 ms at each end of the clean reference, and uses the same clean window for output and noisy baseline. STOI is unshifted and rounded per file to four decimals. Proxies are descriptive.

## Before and after

| Set | Arm | Noisy STOI | Output STOI | Full SI-SDR Δ dB | Interior SI-SDR Δ dB | Modulation ratio | Musical-kurtosis ratio | Echo ACF |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| original | Python legacy EQ | 0.8910 | 0.8383 → 0.8408 | -21.909 → -13.857 | 0.047 → -0.144 | unavailable → unavailable | 4.5181 → 2.0062 | unavailable → unavailable |
| original | Kotlin aligned EQ (production) | 0.8910 | 0.8678 → 0.8681 | -14.904 → 1.516 | 1.612 → 1.438 | unavailable → unavailable | 2.3896 → 1.7767 | unavailable → unavailable |
| padded | Python legacy EQ | 0.8933 | 0.8557 → 0.8576 | -17.165 → -12.486 | 2.730 → 2.505 | 0.4187 → 1.4607 | 24.5306 → 4.3488 | 0.2834 → 0.2846 |
| padded | Kotlin aligned EQ (production) | 0.8933 | 0.8867 → 0.8866 | -6.031 → 4.875 | 5.055 → 4.806 | 0.3705 → 1.2361 | 6.8082 → 3.4883 | 0.2685 → 0.2845 |

Every available mean above uses n=40. Original noise-only segments are too short for modulation and echo, so those values are unavailable. On padded input, the production modulation ratio increased in 30/40 cases; musical-kurtosis ratio decreased in 33/40. These proxies do not by themselves establish perceptual quality. After the fix, production mean STOI still trails the noisy baseline: 0.8681 vs 0.8910 original, and 0.8866 vs 0.8933 padded.

## Full versus interior gap

The production Kotlin full and interior improvements are +1.516 vs +1.438 dB original and +4.875 vs +4.806 dB padded: full exceeds interior by 0.078 and 0.068 dB. The earlier production gap was dominated by the STFT edge transient; this aggregate comparison shows it is no longer large. The remaining difference reflects the different scored windows and the noise included in them; it is not isolated to one cause.

The Python legacy arm appears to have a much larger gap if the unshifted full score is compared directly with the lag-corrected interior score. The local peaks are 64 samples for 32/40 original and 33/40 padded files (zero for the rest). After the same lag correction, full-overlap improvement is -0.144 dB original and +2.575 dB padded, compared with interior -0.144 and +2.505 dB. Thus the large apparent gap comes principally from the causal EQ delay and the mismatch in metric alignment, not a demonstrated remaining edge transient. The correlation search is limited to ±256 samples.

## Forced clean-input routes

Test-only harnesses force each route with `vadHangoverFrames=0`, using the agent's normal Classical or Full parameters. Kotlin uses the same production stage functions with aligned EQ; Python uses legacy EQ. Full includes the ML post-filter. Kotlin ONNX inference completed without fallback; Python reports the post-filter as used, but its internal trained-model-to-spectral-gating fallback is not logged per file. Values below are SI-SDR against clean, without subtracting a noisy baseline. Python's 64-sample lag is also corrected in the second column.

| Set | Clean clip | Route | Python full / aligned dB | Kotlin production full dB |
|---|---|---|---:|---:|
| original | holdout_00_7976-110124-0013.wav | classical | -16.51 / 11.64 | 17.21 |
| original | holdout_00_7976-110124-0013.wav | full | -19.45 / 5.17 | 13.42 |
| original | holdout_01_8842-302196-0003.wav | classical | -9.24 / 6.66 | 7.26 |
| original | holdout_01_8842-302196-0003.wav | full | -11.19 / 2.35 | 4.84 |
| padded | holdout_00_7976-110124-0013.wav | classical | -16.76 / 11.01 | 16.21 |
| padded | holdout_00_7976-110124-0013.wav | full | -19.94 / 4.80 | 11.99 |
| padded | holdout_01_8842-302196-0003.wav | classical | -8.37 / 6.94 | 7.14 |
| padded | holdout_01_8842-302196-0003.wav | full | -9.40 / 3.11 | 4.17 |

These forced routes still change clean speech, particularly Full; these are not Light touch identity measurements. Different EQ and post-filter implementations prevent attributing the language gap to one stage.

## Hangover after the boundary fix

Nominal remains the agent choice 0/5 frames; 4× is 0/20. No default was changed.

| Set | Arm | Multiplier | STOI | Full SI-SDR improvement dB | Interior SI-SDR improvement dB |
|---|---|---:|---:|---:|---:|
| original | Python legacy EQ | 1× | 0.8408 | -13.857 | -0.144 |
| original | Python legacy EQ | 4× | 0.8441 | -13.557 | +0.161 |
| original | Kotlin aligned EQ (production) | 1× | 0.8681 | +1.516 | +1.438 |
| original | Kotlin aligned EQ (production) | 4× | 0.8710 | +1.821 | +1.751 |
| padded | Python legacy EQ | 1× | 0.8576 | -12.486 | +2.505 |
| padded | Python legacy EQ | 4× | 0.8608 | -12.326 | +2.673 |
| padded | Kotlin aligned EQ (production) | 1× | 0.8866 | +4.875 | +4.806 |
| padded | Kotlin aligned EQ (production) | 4× | 0.8896 | +5.041 | +4.974 |

4× improves mean STOI and interior SI-SDR on these sets, but extends sparse-speech hangover by 150 ms. The previous recommendation remains: keep nominal pending broader listening and noise-tracking tests; 20 frames is a candidate, not an applied setting.

## Verification

Identity and modified-spectrum tests were written and run before the fix: Python and Kotlin both failed identity and peak-bound checks. After the fix, reconstruction passes at 1e-6 for lengths 1, 159, 1600, 1601 and 4096, including all edges; clean-speech and original sample-1 fixtures stay below twice input peak. Python and Kotlin match shared references to 1e-5. The original sample-1 case went from -92.895 in the old full-clip stage trace to +0.032 in the bounded fixture. The old exact-output enabled spectral goldens were retired because this fix deliberately changes enabled processing.

Python targeted tests: 8 passed, including feedback retry and disabled-feedback control. Kotlin debug JVM suite: 53 tests, 3 skipped, 0 failures; its stale whole-pipeline snapshots were refreshed after the intended bypass and STFT changes. Separate exporter runs covered both full sets, forced routes and the nominal/4× sweep; Kotlin did not use ML fallback, and Python reported its post-filter as used. The Kotlin nominal harness was checked against production for every file. This was not a release build or release lint.

The feedback regression test failed before commit 525fb42 for aligned EQ and passed afterward for both EQ choices. Since these evaluations use feedback_enabled=false and never enter the retry, that separate change does not affect these evaluation runs.

Per-file data: stftfix_evaluation.csv, stftfix_hangover.csv and stftfix_forced_clean.csv. Local WAVs are ignored; scripts/stftfix_python_export.py and scripts/stftfix_evaluate.py reproduce Python exports and measurements, and the Kotlin export tests use SIGNALCHAIN_ROOT plus the ablation metadata environment variables.
