package com.example.lxvideodownloader.core.m3u8

import com.example.lxvideodownloader.core.model.HlsPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3U8ParserTest {

    @Test
    fun parseMasterPlaylist_extractsVariantsCorrectly() {
        val masterContent = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.4d401e,mp4a.40.2"
            360p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,CODECS="avc1.4d401f,mp4a.40.2"
            720p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
            1080p.m3u8
        """.trimIndent()

        val playlist = M3U8Parser.parse(masterContent, "https://example.com/hls/master.m3u8")

        assertTrue(playlist is HlsPlaylist.Master)
        val master = playlist as HlsPlaylist.Master
        assertEquals(3, master.variants.size)

        // Should be sorted by bandwidth descending
        assertEquals(5000000L, master.variants[0].bandwidth)
        assertEquals("1920x1080", master.variants[0].resolution)
        assertEquals("https://example.com/hls/1080p.m3u8", master.variants[0].url)
        assertTrue(master.variants[0].displayName.contains("1080p"))

        assertEquals(2500000L, master.variants[1].bandwidth)
        assertEquals("https://example.com/hls/720p.m3u8", master.variants[1].url)

        assertEquals(800000L, master.variants[2].bandwidth)
        assertEquals("https://example.com/hls/360p.m3u8", master.variants[2].url)
    }

    @Test
    fun parseMediaPlaylist_extractsSegmentsAndEncryption() {
        val mediaContent = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:100
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x0123456789abcdef0123456789abcdef
            #EXTINF:9.009,
            segment_100.ts
            #EXTINF:9.009,
            segment_101.ts
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:8.5,
            segment_102.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val playlist = M3U8Parser.parse(mediaContent, "https://cdn.example.com/video/index.m3u8")

        assertTrue(playlist is HlsPlaylist.Media)
        val media = playlist as HlsPlaylist.Media
        assertEquals(10.0, media.targetDuration, 0.01)
        assertTrue(media.isVod)
        assertEquals(3, media.segments.size)

        val seg0 = media.segments[0]
        assertEquals(100L, seg0.sequenceNumber)
        assertEquals("https://cdn.example.com/video/segment_100.ts", seg0.url)
        assertEquals(9.009, seg0.duration, 0.001)
        assertEquals("https://cdn.example.com/video/key.bin", seg0.keyUrl)
        assertNotNull(seg0.ivHex)

        val seg1 = media.segments[1]
        assertEquals(101L, seg1.sequenceNumber)
        assertEquals("https://cdn.example.com/video/key.bin", seg1.keyUrl)

        val seg2 = media.segments[2]
        assertEquals(102L, seg2.sequenceNumber)
        assertEquals(null, seg2.keyUrl)
    }

    @Test
    fun resolveUrl_handlesRelativeAndAbsoluteUrls() {
        val base = "https://example.com/videos/stream/index.m3u8"
        assertEquals("https://example.com/videos/stream/chunk1.ts", M3U8Parser.resolveUrl(base, "chunk1.ts"))
        assertEquals("https://example.com/videos/chunk2.ts", M3U8Parser.resolveUrl(base, "../chunk2.ts"))
        assertEquals("https://example.com/chunk3.ts", M3U8Parser.resolveUrl(base, "/chunk3.ts"))
        assertEquals("https://other.com/chunk4.ts", M3U8Parser.resolveUrl(base, "https://other.com/chunk4.ts"))
    }
}
