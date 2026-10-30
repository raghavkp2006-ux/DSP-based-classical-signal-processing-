# v1.1 investigation: STOI and modulation

Report-only Kotlin production-route investigation. No production DSP, defaults, tags, or branches outside this investigation branch were changed. Three padded agent-routed groups contain 12 Classical, 21 Full and 7 Light touch files, drawn from two clean clips. Scores are means of per-file STOI rounded to four decimals; WAVs are evaluated as floating point unless noted. Per-file results are in `v11_downstream_stoi.csv`, `v11_wiener_classical.csv`, and `v11_modulation_matched.csv`.

## Downstream ablation

Production spectral processing, VAD/PSD and pre/de-emphasis are held fixed. In each “off” arm all other applicable production downstream stages stay on; in each “only” arm all other downstream stages are off. The test harness checks the all-on float output against PipelineCore for all 40 files at tolerance 1e-7. It does not enable EQ, dynamics, or ONNX when the route normally bypasses them: those cells are marked —. The limiter and normalization are interleaved inside production `normalizeAndLimit`; a test-only reimplementation separately switches their operations. With normalization off, the limiter sees a different level, so the contrasts are interventions with interactions, not independent component shares. In Full, turning ONNX off also removes its following normalize/limit pass; “only ONNX” feeds it the spectral output with no normalization. Input loading/peak normalization and pre/de-emphasis remain fixed. Float exports avoid implicit PCM clipping in unnormalized arms.

| Arm | Classical (n=12) | Full (n=21) | Light touch (n=7) |
|---|---:|---:|---:|
| Noisy input | 0.954742 | 0.822686 | 0.999886 |
| Production | 0.947542 | 0.813995 | 0.999886 |
| EQ off | 0.947592 | 0.815100 | — |
| Dynamics off | 0.943800 | — | — |
| Limiter off | 0.947550 | 0.813986 | 0.999886 |
| Normalization off | 0.946725 | 0.813667 | 0.999886 |
| ONNX off | — | 0.813457 | — |
| All downstream off | 0.944625 | 0.814543 | 0.999886 |
| Only EQ | 0.943808 | 0.813448 | — |
| Only dynamics | 0.947600 | — | — |
| Only limiter | 0.944625 | 0.814543 | 0.999886 |
| Only normalization | 0.944625 | 0.814543 | 0.999886 |
| Only ONNX | — | 0.814862 | — |

The prior **0.938375 “neither”** Classical baseline bypassed *both spectral operations* while keeping downstream stages on. It is not the same as the 0.944625 “all downstream off” baseline above, which retains the production spectral operations. No measured Classical downstream intervention has a mean below 0.938375; all are still below noisy 0.954742. The spectral-on/all-downstream-off result is already 0.010117 below noisy. Removing dynamics reduces production STOI by 0.003742 (12/12 files), while removing EQ changes it by only +0.000050 on average. These comparisons do not isolate a single cause for the below-noisy gap.

For Full, removing EQ improves mean STOI by 0.001105 (19/21 files); removing ONNX plus its final normalize/limit pass lowers it by 0.000538 on average. Dynamics are already bypassed. For Light touch, every tested arm and noisy input are 0.999886 at four-decimal per-file scoring; EQ, compression and ONNX are already bypassed.

Production floats do not exceed full scale. The hypothetical Classical “only EQ” and “only dynamics” floats exceed it on 3 and 7 files respectively. Simulating the production PCM16 clamp/quantization gives mean STOI 0.943817 and 0.947567, versus float 0.943808 and 0.947600; production PCM is 0.947567 versus float 0.947542. The per-file CSV includes these results and clipping counts.

## Wiener only vs subtraction plus Wiener

Both arms keep the same downstream production stages, aligned EQ, and route-specific VAD/PSD. “Both” is checked against PipelineCore before PCM export. The original and padded sets have different mixtures and route assignments and are not identical paired mixtures.

| Set | Classical files | Noisy | Wiener only | Both | Both − Wiener | Files lower with both |
|---|---:|---:|---:|---:|---:|---:|
| original | 14 | 0.926214 | 0.915536 | 0.888329 | -0.027207 | 14/14 |
| padded | 12 | 0.954742 | 0.951292 | 0.947567 | -0.003725 | 10/12 |

The stacked gains reduce STOI versus Wiener alone on every original Classical file and on 10/12 padded Classical files. This supports testing a Wiener-only or reduced-subtraction candidate; it does not by itself prove over-suppression, determine the noise-removal trade-off, or generalize beyond two clips.

## RMS-matched noise modulation

Each output is multiplied by noisy-input full-file RMS divided by output full-file RMS, without clipping; this is an RMS loudness proxy, not LUFS or a perceptual loudness match. The same clean-derived noise-only mask and 2–10 Hz modulation-power calculation are used as before. Because this power scales quadratically with gain, ratios are rescaled by gain squared; direct recomputation on two files verified the calculation.

| Kotlin production, padded | Before boundary fix | After boundary fix |
|---|---:|---:|
| Unmatched mean ratio | 0.3705 | 1.2361 |
| RMS-matched mean ratio | 0.3766 | 0.7018 |
| Median matching gain | 6.192× | 0.819× |

The RMS-matched mean rises by +0.3252; it rises on 30/40 files. It rises on all white, tonal and bursty cases, but on none of the brown-hum cases. The large pre-fix matching gains reflect its low overall output RMS and can magnify the effect of earlier edge-transient normalization. Values below 1 mean less measured noise-only modulation power than the noisy baseline; this is not a listening-quality threshold.

## Ranked candidate investigations

1. **Classical spectral stack:** evaluate Wiener-only or reduced subtraction on more speech and noise, including residual-noise levels and listening. Its paired STOI advantage is largest and consistent on the original set; no default change is justified from these two clips alone.
2. **Full-route aligned EQ:** investigate gain or bypass variants in a controlled study. EQ-off improves Full STOI in 19/21 padded files by 0.001105 on average, a small effect; verify speech quality and spectral balance before proposing a change.
3. **Noise estimation and gain calibration:** measure estimated noise PSD, suppression and speech distortion by frame, including onset/silence boundaries, before tuning subtraction or Wiener parameters. The present tests do not identify a specific PSD defect. Keep the ONNX/normalization interactions in that follow-up: their individual Full mean effects here are small and coupled to input level.

Do not prioritize removing Classical dynamics from these results: disabling it lowers mean STOI on all 12 files. Limiter and normalization mean effects are small in these float tests, but their interactions and actual PCM headroom remain relevant.

Reproduction: set SIGNALCHAIN_ROOT to the repository root and run `gradlew.bat testDebugUnitTest --tests *V11DownstreamAblationTest* --tests *V11OriginalClassicalTest*`; then run `python scripts/v11_downstream_eval.py`, `python scripts/v11_pcm_eval.py`, and `python scripts/v11_modulation.py`. Original padded Classical Wiener-only PCM exports come from the previously verified `ClassicalStagesAblationTest`. Local WAVs are ignored.
