package com.example.lxvideodownloader.core.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Downloads a single video file (MP4, WEBM, etc.) directly via HTTP with progress reporting.
 * Unlike the M3U8 segment downloader, this streams a single file from start to finish.
 */
class DirectDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    /**
     * Downloads a file from [url] to [outputFile] with progress reporting.
     *
     * @param url The direct URL to the video file
     * @param outputFile The local file to write to
     * @param isCancelled Lambda checked periodically; if true, download aborts
     * @param onProgress Callback with (bytesDownloaded, totalBytes, speedBytesPerSec).
     *                   totalBytes may be -1 if Content-Length is unknown.
     */
    suspend fun download(
        url: String,
        outputFile: File,
        isCancelled: () -> Boolean,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, speedBytesPerSec: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("HTTP ${response.code} downloading video")
        }

        val body = response.body ?: run {
            response.close()
            throw IOException("Empty response body")
        }

        val totalBytes = body.contentLength() // -1 if unknown

        outputFile.parentFile?.mkdirs()

        val bytesDownloaded = AtomicLong(0)
        val lastTime = AtomicLong(System.currentTimeMillis())
        val bytesSinceLastCheck = AtomicLong(0)
        val currentSpeed = AtomicLong(0)

        try {
            body.byteStream().use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled()) {
                            throw CancellationException("Download cancelled by user")
                        }

                        outputStream.write(buffer, 0, bytesRead)

                        val downloaded = bytesDownloaded.addAndGet(bytesRead.toLong())
                        bytesSinceLastCheck.addAndGet(bytesRead.toLong())

                        // Update speed every second
                        val now = System.currentTimeMillis()
                        val elapsed = now - lastTime.get()
                        if (elapsed >= 1000) {
                            val b = bytesSinceLastCheck.getAndSet(0)
                            val speed = if (elapsed > 0) (b * 1000) / elapsed else 0
                            currentSpeed.set(speed)
                            lastTime.set(now)

                            onProgress(downloaded, totalBytes, speed)
                        }
                    }

                    outputStream.flush()

                    // Final progress report
                    onProgress(bytesDownloaded.get(), totalBytes, currentSpeed.get())
                }
            }
        } finally {
            response.close()
        }
    }
}
