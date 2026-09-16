package com.example.lxvideodownloader.core.sniffer

import java.io.Serializable

/**
 * Source type classification for detected video resources.
 */
enum class SourceType {
    M3U8,
    MP4,
    WEBM,
    TS,
    FLV,
    MKV,
    OTHER
}

/**
 * Represents a video resource detected from a web page via network interception or DOM scanning.
 */
data class DetectedVideo(
    val url: String,
    val mimeType: String? = null,
    val sourceType: SourceType = SourceType.OTHER,
    val pageTitle: String = "",
    val pageUrl: String = "",
    val fileSize: Long? = null
) : Serializable

/**
 * Utility for identifying video URLs and MIME types from intercepted network requests.
 */
object VideoUrlMatcher {

    private val VIDEO_EXTENSIONS = listOf(
        ".m3u8", ".mp4", ".webm", ".mkv", ".avi", ".ts", ".flv",
        ".mov", ".wmv", ".3gp", ".mpeg", ".mpg"
    )

    private val VIDEO_MIME_TYPES = setOf(
        "video/mp4", "video/webm", "video/x-matroska", "video/avi",
        "video/x-msvideo", "video/mp2t", "video/x-flv", "video/quicktime",
        "video/x-ms-wmv", "video/3gpp", "video/mpeg",
        "application/x-mpegurl", "application/vnd.apple.mpegurl",
        "audio/x-mpegurl", "audio/mpegurl"
    )

    /**
     * Checks if a URL points to a video resource based on file extension.
     * Strips query parameters and fragments before checking.
     */
    fun isVideoUrl(url: String): Boolean {
        val cleanUrl = url.substringBefore("?").substringBefore("#").lowercase()
        return VIDEO_EXTENSIONS.any { ext -> cleanUrl.endsWith(ext) }
    }

    /**
     * Checks if a MIME type corresponds to a video content type.
     */
    fun isVideoMimeType(mimeType: String?): Boolean {
        if (mimeType.isNullOrBlank()) return false
        val lower = mimeType.lowercase().trim()
        if (lower.startsWith("video/")) return true
        return VIDEO_MIME_TYPES.contains(lower)
    }

    /**
     * Returns true if the URL or MIME type indicates a video resource.
     */
    fun isVideo(url: String, mimeType: String? = null): Boolean {
        return isVideoUrl(url) || isVideoMimeType(mimeType)
    }

    /**
     * Classifies the source type of a detected video based on URL and MIME type.
     */
    fun classifySource(url: String, mimeType: String? = null): SourceType {
        val cleanUrl = url.substringBefore("?").substringBefore("#").lowercase()
        val lower = mimeType?.lowercase()?.trim()

        // Check M3U8 first (HLS)
        if (cleanUrl.endsWith(".m3u8") ||
            lower == "application/x-mpegurl" ||
            lower == "application/vnd.apple.mpegurl" ||
            lower == "audio/x-mpegurl" ||
            lower == "audio/mpegurl") {
            return SourceType.M3U8
        }

        // Check by extension
        return when {
            cleanUrl.endsWith(".mp4") -> SourceType.MP4
            cleanUrl.endsWith(".webm") -> SourceType.WEBM
            cleanUrl.endsWith(".ts") -> SourceType.TS
            cleanUrl.endsWith(".flv") -> SourceType.FLV
            cleanUrl.endsWith(".mkv") -> SourceType.MKV
            // Fallback to MIME type
            lower?.contains("mp4") == true -> SourceType.MP4
            lower?.contains("webm") == true -> SourceType.WEBM
            lower?.contains("mp2t") == true -> SourceType.TS
            lower?.contains("flv") == true -> SourceType.FLV
            lower?.contains("matroska") == true -> SourceType.MKV
            else -> SourceType.OTHER
        }
    }

    /**
     * Extracts a reasonable display name from a video URL.
     */
    fun extractFileName(url: String): String {
        return try {
            val path = url.substringBefore("?").substringBefore("#")
            val name = path.substringAfterLast("/")
            if (name.isNotBlank() && name.length < 100) name else "video"
        } catch (_: Exception) {
            "video"
        }
    }
}
