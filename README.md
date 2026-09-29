# SignalChain

SignalChain is a native Android app that analyzes noisy audio, chooses an enhancement strategy, and processes the audio entirely on-device. It combines a classical DSP pipeline with a CNN post-filter, with an SNR-aware routing agent deciding how the stages are applied. Audio is not uploaded to a server, and the root-level Python code is not a separate running product anymore: it is the offline training and evaluation toolchain used to build and validate the model shipped in the Android app.

## Project structure

The repository has two main pieces:

- `signalchain-android/` — the current app that users install and run. Its bundled ONNX model and Kotlin implementation perform inference and audio processing on the device.
- The root-level Python files — including `dsp_pipeline.py`, `train_denoiser.py`, `self_eval.py`, `ablation_runner.py`, `make_ablation_testset.py`, and `export_onnx.py` — form the offline training, export, and evaluation toolchain for the CNN post-filter. They are developer tools for retraining and validating the Android model, not a live web app or service.

## Android app

The current Android version is `0.2.0-rc1`.

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

- STOI is the only objective metric currently reported. PESQ is unavailable because the current environment lacks the required native build tooling.
- In the current stored full ablation report (40 held-out conditions), the unprocessed baseline has aggregate STOI `0.8910`, while the full agent has aggregate STOI `0.8219`. The full agent therefore still trails the unprocessed baseline on aggregate STOI.
- Real-time or streaming processing is out of scope; the app currently processes complete audio files.
- Whisper/STT integration is out of scope for the Android app.
- iOS support is out of scope.

The Python scripts and stored evaluation artifacts are useful for retraining and further investigation, but they do not change the fact that the shipping product is the Android app.
