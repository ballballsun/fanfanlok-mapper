package com.memoryassist.fanfanlokmapper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.memoryassist.fanfanlokmapper.ui.screens.ImageProcessingScreen
import com.memoryassist.fanfanlokmapper.ui.screens.MainScreen
import com.memoryassist.fanfanlokmapper.ui.theme.FanFanLokMapperTheme
import com.memoryassist.fanfanlokmapper.utils.Logger
import com.memoryassist.fanfanlokmapper.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
// import org.opencv.android.OpenCVLoader // TODO: Add OpenCV Android module

/**
 * Main activity of the FanFanLok Mapper application
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Startup logging
        Logger.info("🚀 FanFanLokMapper started")
        
        // Initialize OpenCV - try multiple methods
        try {
            var opencvLoaded = false
            
            // Method 1: Try initLocal (static loading)
            try {
                if (org.opencv.android.OpenCVLoader.initLocal()) {
                    Logger.success("✅ OpenCV loaded successfully (static/local)")
                    opencvLoaded = true
                }
            } catch (e: Exception) {
                Logger.warning("⚠️ Static OpenCV loading failed: ${e.message}")
            }
            
            if (!opencvLoaded) {
                // Method 2: Try initDebug (for debugging)
                try {
                    if (org.opencv.android.OpenCVLoader.initDebug()) {
                        Logger.success("✅ OpenCV loaded successfully (debug)")
                        opencvLoaded = true
                    }
                } catch (e: Exception) {
                    Logger.warning("⚠️ Debug OpenCV loading failed: ${e.message}")
                }
            }
            
            if (!opencvLoaded) {
                Logger.error("❌ All OpenCV loading methods failed")
                Logger.error("💡 Try installing 'OpenCV Manager' from Google Play Store")
            }
        } catch (e: Exception) {
            Logger.error("❌ OpenCV initialization exception", e)
        }
        
        enableEdgeToEdge()
        
        setContent {
            FanFanLokMapperTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FanFanLokMapperApp()
                }
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Save app state when paused
        Logger.info("App paused")
    }
    
    override fun onResume() {
        super.onResume()
        // Restore app state when resumed
        Logger.info("App resumed")
    }
}

/**
 * Main composable for the application
 */
@Composable
fun FanFanLokMapperApp() {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = hiltViewModel()
    
    // Handle app lifecycle
    LaunchedEffect(Unit) {
        mainViewModel.onAppResumed()
    }
    
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route,
        modifier = Modifier.fillMaxSize()
    ) {
        // Main screen
        composable(route = Screen.Main.route) {
            MainScreen(
                onNavigateToProcessing = { uri ->
                    // Navigate to processing screen with URI
                    val encodedUri = android.net.Uri.encode(uri.toString())
                    navController.navigate(Screen.Processing.createRoute(encodedUri))
                    Logger.logUserInteraction("Navigation", "Navigating to processing with image")
                }
            )
        }
        
        // Image processing screen
        composable(
            route = Screen.Processing.route,
            arguments = listOf(
                navArgument("imageUri") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val encodedUri = backStackEntry.arguments?.getString("imageUri") ?: ""
            val imageUri = android.net.Uri.parse(android.net.Uri.decode(encodedUri))
            
            ImageProcessingScreen(
                imageUri = imageUri,
                onNavigateBack = {
                    navController.popBackStack()
                    Logger.logUserInteraction("Navigation", "Navigating back from processing")
                },
                onExportClick = {
                    // Handle export
                    Logger.logUserInteraction("Export", "Export initiated from processing screen")
                }
            )
        }
        
        // Export screen (optional - can be a dialog instead)
        composable(
            route = Screen.Export.route,
            arguments = listOf(
                navArgument("resultId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val resultId = backStackEntry.arguments?.getString("resultId")
            
            ExportScreen(
                resultId = resultId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onExportComplete = { exportPath ->
                    // Handle export completion
                    Logger.logExport(exportPath, 0)
                    navController.popBackStack()
                }
            )
        }
    }
}

/**
 * Screen routes for navigation
 */
sealed class Screen(val route: String) {
    object Main : Screen("main")
    
    object Processing : Screen("processing/{imageUri}") {
        fun createRoute(imageUri: String) = "processing/$imageUri"
    }
    
    object Export : Screen("export?resultId={resultId}") {
        fun createRoute(resultId: String?) = if (resultId != null) {
            "export?resultId=$resultId"
        } else {
            "export"
        }
    }
}

/**
 * Export screen composable (placeholder)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    resultId: String?,
    onNavigateBack: () -> Unit,
    onExportComplete: (String) -> Unit
) {
    // This would be implemented with export UI
    // For now, it's a placeholder
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export Results") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Export Options",
                    style = MaterialTheme.typography.headlineMedium
                )
                
                Button(
                    onClick = { 
                        onExportComplete("/storage/emulated/0/exports/result.json")
                    }
                ) {
                    Text("Export as JSON")
                }
                
                OutlinedButton(
                    onClick = { 
                        onExportComplete("/storage/emulated/0/exports/result_all.zip")
                    }
                ) {
                    Text("Export All Formats")
                }
            }
        }
    }
}