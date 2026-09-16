package com.example.lxvideodownloader.core.m3u8

import com.example.lxvideodownloader.core.model.ByteRange
import com.example.lxvideodownloader.core.model.HlsPlaylist
import com.example.lxvideodownloader.core.model.MediaSegment
import com.example.lxvideodownloader.core.model.StreamVariant
import java.net.URI

object M3U8Parser {

    /**
     * Resolves a potentially relative URL against the baseUrl of the playlist.
     */
    fun resolveUrl(baseUrl: String, targetUrl: String): String {
        val trimmed = targetUrl.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        return try {
            val baseUri = URI(baseUrl)
            baseUri.resolve(trimmed).toString()
        } catch (_: Exception) {
            trimmed
        }
    }

    /**
     * Parses M3U8 content and returns either Master or Media playlist.
     */
    fun parse(content: String, playlistUrl: String): HlsPlaylist {
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        
        val isMaster = lines.any { it.startsWith("#EXT-X-STREAM-INF") }
        return if (isMaster) {
            parseMasterPlaylist(lines, playlistUrl)
        } else {
            parseMediaPlaylist(lines, playlistUrl)
        }
    }

    private fun parseMasterPlaylist(lines: List<String>, playlistUrl: String): HlsPlaylist.Master {
        val variants = mutableListOf<StreamVariant>()
        var currentBandwidth: Long = 0
        var currentResolution: String? = null
        var currentCodecs: String? = null

        for (i in lines.indices) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val attributes = parseAttributes(line.removePrefix("#EXT-X-STREAM-INF:"))
                currentBandwidth = attributes["BANDWIDTH"]?.toLongOrNull() ?: 0L
                currentResolution = attributes["RESOLUTION"]
                currentCodecs = attributes["CODECS"]
            } else if (!line.startsWith("#") && currentBandwidth > 0) {
                val fullUrl = resolveUrl(playlistUrl, line)
                val displayName = buildDisplayName(currentResolution, currentBandwidth)
                variants.add(
                    StreamVariant(
                        bandwidth = currentBandwidth,
                        resolution = currentResolution,
                        codecs = currentCodecs,
                        url = fullUrl,
                        displayName = displayName
                    )
                )
                currentBandwidth = 0
                currentResolution = null
                currentCodecs = null
            }
        }

        // Sort variants from highest quality to lowest
        variants.sortByDescending { it.bandwidth }
        return HlsPlaylist.Master(variants)
    }

    private fun parseMediaPlaylist(lines: List<String>, playlistUrl: String): HlsPlaylist.Media {
        val segments = mutableListOf<MediaSegment>()
        var targetDuration = 0.0
        var isVod = false
        var sequenceNumber: Long = 0

        var currentDuration = 0.0
        var currentKeyUrl: String? = null
        var currentIvHex: String? = null
        var currentByteRange: ByteRange? = null

        for (line in lines) {
            when {
                line.startsWith("#EXT-X-TARGETDURATION:") -> {
                    targetDuration = line.removePrefix("#EXT-X-TARGETDURATION:").trim().toDoubleOrNull() ?: 0.0
                }
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                    sequenceNumber = line.removePrefix("#EXT-X-MEDIA-SEQUENCE:").trim().toLongOrNull() ?: 0L
                }
                line.startsWith("#EXT-X-ENDLIST") -> {
                    isVod = true
                }
                line.startsWith("#EXT-X-KEY:") -> {
                    val attrs = parseAttributes(line.removePrefix("#EXT-X-KEY:"))
                    val method = attrs["METHOD"]
                    if (method == "AES-128") {
                        val uriVal = attrs["URI"]?.trim('\"')
                        currentKeyUrl = uriVal?.let { resolveUrl(playlistUrl, it) }
                        currentIvHex = attrs["IV"]
                    } else if (method == "NONE") {
                        currentKeyUrl = null
                        currentIvHex = null
                    }
                }
                line.startsWith("#EXT-X-BYTERANGE:") -> {
                    val raw = line.removePrefix("#EXT-X-BYTERANGE:").trim()
                    val parts = raw.split("@")
                    val length = parts[0].toLongOrNull() ?: 0L
                    val offset = parts.getOrNull(1)?.toLongOrNull()
                    currentByteRange = ByteRange(length, offset)
                }
                line.startsWith("#EXTINF:") -> {
                    val raw = line.removePrefix("#EXTINF:").substringBefore(",").trim()
                    currentDuration = raw.toDoubleOrNull() ?: 0.0
                }
                !line.startsWith("#") -> {
                    val fullSegmentUrl = resolveUrl(playlistUrl, line)
                    segments.add(
                        MediaSegment(
                            sequenceNumber = sequenceNumber++,
                            url = fullSegmentUrl,
                            duration = currentDuration,
                            keyUrl = currentKeyUrl,
                            ivHex = currentIvHex,
                            byteRange = currentByteRange
                        )
                    )
                    currentDuration = 0.0
                    currentByteRange = null
                }
            }
        }

        return HlsPlaylist.Media(
            targetDuration = targetDuration,
            segments = segments,
            isVod = isVod
        )
    }

    private fun parseAttributes(attributeString: String): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        val regex = Regex("([A-Z0-9-]+)=(\"([^\"]*)\"|([^,]*))")
        val matches = regex.findAll(attributeString)
        for (match in matches) {
            val key = match.groupValues[1]
            val value = match.groupValues[3].ifEmpty { match.groupValues[4] }
            attributes[key] = value
        }
        return attributes
    }

    private fun buildDisplayName(resolution: String?, bandwidth: Long): String {
        val mbps = String.format("%.1f Mbps", bandwidth / 1_000_000.0)
        return if (!resolution.isNullOrBlank()) {
            val height = resolution.substringAfter("x", resolution)
            "${height}p ($mbps)"
        } else {
            mbps
        }
    }
}
