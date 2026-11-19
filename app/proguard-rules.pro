# Dipakai hanya kalau isMinifyEnabled = true.
## NewPipeExtractor
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn org.jspecify.annotations.**
-dontwarn com.google.errorprone.annotations.**
## Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
