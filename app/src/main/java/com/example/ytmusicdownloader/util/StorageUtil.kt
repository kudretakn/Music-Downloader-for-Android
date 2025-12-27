package com.example.ytmusicdownloader.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

/**
 * Universal Storage Handler for Android 7.0 - 15+
 * Handles the complexity of Scoped Storage vs Legacy Storage seamlessy.
 */
object StorageUtil {

    fun saveFileToGallery(context: Context, sourceFile: File, title: String, mimeType: String): Uri? {
        val fileName = sourceFile.name
        
        // Prepare ContentValues
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, title)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                
                // Determine relative path based on file type
                val relativePath = if (mimeType.startsWith("audio/")) {
                    Environment.DIRECTORY_MUSIC
                } else {
                    Environment.DIRECTORY_MOVIES
                }
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativePath/YTMusicDownloader")
            }
        }

        // Get correct Collection URI
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (mimeType.startsWith("audio/")) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
        } else {
             if (mimeType.startsWith("audio/")) {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        }

        val itemUri = context.contentResolver.insert(collection, values)

        return itemUri?.also { uri ->
            try {
                // Copy stream
                context.contentResolver.openOutputStream(uri)?.use { outStream ->
                    FileInputStream(sourceFile).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                
                // Finish pending state
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
                
                // Cleanup temporary source file if it was in a private cache dir
                if (sourceFile.exists() && sourceFile.absolutePath.contains(context.cacheDir.absolutePath)) {
                    sourceFile.delete()
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                // If failed, try to cleanup the empty entry
                context.contentResolver.delete(uri, null, null)
                return null
            }
        }
    }

    /**
     * Determines MimeType from file extension or fallback.
     */
    fun getMimeType(file: File): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(file.absolutePath) ?: ""
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) 
            ?: if (file.name.endsWith("mp3")) "audio/mpeg" else "video/mp4"
    }
}
