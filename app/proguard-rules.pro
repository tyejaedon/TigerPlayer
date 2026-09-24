# ============================
# TigerPlayer Release Hardening
# ============================

-keepattributes Signature,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,InnerClasses,EnclosingMethod

# --- Media3 / ExoPlayer ---
-keep class * extends androidx.media3.session.MediaSessionService { *; }
-keep class * implements androidx.media3.common.audio.AudioProcessor { *; }
-keep class com.tigerplayer.service.AudioPlayerService { *; }
-keep class com.tigerplayer.service.MediaControllerManager { *; }
-keep class com.tigerplayer.engine.AdaptiveDspEngine { *; }
-dontwarn androidx.media3.**

# --- OpenGL renderer entry points (reflection-safe) ---
-keep class com.tigerplayer.ui.player.TigerVortexRenderer { *; }

# --- Coil ---
-keep class coil.request.ImageRequest$Builder { *; }
-keep class coil.fetch.** { *; }
-keep class coil.decode.** { *; }
-dontwarn coil.**

# --- Retrofit / OkHttp / Gson ---
-keep,allowobfuscation interface * {
	@retrofit2.http.* <methods>;
}
-keepclassmembers,allowshrinking,allowobfuscation interface * {
	@retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**

-keep class com.tigerplayer.data.remote.model.** { *; }
-keepclassmembers class com.tigerplayer.data.remote.model.** {
	<fields>;
}

# --- Backup JSON models ---
# BackupManager relies on Gson reflection over nested generic lists. Keep the model classes and
# fields so release minification does not strip the signatures/structure needed to deserialize
# PlaylistBackup / HistoryBackup rows as their real types instead of LinkedTreeMap.
-keep class com.tigerplayer.data.backup.** { *; }
-keepclassmembers class com.tigerplayer.data.backup.** {
	<fields>;
}

-keepclassmembers,allowobfuscation class * {
	@com.google.gson.annotations.SerializedName <fields>;
}

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

-keepclassmembers class * {
	@androidx.room.PrimaryKey <fields>;
	@androidx.room.ColumnInfo <fields>;
	@androidx.room.Relation <fields>;
	@androidx.room.Embedded <fields>;
}

# Keep generated schema helpers and DAO impls.
-keep class *_Impl { *; }
-keep class *Dao_Impl { *; }

# --- Coroutines / Kotlin metadata interop ---
-dontwarn kotlinx.coroutines.**

# --- Spotify App Remote optional transitive classes ---
-dontwarn com.fasterxml.jackson.databind.**
-dontwarn com.spotify.base.annotations.**
-dontwarn com.spotify.protocol.mappers.jackson.**

# --- JTransforms / JLargeArrays optional JDK internals ---
# JLargeArrays may reference sun.misc.Cleaner on non-Android runtimes.
-dontwarn sun.misc.Cleaner
-dontwarn pl.edu.icm.jlargearrays.**

