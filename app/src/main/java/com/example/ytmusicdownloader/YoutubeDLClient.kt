package com.example.ytmusicdownloader

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

object YoutubeDLClient {
    private const val TAG = "YoutubeDLClient"

    fun init(context: Context) {
        try {
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
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

    fun search(query: String): Result<List<com.example.ytmusicdownloader.data.SearchResult>> {
        return try {
            val request = YoutubeDLRequest("ytsearch10:$query")
            request.addOption("--dump-json")
            request.addOption("--flat-playlist") // Get only metadata, faster
            
            val response = YoutubeDL.getInstance().execute(request)
            val results = mutableListOf<com.example.ytmusicdownloader.data.SearchResult>()
            
            // Output contains one JSON object per line for each video in the search result
            response.out.lines().forEach { line ->
                if (line.isNotBlank()) {
                    try {
                        val json = org.json.JSONObject(line)
                        results.add(
                            com.example.ytmusicdownloader.data.SearchResult(
                                videoId = json.optString("id"),
                                title = json.optString("title"),
                                thumbnailUrl = "https://i.ytimg.com/vi/${json.optString("id")}/mqdefault.jpg", // Fallback thumb
                                duration = formatDuration(json.optInt("duration", 0)),
                                channel = json.optString("uploader")
                            )
                        )
                    } catch (e: Exception) {
                        // Ignore malformed lines
                    }
                }
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getVideoInfo(url: String): Result<com.example.ytmusicdownloader.data.VideoDetail> {
        return try {
            val request = YoutubeDLRequest(url)
            request.addOption("--dump-json")
            
            val response = YoutubeDL.getInstance().execute(request)
            val json = org.json.JSONObject(response.out)
            
            val formats = mutableListOf<com.example.ytmusicdownloader.data.VideoFormat>()
            val jsonFormats = json.optJSONArray("formats")
            
            if (jsonFormats != null) {
                for (i in 0 until jsonFormats.length()) {
                    val f = jsonFormats.getJSONObject(i)
                    // Filter mainly for mp4/m4a to keep it simple, or typical useful formats
                    val ext = f.optString("ext")
                    if (ext == "mp4" || ext == "m4a" || ext == "webm") {
                         formats.add(
                            com.example.ytmusicdownloader.data.VideoFormat(
                                formatId = f.optString("format_id"),
                                ext = ext,
                                resolution = f.optString("resolution", "audio only"),
                                fileSize = f.optLong("filesize", 0),
                                acodec = f.optString("acodec"),
                                vcodec = f.optString("vcodec"),
                                note = f.optString("format_note")
                            )
                        )
                    }
                }
            }
            
            // Dedup/Sort formats if needed, for now just returning all relevant
            
            val detail = com.example.ytmusicdownloader.data.VideoDetail(
                id = json.optString("id"),
                title = json.optString("title"),
                thumbnailUrl = json.optString("thumbnail"),
                formats = formats
            )
            
            Result.success(detail)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatDuration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }

    const val FORMAT_MP3 = "MP3"
    const val FORMAT_MP4_1080 = "MP4_1080"

    fun download(url: String, dir: File, formatParam: String, callback: (Float, Long, String) -> Unit): Result<DownloadResult> {
        return try {
            val request = YoutubeDLRequest(url)
            request.addOption("-o", "${dir.absolutePath}/%(title)s.%(ext)s")
            
            if (formatParam == FORMAT_MP3) {
                request.addOption("-x") // Extract audio
                request.addOption("--audio-format", "mp3")
                request.addOption("--audio-quality", "0") // Best quality
            } else if (formatParam == FORMAT_MP4_1080) {
                 // Best video <= 1080p + best audio, merge them.
                 // Fallback to best single file <= 1080p.
                 request.addOption("-f", "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[height<=1080][ext=mp4]/best[height<=1080]")
            } else {
                // Specific format ID (fallback provided)
                request.addOption("-f", formatParam)
            }
            
            YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                callback(progress, etaInSeconds, line ?: "")
            }
            
            // Best guess for the file
            val downloadedFile = dir.listFiles()?.maxByOrNull { it.lastModified() } ?: dir
            Result.success(DownloadResult(downloadedFile, downloadedFile.nameWithoutExtension, formatParam))
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            Result.failure(e)
        }
    }
}
