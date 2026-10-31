# PROGRESS — SignalChain v1.1.1 hardening

## Implemented on `v1.1.1-hardening`

- The default estimator is the VAD-independent minimum-statistics tracker: smoothed periodograms, a sliding 1.5-second minimum with bias compensation, and a startup ramp. It returns one static PSD from the final analysis frame for the whole file. The prior VAD-gated estimator remains available for A/B evaluation.
- Removed the over-subtraction cap, both 0.30 gain floors, and the `useNewLimits` switch. C1 met the requested comparison rule on both sets: its overall STOI delta was within 0.002 of C3 and its median nonspeech attenuation was stronger. Routing, ONNX, and STFT behavior were not changed.
- Paired C1−C3 STOI difference was −0.00149 (95% CI −0.00209 to −0.00094) original and −0.00045 (CI −0.00073 to −0.00019) padded. Median nonspeech output/noisy level change was 1.31 dB lower with C1 (paired median-difference CI −1.64 to −1.11 dB) original and 1.17 dB lower (CI −1.90 to −1.08 dB) padded, so both conditions in the removal rule passed.
- Evaluation-only JVM tests are marked with the `EvaluationTest` category and can be run with `:app:evaluationTest`; absent local inputs are reported as JUnit skips via assumptions.

## Measured results from this branch

The full agent was rerun on 40 original and 40 padded synthetic conditions after removing the limits. Original-set STOI was 0.8910 noisy / 0.8955 output (delta +0.00447; paired 95% CI +0.00056 to +0.00837). Padded-set STOI was 0.8933 / 0.9018 (delta +0.00846; CI +0.00480 to +0.01248). ESTOI deltas were +0.03168 (CI +0.02266 to +0.04038) and +0.03482 (CI +0.02667 to +0.04310). Gains over unprocessed audio are modest, which is expected for classical single-channel enhancement.

Stress evaluation used 24 generated clips (2 clean sources × 4 noise types × 3 conditions). For a 4× mid-file noise-power step, the current final PSD was 1.01× the median final-frame oracle, while its median ratio to per-frame oracle power over active frames was 2.11×; mean STOI delta was +0.0054. For noise confined to the last 2 seconds, the final-frame ratio was 1.03×, but current output STOI averaged 0.0149 below noisy input. With continuous speech during the final 3 seconds, final-frame ratio was 1.05× and STOI delta +0.0049. Current output was better than the legacy VAD-gated estimator in mean STOI in all three stress conditions; the late-noise output still scored below noisy input. The tracker estimates one PSD for an entire file, so level changes inside the file can leave earlier sections over-suppressed.

## Validation and limits

- Default JVM task on this checkout: 66 tests, 0 failures, 16 assumption skips per variant (debug and release). The skipped tests require local evaluation datasets, metadata, or a recording.
- All-off PCM16 SI-SDR was 83.75 dB; the `61ec650` legacy-estimator snapshot hash test passed.
- The evaluation sets remain small synthetic mixtures (40 conditions each, 2 sources × 4 noise types × 5 SNRs); the stress conditions use only 2 clean sources. PESQ/DNSMOS have not been measured. The owner device smoke checklist remains pending.
