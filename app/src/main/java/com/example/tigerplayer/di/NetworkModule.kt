package com.example.tigerplayer.di

import com.example.tigerplayer.data.remote.api.ITunesApi
import com.example.tigerplayer.data.remote.api.LrclibApi
import com.example.tigerplayer.data.remote.api.SpotifyApiService
import com.example.tigerplayer.data.remote.api.NavidromeApiService // <-- Updated name
import com.example.tigerplayer.data.remote.api.WikipediaApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

// --- Qualifiers ---
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SpotifyRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WikipediaRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ITunesRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LrclibRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SubsonicRetrofit

// ==========================================
// 1. THE DYNAMIC URL ENGINE
// ==========================================

/**
 * A Singleton that holds your server URL. Your UI/ViewModel will update this
 * when the user types in their server address.
 */
@Singleton
class SubsonicHostManager @Inject constructor() {
    var currentBaseUrl: String = "http://localhost/"
}

/**
 * The Interceptor that catches the request and injects the live URL.
 */
class DynamicUrlInterceptor(private val hostManager: SubsonicHostManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val dynamicUrl = hostManager.currentBaseUrl.toHttpUrlOrNull()

        // If a valid custom URL exists, hijack the request and swap the host/port
        if (dynamicUrl != null) {
            val newUrl = request.url.newBuilder()
                .scheme(dynamicUrl.scheme)
                .host(dynamicUrl.host)
                .port(dynamicUrl.port)
                .build()

            request = request.newBuilder().url(newUrl).build()
        }
        return chain.proceed(request)
    }
}


@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val SPOTIFY_BASE_URL = "https://api.spotify.com/"
    private const val WIKI_BASE_URL = "https://en.wikipedia.org/"
    private const val ITUNES_BASE_URL = "https://itunes.apple.com/"
    private const val LRCLIB_BASE_URL = "https://lrclib.net/"
    private const val SUBSONIC_BASE_URL = "http://localhost/"

    // ==========================================
    // HTTP CLIENTS
    // ==========================================

    /**
     * The Standard Client for Wikipedia, iTunes, etc.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * The Specialized Client JUST for Navidrome/Subsonic.
     * It uses the base client, but attaches our Dynamic Interceptor.
     */
    @Provides
    @Singleton
    @SubsonicRetrofit
    fun provideSubsonicOkHttpClient(
        baseClient: OkHttpClient,
        hostManager: SubsonicHostManager
    ): OkHttpClient {
        return baseClient.newBuilder()
            .addInterceptor(DynamicUrlInterceptor(hostManager))
            .build()
    }

    // ==========================================
    // RETROFIT BUILDERS
    // ==========================================

    @Provides
    @Singleton
    @SpotifyRetrofit
    fun provideSpotifyRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(SPOTIFY_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @WikipediaRetrofit
    fun provideWikipediaRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(WIKI_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @ITunesRetrofit
    fun provideITunesRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(ITUNES_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    @LrclibRetrofit
    fun provideLrclibRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(LRCLIB_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // --- Subsonic uses the SPECIALIZED OkHttpClient ---
    @Provides
    @Singleton
    @SubsonicRetrofit
    fun provideSubsonicRetrofit(
        @SubsonicRetrofit subsonicClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(SUBSONIC_BASE_URL) // Dummy URL, the Interceptor overwrites this
            .client(subsonicClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // ==========================================
    // API SERVICES
    // ==========================================

    @Provides
    @Singleton
    fun provideSpotifyApiService(@SpotifyRetrofit retrofit: Retrofit): SpotifyApiService {
        return retrofit.create(SpotifyApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideWikipediaApi(@WikipediaRetrofit retrofit: Retrofit): WikipediaApi {
        return retrofit.create(WikipediaApi::class.java)
    }

    @Provides
    @Singleton
    fun provideITunesApi(@ITunesRetrofit retrofit: Retrofit): ITunesApi {
        return retrofit.create(ITunesApi::class.java)
    }

    @Provides
    @Singleton
    fun provideLrclibApi(@LrclibRetrofit retrofit: Retrofit): LrclibApi {
        return retrofit.create(LrclibApi::class.java)
    }

    @Provides
    @Singleton
    fun provideSubsonicApi(@SubsonicRetrofit retrofit: Retrofit): NavidromeApiService {
        return retrofit.create(NavidromeApiService::class.java)
    }
}