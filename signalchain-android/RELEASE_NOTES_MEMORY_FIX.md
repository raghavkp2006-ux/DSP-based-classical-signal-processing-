# Processing-memory crash fix — 2026-10-28

## Diagnosis and changes

The connected phone's crash log shows SignalChain OutOfMemoryError at 14:16:03 and 14:17:35, with a 268,435,456-byte Java heap limit. The UI's allocation failed while the processing worker consumed the shared heap; this is not evidence of a Compose logic exception. An older failure also occurred before the EQ correction. The earlier 12-second desktop test did not cover this 60.7-second input.

- Replaced per-sample boxed Double lists in compressor RMS and SNR energy calculations with primitive accumulation in the same order. Exact-reference test passes, including empty input behavior.
- Added bounded ONNX processing: globally resample once, process 16,384-sample cores with 1,024-sample context on each side, discard context outputs, and resample back once. Core/halo positions align to the 128-sample STFT hop. The bundled model's 5/3/3/1 temporal convolution kernels have a four-frame receptive radius; the halo also covers analysis/synthesis window support. A changed model with a larger temporal context requires revisiting this bound.
- Kept the original whole-clip method for comparison. Whole-clip versus bounded tests at 16/44.1/48 kHz (including internal seams and the true tail) measured maximum absolute error 0.0 against a fixed 1e-5 assertion. No model weights, EQ coefficients, noise-reduction thresholds or existing golden files changed.
- No larger app heap was requested. Inference working spectra/tensors are bounded, but decoded audio/output storage still scales with duration; this is not a guarantee for arbitrary-length recordings or an unlimited session history.

## Reproduce

From repository root, with a local decoded full recording (audio is not committed):

```powershell
$env:SIGNALCHAIN_CLARITY_WAV = '<absolute path to full input.wav>'
.\signalchain-android\gradlew.bat -p signalchain-android -I "$PWD\scripts\android-test-heap256.gradle" test assembleRelease
```

Both debug and release: 36 tests, 36 passed, 0 failures/errors/skips. Each JVM test worker has maximum Java heap 256 MiB. JVM native memory is separate, as it is on Android; this is not a total-process-memory cap.

The full cafe recording completed both comparison paths with actual bounded ONNX inference: 2,678,400 samples at 44,100 Hz, Full adaptive, finite output, peak 0.99. Full-recording duration is 60.7347 seconds. The legacy comparison retains causal EQ but also uses bounded ML to make full-file comparison feasible at this heap size. Short-input BoundedInferenceTest separately compares the original whole-clip ML implementation. Raw test output is in `verification/memory-fix/`.

Release APK SHA-256: `f40643ea19f97271d9ea5b798a5b33a8c3249dc634127ffa3e6e1a6c8feaa350`. SDK build-tools 34.0.0 apksigner verification succeeded; `adb install -r` succeeded without clearing app data. Cold launch returned Status ok (154 ms).

Intelligibility gain and echo/reverb removal remain not measured. The EQ alignment correction is retained.

## Connected-device processing check

On the OnePlus 13R, selected the original `freesound_community-cafe-noise-32940.mp3` through the app's file picker. MediaCodec decoded 60.7 seconds at 44,100 Hz. Enhancement completed into the results screen at 14:23:58, displaying Full adaptive and a history entry for the same input. PID 24836 survived from launch through processing. Filtered SignalChain/AndroidRuntime logs for that PID contained no fallback or crash errors. This is a real device processing check, not just launch verification.

`adb shell dumpsys meminfo com.signalchain.app` after completion showed Dalvik Heap Size 101,281 KB / Alloc 25,320 KB / Free 75,961 KB. This is a post-run snapshot, not measured peak heap usage. Native heap allocation was 22,535 KB. The app's original 256 MiB heap limit was not increased.

The enhanced play button entered Playing state on the device. Playback-to-end verification is recorded separately after completion; audible quality cannot be inferred from UI state.

Follow-up after the interruption: PID 24836 was still alive and filtered app crash/error logs were empty. The installed base.apk SHA-256 matched the tested replacement. The phone had been used in another app and SignalChain subsequently showed a fresh upload screen; uninterrupted playback-to-end was therefore not verified. Only playback start and successful full-file processing are claimed.
