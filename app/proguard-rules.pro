# ProGuard & R8 Configuration for Markdown Editor
# --------------------------------------------------

# 1. General & Line Number Preservation (for crash traces)
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 2. Kotlin Coroutines & Reflection
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# 3. Room Persistence
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class * extends androidx.room.migration.Migration
-dontwarn androidx.room.**

# 4. Dagger & Hilt Dependency Injection
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent
-keep class * extends androidx.lifecycle.ViewModel
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep @dagger.hilt.EntryPoint class * { *; }
-dontwarn dagger.hilt.**

# 5. Jetpack Compose & Glance AppWidget
-keep class androidx.compose.** { *; }
-keep class androidx.glance.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-dontwarn androidx.compose.**
-dontwarn androidx.glance.**

# 6. Biometric Authentication
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# 7. Commonmark Java AST Engine
-keep class org.commonmark.** { *; }
-dontwarn org.commonmark.**

# 8. Myers java-diff-utils
-keep class io.github.java_diff_utils.** { *; }
-keep class com.github.difflib.** { *; }
-dontwarn com.github.difflib.**

# 9. PDFBox Android
-keep class com.tom_roush.pdfbox.** { *; }
-keepclassmembers class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**

# 10. JSoup HTML Parser
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

