# SI-SDR and padded-set artifact metrics

Clean-referenced SI-SDR after mean removal and least-squares scaling; improvement is relative to the noisy baseline. Padding is three seconds per side, with noise scaled to the original unpadded clip power and the generator seed unchanged; mixing still clips to [-1,1].

| Dataset | SNR | n | Python dB | Kotlin legacy dB | Kotlin aligned dB |
|---|---|---:|---:|---:|---:|
| original | -5 | 8 | -20.257 | -20.325 | -10.476 |
| original | 0 | 8 | -19.419 | -19.627 | -9.471 |
| original | 5 | 8 | -29.438 | -30.822 | -20.302 |
| original | 10 | 8 | -32.377 | -32.879 | -24.009 |
| original | 15 | 8 | -33.443 | -32.838 | -29.334 |
| original | all | 40 | -26.987 | -27.298 | -18.718 |
| padded | -5 | 8 | -10.912 | -10.935 | -0.427 |
| padded | 0 | 8 | -12.351 | -12.408 | -0.027 |
| padded | 5 | 8 | -15.277 | -15.445 | -0.607 |
| padded | 10 | 8 | -25.695 | -25.882 | -17.339 |
| padded | 15 | 8 | -26.829 | -27.184 | -17.797 |
| padded | all | 40 | -18.213 | -18.371 | -7.240 |

Padded noise-only metrics (arithmetic mean over finite values; n explicitly shown):

| Arm | Modulation ratio (n) | Musical kurtosis ratio (n) | Echo ACF (n) |
|---|---:|---:|---:|
| noisy | 1.0000 (40) | 1.0000 (40) | 0.3462 (40) |
| python | 0.1080 (40) | 24.5856 (40) | 0.2735 (40) |
| kotlin_legacy | 0.1117 (40) | 24.2139 (40) | 0.2720 (40) |
| kotlin_aligned | 0.0599 (40) | 6.1301 (40) | 0.2577 (40) |

The padded set is distinct from the original; values across the two are not paired measurements of the same mixture. Proxies are descriptive, not perceptual thresholds.
