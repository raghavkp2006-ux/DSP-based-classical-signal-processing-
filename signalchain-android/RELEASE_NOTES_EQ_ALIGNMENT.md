# EQ alignment correction — 2026-10-28

This is a correctness fix for the offline Android EQ, not a validated speech-intelligibility or dereverberation improvement. It is not a new v1.0.0 release tag.

## Change

The app now uses `Eq.applyAligned`: each symmetric FIR is compensated for its group delay before its band output is mixed with the dry signal. Previously those delayed bands interfered destructively with the dry signal. The legacy `Eq.apply` remains unchanged in behavior for comparison and its original golden tests. Agent thresholds, compressor, spectral subtraction and ONNX math were not changed. This intentionally changes Android EQ output relative to the legacy Python causal reference; whole-pipeline Python parity is not claimed.

## Validation

- `python scripts/make_eq_aligned_reference.py`: 18 independent scipy FIR/direct-convolution cases, 36,864 samples; middle impulse, last-sample impulse and mixed signal; rates 16/44.1/48 kHz; EQ strengths 0.6 and 1.0. Existing golden fixtures were not edited.
- `gradlew.bat test assembleRelease` with `SIGNALCHAIN_CLARITY_WAV` pointing to the local 12-second cafe excerpt: 34/34 debug tests and 34/34 release tests; zero failures, errors or skips. Without that variable the private-recording comparison is intentionally skipped.
- Independent-reference maximum absolute error: 2.3841858e-7 with a fixed 1e-5 tolerance. The middle impulse lands at the original index, matches the independently convolved composite kernel center tap and remains symmetric. The edge matches stage-by-stage convolution with zero extension and truncation.
- Full frequency response is evaluated from the actual Kotlin impulse responses on an 8192-point FFT grid, for both strengths and all three rates. This tests the complete linear EQ directly rather than estimating it from a finite swept sine. Complete DC-to-Nyquist old/new/difference CSVs and a plot are in `verification/eq-alignment/`. Across the 300–7000 Hz passband the aligned minimum is -0.01418 dB; the worst old Full-adaptive notch is -19.98507 dB. Intentional high-pass and low-pass roll-offs are retained.
- 18 loud-input checks: near-full-scale sine, multitone and alternating full-scale samples at all three rates and both strengths. Maximum EQ peak 1.922686; compressor peak 2.9765346. These are floating-point intermediates, never exported or converted to PCM before normalization; EQ scaling linearity passes within 1e-5, excluding hard-clipping in that stage. The pre-existing compressor/normalizer behavior is unchanged. Normalized peak 0.99 for all cases, zero samples at or beyond full scale. The normalizer's existing internal 0.98 limiter remains; zero full-scale clipping is not a claim of zero limiting or distortion.
- The first 12 seconds of `freesound_community-cafe-noise-32940.mp3` were decoded locally and processed through both chains with the real ONNX model. Both selected Full adaptive, preserved 529,200 samples at 44.1 kHz, produced finite outputs with peak 0.99, and completed native inference without fallback. Original MP3 SHA-256: `c28b74685b2799fc1f12c0d566768a79e2c5776c1abdc4d79de4210bbb8c6432`. Private audio is ignored and not committed.

The previous new end-impulse assertion (>0.5 amplitude) was invalid for a bandwidth-limited filter. The owner explicitly approved replacing it with independent convolution on 2026-10-28. No tolerance was widened; the old verified golden tests remain intact.

## Build and device evidence

- Release APK: `app/build/outputs/apk/release/app-release.apk`; 67,954,896 bytes.
- APK SHA-256: `0ad97e9205d46712f116095465573048406d139ee430c2ed7b6069763b217e33`.
- SDK build-tools 34.0.0 `apksigner.bat verify --verbose --print-certs`: verifies, v2 true, v3 false. Certificate SHA-256: `fd3d8393750d142c3182d22f23c18758528f7cc15f052631b813b39013690e4f`.
- SDK platform-tools `adb -s ed01e333 install -r .../app-release.apk`: Success, preserving app data.
- `adb shell am start -W -n com.signalchain.app/.MainActivity`: Status ok, cold launch, TotalTime 198 ms. OnePlus 13R / Android 16; installed version remains 1.0.0 / code 3. Identify this correction by APK hash.
- `adb shell sha256sum <installed base.apk>` matches the APK hash above.

Device installation and launch were checked. Processing and listening to the corrected output on the device remain pending; no claim is made that the full device smoke checklist passed.

## Not measured

- Speech intelligibility gain: **not measured**. The cafe recording has no isolated clean target reference.
- Echo/reverb removal: **not measured and unproven**. Delay alignment does not implement dereverberation or target-speaker separation.
- Whole-recording listening quality and the earlier 40-condition Kotlin evaluation remain outstanding.

Reprocess the cafe file on the phone to create a fresh output; old history entries contain old audio.
