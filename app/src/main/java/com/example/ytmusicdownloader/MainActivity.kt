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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize YoutubeDL
        YoutubeDLClient.init(applicationContext)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    var url by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Ready to download") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Permission launcher (Only for Android 9 and below strictly needed for external storage, 
    // but good practice for downloads folder access generally if not using MediaStore wrapper yet)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startDownload(url, context) { downloading, msg ->
                isDownloading = downloading
                statusMessage = msg
            }
        } else {
            Toast.makeText(context, "Storage permission needed to save files", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "YT Music Downloader",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("YouTube URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (url.isNotBlank()) {
                    // Check permissions
                    if (android.os.Build.VERSION.SDK_INT < 29 && 
                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        // Android 10+ or perm granted
                        scope.launch {
                            isDownloading = true
                            statusMessage = "Starting download..."
                            
                            withContext(Dispatchers.IO) {
                                try {
                                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                    val result = YoutubeDLClient.downloadAudio(url, downloadsDir)
                                    
                                    withContext(Dispatchers.Main) {
                                        if (result.isSuccess) {
                                            statusMessage = "Download Complete! Saved to Downloads."
                                            url = ""
                                        } else {
                                            statusMessage = "Error: ${result.exceptionOrNull()?.message}"
                                        }
                                        isDownloading = false
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        statusMessage = "Error: ${e.message}"
                                        isDownloading = false
                                    }
                                }
                            }
                        }
                    }
                }
            },
            enabled = !isDownloading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isDownloading) "Downloading..." else "Download MP3")
        }

        if (isDownloading) {
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = statusMessage)
    }
}

// Helper stub for separate function call if needed, currently inlined in UI for simplicity
fun startDownload(url: String, context: Context, callback: (Boolean, String) -> Unit) {
    // This block is logic usually separate, but kept inline in Composable for this simple script
}
