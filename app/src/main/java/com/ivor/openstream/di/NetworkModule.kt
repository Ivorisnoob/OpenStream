package com.ivor.openstream.di

import android.content.Context
import com.ivor.openstream.BuildConfig
import com.ivor.openstream.data.cache.CacheBudget
import com.ivor.openstream.data.cache.ForceRefreshInterceptor
import com.ivor.openstream.data.cache.MetadataCacheHeadersInterceptor
import com.ivor.openstream.data.cache.StaleIfErrorInterceptor
import com.ivor.openstream.data.remote.TmdbApi
import com.ivor.openstream.data.remote.GithubApi
import com.ivor.openstream.data.settings.AppDns
import com.ivor.openstream.data.settings.AppSettingsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Named
import javax.inject.Singleton

import java.io.File
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(): Interceptor = Interceptor { chain ->
        val original = chain.request()
        val originalHttpUrl = original.url
        val url = originalHttpUrl.newBuilder()
            .addQueryParameter("api_key", BuildConfig.TMDB_API_KEY)
            .apply {
                // No adult titles anywhere in the app, for every profile.
                if (originalHttpUrl.host == "api.themoviedb.org" && originalHttpUrl.queryParameter("include_adult") == null) addQueryParameter("include_adult", "false")
            }
            .build()
        
        val request = original.newBuilder()
            .url(url)
            .build()
        chain.proceed(request)
    }

    /** TMDB answers on disk, sized by the Cache size setting (read once, at start). */
    @Provides
    @Singleton
    fun provideMetadataCache(@ApplicationContext context: Context, settings: AppSettingsStore): Cache {
        val directory = File(context.cacheDir, CacheBudget.METADATA_DIR)
        return Cache(directory, CacheBudget.bytesFor(directory, settings.current.cacheSize.metadataBytes))
    }

    /**
     * The TMDB client. The auth interceptor runs first so cached answers are keyed by the final
     * URL; [StaleIfErrorInterceptor] then falls back to the cache when TMDB can't answer, and
     * [MetadataCacheHeadersInterceptor] decides how long each answer stays fresh.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: Interceptor, dns: AppDns, cache: Cache): OkHttpClient {
        return OkHttpClient.Builder()
            .dns(dns)
            .cache(cache)
            .addInterceptor(authInterceptor)
            .addInterceptor(StaleIfErrorInterceptor())
            .addInterceptor(createLoggingInterceptor(detailed = BuildConfig.DEBUG))
            .addNetworkInterceptor(MetadataCacheHeadersInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("StreamingClient")
    fun provideStreamingOkHttpClient(dns: AppDns): OkHttpClient {
        return OkHttpClient.Builder()
            .dns(dns)
            .addInterceptor(createLoggingInterceptor(detailed = false))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("Tmdb")
    fun provideTmdbRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideTmdbApi(@Named("Tmdb") retrofit: Retrofit): TmdbApi {
        return retrofit.create(TmdbApi::class.java)
    }

    /**
     * Same client and cache, but every answer is checked with TMDB first (pull to refresh). Still
     * falls back to the cache when offline, and what it fetches refreshes the cache for everyone.
     */
    @Provides
    @Singleton
    @Named(FRESH_TMDB)
    fun provideFreshTmdbApi(@Named("Tmdb") retrofit: Retrofit, okHttpClient: OkHttpClient): TmdbApi {
        val client = okHttpClient.newBuilder().addInterceptor(ForceRefreshInterceptor()).build()
        return retrofit.newBuilder().client(client).build().create(TmdbApi::class.java)
    }

    const val FRESH_TMDB = "TmdbFresh"

    @Provides
    @Singleton
    fun provideGithubApi(json: Json, dns: AppDns): GithubApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(
                OkHttpClient.Builder()
                    .dns(dns)
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header("Accept", "application/vnd.github+json")
                                .build()
                        )
                    }
                    .build()
            )
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(GithubApi::class.java)
    }
}
