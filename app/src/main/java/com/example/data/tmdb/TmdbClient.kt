package com.example.data.tmdb

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object TmdbClient {
    const val API_KEY = "1c94c7cf6636d243e6e3eafbbe690d4d"
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

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
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
}
