# Light touch fidelity investigation

A deterministic two-second synthetic clip routes to Light touch. Fidelity is measured against input after fitting least-squares global gain; residual dB is the negative of signal-to-error SNR. A deviation greater than -60 dB means residual dB > -60.

| Stage comparison | SNR dB | Max absolute residual |
|---|---:|---:|
| Pre-emphasis followed directly by de-emphasis | 158.0055 | 1.5033e-8 |
| SpectralSubtraction(use=false) output vs pre-emphasis input | 16.1769 | 0.17287 |
| De-emphasis vs original input | 17.3789 | 0.23488 |
| EQ off vs input | 17.3789 | 0.23488 |
| Compressor bypass vs input | 17.3789 | 0.23488 |
| Normalize vs input | 17.3810 | 0.98152 |
| Core output vs input | 17.3810 | 0.98152 |
| PCM16 output vs input | 17.3804 | 0.98148 |

The first measured violation is `SpectralSubtraction.apply(use=false)`. It disables spectral subtraction but still computes and applies a smoothed Wiener gain with a 0.25 floor; its STFT overlap-add and edge handling can also introduce differences. This result does not independently apportion the error among those mechanisms. VAD and noise PSD are measurement-only stages; the direct pre/de-emphasis check isolates the filter pair.

A candidate that bypasses the whole pre-emphasis/spectral/de-emphasis chain in Light touch, while preserving the existing normalizer and PCM writer, was evaluated without changing production. On the 8 Light touch files, mean STOI was 0.9701125 for current output and 0.9809250 for the candidate: +0.0108125. Input float and PCM16-only means were both 0.9809250 at `self_eval.evaluate`'s four-decimal precision; this does not establish exactly zero quantization effect. Per-file values are in `light_touch_fidelity.csv`.

Proposed fix: bypass the noise-reduction stages in Light touch, retain normalization/export, and add regression tests covering short clips, tails, and normalization. It has not been applied; causal contribution of the Wiener gain versus STFT boundaries needs a separate controlled test before choosing an implementation.

Reproduce: set SIGNALCHAIN_ABLATION_METADATA to the absolute metadata path, run `gradlew.bat testDebugUnitTest --tests *LightTouchFidelityTest*`, then `python light_touch_eval.py`. Inputs and candidate WAVs are local and ignored; `light_touch_stage_evidence.txt` captures the JVM output.

## Fix verification

`use=false` now returns a copy of the stage input in Python and Kotlin, bypassing the adaptive gain and STFT reconstruction. Enabled processing is unchanged and checked against pre-fix sampled output values; bypass tests cover short inputs, boundaries and a tail. Python's two unit tests and Kotlin's two stage tests plus the Light touch export test passed. The export now uses the actual `PipelineCore` output with the fix, not the earlier candidate approximation. For the eight original Light touch files, mean STOI changed from 0.9701125 to 0.9809250: **+0.0108125**. These results are rounded by `self_eval.evaluate` to four decimals per file. The historical table above describes the pre-fix baseline.
