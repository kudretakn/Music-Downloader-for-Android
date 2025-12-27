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
import androidx.compose.foundation.text.BasicTextField
import coil.compose.AsyncImage
import androidx.compose.ui.text.TextStyle
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
// --- TIMELESS MINIMAL THEME ---
val AbsoluteBlack = Color(0xFF000000)
val OffWhite = Color(0xFFF2F2F2)
val SoftGray = Color(0xFF2A2A2A)
val TextGray = Color(0xFF888888)
val AccentWhite = Color(0xFFFFFFFF)
val ErrorRed = Color(0xFFCF6679)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = AbsoluteBlack,
                    surface = AbsoluteBlack,
                    primary = AccentWhite,
                    secondary = SoftGray,
                    onBackground = OffWhite,
                    onSurface = OffWhite
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    AppContent(viewModel, uiState)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(viewModel: MainViewModel, uiState: UiState) {
    
    // --- Initializing State ---
    if (uiState.isInitializing) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("INITIALIZING ENGINE", style = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, letterSpacing = 2.sp, fontSize = 12.sp, color = TextGray))
        }
        return
    }

    if (uiState.initError != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("FATAL: ${uiState.initError}", color = ErrorRed)
        }
        return
    }
    
    // --- Main UI ---
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        
        Spacer(modifier = Modifier.height(60.dp))
        
        // Header
        Text(
            "YT DOWNLOADER", 
            style = TextStyle(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                letterSpacing = (-1).sp,
                color = AccentWhite
            )
        )
        Text(
            "ARCHITECT EDITION",
            style = TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 12.sp,
                color = TextGray
            )
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // Search & Input
        var query by remember { mutableStateOf("") }
        
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = AccentWhite, fontSize = 24.sp, fontWeight = FontWeight.Medium),
            cursorBrush = Brush.verticalGradient(listOf(AccentWhite, AccentWhite)),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) Text("Paste Link or Search", color = SoftGray, fontSize = 24.sp)
                    innerTextField()
                }
            },
            modifier = Modifier.fillMaxWidth().height(40.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Divider(color = SoftGray, thickness = 2.dp)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Action Button
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100))
                    .background(if (query.isNotBlank()) AccentWhite else SoftGray)
                    .clickable(enabled = query.isNotBlank()) { viewModel.search(query) }
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                 if (uiState.isLoading && uiState.downloadProgress == 0f) {
                     CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AbsoluteBlack, strokeWidth = 2.dp)
                 } else {
                     Text(
                         "EXECUTE", 
                         style = TextStyle(
                             color = if (query.isNotBlank()) AbsoluteBlack else TextGray, 
                             fontWeight = FontWeight.Bold,
                             fontSize = 12.sp,
                             letterSpacing = 1.sp
                         )
                     )
                 }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Status & Progress
        if (uiState.isLoading || uiState.downloadStatus != null) {
            Text(
                text = (uiState.currentOperation ?: uiState.downloadStatus ?: "").uppercase(),
                style = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 10.sp, color = TextGray),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (uiState.downloadProgress > 0f) uiState.downloadProgress else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = AccentWhite,
                trackColor = SoftGray
            )
            Spacer(modifier = Modifier.height(32.dp))
        }

        // Results List
        if (uiState.searchResults.isNotEmpty()) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                items(uiState.searchResults) { item ->
                    SearchResultItem(item, 
                        onAudioClick = { viewModel.downloadVideo(item, true) },
                        onVideoClick = { viewModel.downloadVideo(item, false) }
                    )
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(item: SearchResult, onAudioClick: () -> Unit, onVideoClick: () -> Unit) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        // Simple Thumbnail (using AsyncImage if coil available, otherwise placeholder logic could go here)
        // Since we added coil, let's use AsyncImage
        AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(80.dp, 60.dp).clip(RoundedCornerShape(4.dp)).background(SoftGray)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = TextStyle(color = AccentWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 2)
            Spacer(modifier = Modifier.height(4.dp))
            Text(item.channel ?: "", style = TextStyle(color = TextGray, fontSize = 12.sp))
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "[ AUDIO ]", 
                    modifier = Modifier.clickable { onAudioClick() },
                    style = TextStyle(color = OffWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    "[ VIDEO ]", 
                    modifier = Modifier.clickable { onVideoClick() },
                    style = TextStyle(color = OffWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}                            

