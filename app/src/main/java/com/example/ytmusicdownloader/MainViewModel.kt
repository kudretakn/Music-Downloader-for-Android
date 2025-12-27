package com.example.ytmusicdownloader

import android.app.Application
import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ytmusicdownloader.data.SearchResult
import com.example.ytmusicdownloader.data.VideoDetail
import com.example.ytmusicdownloader.util.StorageUtil
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UiState(
    val isInitializing: Boolean = true,
    val initError: String? = null,
    val isLoading: Boolean = false,
    val currentOperation: String? = null,
    val searchResults: List<SearchResult> = emptyList(),
    val downloadProgress: Float = 0f,
    val downloadStatus: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        initializeYoutubeDL()
    }

    private fun initializeYoutubeDL() {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Fast Init (Local Files)
            val result = YoutubeDLClient.init(getApplication())
            
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isInitializing = false,
                        initError = null
                    )
                    
                    // 2. Silent Update (Background Network)
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            YoutubeDLClient.update(getApplication())
                        } catch (e: Exception) {
                            // Ignore update errors (silent)
                        }
                    }
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown init error"
                    _uiState.value = _uiState.value.copy(
                        isInitializing = false,
                        initError = "INIT FAILED: $errorMsg"
                    )
                }
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return

        _uiState.value = _uiState.value.copy(isLoading = true, currentOperation = "Searching...", searchResults = emptyList())

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Determine if ID or Text
                // Simple heuristic: if it looks like a URL, use getVideoInfo, otherwise search
                val results = if (query.contains("youtube.com") || query.contains("youtu.be")) {
                    // It's a URL, wrap single result as list
                     val info = YoutubeDLClient.getVideoInfo(query) // Reusing existing helper if available or calling raw
                     if (info.isSuccess) {
                         val detail = info.getOrThrow()
                         listOf(SearchResult(
                             videoId = detail.id,
                             title = detail.title,
                             duration = detail.duration,
                             thumbnailUrl = detail.thumbnailUrl,
                             channel = detail.uploader
                         ))
                     } else {
                         emptyList()
                     }
                } else {
                     // Raw Search
                     val searchResult = YoutubeDLClient.search(query)
                     searchResult.getOrThrow()
                }

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        searchResults = results
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        currentOperation = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    fun downloadVideo(video: SearchResult, isAudio: Boolean) {
        _uiState.value = _uiState.value.copy(
            isLoading = true, 
            downloadProgress = 0f, 
            downloadStatus = "Starting download...",
            currentOperation = "Downloading ${video.title}"
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                val format = if (isAudio) "bestaudio/best" else "bestvideo[height<=1080]+bestaudio/best[height<=1080]"
                
                // Use cache dir for temp download
                val tempDir = context.cacheDir
                
                val request = YoutubeDLRequest(video.videoId)
                request.addOption("-f", format)
                request.addOption("-o", "${tempDir.absolutePath}/%(title)s.%(ext)s")
                
                // If audio, might want to extract audio
                if (isAudio) {
                    request.addOption("-x")
                    request.addOption("--audio-format", "mp3")
                }

                YoutubeDL.getInstance().execute(request) { progress, _, line ->
                     viewModelScope.launch(Dispatchers.Main) {
                         _uiState.value = _uiState.value.copy(
                             downloadProgress = progress / 100f,
                             downloadStatus = line
                         )
                     }
                }
                
                // File downloaded to tempDir. Find it.
                // This is tricky because we don't know the exact filename sanitized by YTDL.
                // We'll search the dir for the newest file matching expected extension.
                val ext = if(isAudio) "mp3" else "mp4" // Rough guess, YTDL might output mkv/webm
                val downloadedFile = findNewestFile(tempDir)
                
                if (downloadedFile != null) {
                    // Move to Public Storage
                    StorageUtil.saveFileToGallery(
                        context, 
                        downloadedFile, 
                        video.title, 
                        if (isAudio) "audio/mpeg" else "video/mp4" // Simplified Mime
                    )
                    
                    withContext(Dispatchers.Main) {
                         _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            downloadStatus = "Saved to Gallery",
                            currentOperation = null,
                            downloadProgress = 1f
                        )
                        Toast.makeText(context, "Download Complete", Toast.LENGTH_SHORT).show()
                    }
                } else {
                     throw Exception("Could not locate downloaded file")
                }

            } catch (e: Exception) {
                 withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        downloadStatus = "Failed: ${e.message}",
                        currentOperation = null
                    )
                }
            }
        }
    }
    
    private fun findNewestFile(dir: File): File? {
        // Simple heuristic: Get latest modified file in cache dir
        // In prod, better to use regex match on title
        return dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.firstOrNull()
    }
}
