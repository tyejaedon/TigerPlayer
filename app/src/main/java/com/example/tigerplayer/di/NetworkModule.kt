package com.example.tigerplayer.di

import com.example.tigerplayer.data.remote.api.*
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
import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Singleton

// --- Qualifiers: The Magical Signposts ---
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class SpotifyRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class SpotifyAuthRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class SubsonicRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class WikipediaRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ITunesRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class LrclibRetrofit

@Singleton
class SubsonicHostManager @Inject constructor() {
    var currentBaseUrl: String = "http://localhost/"
}

class DynamicUrlInterceptor(private val hostManager: SubsonicHostManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val dynamicUrl = hostManager.currentBaseUrl.toHttpUrlOrNull()

        val finalRequest = if (dynamicUrl != null && request.url.host == "localhost") {
            val newUrl = request.url.newBuilder()
                .scheme(dynamicUrl.scheme)
                .host(dynamicUrl.host)
                .port(dynamicUrl.port)
                .build()
            request.newBuilder().url(newUrl).build()
        } else request

        return chain.proceed(finalRequest)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val SPOTIFY_BASE_URL = "https://api.spotify.com/"
    private const val SPOTIFY_AUTH_URL = "https://accounts.spotify.com/"
    private const val WIKI_BASE_URL = "https://en.wikipedia.org/"
    private const val ITUNES_BASE_URL = "https://itunes.apple.com/"
    private const val LRCLIB_BASE_URL = "https://lrclib.net/"

    @Provides
    @Singleton
    fun provideBaseClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @SubsonicRetrofit
    fun provideSubsonicClient(base: OkHttpClient, host: SubsonicHostManager): OkHttpClient {
        return base.newBuilder()
            .addInterceptor(DynamicUrlInterceptor(host))
            // THE FORTIFICATION: Extend timeouts just for the self-hosted archive
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    // --- Retrofit Builders ---

    @Provides
    @Singleton
    @SubsonicRetrofit
    fun provideSubsonicRetrofit(@SubsonicRetrofit client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    @SpotifyRetrofit
    fun provideSpotifyRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(SPOTIFY_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    @SpotifyAuthRetrofit
    fun provideSpotifyAuthRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(SPOTIFY_AUTH_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    @LrclibRetrofit
    fun provideLrclibRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(LRCLIB_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    @WikipediaRetrofit
    fun provideWikipediaRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(WIKI_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    @ITunesRetrofit
    fun provideITunesRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(ITUNES_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    // --- API Service Implementations ---

    @Provides
    @Singleton
    fun provideSubsonicApi(@SubsonicRetrofit retrofit: Retrofit): NavidromeApiService =
        retrofit.create(NavidromeApiService::class.java)

    @Provides
    @Singleton
    fun provideSpotifyApi(@SpotifyRetrofit retrofit: Retrofit): SpotifyApiService =
        retrofit.create(SpotifyApiService::class.java)

    @Provides
    @Singleton
    fun provideSpotifyAuthApi(@SpotifyAuthRetrofit retrofit: Retrofit): SpotifyAuthApi =
        retrofit.create(SpotifyAuthApi::class.java)

    @Provides
    @Singleton
    fun provideLrclibApi(@LrclibRetrofit retrofit: Retrofit): LrclibApi =
        retrofit.create(LrclibApi::class.java)

    @Provides
    @Singleton
    fun provideWikipediaApi(@WikipediaRetrofit retrofit: Retrofit): WikipediaApi =
        retrofit.create(WikipediaApi::class.java)

    @Provides
    @Singleton
    fun provideITunesApi(@ITunesRetrofit retrofit: Retrofit): ITunesApi =
        retrofit.create(ITunesApi::class.java)

    // --- LAST.FM INJECTIONS ---

    @Provides
    @Singleton
    @Named("LastFmRetrofit") // Use Named so Hilt doesn't confuse it with Spotify's Retrofit
    fun provideLastFmRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://ws.audioscrobbler.com/")
            .addConverterFactory(GsonConverterFactory.create())
            // Consider adding an OkHttpClient with a custom User-Agent interceptor later!
            .build()
    }

    @Provides
    @Singleton
    fun provideLastFmApi(@Named("LastFmRetrofit") retrofit: Retrofit): LastFmApi {
        return retrofit.create(LastFmApi::class.java)
    }
}