# Sample alignment check (before fixes)

The existing PCM outputs were compared with their clean references at 16 kHz. `alignment_check.csv` gives the dataset, file ID, noise and SNR (together identifying each file), arm, lag in samples, uncorrected SI-SDR, overlap-only lag-corrected SI-SDR, matched-overlap noisy baseline and improvement. Positive lag means output is delayed. The lag is the maximum absolute full linear cross-correlation after mean removal; the additional local lag uses a ±256-sample search. An unrestricted peak is not necessarily a true delay, particularly when a boundary transient dominates. Reproduce with `python alignment_check.py` on the existing local audio sets and outputs.

| Set | Arm | Mean uncorrected SI-SDR (dB) | Full-peak aligned SI-SDR (dB) | Full-peak aligned improvement (dB) | ±256 aligned improvement (dB) | Local lag counts (samples) |
|---|---|---:|---:|---:|---:|---|
| Original | Python legacy | -20.955 | -10.677 | -16.721 | -17.533 | 0: 8, 64: 32 |
| Original | Kotlin legacy | -21.266 | -9.869 | -15.905 | -16.389 | 0: 8, 64: 32 |
| Original | Kotlin aligned | -12.686 | -11.409 | -17.530 | -18.718 | 0: 40 |
| Padded | Python legacy | -16.764 | -4.520 | -6.002 | -6.111 | 0: 7, 64: 33 |
| Padded | Kotlin legacy | -16.922 | -4.644 | -6.126 | -6.216 | 0: 7, 64: 33 |
| Padded | Kotlin aligned | -5.791 | -5.499 | -7.003 | -7.240 | 0: 40 |

The negative improvement reflects **both delay and remaining waveform error**, not a uniform timing offset alone: the common 64-sample local peak in the legacy arms corresponds to 4 ms, and correcting it improves their averages, while the aligned EQ arm peaks at zero locally and still scores below noisy. SI-SDR quantifies residual waveform mismatch; it does not isolate its causes or establish perceptual degradation. The full-search averages are descriptive, not trustworthy estimates of physical delay: some peaks lie at -11,793 or +36,061 samples in the original set and -59,793 in the padded set. For example, original holdout 01 white +5 dB clean peaks at sample 11,875, while Python output peaks at sample 66 at 0.99 amplitude (overall RMS 0.01406); noisy peaks at 11,877. In that case the large correlation lag aligns an output boundary transient to a clean speech peak, not a demonstrated pipeline delay. Its padded counterpart has a clean peak at 59,875. Noise and clipping can also affect cross-correlation. These unexpected, dominant edge transients and extreme SI-SDR residuals need investigation before calling this check green or attributing the residual to a specific stage. The local window is a diagnostic assumption, not proof that delays outside it are impossible.

No DSP code, EQ default, or release tag was changed in this step.

## Post-fix Task 1b

The following uses the post-bypass and post-VAD-fix exports. Kotlin aligned is the current production app. For each file, a ±256-sample correlation search determines one lag; both output and noisy baseline use the same clean-coordinate overlap. The interior calculation additionally excludes the first and last 1,600 samples (100 ms) of the clean reference and takes the correspondingly shifted output samples. [alignment_postfix.csv](alignment_postfix.csv) lists all file-level results. Means are over 40 files per set and improvement is relative to noisy. Padding changes the noise mixture and duration, so the sets are not paired measurements of identical signals.

| Set | Arm | Full overlap improvement (dB) | Interior improvement (dB) |
|---|---|---:|---:|
| Original | Python legacy EQ | -12.490 | +0.047 |
| Original | Kotlin legacy EQ | -12.525 | +0.057 |
| Original | Kotlin aligned EQ **(production)** | -14.904 | +1.612 |
| Padded | Python legacy EQ | -5.019 | +2.730 |
| Padded | Kotlin legacy EQ | -5.035 | +2.752 |
| Padded | Kotlin aligned EQ **(production)** | -6.031 | +5.055 |

The padded-minus-original full-file gap is +7.471, +7.489 and +8.873 dB respectively; the interior gap is +2.683, +2.695 and +3.443 dB. Thus trimming reduces those aggregate gaps by 4.788, 4.794 and 5.430 dB (64%, 64%, 61%). This describes these differing sets, not a causal decomposition of padding alone. The trim does not remove all effects of a transient, since normalization and de-emphasis can affect later samples.

**Identity:** Python's full pipeline was run with agent disabled and configurable processing stages bypassed, including zero pre-emphasis, no subtraction, EQ, compression or ML; the pipeline's mandatory normalization/limiter and PCM export remain active. SI-SDR versus input was 71.80 dB original and 76.35 dB padded on the first noisy file of each set, at zero lag. Kotlin has no full-pipeline override to bypass every stage, so an exact equivalent could not be run without changing production control flow. As a practical production control, actual Light touch outputs (pre/de-emphasis pair and mandatory normalization still active) versus input had minimum/mean SI-SDR 66.65/67.70 dB for 8 original files and 66.13/68.78 dB for 7 padded files. These are not a literal all-stage bypass test. See identity_postfix.csv.

**Clean input:** Both clean clips in both sets routed to Light touch. Against their clean inputs, Python legacy EQ measured 66.50–68.67 dB and Kotlin aligned production 66.60–68.45 dB, all with zero local lag. These results cover the selected clean routing, not forced Classical or Full processing. File results are in clean_input_postfix.csv.

**Oracle:** A noncausal oracle Wiener mask uses the true clean and true residual-noise STFT powers per bin, with a 512-sample Hann frame, 384 overlap and zero-padded boundaries; it has access to information unavailable to the actual system. Mean full-overlap improvement is +13.338 dB original and +18.232 dB padded; interior is +13.262 and +18.069 dB. This is a positive sanity check, not proof the metric is error-free or a deployable baseline.

**Edge source and proposed fix:** On original holdout 00 white +5 dB, the pre-emphasis peak is 0.64084, but enabled spectral reconstruction jumps to -92.8948 at sample 1 in both Python and Kotlin. The frame starts at the signal boundary with a periodic Hann window; the synthesis divides by a very small squared-window overlap weight near the edge after spectral modification, amplifying the inverse-FFT result. The first sample has zero window weight; incomplete final framing also leaves a zero tail in the spectral output. De-emphasis spreads the initial transient, legacy EQ moves its peak, and normalization scales the whole result around it. This points to STFT boundary handling and overlap-add normalization, not an initial pre-emphasis spike. A candidate fix would add appropriate boundary padding and ensure complete overlap coverage, then crop after reconstruction; it needs controlled reconstruction, tail and quality tests before adoption. It was not applied. Tests of clean input and bypass do not rule out distortion when subtraction is enabled.

## Boundary-fix follow-up

The STFT padding and overlap-add fix has now been applied in both implementations. This document's pre-fix measurements remain historical. See [STFT_BOUNDARY_FIX.md](STFT_BOUNDARY_FIX.md) for the post-fix checks, forced clean routes, quality trade-offs and evaluation.
