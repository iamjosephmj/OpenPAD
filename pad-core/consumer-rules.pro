# Public API
-keep class com.openpad.core.OpenPad { *; }
-keep class com.openpad.core.OpenPadConfig { *; }
-keep class com.openpad.core.OpenPadResult { *; }
-keep interface com.openpad.core.OpenPadListener { *; }
-keep class com.openpad.core.OpenPadError { *; }
-keep class com.openpad.core.OpenPadError$* { *; }
-keep interface com.openpad.core.OpenPadSession { *; }
-keep class com.openpad.core.OpenPadThemeConfig { *; }
-keep enum com.openpad.core.aggregation.PadStatus { *; }
-keep enum com.openpad.core.challenge.ChallengePhase { *; }

# JNI
-keep class com.openpad.core.ndk.OpenPadNative { *; }
-keep class com.openpad.core.ndk.NativeFrameOutput { *; }

# Activity (manifest-referenced)
-keep class com.openpad.core.ui.PadActivity { *; }

# TFLite / LiteRT
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn org.tensorflow.lite.**
-dontwarn com.google.ai.edge.litert.**
