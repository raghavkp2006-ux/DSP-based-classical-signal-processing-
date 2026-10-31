# SignalChain

SignalChain is a native Android app that analyzes noisy audio, chooses an enhancement strategy, and processes the audio entirely on-device. It combines a classical DSP pipeline with a CNN post-filter, with an SNR-aware routing agent deciding how the stages are applied. Audio is not uploaded to a server, and the root-level Python code is not a separate running product anymore: it is the offline training and evaluation toolchain used to build and validate the model shipped in the Android app.

## Project structure

The repository has two main pieces:

- `signalchain-android/` — the current app that users install and run. Its bundled ONNX model and Kotlin implementation perform inference and audio processing on the device.
- The root-level Python files — including `dsp_pipeline.py`, `train_denoiser.py`, `self_eval.py`, `ablation_runner.py`, `make_ablation_testset.py`, and `export_onnx.py` — form the offline training, export, and evaluation toolchain for the CNN post-filter. They are developer tools for retraining and validating the Android model, not a live web app or service.

## Android app

The current Android version is `1.0.0`.

### Requirements

- JDK 17
- Android SDK with the required command-line tools; Android Studio is not required
- A physical Android device or emulator running API 26 or newer
- `adb` available on your `PATH` for installation to a connected device

### Build a debug APK

From the repository root:

```bash
cd signalchain-android
./gradlew assembleDebug
```

On Windows PowerShell, use `./gradlew.bat assembleDebug` if needed. The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device or emulator with:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

`assembleRelease` is not required for local development or testing. A release build requires a local keystore configured in `signalchain-android/local.properties`; `signalchain-android/local.properties.template` lists the required keys.

### Run the JVM tests

The test suite covers the Android DSP stages and the SNR-aware decision agent, along with the ONNX denoiser math and pipeline/UI helpers. The tests are under `signalchain-android/app/src/test/java/com/signalchain/app/` and do not require a device:

```bash
cd signalchain-android
./gradlew test
```

## Train and evaluate the CNN

These commands are for the offline Python toolchain, not for running the Android app. From the repository root, install the dependencies in a suitable Python environment:

```bash
python -m pip install -r requirements.txt
```

### Train on LibriSpeech `dev-clean`

Point `--data` at the directory containing the LibriSpeech `dev-clean` FLAC files:

```bash
python train_denoiser.py --data "C:\\path\\to\\LibriSpeech\\dev-clean" --epochs 8 --steps-per-epoch 500
```

By default, the best checkpoint is saved as `models/spectral_mask_denoiser.pt`, with metadata in the matching JSON sidecar.

### Create an evaluation set and run ablations

Create the deterministic held-out noisy set, then evaluate the unprocessed baseline, DSP-only pipeline, DSP plus CNN, and agent configurations:

```bash
python make_ablation_testset.py --data "C:\\path\\to\\LibriSpeech\\dev-clean"
python ablation_runner.py --metadata ablation_testset/metadata.csv --output ablation_output
```

Use `--limit` with `ablation_runner.py` for a smaller pilot run. The runner writes `ablation_results.csv`, `ablation_summary.csv`, and plots under the selected output directory.

### Export the model for Android

After training, export and numerically validate the checkpoint:

```bash
python export_onnx.py
```

This reads `models/spectral_mask_denoiser.pt` and writes `models/spectral_mask_denoiser.onnx`. Copy the resulting file to the Android asset path below so the app can load it at runtime:

```text
signalchain-android/app/src/main/assets/models/spectral_mask_denoiser.onnx
```

That asset path is already present in the current checkout.

## Current status and limitations

- Room reverb and echo in source recordings are not removed. Speech-to-text integration is outside the scope of this audio-enhancement release.
- The default noise estimate is VAD-independent minimum statistics over the final 1.5 seconds of analysis; one static PSD is then used for the whole file. The former VAD-gated estimator remains available for A/B evaluation. On a fresh 40-condition full-agent rerun, original-set STOI was 0.8910 noisy and 0.8955 output (delta +0.0045; paired 95% CI +0.0006 to +0.0084); padded-set STOI was 0.8933 noisy and 0.9018 output (delta +0.0085; CI +0.0048 to +0.0125). These gains over unprocessed audio are small, as expected for classical single-channel enhancement. ESTOI deltas were +0.0317 (CI +0.0227 to +0.0404) and +0.0348 (CI +0.0267 to +0.0431), respectively.
- Stress measurements (8 clips per condition) expose the static-PSD limitation. For noise power rising 4× halfway through a file, the current estimate matched the final-frame oracle at 1.01× median but was 2.11× the per-frame oracle at the median over active frames; average STOI delta was +0.0054. With noise only in the final 2 seconds, the final-frame PSD ratio was 1.03×, yet output STOI remained 0.0149 below noisy input on average. With continuous speech over the last 3 seconds, median final-frame ratio was 1.05× and STOI delta +0.0049; the legacy estimator had a -0.0145 delta. Thus the tracker handles late noise better than the VAD-gated legacy estimate, but one static PSD can still over-subtract earlier quieter regions and spectral processing can reduce intelligibility in some conditions. Detailed per-file results are in `ablation_output/v111_stress_per_file.csv`.
- The evaluation sets are small synthetic mixtures: 40 conditions each (2 speakers × 4 noise types × 5 SNRs). Stress tests used 2 clean sources × 4 generated noise types per condition. PESQ/DNSMOS have not been measured. Earlier stored Python ablations used legacy causal EQ.
- On the C3 Classical route, mean clean-referenced full-band SI-SDR improvement over noisy input was +0.33 dB (95% CI −2.41 to +2.95) original and +2.06 dB (CI +0.25 to +3.77) padded; speech-band improvements were +2.98 dB and +4.08 dB. These objective metrics do not establish perceived quality.
- The processing is file-based, not real-time. The CNN post-filter operates at 16 kHz; bounded inference reduces memory but complete decoded audio remains in memory.
- Android device smoke test is pending owner. A connected device processed and played one recording without crashing, but that does not complete the smoke checklist.
- Release APKs range from 12.31 MiB (armeabi-v7a) to 19.38 MiB (x86_64); the universal APK is 64.81 MiB. See [release notes](RELEASE_NOTES_v1.0.0.md) for hashes and ABI details.
- The last web-app version is tag `flask-legacy-final`.
