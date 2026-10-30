# PROGRESS — SignalChain v1.1

## Implemented on `v1.1-psd-fix`

- Replaced the default VAD-gated noise PSD average with a VAD-independent Martin-style minimum-statistics tracker. It smooths periodograms at 0.80, tracks a 1.5 s rolling minimum with 2.20× bias compensation, and scales startup from 0.75× to 1.00× during the first second. The previous estimator remains selectable for A/B evaluation.
- Capped over-subtraction at 1.00 and set named minimum gains of 0.30 for spectral subtraction and Wiener filtering. The cap/floors are applied by the production path with the new tracker; legacy mode keeps the pre-v1.1 stage parameters.
- No routing thresholds, defaults, ONNX processing, STFT, framing, or windowing code changed.

## Measured results

On the original Classical subset (n=14), full-agent STOI changed from 0.9262 noisy to 0.9318 output (+0.0056; paired 95% CI +0.0010 to +0.0103). On the padded Classical subset (n=12), it changed from 0.9547 to 0.9570 (+0.0023; CI −0.0004 to +0.0046). The full agent across all 40 conditions scored 0.8910 noisy / 0.8970 output original (+0.0060) and 0.8933 / 0.9022 padded (+0.0089). STOI gains over the unprocessed input are small, which is expected for classical single-channel enhancement.

The PSD/oracle ratio median on the original Classical subset was 1.10× after the tracker versus 8.48× with the prior estimator. The full before/after, four-configuration, per-SNR, and per-file results are in [V11C_PSD_FIX.md](ablation_output/V11C_PSD_FIX.md) and the adjacent `v11c_*.csv` files.

## Validation and limits

- JVM suite: 64 tests, 0 failures, 3 skipped. The skipped exports require optional `SIGNALCHAIN_CLARITY_WAV` or `SIGNALCHAIN_ABLATION_METADATA` local inputs.
- Debug and release APK builds succeeded; release ran `minifyReleaseWithR8` with minification enabled.
- The synthetic evaluation is small (40 conditions per set: two speakers, four noises, five SNRs). PESQ and DNSMOS are not measured. The device smoke checklist remains with the owner.
