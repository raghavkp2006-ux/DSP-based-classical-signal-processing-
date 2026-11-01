# SignalChain v1.1.1 device smoke run

## Status

This is a partial smoke run. The release APK installed and the launcher cold-started. No evaluation audio was transferred to or processed on the phone, so per-clip route, processing time, peak processing memory, output, parity, D1, D2, and D3 are **not measured**. No device crash, ANR, or OOM was observed during the app launch; this does not establish D1 for the seven clips.

The app imports files only through the Android system `OpenDocument` picker. Its enhancement output is written under its private `cache/exports` directory, and export opens a system share chooser. Source review found no test intent, batch interface, or instrumentation runner for processing/import/export. The task limits adb file changes to this app and its own scratch area, which prevents staging evaluation WAVs in the shared storage that `OpenDocument` can browse. I did not copy audio to shared storage or attempt coordinate-based picker navigation. The owner must stage the listed inputs through USB/MTP and select them in the app; the manual procedure below records route, time, memory, filtered logcat, and output retrieval.

## Step 0: preflight captured in this session

| Item | Result |
|---|---|
| `adb devices -l` | Exactly one device: `ed01e333`, state `device`, product `OP5D3BL1`, model `CPH2691`, transport 1 |
| Model / Android / SDK | CPH2691 / Android 16 / API 36 |
| ABI | `arm64-v8a` |
| `/data` storage | 219 GB total, 90 GB used, 129 GB available (as reported by `df -h`) |
| Battery | 96%; USB powered; battery service temperature 31.7 °C |
| RAM | MemTotal 11,483,112 kB; MemAvailable 4,892,004 kB |
| Thermal status at preflight | 0 (none); battery 32.0 °C, skin 36.2 °C in the later sample |

## Step 1: APK verification and install

All local APK SHA-256 values matched `PROGRESS.md` for the v1.1.1 artifacts.

| APK | SHA-256 | Install / signer result |
|---|---|---|
| `app-universal-debug.apk` | `F6AB6D6E95A5D0C965EAA08A58DA7C7E5917A76E89C3DD99143DD29E485D2DB2` | `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: the existing `com.signalchain.app` has a different signer. No uninstall or data clear was attempted. APK signer: Android Debug. |
| `app-arm64-v8a-release.apk` | `4C87BE3AA5DF9524E95EA98F35D43D036CEC715C3BAFD060BF04BF3C1962FE9E` | `Success`, installed as an update. `apksigner verify --print-certs` passed; non-debug signer DN `CN=Signalchain, OU=Dev, O=Signalchain, L=City, ST=State, C=US`, certificate SHA-256 `fd3d8393750d142c3182d22f23c18758528f7cc15f052631b813b39013690e4f`. The update succeeded against the pre-existing app, whose prior package version was 1.0.0. |
| `app-armeabi-v7a-release.apk` | `12DF1A68CC15FF1021FB0EEEC767484C2FC885FE2FAD40BF86F4B7BD97517010` | Hash checked; not installed (device ABI is arm64-v8a). |
| `app-x86_64-release.apk` | `F1A6E5F4198BEE02C6C99C975D9639A30ABEC485EEE94BEAD5A0A24F4804A32E` | Hash checked; not installed (device ABI is arm64-v8a). |
| `app-universal-release.apk` | `2936F3A1E9DA59F817AF63914942B91C20E5D6F38FCC916FC5C861DF9110C2DE` | Hash checked; not installed. |

The installed package reports `versionCode=4`, `versionName=1.0.1` (build metadata), min SDK 26 and target SDK 34. Cold start command `adb shell am start -W -n com.signalchain.app/.MainActivity` returned `Status: ok`, `LaunchState: COLD`, `TotalTime: 231 ms`, `WaitTime: 241 ms`. One app process was present. Filtered `SignalChain` logcat for that PID contained no entries at the time sampled. Idle app memory was 19,355 kB total PSS, including 6,579 kB native-heap PSS; this is not a processing peak. No clip processing occurred, so no processing thermal or crash claim can be made.

## Step 2: selected inputs

`clips.csv` records full local paths, clean-reference paths, duration, sample rate, channels, sample width, expected/observed-route field, and listening prompts. The base Light touch / Classical / Full route labels are from the local original-set C3 routing record; the app's actual device route still must be recorded from the app log or UI.

The three existing stress inputs are 6 s, 16 kHz mono float32 WAVs, supported by the app WAV loader. The 2-minute input is generated locally by concatenating all 40 original noisy conditions: 120.000 s, 16 kHz mono PCM16. A matching clean reference is generated in the same order. Both generated WAVs are gitignored and were not copied to the phone or committed.

Non-WAV conversion was skipped: `where.exe ffmpeg` found no executable on this host. No MP3/M4A was produced or tested.

## Steps 3–4: clip processing and parity

Not run. There is no device output or route/time/memory sample for any clip, and thus no valid device-vs-JVM SI-SDR, STOI/ESTOI delta, duration, NaN/Inf, clipping, or peak-memory result. The app launch alone produced no filtered app-tag log entries. The local JVM evaluation outputs are not substituted for device outputs.

## Gates

| Gate | Result | Evidence |
|---|---|---|
| D1 no crash/ANR/OOM for clips 1–7 | **NOT MEASURED** | No clips were run. Launcher cold-start succeeded, but that cannot pass the processing gate. |
| D2 device/JVM parity | **NOT MEASURED** | No device outputs. |
| D3 >=2-minute completion without OOM | **NOT MEASURED** | 120.000 s local input was prepared but not run on the device. |

Report-only device values: release update install `Success`; debug install failed with the exact signature conflict above; release signer is non-debug and verifiable; cold start 231 ms; idle PSS 19,355 kB / native heap PSS 6,579 kB; baseline thermal status 0. Processing RTF, peak PSS/native heap, route behavior and processing thermal state are not measured.

## Owner manual run procedure

1. Use the paths in `clips.csv`. Stage the inputs on the phone through USB/MTP into a user-visible folder (for example `Download/SignalChainSmoke`); this must be an owner action because this run is constrained to adb access to the app and its own scratch folder.
2. Open SignalChain, choose file import, select each WAV, run enhancement, and note the displayed `Processed via ...` route. In parallel, sample only this package with `adb shell dumpsys meminfo com.signalchain.app` about once per second. Record a start/end wall-clock time for RTF.
3. Capture only the app PID and its app tag; do not save unfiltered logcat:

   ```powershell
   $appPid = (adb shell pidof com.signalchain.app).Trim()
   adb logcat -v time --pid=$appPid -s SignalChain:I SignalChain:W SignalChain:E AndroidRuntime:E
   ```

4. Output is app-private under `cache/exports`. After a run, list only that app-owned folder with `adb shell run-as com.signalchain.app ls cache/exports`, then pull a named WAV with:

   ```powershell
   adb exec-out run-as com.signalchain.app cat cache/exports/<output-name>.wav > <local-output-name>.wav
   ```

5. Run the 120 s clip last; poll package memory every second through completion and note thermal status before/after with `adb shell dumpsys thermalservice`. For listening, compare each noisy input against its enhanced output and fill `listening_sheet.csv`.

## Files and changes

- `prepare_run_sheet.py`: creates the local long clip and manifests from the synthetic evaluation WAVs; it does not touch the device.
- `clips.csv` and `listening_sheet.csv`: manual measurement and listening sheets.
- No production source, tags, or main branch were changed. No WAVs or logcat were committed.
