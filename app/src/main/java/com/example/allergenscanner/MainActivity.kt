package com.example.allergenscanner // Make sure this matches your file path

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.random.Random

/*
 * 5. This is our main screen. It handles Camera Permission,
 * shows the Camera Preview, and displays the results from
 * the ViewModel.
 */

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Activity result launcher for camera permission
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // This is handled in the Composable's LaunchedEffect, which will recompose
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // A simple theme
            MaterialTheme {
                // --- UPDATED: Call the new main app screen ---
                MainAppScreen(
                    viewModel = viewModel,
                    onRequestPermission = {
                        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
            }
        }
    }
}

// --- NEW: Main App Composable with Bottom Navigation ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: MainViewModel,
    onRequestPermission: () -> Unit
) {
    var selectedScreen by remember { mutableStateOf("scanner") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (selectedScreen) {
                            "scanner" -> "Allergen Scanner"
                            "profile" -> "My Allergen Profile"
                            "history" -> "Scan History"
                            else -> "Allergen Scanner"
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.CameraAlt, contentDescription = "Scanner") },
                    label = { Text("Scanner") },
                    selected = selectedScreen == "scanner",
                    onClick = { selectedScreen = "scanner" }
                )
                // --- NEW: History Tab ---
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.History, contentDescription = "History") },
                    label = { Text("History") },
                    selected = selectedScreen == "history",
                    onClick = { selectedScreen = "history" }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                    label = { Text("Profile") },
                    selected = selectedScreen == "profile",
                    onClick = { selectedScreen = "profile" }
                )
            }
        }
    ) { padding ->
        // Content area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding) // Apply padding from the Scaffold
        ) {
            Crossfade(targetState = selectedScreen, label = "screen_switch") { screen ->
                when (screen) {
                    "scanner" -> AllergenScannerScreen(
                        viewModel = viewModel,
                        onRequestPermission = onRequestPermission
                    )
                    "history" -> HistoryScreen(
                        viewModel = viewModel
                    )
                    "profile" -> ProfileScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AllergenScannerScreen(
    viewModel: MainViewModel,
    onRequestPermission: () -> Unit
    // onShowProfile was removed
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Observe the UI state from the ViewModel
    val uiState by viewModel.uiState.collectAsState()

    // Re-check permission when the composable launches or permission state changes
    LaunchedEffect(key1 = Unit, key2 = uiState) {
        hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    // --- Bottom Sheet State ---
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isSheetVisible = uiState.isLoading || uiState.scanResult != ScanResult.None || uiState.errorMessage != null

    // --- UPDATED: Root is now a Box, not Scaffold ---
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(), // No padding here, padding is from parent
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (hasCameraPermission) {
                // --- Camera Preview Box ---
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    CameraPreview(
                        onBarcodeScanned = { barcode ->
                            viewModel.onBarcodeScanned(barcode)
                        },
                        isAnalysisPaused = isSheetVisible
                    )
                    ScannerOverlay()
                    Box(
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !isSheetVisible,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.4f))
                                .padding(16.dp),
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Text(
                                "Point camera at a barcode to scan",
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center,
                                color = Color.White
                            )
                        }
                    }
                }
            } else {
                // --- Improved Permission Request UI ---
                PermissionRequestUI(onRequestPermission)
            }
        }

        // --- Real Modal Bottom Sheet ---
        if (isSheetVisible) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.clearProduct() },
                sheetState = sheetState,
                containerColor = Color.White,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            ) {
                val currentErrorMessage = uiState.errorMessage
                val scanResult = uiState.scanResult

                // --- UPDATED: Simplified layout logic ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 220.dp)
                        .padding(16.dp)
                        .animateContentSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        // Loading
                        uiState.isLoading -> {
                            CircularProgressIndicator(modifier = Modifier.padding(vertical = 48.dp))
                        }
                        // Error
                        currentErrorMessage != null -> {
                            ScanErrorResult(currentErrorMessage) {
                                viewModel.clearProduct()
                            }
                        }
                        // --- UPDATED: SAFE/UNSAFE Logic ---
                        scanResult is ScanResult.Unsafe -> {
                            UnsafeResult(scanResult.conflictingAllergens) {
                                viewModel.clearProduct()
                            }
                        }
                        scanResult is ScanResult.Safe -> {
                            SafeResult {
                                viewModel.clearProduct()
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- NEW: History Screen Composable ---
@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val history = uiState.scanHistory

    // Date formatter for the list
    val dateFormatter = remember {
        SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        if (history.isEmpty()) {
            item {
                Text(
                    "Your scan history is empty.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp)
                )
            }
        } else {
            items(history) { item ->
                HistoryItemCard(item = item, dateFormatter = dateFormatter)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

// --- NEW: Card for a single History Item ---
@Composable
fun HistoryItemCard(item: ScanHistoryItem, dateFormatter: SimpleDateFormat) {
    val isUnsafe = item.scanResult == "UNSAFE"
    val cardColor = if (isUnsafe) MaterialTheme.colorScheme.errorContainer else Color.White

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isUnsafe) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                contentDescription = item.scanResult,
                tint = if (isUnsafe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.productName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    dateFormatter.format(Date(item.scanTime)),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                if (isUnsafe && item.conflictingAllergens.isNotEmpty()) {
                    Text(
                        "Conflicts: ${item.conflictingAllergens}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}


// --- NEW: Profile Screen Composable ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MainViewModel
    // onBack was removed
) {
    val uiState by viewModel.uiState.collectAsState()
    val userProfile = uiState.userProfile
    val allAllergens = allAllergensForProfile // Get the master list

    // --- UPDATED: Root is now LazyColumn, not Scaffold ---
    LazyColumn(
        modifier = Modifier.fillMaxSize() // Padding is handled by parent Scaffold
    ) {
        item {
            Text(
                "Select items you are allergic to. The app will warn you if they are found in a product's ingredients or traces.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        items(allAllergens) { allergen ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = userProfile.contains(allergen),
                        onValueChange = { viewModel.toggleAllergen(allergen) },
                        role = Role.Checkbox
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = userProfile.contains(allergen),
                    onCheckedChange = null // Handled by Row's toggleable
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(allergen, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}


// --- NEW: Composable for "UNSAFE" result ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnsafeResult(conflicts: List<String>, onScanNew: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            // UPDATED: Stronger icon
            Icons.Filled.Warning,
            contentDescription = "Unsafe",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "UNSAFE",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "This product contains or may contain allergens from your profile:",
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            conflicts.forEach { allergen ->
                Chip(allergen, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        DisclaimerCard()
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onScanNew, modifier = Modifier.fillMaxWidth()) {
            Text("Scan New Item")
        }
    }
}

// --- NEW: Composable for "SAFE" result ---
@Composable
fun SafeResult(onScanNew: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = "Safe",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "SAFE",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "This product appears to be safe based on your profile.",
            textAlign = TextAlign.Center
        )

        NoAllergensCelebration() // "WOOOW!!!"

        Spacer(modifier = Modifier.height(16.dp))
        DisclaimerCard()
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onScanNew, modifier = Modifier.fillMaxWidth()) {
            Text("Scan New Item")
        }
    }
}

// --- NEW: Composable for "ERROR" result ---
@Composable
fun ScanErrorResult(message: String, onScanNew: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Filled.Error,
            contentDescription = "Error",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Error",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onScanNew, modifier = Modifier.fillMaxWidth()) {
            Text("Scan Again")
        }
    }
}

// --- NEW: Permanent Disclaimer Card ---
@Composable
fun DisclaimerCard() {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4)) // Light Yellow
    ) {
        Text(
            "Data is from a public database and may be incomplete or inaccurate. ALWAYS double-check the product's physical label before consuming.",
            modifier = Modifier.padding(10.dp),
            color = Color(0xFFF57F17), // Dark Yellow/Orange
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontSize = 13.sp
        )
    }
}

// --- NEW: Reusable Chip composable ---
@Composable
fun Chip(text: String, backgroundColor: Color, textColor: Color) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = textColor,
            fontSize = 14.sp
        )
    }
}

// --- NEW: Permission Request UI ---
@Composable
fun PermissionRequestUI(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.CameraAlt,
            contentDescription = "Camera Icon",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Camera Permission Needed",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "This app needs camera access to scan barcodes.",
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Camera Permission")
        }
    }
}


@Composable
fun CameraPreview(
    onBarcodeScanned: (String) -> Unit,
    isAnalysisPaused: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    // 1. Remember the analyzer instance itself
    val barcodeAnalyzer = remember {
        BarcodeAnalyzer(
            onBarcodeScanned = onBarcodeScanned,
            isPaused = isAnalysisPaused
        )
    }

    // 2. Update the analyzer's paused state directly when isAnalysisPaused changes
    LaunchedEffect(isAnalysisPaused) {
        barcodeAnalyzer.isPaused = isAnalysisPaused
    }

    // 3. Remember the ImageAnalysis object and pass the analyzer to it
    val imageAnalysis = remember(barcodeAnalyzer) { // Recreate if analyzer changes
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(
                    Executors.newSingleThreadExecutor(),
                    barcodeAnalyzer // <-- Pass the remembered instance
                )
            }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).also {
                it.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                it.scaleType = PreviewView.ScaleType.FILL_CENTER
                previewView = it
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = {
            // This runs once the view is ready
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                // Set up the preview
                val preview = Preview.Builder().build().also {
                    // This line is now correct and will compile
                    it.setSurfaceProvider(previewView?.surfaceProvider)
                }

                // Select back camera
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    // Unbind use cases before rebinding
                    cameraProvider.unbindAll()
                    // Bind use cases to camera
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis // Add our analyzer here
                    )
                } catch (exc: Exception) {
                    Log.e("CameraPreview", "Use case binding. Failed to bind preview and image analysis.", exc)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}

// Custom ImageAnalysis.Analyzer class
private class BarcodeAnalyzer(
    private val onBarcodeScanned: (String) -> Unit,
    @Volatile var isPaused: Boolean // Use @Volatile for thread safety
) : ImageAnalysis.Analyzer {

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_UPC_A, Barcode.FORMAT_EAN_8)
        .build()
    private val scanner = BarcodeScanning.getClient(options)

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        // If we have a product, don't analyze new frames
        if (isPaused) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        barcodes.first().rawValue?.let {
                            // Barcode found!
                            onBarcodeScanned(it)
                        }
                    }
                }
                .addOnFailureListener {
                    Log.e("BarcodeAnalyzer", "Barcode analysis failed", it)
                }
                .addOnCompleteListener {
                    // Always close the imageProxy
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}

// NEW: Composable for the scanner reticle and laser
@Composable
@Suppress("UnusedBoxWithConstraintsScope") // FIXED: Added suppression for linter warning
fun ScannerOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_laser")

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val reticleHeight = maxHeight * 0.4f
        val reticleTop = (maxHeight - reticleHeight) / 2

        val laserY by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = with(LocalDensity.current) { reticleHeight.toPx() },
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "laser_y"
        )

        // Reticle
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(reticleHeight)
                .align(Alignment.Center)
                .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
        )

        // Laser
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = reticleTop + with(LocalDensity.current) { laserY.toDp() })
                .fillMaxWidth(0.73f)
                .height(3.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Red.copy(alpha = 0.8f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

// NEW: Celebration animation for no allergens found
@Composable
fun NoAllergensCelebration() {
    val infiniteTransition = rememberInfiniteTransition(label = "no_allergens_celebration")

    // Scale and Alpha animation for "WOOOW!!!" text
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wooow_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wooow_alpha"
    )

    // Haptic feedback for a more satisfying feel
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)
        vibrator?.let {
            if (it.hasVibrator()) {
                // Short, strong burst
                @SuppressLint("MissingPermission")
                it.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp), // Give enough space for animation
        contentAlignment = Alignment.Center
    ) {
        // "WOOOW!!!" Text
        Text(
            "WOOOW!!!",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .scale(scale)
                .alpha(alpha)
        )

        // Simple sparkles animation
        val sparkleColors = listOf(Color.Yellow, Color.Cyan, Color.Magenta, Color.Green)
        repeat(5) { index ->
            val sparkleOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1500 + (index * 200), easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "sparkle_offset_$index"
            )
            val sparkleAlpha by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1500 + (index * 200), easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "sparkle_alpha_$index"
            )

            val xOffset = remember { Random.nextInt(-70, 70).dp }
            val yOffset = remember { Random.nextInt(-20, 20).dp }
            val size = remember { Random.nextInt(5, 12).dp }
            val color = remember { sparkleColors[Random.nextInt(sparkleColors.size)] }

            Box(
                modifier = Modifier
                    .offset(x = xOffset * sparkleOffset, y = yOffset * sparkleOffset)
                    .size(size)
                    .alpha(sparkleAlpha)
                    .background(color, CircleShape)
            )
        }
    }
}