# SignalChain v1.0.0 build notes

This build supports local audio selection and recording, adaptive DSP, an optional local ONNX post-filter, playback and WAV sharing. Room echo/reverb is not removed. STT is not in v1.0.0; planned for v1.1. Device smoke test pending owner.

On 40 conditions, mean STOI was 0.8910 noisy, 0.8378 Python legacy EQ, 0.8669 Python aligned EQ, 0.8461 Kotlin legacy EQ and 0.8757 Kotlin aligned EQ. The Kotlin result trails the unprocessed baseline; these are reference-based measurements rather than a listening-quality guarantee. See [Kotlin evaluation](ablation_output/KOTLIN_EVAL.md) and [SI-SDR evaluation](ablation_output/PADDED_EVAL.md).

Artifacts below were built in this session. Sizes use MiB (1,048,576 bytes); APKs are in `signalchain-android/app/build/outputs/apk/release/` and the AAB is in `signalchain-android/app/build/outputs/bundle/release/`.

| File | Bytes | MiB | SHA-256 |
|---|---:|---:|---|
| app-arm64-v8a-release.apk | 18392582 | 17.54 | 20c3899e1aa9eb6f5974a96c28119c9c35a913b310efcf58b8d7439eab604c47 |
| app-armeabi-v7a-release.apk | 12909734 | 12.31 | e19459746c1b9b81264a294fc456d6ed1a72b1d1ebd17ac81ce63713396b000d |
| app-x86_64-release.apk | 20326136 | 19.38 | 0832ebbd6b0f4d8092eeab8612d60000ec3bb9815198f549ae2877f9ba7485cc |
| app-universal-release.apk | 67954896 | 64.81 | 82123fac5ecf1825f8369bd51b3be8ad1f8a99a9f77eee5dedb6508372c86cfd |
| app-release.aab | 26700795 | 25.46 | 3ff038a0adf655f1fb6ee89d3f9e72ec0e18e435fd6c5fec16e122ad56ab0eba |

All four APKs verify with signature scheme v2; v3 is false. The certificate SHA-256 reported by build-tools 34.0.0 apksigner is `fd3d8393750d142c3182d22f23c18758528f7cc15f052631b813b39013690e4f`. They identify versionName 1.0.0, versionCode 3, minSdk 26, targetSdk 34. The universal APK retains x86 as well as the three targeted ABIs; debug is still universal with all four ABIs but its output file is now named `app-universal-debug.apk` by the split configuration.

The model in each APK matches source SHA-256 `d4bafec841570fc8cbbccfea0f8007b52324208f4fcf232a6f2c977e436b1506`. The ONNX Runtime keep rule remains in `app/proguard-rules.pro`, and mapping contains retained `ai.onnxruntime` classes; `testReleaseUnitTest` passed.

`lintRelease` passed with 0 errors and 10 warnings: old target API; four dependency-version notices; data extraction rules; obsolete v26 resource qualifier; unused app_name resource; and two monochrome launcher-icon notices. A missing microphone-permission exception handling lint error was fixed. No local uncaught-exception logger was added.
