# Release APK smoke test

The release APK is signed with the configured release keystore. The full checklist is pending owner device testing; an earlier connected-device run processed and played the cafe file but did not complete this checklist.

```powershell
adb devices
adb install -r "C:\Users\ragha\OneDrive\Desktop\gitdsp\DSP-based-classical-signal-processing-\signalchain-android\app\build\outputs\apk\release\app-release.apk"
adb logcat -s SignalChain
```

Inspect the analysis line and chosen mode in logcat. Expected results:

| Step | Expected result | Status |
|---|---|---|
| Launch | Upload screen opens without a crash | PENDING OWNER DEVICE TEST |
| Noisy clip | Full adaptive selected, no fallback warning | PENDING OWNER DEVICE TEST |
| High-SNR clip | Light touch selected | PENDING OWNER DEVICE TEST |
| Playback | Enhanced audio plays to the end | PENDING OWNER DEVICE TEST |
| Share/save | Recipient or saved WAV is playable | PENDING OWNER DEVICE TEST |
| Clear history | History and selected result clear | PENDING OWNER DEVICE TEST |
| Rotate during processing | Processing does not restart | PENDING OWNER DEVICE TEST |
| File larger than 50 MB | Rejected with friendly message | PENDING OWNER DEVICE TEST |
| Corrupt file | Rejected with friendly message | PENDING OWNER DEVICE TEST |
| MP3/M4A | Decodes and processes | PENDING OWNER DEVICE TEST |
