# Post-fix evaluation and hangover sensitivity

Original and padded sets each contain 40 conditions derived from two clean clips. Kotlin aligned EQ is the **current production configuration**; Kotlin legacy EQ matches the EQ mode shipped in v1.0.0. Python uses legacy EQ. Each pipeline uses its own routing; EQ is bypassed in Light touch. Full SI-SDR improvement uses the original unshifted clean and output over the complete file; interior uses the ±256-sample local-correlation lag and excludes the first and last 100 ms of the clean window, with the same window for the noisy baseline. See ALIGNMENT_CHECK.md for the boundary defect and lag limits. STOI is unshifted, as in the earlier report. Proxies are descriptive, not perceptual thresholds.

## Overall metrics

| Set | Arm | STOI (n) | Full SI-SDR Δ dB (n) | Interior SI-SDR Δ dB (n) | Modulation ratio (n) | Musical kurtosis ratio (n) | Echo ACF (n) |
|---|---|---:|---:|---:|---:|---:|---:|
| original | Noisy input | 0.8910 (40) | 0.0000 (40) | 0.0000 (40) | — (0) | 1.0000 (40) | — (0) |
| original | Python legacy EQ | 0.8383 (40) | -21.9088 (40) | 0.0467 (40) | — (0) | 4.5181 (40) | — (0) |
| original | Kotlin legacy EQ | 0.8385 (40) | -21.9412 (40) | 0.0572 (40) | — (0) | 4.5227 (40) | — (0) |
| original | Kotlin aligned EQ (production) | 0.8678 (40) | -14.9036 (40) | 1.6122 (40) | — (0) | 2.3896 (40) | — (0) |
| padded | Noisy input | 0.8933 (40) | 0.0000 (40) | 0.0000 (40) | 1.0000 (40) | 1.0000 (40) | 0.3462 (40) |
| padded | Python legacy EQ | 0.8557 (40) | -17.1654 (40) | 2.7297 (40) | 0.4187 (40) | 24.5306 (40) | 0.2834 (40) |
| padded | Kotlin legacy EQ | 0.8558 (40) | -17.1814 (40) | 2.7523 (40) | 0.4185 (40) | 24.5751 (40) | 0.2833 (40) |
| padded | Kotlin aligned EQ (production) | 0.8867 (40) | -6.0306 (40) | 5.0555 (40) | 0.3705 (40) | 6.8082 (40) | 0.2685 (40) |

Original noise-only segments are too short for modulation and echo metrics; these entries are unavailable, not zero. The padded set provides 40 values per proxy.

## SI-SDR and STOI by SNR

| Set | Input SNR dB | n | Arm | Mean STOI | Full SI-SDR Δ dB | Interior SI-SDR Δ dB |
|---|---:|---:|---|---:|---:|---:|
| original | -5 | 8 | Python legacy EQ | 0.7106 | -20.257 | +4.468 |
| original | -5 | 8 | Kotlin legacy EQ | 0.7106 | -20.325 | +4.476 |
| original | -5 | 8 | Kotlin aligned EQ (production) | 0.7455 | -10.476 | +7.170 |
| original | 0 | 8 | Python legacy EQ | 0.7839 | -19.576 | +1.633 |
| original | 0 | 8 | Kotlin legacy EQ | 0.7840 | -19.602 | +1.637 |
| original | 0 | 8 | Kotlin aligned EQ (production) | 0.8251 | -9.623 | +4.550 |
| original | 5 | 8 | Python legacy EQ | 0.8552 | -27.007 | +0.028 |
| original | 5 | 8 | Kotlin legacy EQ | 0.8558 | -27.042 | +0.057 |
| original | 5 | 8 | Kotlin aligned EQ (production) | 0.8866 | -20.661 | +1.183 |
| original | 10 | 8 | Python legacy EQ | 0.9012 | -27.314 | -2.194 |
| original | 10 | 8 | Kotlin legacy EQ | 0.9016 | -27.334 | -2.184 |
| original | 10 | 8 | Kotlin aligned EQ (production) | 0.9262 | -21.860 | -1.308 |
| original | 15 | 8 | Python legacy EQ | 0.9406 | -15.389 | -3.701 |
| original | 15 | 8 | Kotlin legacy EQ | 0.9405 | -15.402 | -3.700 |
| original | 15 | 8 | Kotlin aligned EQ (production) | 0.9555 | -11.897 | -3.534 |
| original | all | 40 | Python legacy EQ | 0.8383 | -21.909 | +0.047 |
| original | all | 40 | Kotlin legacy EQ | 0.8385 | -21.941 | +0.057 |
| original | all | 40 | Kotlin aligned EQ (production) | 0.8678 | -14.904 | +1.612 |
| padded | -5 | 8 | Python legacy EQ | 0.7396 | -10.691 | +6.048 |
| padded | -5 | 8 | Kotlin legacy EQ | 0.7397 | -10.714 | +6.061 |
| padded | -5 | 8 | Kotlin aligned EQ (production) | 0.7677 | -0.206 | +8.668 |
| padded | 0 | 8 | Python legacy EQ | 0.8102 | -12.254 | +4.687 |
| padded | 0 | 8 | Kotlin legacy EQ | 0.8102 | -12.269 | +4.697 |
| padded | 0 | 8 | Kotlin aligned EQ (production) | 0.8451 | -0.027 | +7.508 |
| padded | 5 | 8 | Python legacy EQ | 0.8598 | -14.945 | +1.950 |
| padded | 5 | 8 | Kotlin legacy EQ | 0.8598 | -14.952 | +1.955 |
| padded | 5 | 8 | Kotlin aligned EQ (production) | 0.8984 | -0.353 | +5.145 |
| padded | 10 | 8 | Python legacy EQ | 0.9216 | -23.517 | +1.794 |
| padded | 10 | 8 | Kotlin legacy EQ | 0.9220 | -23.540 | +1.852 |
| padded | 10 | 8 | Kotlin aligned EQ (production) | 0.9481 | -14.657 | +2.950 |
| padded | 15 | 8 | Python legacy EQ | 0.9473 | -24.420 | -0.831 |
| padded | 15 | 8 | Kotlin legacy EQ | 0.9474 | -24.431 | -0.804 |
| padded | 15 | 8 | Kotlin aligned EQ (production) | 0.9745 | -14.909 | +1.007 |
| padded | all | 40 | Python legacy EQ | 0.8557 | -17.165 | +2.730 |
| padded | all | 40 | Kotlin legacy EQ | 0.8558 | -17.181 | +2.752 |
| padded | all | 40 | Kotlin aligned EQ (production) | 0.8867 | -6.031 | +5.055 |

## Hangover sensitivity

Nominal is the agent choice: 0 frames for non-sparse inputs and 5 frames for sparse inputs. Multipliers 2–4 therefore test 0/10, 0/15 and 0/20 frames; each run changes only that choice. Python uses legacy EQ; Kotlin uses aligned production EQ. The Kotlin test harness was checked against production for every nominal file. VAD mismatch counts compare the two languages at the same multiplier.

| Set | Multiplier | Arm | STOI | Full SI-SDR Δ dB | Interior SI-SDR Δ dB | VAD mismatches / frames |
|---|---:|---|---:|---:|---:|---:|
| original | 1× | Python legacy EQ | 0.8383 | -21.909 | +0.047 | 0 / 11920 |
| original | 1× | Kotlin aligned EQ (production) | 0.8678 | -14.904 | +1.612 | 0 / 11920 |
| original | 2× | Python legacy EQ | 0.8391 | -21.874 | +0.155 | 0 / 11920 |
| original | 2× | Kotlin aligned EQ (production) | 0.8685 | -14.881 | +1.723 | 0 / 11920 |
| original | 3× | Python legacy EQ | 0.8400 | -21.851 | +0.263 | 0 / 11920 |
| original | 3× | Kotlin aligned EQ (production) | 0.8695 | -14.807 | +1.834 | 0 / 11920 |
| original | 4× | Python legacy EQ | 0.8415 | -21.853 | +0.365 | 0 / 11920 |
| original | 4× | Kotlin aligned EQ (production) | 0.8709 | -14.751 | +1.932 | 0 / 11920 |
| padded | 1× | Python legacy EQ | 0.8557 | -17.165 | +2.730 | 0 / 35920 |
| padded | 1× | Kotlin aligned EQ (production) | 0.8867 | -6.031 | +5.055 | 0 / 35920 |
| padded | 2× | Python legacy EQ | 0.8568 | -17.077 | +2.838 | 0 / 35920 |
| padded | 2× | Kotlin aligned EQ (production) | 0.8880 | -5.972 | +5.163 | 0 / 35920 |
| padded | 3× | Python legacy EQ | 0.8576 | -16.939 | +2.887 | 0 / 35920 |
| padded | 3× | Kotlin aligned EQ (production) | 0.8888 | -5.863 | +5.222 | 0 / 35920 |
| padded | 4× | Python legacy EQ | 0.8586 | -16.872 | +2.916 | 0 / 35920 |
| padded | 4× | Kotlin aligned EQ (production) | 0.8896 | -5.805 | +5.250 | 0 / 35920 |

4× (20 frames when sparse) has the highest measured mean STOI and interior SI-SDR in both sets: versus nominal, production Kotlin STOI rises by +0.0031 original and +0.0029 padded, while interior SI-SDR rises by +0.320 and +0.195 dB. This adds up to 150 ms of hangover relative to 5 frames and changes which frames are treated as noise; effects on other speech and noise conditions are unmeasured. **Recommendation: retain nominal 5 as the default for now; propose 20 frames as a candidate for broader listening and noise-tracking tests after the boundary defect is addressed.** No setting was changed.

## Isolated VAD-fix STOI change

Both before and after runs include the spectral bypass fix. The before runs reproduce the original language-specific hangover operations in evaluation-only harnesses; after uses fixed-length hangover. Routing categories are Kotlin agent routes, which agree with Python on these sets. Each value is a mean of per-file STOI rounded to four decimals.

| Set | Routing | n | Arm | Before | After | Change |
|---|---|---:|---|---:|---:|---:|
| original | Light touch | 8 | Python legacy EQ | 0.9809 | 0.9809 | +0.0000 |
| original | Light touch | 8 | Kotlin aligned EQ (production) | 0.9809 | 0.9809 | +0.0000 |
| original | Classical DSP | 14 | Python legacy EQ | 0.8601 | 0.8553 | -0.0048 |
| original | Classical DSP | 14 | Kotlin aligned EQ (production) | 0.9150 | 0.8889 | -0.0261 |
| original | Full adaptive | 18 | Python legacy EQ | 0.7618 | 0.7617 | -0.0001 |
| original | Full adaptive | 18 | Kotlin aligned EQ (production) | 0.8032 | 0.8011 | -0.0021 |
| padded | Light touch | 7 | Python legacy EQ | 0.9999 | 0.9999 | +0.0000 |
| padded | Light touch | 7 | Kotlin aligned EQ (production) | 0.9999 | 0.9999 | +0.0000 |
| padded | Classical DSP | 12 | Python legacy EQ | 0.9172 | 0.9126 | -0.0046 |
| padded | Classical DSP | 12 | Kotlin aligned EQ (production) | 0.9567 | 0.9484 | -0.0083 |
| padded | Full adaptive | 21 | Python legacy EQ | 0.7762 | 0.7751 | -0.0011 |
| padded | Full adaptive | 21 | Kotlin aligned EQ (production) | 0.8206 | 0.8138 | -0.0068 |

Light touch is unaffected by VAD-only changes because spectral processing is bypassed. Earlier before-both-fixes comparisons also include the bypass fix; they are in stoi_before_after.csv.

## EQ recommendation

Keep the current Kotlin aligned EQ as the production configuration: compared with Kotlin legacy, mean STOI rises from 0.8385 to 0.8678 on original and 0.8558 to 0.8867 on padded; mean interior SI-SDR improvement rises from +0.057 to +1.612 dB and +2.752 to +5.055 dB. Musical-kurtosis ratio drops from 4.523 to 2.390 and 24.575 to 6.808, respectively. These are limited synthetic-set measures; the tagged release used legacy, and the enabled-STFT boundary defect remains. No EQ default was changed.

Per-file data: postfix_evaluation.csv, hangover_sensitivity.csv and vad_fix_isolated_stoi.csv. Export and report scripts are in scripts/; WAV files and traces remain local and ignored.
