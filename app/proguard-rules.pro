# ==============================================================================
# Kontre Production ProGuard / R8 Optimization Rules
# ==============================================================================

# Keep line numbers and original source file attributes for actionable crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Android Component Entry Points
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgent
-keep public class * extends android.app.admin.DeviceAdminReceiver
-keep class androidx.core.content.FileProvider { *; }

# WorkManager Workers & Reflection Instantiation
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class androidx.work.WorkerParameters { *; }

# Jetpack Compose Rules
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
-dontwarn androidx.compose.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-dontwarn kotlinx.coroutines.**

# App Domain Data & Rules
-keep class com.kontre.app.data.rules.** { *; }
-keepclassmembers class com.kontre.app.data.rules.** { *; }
-keep class com.kontre.app.data.QuotaManager { *; }

# Keep Services, Floating Overlay, and UI Receivers
-keep class com.kontre.app.service.** { *; }
-keep class com.kontre.app.receiver.** { *; }
-keep class com.kontre.app.ui.** { *; }
-keep class com.kontre.app.util.** { *; }

# Suppress harmless third-party library warnings
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.bouncycastle.**
-dontwarn sun.misc.**

