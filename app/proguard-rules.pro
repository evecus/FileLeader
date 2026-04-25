# FileLeader ProGuard Rules

# ===== Keep app entry points =====
-keep class com.fileleader.** { *; }

# ===== Kotlin =====
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlin.**

# ===== Hilt / Dagger =====
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# ===== Room =====
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.**

# ===== libsu (Root) =====
-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**

# ===== Shizuku =====
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# ===== MPAndroidChart =====
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# ===== Glide =====
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { *; }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-dontwarn com.bumptech.glide.**

# ===== Lottie =====
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ===== AndroidX =====
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# ===== Serialization / Reflection =====
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-keepattributes Exceptions

# ===== Keep data models =====
-keep class com.fileleader.data.model.** { *; }

# ===== Remove logging in release =====
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
