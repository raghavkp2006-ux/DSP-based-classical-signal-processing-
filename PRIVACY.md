# Privacy

SignalChain processes selected or recorded audio on the device. The release manifest has no INTERNET permission and the app does not upload audio or include analytics or crash-reporting services.

The complete APK permission list is `android.permission.RECORD_AUDIO` and `com.signalchain.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Microphone permission is requested for the Record feature; the latter is an app-specific signature-protected permission in the merged manifest for non-exported dynamic receivers.

Selected input may be copied and decoded into app-private cache files. Enhanced WAVs are stored in the app-private `cache/exports/` directory and may be shared with another app by the user through an Android FileProvider; what that recipient does with a shared file depends on the recipient. Copies saved by a recipient are outside SignalChain's cleanup control.

On startup, the app deletes its managed temporary files in its cache. Clearing session history removes managed enhanced files and input/canonical/microphone bridge files; replacing an unreferenced selection deletes its managed cache file when possible. Android may also clear app cache independently. Cleanup is best effort and does not delete external copies.

The app uses AndroidX and ONNX Runtime libraries; ONNX Runtime executes the bundled model locally. The app logs signal-analysis values and selected mode to Android logcat, along with operational errors; it does not log raw audio. No Firebase or remote crash reporter is included.
