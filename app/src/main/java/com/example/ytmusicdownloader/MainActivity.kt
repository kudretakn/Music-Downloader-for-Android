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
    var url by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var statusMessage by remember { mutableStateOf("Ready to start") }
    var isVideo by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) Toast.makeText(context, "Permission Granted", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "YTDL Premium",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            brush = Brush.horizontalGradient(listOf(NeonCyan, NeonPink))
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        // Input Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Paste YouTube Link") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = NeonCyan
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Download Format:", color = Color.White)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = !isVideo,
                            onClick = { isVideo = false },
                            label = { Text("Audio (MP3)") },
                            leadingIcon = { Icon(Icons.Rounded.MusicNote, null) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                selectedLabelColor = NeonCyan
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = isVideo,
                            onClick = { isVideo = true },
                            label = { Text("Video (MP4)") },
                            leadingIcon = { Icon(Icons.Rounded.Videocam, null) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonPink.copy(alpha = 0.2f),
                                selectedLabelColor = NeonPink
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Download Button
        val animatedProgress by animateFloatAsState(targetValue = progress, label = "Progress")

        Box(contentAlignment = Alignment.Center) {
             Button(
                onClick = {
                    if (url.isNotBlank()) {
                        if (android.os.Build.VERSION.SDK_INT < 29 && 
                            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            return@Button
                        }
                        
                        isDownloading = true
                        progress = 0f
                        statusMessage = "Initializing..."
                        
                        scope.launch(Dispatchers.IO) {
                            try {
                                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                val format = if(isVideo) YoutubeDLClient.DownloadFormat.VIDEO else YoutubeDLClient.DownloadFormat.AUDIO
                                
                                val result = YoutubeDLClient.download(url, downloadsDir, format) { prog, _, line ->
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
                                    url = ""
                                } else {
                                    statusMessage = "Error: ${result.exceptionOrNull()?.message}"
                                }
                            } catch (e: Exception) {
                                statusMessage = "Failed: ${e.message}"
                            } finally {
                                isDownloading = false
                                progress = 0f
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isDownloading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isVideo) NeonPink else NeonCyan,
                    contentColor = DarkBlue
                )
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = DarkBlue,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Processing...")
                } else {
                    Icon(Icons.Default.Download, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("START DOWNLOAD", fontWeight = FontWeight.Bold)
                }
            }
        }
        
        if(isDownloading) {
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = if(isVideo) NeonPink else NeonCyan,
                trackColor = CardDark,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = statusMessage,
                color = Color.LightGray,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
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
