package com.example.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest
import org.schabi.newpipe.extractor.downloader.Response as ExtractorResponse
import java.util.concurrent.TimeUnit

/**
 * Shared NewPipe Extractor bootstrap. Previously each extractor owned its own
 * OkHttp downloader + `NewPipe.init` guard, which risked double-init with
 * different downloaders as more YouTube surfaces (channel art, trailer view
 * counts) came online. All YouTube scraping goes through here now.
 */
internal object NewPipeClient {

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var isInitialized = false

    fun ensureInitialized() {
        if (isInitialized) return
        synchronized(this) {
            if (!isInitialized) {
                NewPipe.init(OkHttpDownloader(httpClient))
                isInitialized = true
            }
        }
    }

    suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    internal class OkHttpDownloader(
        private val client: OkHttpClient
    ) : Downloader() {
        override fun execute(request: ExtractorRequest): ExtractorResponse {
            val requestHeaders = request.headers()
            val builder = OkHttpRequest.Builder()
                .url(request.url())
                .apply {
                    if (requestHeaders.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                        header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
                        )
                    }
                    if (requestHeaders.keys.none { it.equals("Accept-Language", ignoreCase = true) }) {
                        header("Accept-Language", "en-US,en;q=0.9")
                    }
                }
            requestHeaders.forEach { (name, values) ->
                values.forEach { value -> builder.addHeader(name, value) }
            }

            val body = request.dataToSend()?.let { bytes ->
                okhttp3.RequestBody.create(null, bytes)
            }
            val httpRequest = when (request.httpMethod().uppercase()) {
                "POST" -> builder.post(body ?: okhttp3.RequestBody.create(null, ByteArray(0))).build()
                "HEAD" -> builder.head().build()
                else -> builder.get().build()
            }

            return client.newCall(httpRequest).execute().use { response ->
                ExtractorResponse(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    response.body?.string().orEmpty(),
                    response.request.url.toString()
                )
            }
        }
    }
}
