package com.example.lxvideodownloader.core.m3u8

import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AesDecryptor {

    fun hexStringToByteArray(hex: String): ByteArray {
        val clean = hex.removePrefix("0x").removePrefix("0X")
        val padded = clean.padStart(32, '0')
        val result = ByteArray(16)
        for (i in 0 until 16) {
            val index = i * 2
            result[i] = padded.substring(index, index + 2).toInt(16).toByte()
        }
        return result
    }

    fun sequenceNumberToIv(sequenceNumber: Long): ByteArray {
        val buffer = ByteBuffer.allocate(16)
        buffer.putLong(0, 0L)
        buffer.putLong(8, sequenceNumber)
        return buffer.array()
    }

    fun decrypt(encryptedData: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = try {
            Cipher.getInstance("AES/CBC/PKCS7Padding")
        } catch (_: Exception) {
            Cipher.getInstance("AES/CBC/PKCS5Padding")
        }
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(encryptedData)
    }
}
