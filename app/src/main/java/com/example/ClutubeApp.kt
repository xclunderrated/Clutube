package com.example

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import com.example.notification.ReleaseNotificationPublisher

class ClutubeApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        ReleaseNotificationPublisher.ensureChannel(this)
        // Initialize the download manager early so interrupted downloads auto-resume
        // on cold start without requiring the user to open the app UI.
        com.example.data.download.DownloadManager.getInstance(this)
        // Prewarm the shared player WebView + DNS so the first play tap has
        // no init jank. Runs on a background thread; WebView creation itself
        // posts to main inside PlayerViewManager.prewarm().
        Thread({
            try {
                android.webkit.WebView.getCurrentWebViewPackage()
            } catch (_: Exception) {}
            try {
                // DNS prefetch for both embed providers.
                java.net.InetAddress.getByName("vidsrc2.ru")
            } catch (_: Exception) {}
            try {
                java.net.InetAddress.getByName("vidlink.pro")
            } catch (_: Exception) {}
            try {
                com.example.util.PlayerViewManager.prewarm(this)
            } catch (_: Exception) {}
        }, "player-prewarm").apply { isDaemon = true }.start()
    }

    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .dispatcher(Dispatcher().apply {
                maxRequests = 32
                maxRequestsPerHost = 12
            })
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    // A new namespace guarantees that an APK update cannot
                    // reuse stale artwork bytes from the previous release.
                    .directory(File(cacheDir, "image_cache_v${BuildConfig.VERSION_CODE}"))
                    .maxSizeBytes(150L * 1024 * 1024) // 150 MB disk cache
                    .build()
            }
            .respectCacheHeaders(false) // Cache TMDb and video thumbnails reliably
            .crossfade(false) // Disable crossfade animation to prevent frame drops during scrolling
            .allowHardware(true)
            .allowRgb565(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}
