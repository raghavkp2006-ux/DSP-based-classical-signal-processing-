# Kotlin evaluation

40 conditions; STOI uses self_eval.evaluate on exported PCM WAVs. Python uses the legacy causal EQ; the aligned-minus-legacy Kotlin arm isolates the EQ change while leaving routing and other Kotlin stages unchanged.

| SNR | n | Noisy | Python | Kotlin legacy | Kotlin aligned |
|---|---:|---:|---:|---:|---:|
| -5 | 8 | 0.7671 | 0.7106 | 0.7106 | 0.7455 |
| 0 | 8 | 0.8473 | 0.7838 | 0.7833 | 0.8253 |
| 5 | 8 | 0.9117 | 0.8572 | 0.8780 | 0.9090 |
| 10 | 8 | 0.9529 | 0.9039 | 0.9249 | 0.9499 |
| 15 | 8 | 0.9761 | 0.9336 | 0.9339 | 0.9489 |
| all | 40 | 0.8910 | 0.8378 | 0.8461 | 0.8757 |

Aligned minus legacy overall STOI: +0.0296; aligned minus Python: +0.0379.
Within ±0.02 of Python: False; aligned trails noisy baseline: True.

| Version | Modulation ratio | Musical kurtosis ratio | Echo ACF |
|---|---:|---:|---:|
| noisy | nan | 1.0000 | nan |
| python | nan | 4.6542 | nan |
| kotlin_legacy | nan | 4.6681 | nan |
| kotlin_aligned | nan | 2.4540 | nan |

Available conditions per metric (modulation, musical, echo): {'noisy': {'modulation_ratio': 0, 'musical_kurtosis_ratio': 40, 'echo_acf': 0}, 'python': {'modulation_ratio': 0, 'musical_kurtosis_ratio': 40, 'echo_acf': 0}, 'kotlin_legacy': {'modulation_ratio': 0, 'musical_kurtosis_ratio': 40, 'echo_acf': 0}, 'kotlin_aligned': {'modulation_ratio': 0, 'musical_kurtosis_ratio': 40, 'echo_acf': 0}}. No sufficiently long contiguous noise-only segments were available for modulation or echo; those comparisons are not measured.
Aligned minus legacy musical ratio: -2.2141; aligned minus Python: -2.2002.
Mode counts (Kotlin): {'Full adaptive': 18, 'Classical DSP': 14, 'Light touch': 8}; Python: {'Full adaptive': 18, 'Classical DSP': 14, 'Light touch': 8}.
Legacy Kotlin minus Python STOI: +0.0083; this is within 0.02. The aligned-vs-legacy measurement shows the legacy EQ explains a +0.0296 aligned-arm shift for this set, without claiming it is the only difference between Python and Kotlin.

STOI here favors the unprocessed baseline in aggregate; it does not capture all perceptual quality aspects.

These proxies are descriptive; no perceptual or pass/fail threshold is implied. Missing/too-short noise-only segments produce NaN and are excluded from each mean.

Mode mismatches: 0 of 40; see kotlin_routing.csv for per-file analysis.

Aligned musical ratio exceeds Python in 11 of 40 conditions; the largest difference is +0.2152 at holdout_00_7976-110124-0013, bursty, 15 dB (Python 2.7889, legacy 3.0040, aligned 3.0040). Both Kotlin arms agree there, so EQ alignment does not explain this case. Upstream DSP and normalization differences are possible contributors, but the first divergent stage has not been identified; this is a diagnosis to investigate, not a confirmed cause.

## Python aligned-EQ arm

Same 40 conditions; both Python outputs are exported PCM and scored with `self_eval.evaluate`. The legacy arm remains the default for reproducibility.

| SNR | n | Python legacy | Python aligned | Aligned minus legacy |
|---|---:|---:|---:|---:|
| -5 | 8 | 0.7106 | 0.7453 | +0.0348 |
| 0 | 8 | 0.7838 | 0.8248 | +0.0410 |
| 5 | 8 | 0.8572 | 0.8872 | +0.0301 |
| 10 | 8 | 0.9039 | 0.9284 | +0.0245 |
| 15 | 8 | 0.9336 | 0.9486 | +0.0150 |
| all | 40 | 0.8378 | 0.8669 | +0.0291 |
