package com.utkusuer.hatirlatici.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.utkusuer.hatirlatici.HatirlaticiApp
import com.utkusuer.hatirlatici.ui.theme.HatirlaticiTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val windowSize = calculateWindowSizeClass(this)
            HatirlaticiTheme {
                NotificationPermissionRequest()
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HatirlaticiAppUi(windowSize.widthSizeClass)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Uygulamaya dönüldüğünde bildirim durumu güncel olsun (ör. gün değişmişse).
        (application as HatirlaticiApp).container.reminderSync.refreshNow()
    }
}

@Composable
private fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) (context.applicationContext as HatirlaticiApp).container.reminderSync.refreshNow()
    }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
