package com.example.data.tmdb

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object TmdbClient {
    /**
     * Single source for the TMDB key. The secrets plugin generates
     * BuildConfig.TMDB_API_KEY from .env (local override) or .env.example
     * (shipped default); reflection keeps the compile working even when the
     * field is absent, e.g. plain unit-test builds. The embedded fallback
     * keeps fresh clones working — rotate via .env, not code.
     */
    private const val FALLBACK_API_KEY = "1c94c7cf6636d243e6e3eafbbe690d4d"
    val API_KEY: String = runCatching {
        (Class.forName("com.example.BuildConfig").getField("TMDB_API_KEY").get(null) as? String)
    }.getOrNull()?.trim().takeIf { !it.isNullOrEmpty() && !it.equals("UNSET", ignoreCase = true) }
        ?: FALLBACK_API_KEY
    const val BASE_URL = "https://api.themoviedb.org/3/"
    const val IMAGE_BASE_W780 = "https://image.tmdb.org/t/p/w780"
    const val IMAGE_BASE_W1280 = "https://image.tmdb.org/t/p/w1280"
    const val IMAGE_BASE_W500 = "https://image.tmdb.org/t/p/w500"
    const val IMAGE_BASE_W185 = "https://image.tmdb.org/t/p/w185"
    const val IMAGE_BASE_ORIGINAL = "https://image.tmdb.org/t/p/original"

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val originalHttpUrl = original.url

        val url = originalHttpUrl.newBuilder()
            .addQueryParameter("api_key", API_KEY)
            .build()

        val requestBuilder = original.newBuilder().url(url)
        val request = requestBuilder.build()
        chain.proceed(request)
    }

    /** Retries idempotent GETs on rate-limit / transient server errors with backoff. */
    private val retryInterceptor = Interceptor { chain ->
        val request = chain.request()
        var attempt = 0
        var result: Response? = null
        while (result == null) {
            try {
                val candidate = chain.proceed(request)
                if (candidate.code !in RETRY_STATUS_CODES || attempt >= MAX_RETRIES) {
                    result = candidate
                } else {
                    candidate.close()
                }
            } catch (e: IOException) {
                if (attempt >= MAX_RETRIES) throw e
            }
            if (result == null) {
                try {
                    Thread.sleep(1000L shl attempt)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("TMDB request interrupted during retry backoff")
                }
                attempt++
            }
        }
        result
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            // BASIC logs request URLs, which carry ?api_key=. Only in debug.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        })
        .addInterceptor(retryInterceptor)
        .retryOnConnectionFailure(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val apiService: TmdbApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TmdbApiService::class.java)
    }

    private val RETRY_STATUS_CODES = setOf(429, 500, 502, 503, 504)
    /** Retries after the initial attempt: sleeps 1s, then 2s. */
    private const val MAX_RETRIES = 2
}
