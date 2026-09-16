package com.example.lxvideodownloader.core.m3u8

import android.content.Context
import com.example.lxvideodownloader.core.model.MediaSegment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class M3U8Downloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    suspend fun fetchPlaylist(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to fetch playlist: HTTP ${response.code}")
            }
            response.body?.string() ?: throw IOException("Empty playlist response")
        }
    }

    suspend fun downloadSegments(
        context: Context,
        taskId: String,
        segments: List<MediaSegment>,
        outputFile: File,
        concurrency: Int = 3,
        isCancelled: () -> Boolean,
        onProgress: (downloadedSegments: Int, totalSegments: Int, bytesDownloaded: Long, speedBytesPerSec: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "m3u8_tasks/$taskId").apply { mkdirs() }
        val keyCache = ConcurrentHashMap<String, ByteArray>()
        val totalSegments = segments.size

        val downloadedCount = AtomicInteger(0)
        val totalBytesDownloaded = AtomicLong(0)
        val lastTime = AtomicLong(System.currentTimeMillis())
        val bytesSinceLastCheck = AtomicLong(0)
        val currentSpeed = AtomicLong(0)

        val semaphore = Semaphore(concurrency)

        try {
            coroutineScope {
                segments.mapIndexed { index, segment ->
                    async {
                        semaphore.withPermit {
                            if (isCancelled()) {
                                throw CancellationException("Download cancelled by user")
                            }

                            val partFile = File(tempDir, "part_%05d.ts".format(index))
                            if (!partFile.exists() || partFile.length() == 0L) {
                                val segmentBytes = downloadSegmentData(segment, keyCache)
                                partFile.writeBytes(segmentBytes)
                                
                                val bytes = segmentBytes.size.toLong()
                                totalBytesDownloaded.addAndGet(bytes)
                                bytesSinceLastCheck.addAndGet(bytes)
                            }

                            val downloaded = downloadedCount.incrementAndGet()
                            
                            val now = System.currentTimeMillis()
                            val elapsed = now - lastTime.get()
                            if (elapsed >= 1000) {
                                val b = bytesSinceLastCheck.getAndSet(0)
                                val speed = if (elapsed > 0) (b * 1000) / elapsed else 0
                                currentSpeed.set(speed)
                                lastTime.set(now)
                            }

                            onProgress(downloaded, totalSegments, totalBytesDownloaded.get(), currentSpeed.get())
                        }
                    }
                }.awaitAll()
            }

            if (isCancelled()) {
                throw CancellationException("Download cancelled by user")
            }

            // Merge segments in numerical order into the final output file
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { outStream ->
                for (index in segments.indices) {
                    val partFile = File(tempDir, "part_%05d.ts".format(index))
                    if (partFile.exists()) {
                        FileInputStream(partFile).use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                }
            }

        } finally {
            // Clean up temporary segment files
            tempDir.deleteRecursively()
        }
    }

    private fun downloadSegmentData(
        segment: MediaSegment,
        keyCache: ConcurrentHashMap<String, ByteArray>
    ): ByteArray {
        val reqBuilder = Request.Builder()
            .url(segment.url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")

        segment.byteRange?.let { br ->
            val rangeHeader = if (br.offset != null) {
                "bytes=${br.offset}-${br.offset + br.length - 1}"
            } else {
                "bytes=0-${br.length - 1}"
            }
            reqBuilder.header("Range", rangeHeader)
        }

        val rawData = client.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} downloading segment ${segment.sequenceNumber}")
            }
            response.body?.bytes() ?: throw IOException("Empty segment response")
        }

        // Decrypt if encrypted with AES-128
        if (!segment.keyUrl.isNullOrBlank()) {
            val key = keyCache.getOrPut(segment.keyUrl) {
                fetchKey(segment.keyUrl)
            }
            val iv = segment.ivHex?.let { AesDecryptor.hexStringToByteArray(it) }
                ?: AesDecryptor.sequenceNumberToIv(segment.sequenceNumber)
            return AesDecryptor.decrypt(rawData, key, iv)
        }

        return rawData
    }

    private fun fetchKey(keyUrl: String): ByteArray {
        val request = Request.Builder()
            .url(keyUrl)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to fetch AES key: HTTP ${response.code}")
            }
            val bytes = response.body?.bytes() ?: throw IOException("Empty key response")
            if (bytes.size != 16) {
                throw IOException("Invalid AES key length: ${bytes.size} (expected 16)")
            }
            bytes
        }
    }
}
