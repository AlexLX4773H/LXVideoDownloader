package com.example.lxvideodownloader.core.m3u8

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AesDecryptorTest {

    @Test
    fun hexStringToByteArray_convertsProperly() {
        val hex = "0x00112233445566778899aabbccddeeff"
        val bytes = AesDecryptor.hexStringToByteArray(hex)
        assertEquals(16, bytes.size)
        assertEquals(0x00.toByte(), bytes[0])
        assertEquals(0x11.toByte(), bytes[1])
        assertEquals(0xff.toByte(), bytes[15])
    }

    @Test
    fun sequenceNumberToIv_formatsBigEndian16Bytes() {
        val iv = AesDecryptor.sequenceNumberToIv(1L)
        assertEquals(16, iv.size)
        // High 15 bytes should be 0, lowest byte should be 1
        for (i in 0 until 15) {
            assertEquals(0.toByte(), iv[i])
        }
        assertEquals(1.toByte(), iv[15])
    }

    @Test
    fun decrypt_decryptsAes128CbcPkcs7() {
        val key = ByteArray(16) { it.toByte() }
        val iv = ByteArray(16) { (15 - it).toByte() }
        val plainText = "Hello HLS M3U8 Video Stream Decryption Test!".toByteArray(Charsets.UTF_8)

        // Encrypt with Java standard AES
        val cipher = try {
            Cipher.getInstance("AES/CBC/PKCS7Padding")
        } catch (_: Exception) {
            Cipher.getInstance("AES/CBC/PKCS5Padding")
        }
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val cipherText = cipher.doFinal(plainText)

        // Decrypt using AesDecryptor
        val decrypted = AesDecryptor.decrypt(cipherText, key, iv)

        assertArrayEquals(plainText, decrypted)
        assertEquals("Hello HLS M3U8 Video Stream Decryption Test!", String(decrypted, Charsets.UTF_8))
    }
}
