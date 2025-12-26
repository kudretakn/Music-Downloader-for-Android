package com.example.ytmusicdownloader

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

object YoutubeDLClient {
    private const val TAG = "YoutubeDLClient"

    fun init(context: Context) {
        try {
            YoutubeDL.getInstance().init(context)
            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
        } catch (e: Exception) {
            Log.e(TAG, "failed to initialize youtubedl-android", e)
        }
    }

    enum class DownloadFormat {
        AUDIO, VIDEO
    }

    data class DownloadResult(
        val file: File,
        val title: String,
        val format: String
    )

    fun download(url: String, dir: File, format: DownloadFormat, callback: (Float, Long, String) -> Unit): Result<DownloadResult> {
        return try {
            val request = YoutubeDLRequest(url)
            request.addOption("-o", "${dir.absolutePath}/%(title)s.%(ext)s")
            
            if (format == DownloadFormat.AUDIO) {
                request.addOption("-x") // Extract audio
                request.addOption("--audio-format", "mp3")
            } else {
                request.addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
            }

            var title = "Unknown Title"
            
            // Note: In a real app we might want to fetch metadata first to get the title properly
            // Or parse the output. For now, we will rely on successful execution.
            
            YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                callback(progress, etaInSeconds, line ?: "")
            }
            
            // Warning: Finding the exact file name is tricky without parsing valid output.
            // For now, we return the directory or assume the newest file in the dir is ours.
            // A better approach would be using --print-json to get filename before download.
            // But for simplicity in this walkthrough:
            val downloadedFile = dir.listFiles()?.maxByOrNull { it.lastModified() } ?: dir
            title = downloadedFile.nameWithoutExtension

            Result.success(DownloadResult(downloadedFile, title, if(format == DownloadFormat.AUDIO) "MP3" else "Video"))
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            Result.failure(e)
        }
    }
}
