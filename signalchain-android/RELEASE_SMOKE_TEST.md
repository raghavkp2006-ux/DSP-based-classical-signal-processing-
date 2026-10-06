# Release APK smoke test

The verified APK is debug-signed because this checkout has no release keystore configured. Install it on the owner’s Android phone from PowerShell with:

```powershell
adb devices
adb install -r "C:\Users\ragha\OneDrive\Desktop\dspproject\signalchain-android\app\build\outputs\apk\release\app-release.apk"
```

## Manual checklist

1. Launch **SignalChain** and confirm the upload screen opens without a crash.
2. Choose a short recording with clearly audible background noise and wait for enhancement to finish.
3. Confirm the result screen shows a processing mode. For a low-SNR or non-stationary sample, confirm it shows **Full adaptive** and no ML-fallback warning appears.
4. Play the enhanced output through to the end and confirm playback works.
5. Use **Share or Save Enhanced Audio**, choose an available share target, and confirm it receives a playable WAV file.
6. Clear session history and confirm the result and selected recording disappear from the screen.

Device installation and this manual smoke test have not been performed by Codex.
