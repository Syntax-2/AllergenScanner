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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
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
                AllergenScannerScreen(viewModel) {
                    // Ask for permission when the Composable needs it
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
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
    val isSheetVisible = uiState.isLoading || uiState.product != null || uiState.errorMessage != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Allergen Scanner") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (hasCameraPermission) {
                // --- Camera Preview Box ---
                // This Box fills the remaining space (due to weight)
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    CameraPreview(
                        onBarcodeScanned = { barcode ->
                            viewModel.onBarcodeScanned(barcode)
                        },
                        // Only analyze if no product is currently displayed
                        isAnalysisPaused = isSheetVisible
                    )

                    // NEW: Add the scanner overlay
                    ScannerOverlay()

                    // FIXED: Re-added a wrapper Box to explicitly break the ColumnScope
                    Box(
                        modifier = Modifier.align(Alignment.BottomCenter) // This align works on the Box
                    ) {
                        // FIXED: Use fully qualified name to resolve scope ambiguity
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !isSheetVisible, // Show when sheet is hidden
                            modifier = Modifier
                                .fillMaxWidth() // This modifier is for the AnimatedVisibility itself
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
        }

        // --- Real Modal Bottom Sheet ---
        if (isSheetVisible) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.clearProduct() },
                sheetState = sheetState,
                containerColor = Color.White,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            ) {
                // Create local variables to avoid smart cast errors
                val currentErrorMessage = uiState.errorMessage
                val currentProduct = uiState.product

                // FIXED: This is now a Column that can scroll
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 220.dp) // Give it some space
                        .padding(16.dp)
                        .animateContentSize() // NEW: Animate size changes
                        .verticalScroll(rememberScrollState()), // FIXED: Scrolling is here
                    horizontalAlignment = Alignment.CenterHorizontally // FIXED: Alignment is here
                ) {
                    when {
                        // Loading
                        uiState.isLoading -> {
                            CircularProgressIndicator(modifier = Modifier.padding(vertical = 48.dp))
                        }
                        // Error
                        currentErrorMessage != null -> {
                            InfoColumn( // This no longer scrolls or has a button
                                icon = Icons.Filled.Error,
                                title = "Error",
                                titleColor = MaterialTheme.colorScheme.error
                            ) {
                                // Pass error message as content
                                Text(currentErrorMessage, fontSize = 16.sp, textAlign = TextAlign.Center)
                            }
                            // FIXED: Button is now outside InfoColumn
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.clearProduct() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Scan Again")
                            }
                        }
                        // Success
                        currentProduct != null -> {
                            val allergensList = currentProduct.allergensTags
                                ?.map {
                                    it.removePrefix("en:").replace("-", " ")
                                        .replaceFirstChar { char -> if (char.isLowerCase()) char.uppercase() else char.toString() }
                                }
                                ?: emptyList()

                            // --- NEW: Get traces list ---
                            val tracesList = currentProduct.tracesTags
                                ?.map {
                                    it.removePrefix("en:").replace("-", " ")
                                        .replaceFirstChar { char -> if (char.isLowerCase()) char.uppercase() else char.toString() }
                                }
                                ?: emptyList()

                            val hasAllergens = allergensList.isNotEmpty()
                            val hasTraces = tracesList.isNotEmpty()
                            // --- NEW: Only celebrate if BOTH are empty ---
                            val isTrulyClear = !hasAllergens && !hasTraces

                            InfoColumn( // This no longer scrolls or has a button
                                icon = if (isTrulyClear) Icons.Filled.CheckCircle else Icons.Filled.Info,
                                title = currentProduct.productName ?: "Unknown Product",
                                titleColor = if (isTrulyClear) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            ) {
                                // This content Column is fine, as it's inside the parent scrolling Column
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // --- Main Allergens ---
                                    Text("Contains Allergens:", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (hasAllergens) {
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            allergensList.forEach { allergen ->
                                                Chip(allergen, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                                            }
                                        }
                                    } else {
                                        Text("None found.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    }

                                    // --- Traces ("May Contain") ---
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("May Contain Traces Of:", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (hasTraces) {
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            tracesList.forEach { trace ->
                                                Chip(trace, Color(0xFFFFF0E0), Color(0xFF8B4513)) // Light Orange / Brown text
                                            }
                                        }
                                    } else {
                                        Text("None found.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    }

                                    // --- NEW: Celebration ---
                                    if (isTrulyClear) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            NoAllergensCelebration()
                                        }
                                    }
                                }
                            }
                            // FIXED: Button is now outside InfoColumn
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.clearProduct() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Scan New Item")
                            }
                        }
                    }
                }
            }
        }
    }
}

// NEW: Reusable Chip composable
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
                // FIXED: Suppress lint warning for VIBRATE permission.
                // This assumes you have <uses-permission android:name="android.permission.VIBRATE" />
                // in your AndroidManifest.xml
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


// FIXED: This composable is now simplified and no longer scrolls.
@Composable
fun InfoColumn(
    icon: ImageVector? = null,
    title: String,
    titleColor: Color = Color.Black,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth() // No longer scrolls or fills max size
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = titleColor // Tint the icon with the title color
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = titleColor,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Call the content lambda
        content()

        // Removed the Spacer(weight) and Button from here
    }
}

