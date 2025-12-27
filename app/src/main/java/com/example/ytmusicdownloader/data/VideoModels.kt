package com.example.ytmusicdownloader.data

data class SearchResult(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String?,
    val duration: String,
    val channel: String
)

data class VideoDetail(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val duration: String,
    val uploader: String,
    val formats: List<VideoFormat>
)

data class VideoFormat(
    val formatId: String,
    val ext: String,
    val resolution: String?,
    val fileSize: Long?,
    val acodec: String?,
    val vcodec: String?,
    val note: String?
) {
    val description: String
        get() {
            return if (vcodec == "none") {
                "Audio Only (${ext})"
            } else {
                "${resolution ?: "Unknown"} (${ext})"
            }
        }
}
