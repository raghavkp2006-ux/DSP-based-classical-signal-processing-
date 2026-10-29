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
