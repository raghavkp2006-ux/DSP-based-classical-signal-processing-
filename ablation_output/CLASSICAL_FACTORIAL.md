# Classical spectral-stage ablation: padded set

Twelve padded files routed to Classical DSP by the Kotlin production agent. The test-only factorial harness retains production routing and parameters, VAD/noise PSD, pre/de-emphasis, aligned EQ, compression, normalization, and PCM export; it independently enables subtraction and adaptive Wiener gain. Neither bypasses the spectral stage entirely. Both is asserted equal to production float output on all twelve files. No DSP behavior or default was changed. STOI is computed against clean, rounded per file to four decimals, then averaged.

| Arm | Mean STOI | Difference vs noisy | Difference vs neither |
|---|---:|---:|---:|
| Noisy input | 0.954742 | 0 | +0.016367 |
| Subtraction only | 0.948100 | -0.006642 | +0.009725 |
| Wiener only | 0.951292 | -0.003450 | +0.012917 |
| Both (production) | 0.947567 | -0.007175 | +0.009192 |
| Neither | 0.938375 | -0.016367 | 0 |

The below-noisy mean is already present with neither spectral operation, so this experiment does not identify either one as the sole cause of the gap. All three enabled arms score above neither. Adding subtraction to Wiener changes mean STOI by -0.003725; adding Wiener to subtraction changes it by -0.000533. These are conditional contrasts, not independent causal shares: the spectral gain, other nonlinear stages and peak normalization can interact. Isolating EQ/compression/normalization would require further controlled ablations. Per-file scores are in `classical_factorial.csv`.

The reported modulation ratio is the output-to-input ratio of **absolute noise-only 2–10 Hz envelope modulation power**. For the narrow goal of reducing that fluctuation in the noise-only regions, lower is favorable; 1 means the same measured power as noisy input and greater than 1 means an increase. It is not a validated standalone perceptual quality score: level changes can lower it even when artifacts or speech quality worsen, and 2–10 Hz modulation is important to speech. Listening and complementary metrics are required. See [Elliott and Theunissen, envelope modulation and quality](https://pmc.ncbi.nlm.nih.gov/articles/PMC4627935/) for the broader perceptual context; it does not validate this particular proxy.

Reproduce: set `SIGNALCHAIN_ROOT` to the repository path, run `gradlew.bat testDebugUnitTest --tests *ClassicalStagesAblationTest*`, then run `python scripts/classical_factorial_eval.py`. Audio stays local and ignored.
