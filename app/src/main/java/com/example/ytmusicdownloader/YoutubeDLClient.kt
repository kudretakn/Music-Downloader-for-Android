package com.example.ytmusicdownloader

import android.content.Context
import android.os.Environment
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

    fun downloadAudio(url: String, dir: File): Result<File> {
        return try {
            val request = YoutubeDLRequest(url)
            request.addOption("-x") // Extract audio
            request.addOption("--audio-format", "mp3")
            request.addOption("-o", "${dir.absolutePath}/%(title)s.%(ext)s")
            
            val response = YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                // Callback connection could be added here for progress updates via Flow/LiveData
                println("$progress% (ETA $etaInSeconds) $line") 
            }
            
            // Assume download is successful if no exception
            // We can try to find the specific file, but for now return the directory or find the newest file
            Result.success(dir)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            Result.failure(e)
        }
    }
}
