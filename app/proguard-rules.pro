# Aggressive Obfuskierung
-repackageclasses 'a.b'
-flattenpackagehierarchy
-allowaccessmodification
-overloadaggressively
-useuniqueclassmembernames

# Logs entfernen
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep-Regeln für AndroidX ExifInterface (WICHTIG!)
-keep class androidx.exifinterface.media.** { *; }

# Keep-Regeln für osmdroid (WICHTIG!)
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
