package com.example.ytmusicdownloader

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ytmusicdownloader.data.AppDatabase
import com.example.ytmusicdownloader.data.DownloadItem
import com.example.ytmusicdownloader.data.SearchResult
import com.example.ytmusicdownloader.data.VideoDetail
import com.example.ytmusicdownloader.data.VideoFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- THEME COLORS ---
val DarkBlue = Color(0xFF101426)
val DeepPurple = Color(0xFF241538)
val NeonPink = Color(0xFFF85D7F)
val NeonCyan = Color(0xFF4DEEEA)
val CardDark = Color(0xFF1E243D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        YoutubeDLClient.init(applicationContext)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = DarkBlue,
                    surface = CardDark,
                    primary = NeonCyan,
                    secondary = NeonPink,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Download", "History")

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = DeepPurple,
                contentColor = NeonCyan
            ) {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                if (index == 0) Icons.Rounded.Download else Icons.Rounded.History,
                                contentDescription = title
                            )
                        },
                        label = { Text(title) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonPink,
                            selectedTextColor = NeonPink,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = DeepPurple
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DeepPurple, DarkBlue)
                    )
                )
        ) {
            AnimatedContent(targetState = selectedTab, label = "TabAnimation") { tabIndex ->
                if (tabIndex == 0) DownloadScreen() else HistoryScreen()
            }
        }
    }
}

@Composable
fun DownloadScreen() {
    var query by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) } // General loading state
    var progress by remember { mutableStateOf(0f) }
    var statusMessage by remember { mutableStateOf("Ready to search or download") }
    
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var selectedVideoDetail by remember { mutableStateOf<VideoDetail?>(null) }
    var showFormatDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) Toast.makeText(context, "Permission Granted", Toast.LENGTH_SHORT).show()
    }

    fun startDownload(video: VideoDetail, formatId: String) {
        if (android.os.Build.VERSION.SDK_INT < 29 && 
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        
        showFormatDialog = false
        isProcessing = true
        progress = 0f
        statusMessage = "Starting download..."

        scope.launch(Dispatchers.IO) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val result = YoutubeDLClient.download(video.id, downloadsDir, formatId) { prog, _, line ->
                    progress = prog / 100f
                    statusMessage = line
                }
                
                if (result.isSuccess) {
                    val item = result.getOrThrow()
                    database.downloadDao().insert(
                        DownloadItem(
                            title = item.title,
                            format = item.format,
                            filePath = item.file.absolutePath
                        )
                    )
                    statusMessage = "Success! Saved to Downloads."
                    // Reset UI slightly but keep success message
                } else {
                    statusMessage = "Error: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                statusMessage = "Failed: ${e.message}"
            } finally {
                isProcessing = false
                progress = 0f
            }
        }
    }

    fun fetchVideoInfo(urlOrId: String) {
        isProcessing = true
        statusMessage = "Fetching video info..."
        scope.launch(Dispatchers.IO) {
            val result = YoutubeDLClient.getVideoInfo(urlOrId)
            withContext(Dispatchers.Main) {
                isProcessing = false
                if (result.isSuccess) {
                    selectedVideoDetail = result.getOrThrow()
                    showFormatDialog = true
                } else {
                    statusMessage = "Error fetching info: ${result.exceptionOrNull()?.message}"
                }
            }
        }
    }

    fun performSearch() {
        if (query.isBlank()) return
        
        // If query looks like a URL, fetch info directly
        if (query.contains("youtube.com") || query.contains("youtu.be")) {
            fetchVideoInfo(query)
            return
        }

        isProcessing = true
        statusMessage = "Searching YouTube..."
        searchResults = emptyList() // Clear previous results
        
        scope.launch(Dispatchers.IO) {
            val result = YoutubeDLClient.search(query)
            withContext(Dispatchers.Main) {
                isProcessing = false
                if (result.isSuccess) {
                    searchResults = result.getOrThrow()
                    if (searchResults.isEmpty()) statusMessage = "No results found."
                    else statusMessage = "Found ${searchResults.size} videos."
                } else {
                    statusMessage = "Search failed: ${result.exceptionOrNull()?.message}"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "YTDL Premium",
            style = MaterialTheme.typography.headlineLarge.copy(
                brush = Brush.horizontalGradient(listOf(NeonCyan, NeonPink))
            ),
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        // Search Input
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search or Paste Link") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { performSearch() }) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = NeonCyan)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = NeonCyan
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // Status / Loading
        if (isProcessing) {
             LinearProgressIndicator(
                progress = { if (progress > 0) progress else 0f }, // Show indeterminate if 0
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = NeonPink,
                trackColor = CardDark,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        Text(
            text = statusMessage,
            color = Color.LightGray,
            fontSize = 12.sp,
            maxLines = 1
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // Results List
        if (searchResults.isNotEmpty() && !isProcessing) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(searchResults) { video ->
                    SearchItemCard(video) {
                        fetchVideoInfo(video.videoId)
                    }
                }
            }
        }
    }

    if (showFormatDialog && selectedVideoDetail != null) {
        FormatSelectionDialog(
            video = selectedVideoDetail!!,
            onDismiss = { showFormatDialog = false },
            onFormatSelected = { format ->
                startDownload(selectedVideoDetail!!, format.formatId)
            }
        )
    }
}

@Composable
fun SearchItemCard(video: SearchResult, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail placeholder (Icon for now as loading URL images requires coil)
            Box(
                modifier = Modifier
                    .size(80.dp, 60.dp)
                    .background(Color.Black, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                 Icon(Icons.Rounded.Videocam, null, tint = Color.Gray)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${video.channel} • ${video.duration}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun FormatSelectionDialog(
    video: VideoDetail,
    onDismiss: () -> Unit,
    onFormatSelected: (VideoFormat) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = {
            Text(text = "Select Quality", color = NeonCyan)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                 Text(text = video.title, color = Color.White, fontSize = 14.sp, maxLines = 2)
                 Spacer(modifier = Modifier.height(16.dp))
                 LazyColumn(
                     modifier = Modifier.heightIn(max = 300.dp)
                 ) {
                     items(video.formats) { format ->
                         Row(
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .clickable { onFormatSelected(format) }
                                 .padding(vertical = 12.dp),
                             horizontalArrangement = Arrangement.SpaceBetween
                         ) {
                             Column {
                                 Text(text = format.description, color = Color.White, fontWeight = FontWeight.Bold)
                                 format.note?.let {
                                     Text(text = it, color = Color.Gray, fontSize = 12.sp)
                                 }
                             }
                             if (format.fileSize != null && format.fileSize > 0) {
                                  Text(
                                     text = "%.1f MB".format(format.fileSize / 1024f / 1024f),
                                     color = NeonPink,
                                     fontSize = 12.sp
                                 )
                             }
                         }
                         HorizontalDivider(color = Color.DarkGray)
                     }
                 }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val historyList by database.downloadDao().getAll().collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Download History",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (historyList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No downloads yet", color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(historyList) { item ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (item.format == "MP3") NeonCyan.copy(alpha = 0.2f) else NeonPink.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (item.format == "MP3") Icons.Rounded.MusicNote else Icons.Rounded.Videocam,
                                    contentDescription = null,
                                    tint = if (item.format == "MP3") NeonCyan else NeonPink
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(item.date)),
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Extension to use Text with Brush
fun androidx.compose.ui.text.TextStyle.copy(brush: Brush): androidx.compose.ui.text.TextStyle {
    return this.copy(brush = brush)
}
