# v1.1 STOI follow-up (report only)

**Branch:** `investigate/v1.1-stoi`  
**Scope:** report-only. Production DSP, defaults, tags, and merge state were not changed.  
**Scores:** STOI and ESTOI (extended STOI) via `pystoi`, 16 kHz mono. `PESQ` was not installable (Cython build requires MSVC); DNSMOS package dependencies installed in a temporary target, but model download from its upstream URL was reset, so no PESQ/DNSMOS scores are claimed.

## Summary

The spectral-off/downstream-off cell reproduces the noisy input to numerical precision before PCM export on both sets; PCM WAV rounding changes STOI by under 0.00001. Therefore no fixed stage breaks the all-off identity. The main Classical-route signal is a poorly matched noise PSD: on the original Classical subset its estimated mean power is 12.33× the oracle mean (median 8.48×), and 75.1% of frames selected by VAD as noise overlap clean speech by a -45 dBFS reference mask. With the oracle PSD, STOI is positive vs noisy for both Wiener-only and both spectral stages.

## 1. Classical spectral × downstream factorial

Spectral stack “on” means subtraction + adaptive Wiener. Downstream “on” means de-emphasis, aligned EQ, compressor, and normalize/limit. `off_off_pcm` is the all-off float result exported through PCM16. Values below are means; n=14 original, n=12 padded.

| Set | Arm | STOI | ΔSTOI vs noisy | ESTOI | ΔESTOI vs noisy | SI-SDR vs noisy (dB) |
|---|---|---:|---:|---:|---:|---:|
| original | both_on | 0.8883 | -0.0379 | 0.7403 | -0.0385 | 6.4417 |
| original | off_off | 0.9262 | 0.0000 | 0.7788 | -0.0000 | 140.5943 |
| original | off_off_pcm | 0.9262 | 0.0000 | 0.7788 | 0.0000 | 78.2846 |
| original | off_on | 0.9127 | -0.0135 | 0.7803 | 0.0015 | 10.8740 |
| original | subtraction_on | 0.9143 | -0.0119 | 0.7782 | -0.0006 | 9.1060 |
| original | wiener_on | 0.9155 | -0.0107 | 0.7817 | 0.0029 | 8.8434 |
| padded | both_on | 0.9476 | -0.0072 | 0.8542 | 0.0185 | 8.1960 |
| padded | off_off | 0.9547 | -0.0000 | 0.8357 | -0.0000 | 140.4384 |
| padded | off_off_pcm | 0.9548 | 0.0000 | 0.8357 | 0.0000 | 73.2140 |
| padded | off_on | 0.9384 | -0.0164 | 0.8289 | -0.0068 | 9.8564 |
| padded | subtraction_on | 0.9481 | -0.0067 | 0.8481 | 0.0124 | 10.6128 |
| padded | wiener_on | 0.9513 | -0.0035 | 0.8563 | 0.0206 | 10.6364 |

Identity check: float off/off mean SI-SDR is 140.59 dB original and 140.44 dB padded. PCM16 off/off is 78.28 dB original and 73.21 dB padded; STOI differs from noisy by +0.0000065 on each set. This confirms the fixed preprocessing/de-emphasis/PCM path preserves identity to the expected tolerance. The all-off metric matches noisy without needing a level adjustment; STOI is level-insensitive.

## 2. Oracle noise PSD (original Classical, n=14)

| Arm | STOI | ΔSTOI vs noisy | ESTOI | ΔESTOI vs noisy |
|---|---:|---:|---:|---:|
| wiener_on | 0.9155 | -0.0107 | 0.7817 | 0.0029 |
| both_on | 0.8883 | -0.0379 | 0.7403 | -0.0385 |
| oracle_wiener_on | 0.9312 | 0.0050 | 0.8162 | 0.0374 |
| oracle_both_on | 0.9331 | 0.0069 | 0.8241 | 0.0454 |

Estimated/oracle noise PSD mean bin ratio: 12.33× (median 8.48×; range 3.56–39.99×). `NoisePsd` selects frames that VAD labels non-speech; it is an average, not a recursive initializer. The first selected frame was clean-speech by the reference mask in 0/14 files, but subsequent selected frames overlap clean speech: mean 75.1% (range 49.5–90.7%). This is consistent with VAD false negatives contaminating the estimated PSD. Per-file estimates and contamination counts are in [v11b_oracle_psd_per_file.csv](v11b_oracle_psd_per_file.csv).

## 3. STOI/ESTOI change and modulation by SNR bucket

Each value is mean output minus noisy baseline; modulation is output/noisy using the existing noise-only modulation proxy. “—” means the proxy had no qualifying nonspeech run (at least 0.5 s), not a zero ratio. Original audio had no measurable qualifying runs; padded audio did.

| Set | Route | SNR dB | n | ΔSTOI | ΔESTOI | Modulation ratio |
|---|---|---:|---:|---:|---:|---:|
| original | Classical DSP | +5 | 5 | -0.0307 | -0.0118 | — |
| original | Classical DSP | +10 | 6 | -0.0362 | -0.0347 | — |
| original | Classical DSP | +15 | 3 | -0.0533 | -0.0903 | — |
| original | Full adaptive | -5 | 8 | -0.0179 | 0.0010 | — |
| original | Full adaptive | +0 | 8 | -0.0230 | -0.0166 | — |
| original | Full adaptive | +5 | 2 | -0.0303 | -0.0494 | — |
| original | Light touch | +5 | 1 | -0.0000 | -0.0000 | — |
| original | Light touch | +10 | 2 | -0.0000 | -0.0000 | — |
| original | Light touch | +15 | 5 | -0.0000 | -0.0000 | — |
| padded | Classical DSP | +10 | 6 | -0.0094 | 0.0171 | 1.8139 |
| padded | Classical DSP | +15 | 6 | -0.0049 | 0.0200 | 1.5671 |
| padded | Full adaptive | -5 | 7 | -0.0024 | 0.0279 | 0.7312 |
| padded | Full adaptive | +0 | 7 | -0.0059 | 0.0126 | 0.7132 |
| padded | Full adaptive | +5 | 7 | -0.0177 | -0.0037 | 0.6417 |
| padded | Light touch | -5 | 1 | 0.0000 | -0.0000 | 2.2640 |
| padded | Light touch | +0 | 1 | 0.0000 | -0.0000 | 2.1622 |
| padded | Light touch | +5 | 1 | -0.0000 | -0.0000 | 2.1076 |
| padded | Light touch | +10 | 2 | -0.0000 | -0.0000 | 1.9954 |
| padded | Light touch | +15 | 2 | -0.0000 | 0.0000 | 2.0144 |

Actual-route totals by dataset:

| Set | n | Noisy STOI | Output STOI | ΔSTOI | Noisy ESTOI | Output ESTOI | ΔESTOI |
|---|---:|---:|---:|---:|---:|---:|---:|
| original | 40 | 0.8910 | 0.8681 | -0.0230 | 0.7453 | 0.7263 | -0.0190 |
| padded | 40 | 0.8933 | 0.8866 | -0.0067 | 0.7489 | 0.7609 | 0.0120 |

### Higher-SNR Light touch counterfactual

For the 16 files per dataset at +10/+15 dB, compare current route output against the test-only Light touch output (strict improvement threshold +0.0001 STOI). The output is a recommendation signal, not a routing change.

| Set | Better with Light touch | Mean ΔSTOI across all 16 | Mean gain among improved |
|---|---:|---:|---:|
| original | 9/16 | 0.0236 | 0.0419 |
| padded | 8/16 | 0.0054 | 0.0115 |

## 4. Brown-hum files vs other Full-route files

“Brown hum” is the dataset label for brown-noise interference; it is not a separate sinusoidal hum component. The paired no-brown rows remove the entire brown-noise component and run the Full path test helper with the route parameters held from the noisy input. This is an optimistic clean-input counterfactual, not a deployable route comparison.

| Set | Full-route subset | n | STOI | ESTOI | Speech-band SI-SDR vs clean (dB) |
|---|---|---:|---:|---:|---:|
| original | brown_hum | 5 | 0.9678 | 0.9260 | 13.4018 |
| original | other | 13 | 0.7384 | 0.5054 | 3.5844 |
| padded | brown_hum | 3 | 0.9443 | 0.8930 | 11.5955 |
| padded | other | 18 | 0.7923 | 0.5840 | 3.4365 |

Paired brown-noise removal counterfactual:

| Set | n | Brown actual STOI | No-brown STOI | Brown actual ESTOI | No-brown ESTOI | Speech-band SI-SDR actual / no-brown (dB) |
|---|---:|---:|---:|---:|---:|---:|
| original | 5 | 0.9678 | 0.9749 | 0.9260 | 0.9493 | 13.4018 / 14.0994 |
| padded | 3 | 0.9443 | 0.9646 | 0.8930 | 0.9378 | 11.5955 / 12.8046 |

## 5. Ranked candidate fixes (proposals only)

1. **Prevent speech leakage into the Classical noise PSD.** Observed estimate/oracle ratio is 12.33× on average; oracle both-stage STOI is +0.045 vs current both-stage output on original Classical (0.9331 vs 0.8883). Risk: conservative masking may leave more residual noise; the oracle is unattainable in production. Confirm with per-file speech-overlap traces and a held-out set, comparing STOI/ESTOI and residual noise.
2. **Retune the Classical spectral stack, especially the adaptive Wiener component.** Removing the Wiener gain while keeping subtraction and downstream processing raises original Classical STOI by about +0.026 (0.9143 vs 0.8883) and padded by +0.0005 (0.9481 vs 0.9476); Wiener-only is +0.0272 original and +0.0037 padded. Risk: more residual noise, and behavior must remain consistent with the intended Classical spec. Confirm with the same factorial plus listening/residual-noise checks.
3. **Consider Light touch routing for a subset of +10/+15 dB cases.** Counterfactual Light touch scored higher on 9/16 original and 8/16 padded files; average gain across all 16 was +0.0236 and +0.0054 STOI. Risk: increased residual noise and route changes; confirm on unseen speakers/noise seeds with paired ESTOI and listening.
4. **Inspect downstream EQ/dynamics on the Classical route.** With spectral stages bypassed, downstream-on drops STOI by 0.0135 original and 0.0164 padded versus all-off. This combined ablation cannot attribute the loss to EQ, compressor, normalization, or pre/de-emphasis individually. Risk: loudness or timbre changes. Confirm by separately toggling each fixed stage, level-matching, and checking clipping and speech-band SI-SDR.

## 6. Artifacts and test evidence

- [Per-file actual-route STOI/ESTOI and speech-band SI-SDR](v11b_per_file.csv)
- [Per-file spectral factorial and oracle results](v11b_factorial_per_file.csv)
- [Factorial means](v11b_factorial_summary.csv)
- [Per-file oracle PSD ratio and VAD overlap](v11b_oracle_psd_per_file.csv)
- [Per-file SNR/modulation metrics](v11b_modulation_per_file.csv)
- [SNR-bucket means](v11b_snr_summary.csv)
- [High-SNR Light touch counterfactual](v11b_light_counterfactual.csv)
- [Brown-hum files and paired no-brown counterfactual](v11b_brown_hum_per_file.csv)
- Reproducers: `scripts/v11b_metrics.py`, `scripts/v11b_oracle_psd.py`, and Kotlin evaluation-only tests `V11BFactorialTest` / `V11BCounterfactualTest`.
- Targeted JVM tests: 2 tests, 0 failures, 0 skipped after setting `SIGNALCHAIN_ROOT` to the repository root. No production source files changed; two evaluation-only JVM tests were added.
- DNSMOS was not scored: temporary package dependencies installed, but the model download was reset by upstream; PESQ build failed because MSVC is unavailable. ESTOI is included in each metric table as the additional intelligibility metric.
- Original-set modulation values are N/A because the noise-only proxy requires a contiguous nonspeech span of at least 0.5 s; padded set provides that span. Do not interpret N/A as a favorable or unfavorable change.
