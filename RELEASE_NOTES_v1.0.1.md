# SignalChain v1.0.1 release preparation

Prepared on `fix/dsp-fidelity`; version name `1.0.1`, version code `4`. No v1.0.1 tag or merge has been created. The Android production code and version in these artifacts were built at `0ff6951`; subsequent commits add evaluation scripts, tests, and documentation only.

## Changes

- `2a6225b`: make disabled spectral subtraction an identity in Python and Kotlin.
- `48af309`: use fixed-length VAD hangover based on original speech detections in both implementations.
- `aa09186`: pad enabled STFT boundaries, cover the final frame, crop after overlap-add, and floor the overlap weight in both implementations.
- `525fb42`: forward the Python EQ selection through feedback retries.
- `0ff6951`: set Android version name/code to 1.0.1/4.
- `ae805f2`: add per-file, evaluation-only Python post-filter path logging, with no production signal-processing change.
- `fabf925`: add a report-only Classical subtraction/Wiener factorial diagnostic.

## Signed artifacts

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `app-arm64-v8a-release.apk` | 18392582 | `14c10f752b66c40364ff13275cc105f8bcbeedb291ed98e7799ee4fed9a2c043` |
| `app-armeabi-v7a-release.apk` | 12909734 | `69f73a4cd139f9422bf25051a9c8b1a6f4399146e35df9dcfff9df035a7b3689` |
| `app-x86_64-release.apk` | 20326136 | `255ae7c77c48cd820ee1e11782532760a83a086feb54629c45afedc93adeb724` |
| `app-universal-release.apk` | 67954896 | `3c706c6106254a0b2ac8ac942e25b1dbf511690ed56dbd7540afe8c075601b0a` |
| `app-release.aab` | 26701540 | `0e2c7b31866477125376b650208f37be6df615a874bbcc096b69430deb8952fc` |

APKs are under `signalchain-android/app/build/outputs/apk/release/`; the AAB is under `signalchain-android/app/build/outputs/bundle/release/`. All APKs verify using APK Signature Scheme v2, with v1 and v3 reported false. The AAB passes `jarsigner -verify` and cached bundletool 1.16.0 structural validation (using cached aapt2-proto 8.5.2). The signer certificate SHA-256 is `fd3d8393750d142c3182d22f23c18758528f7cc15f052631b813b39013690e4f` for both APKs and AAB. All APKs identify version name 1.0.1 and version code 4. Each of the five artifacts contains the same ONNX model as the source asset: SHA-256 `d4bafec841570fc8cbbccfea0f8007b52324208f4fcf232a6f2c977e436b1506`.

**AAB verification caveat:** `jarsigner` reports `jar verified` and exit code 0, but warns that the certificate is self-signed/untrusted, the signature lacks a timestamp, POSIX attributes are not signature-protected, and `JarInputStream` sees a missing manifest and different signed entries because the archive's signature manifest occurs at the end. Bundletool structural validation passes, but that does not establish compatibility with every streaming verifier. This warning remains unresolved and should be reviewed before distribution.

## Checks

- `assembleRelease`, `bundleRelease`, `lintRelease`, `testDebugUnitTest`, and `testReleaseUnitTest` passed. Each JVM variant reported **54 tests, 3 skipped, 0 failures**. The skipped classes are `ClarityComparisonTest`, `KotlinAblationExportTest`, and `StageComparisonExportTest`, which require evaluation inputs. Other environment-gated export tests can return without exporting during the ordinary suite; the Classical factorial test was run separately with its inputs and passed for all 12 files.
- `lintRelease`: **0 errors, 10 warnings**: one old target API, four dependency-version notices, one data extraction rules notice, one obsolete SDK/resource qualifier notice, one unused resource, and two monochrome launcher icon notices. Kotlin test compilation also reports nullable-receiver warnings in three evaluation test files.
- Python targeted suite: **11 passed**, including trained-path, fallback-path and not-used logging tests. The logging evaluation reran 80 files: **39 trained model, 0 spectral-gating fallback, 41 post-filter not used**. All 80 exported WAVs were byte-identical to the prior evaluation outputs. See `ablation_output/python_model_path.csv`.
- Kotlin production aligned EQ after the boundary fix, 40 conditions per set: original mean STOI **0.8681 output vs 0.8910 noisy input**; padded **0.8866 vs 0.8933**. See `ablation_output/STFT_BOUNDARY_FIX.md`.
- Padded Classical factorial, 12 conditions: noisy 0.954742 STOI; subtraction only 0.948100; Wiener only 0.951292; both 0.947567; neither 0.938375. Neither is already below noisy, so neither operation alone explains the gap. See `ablation_output/CLASSICAL_FACTORIAL.md`.

## Known limitations

- Kotlin production uses aligned EQ; v1.0.0 shipped legacy EQ. No default was changed during this release preparation.
- Mean output STOI is below noisy-input STOI on both synthetic sets (numbers above). These two-clip synthetic evaluations do not establish subjective listening quality.
- Forced Classical and Full processing alter clean speech: production Kotlin measured about **4–17 dB SI-SDR vs clean** across the two clips and sets; route-specific results are in `ablation_output/stftfix_forced_clean.csv`.
- The padded production modulation-ratio proxy rose from **0.3705 to 1.2361** with the STFT boundary fix. For this noise-only absolute 2–10 Hz modulation-power proxy, lower means less measured residual-noise fluctuation and is favorable for that narrow objective. It is not a validated standalone quality score; amplitude reduction can also lower it, so listening and complementary metrics matter.
- Python's production fallback from the trained model to spectral gating is not logged. The new path logging applies to the evaluation script only.
- Room echo/reverb removal and STT remain outside this release. The device smoke checklist remains pending with the owner.
