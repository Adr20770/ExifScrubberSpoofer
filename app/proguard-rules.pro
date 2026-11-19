# Konservative, sichere R8 / ProGuard Keep-Regeln

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Alle Android Manifest Komponenten schützen (Aktivitäten, Services, Receiver)
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends androidx.fragment.app.Fragment

# Keep-Regeln für AndroidX ExifInterface
-keep class androidx.exifinterface.media.** { *; }

# Keep-Regeln für osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Data Classes (JSON-Serialisierung) schützen
-keep class com.ADR20770.exifscrubberspoofer.CustomPreset { *; }
-keep class com.ADR20770.exifscrubberspoofer.ScrubHistoryItem { *; }
