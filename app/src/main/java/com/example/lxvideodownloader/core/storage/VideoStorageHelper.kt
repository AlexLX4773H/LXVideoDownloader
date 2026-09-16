package com.example.lxvideodownloader.core.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class CompletedVideo(
    val file: File,
    val title: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val formattedSize: String
)

object VideoStorageHelper {

    private val VIDEO_EXTENSIONS = listOf(
        ".mp4", ".ts", ".webm", ".mkv", ".avi", ".mov", ".flv", ".3gp", ".mpeg", ".mpg", ".wmv"
    )

    fun getVideosDirectory(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) 
            ?: File(context.filesDir, "movies")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun generateOutputFile(context: Context, rawTitle: String, extension: String = "mp4"): File {
        val safeName = rawTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { "video_${System.currentTimeMillis()}" }
        val ext = extension.removePrefix(".")
        val dir = getVideosDirectory(context)
        var file = File(dir, "$safeName.$ext")
        var counter = 1
        while (file.exists()) {
            file = File(dir, "${safeName}_$counter.$ext")
            counter++
        }
        return file
    }

    fun listCompletedVideos(context: Context): List<CompletedVideo> {
        val dir = getVideosDirectory(context)
        val files = dir.listFiles { f ->
            f.isFile && VIDEO_EXTENSIONS.any { ext -> f.name.endsWith(ext, ignoreCase = true) }
        } ?: emptyArray()

        return files.sortedByDescending { it.lastModified() }.map { file ->
            val size = file.length()
            val formatted = when {
                size >= 1024 * 1024 * 1024 -> String.format("%.2f GB", size / (1024.0 * 1024 * 1024))
                size >= 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024))
                size >= 1024 -> String.format("%.2f KB", size / 1024.0)
                else -> "$size B"
            }
            CompletedVideo(
                file = file,
                title = file.nameWithoutExtension,
                sizeBytes = size,
                lastModified = file.lastModified(),
                formattedSize = formatted
            )
        }
    }

    fun deleteVideo(file: File): Boolean {
        return if (file.exists()) file.delete() else false
    }

    fun exportToGallery(context: Context, videoFile: File): Uri? {
        val contentResolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/LXVideoDownloader")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val uri = contentResolver.insert(collection, contentValues) ?: return null

        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                FileInputStream(videoFile).use { input ->
                    input.copyTo(out)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }
            return uri
        } catch (_: Exception) {
            contentResolver.delete(uri, null, null)
            return null
        }
    }
}
