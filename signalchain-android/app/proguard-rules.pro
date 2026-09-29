# onnxruntime-android 1.18.0 does not package consumer ProGuard rules. Its Java
# API calls native code through JNI, so retain the package and all members.
-keep class ai.onnxruntime.** { *; }
