# ============================
# TigerPlayer Release Hardening
# ============================

-keepattributes Signature,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,InnerClasses,EnclosingMethod

# --- Media3 / ExoPlayer ---
-keep class * extends androidx.media3.session.MediaSessionService { *; }
-keep class * implements androidx.media3.common.audio.AudioProcessor { *; }
-keep class com.example.tigerplayer.service.AudioPlayerService { *; }
-keep class com.example.tigerplayer.service.MediaControllerManager { *; }
-keep class com.example.tigerplayer.engine.AdaptiveDspEngine { *; }
-dontwarn androidx.media3.**

# --- OpenGL renderer entry points (reflection-safe) ---
-keep class com.example.tigerplayer.ui.player.TigerVortexRenderer { *; }

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

-keep class com.example.tigerplayer.data.remote.model.** { *; }
-keepclassmembers class com.example.tigerplayer.data.remote.model.** {
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
