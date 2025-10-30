package com.example.allergenscanner // FIXED: Must match your file path

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
// Note: We import AnimatedVisibility, but will use the fully qualified name to solve the error
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

                // Content of the bottom sheet
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 220.dp) // Give it some space
                        .padding(16.dp)
                        .animateContentSize(), // NEW: Animate size changes
                    contentAlignment = Alignment.TopCenter
                ) {
                    when {
                        // Loading
                        uiState.isLoading -> {
                            CircularProgressIndicator(modifier = Modifier.padding(vertical = 48.dp))
                        }
                        // Error
                        currentErrorMessage != null -> {
                            InfoColumn(
                                icon = Icons.Filled.Error,
                                title = "Error",
                                buttonText = "Scan Again",
                                onButtonClick = { viewModel.clearProduct() },
                                titleColor = MaterialTheme.colorScheme.error
                            ) {
                                // Pass error message as content
                                Text(currentErrorMessage, fontSize = 16.sp, textAlign = TextAlign.Center)
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

                            val hasAllergens = allergensList.isNotEmpty()

                            InfoColumn(
                                icon = if (hasAllergens) Icons.Filled.Info else Icons.Filled.CheckCircle,
                                title = currentProduct.productName ?: "Unknown Product",
                                buttonText = "Scan New Item",
                                onButtonClick = { viewModel.clearProduct() },
                                titleColor = if (hasAllergens) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            ) {
                                // NEW: Pass allergens as content
                                Text("Allergens:", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(8.dp))
                                if (hasAllergens) {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        allergensList.forEach { allergen ->
                                            Card(
                                                shape = RoundedCornerShape(8.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                            ) {
                                                Text(
                                                    allergen,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Text("No allergen information found.")
                                }
                            }
                        }
                    }
                }
            }
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
    // FIXED: Typo was 'mutableStateof' (lowercase 'o')
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

    // FIXED: Removed the junk text that was pasted in here
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


// MODIFIED: This composable now accepts a composable lambda for its content
@Composable
fun InfoColumn(
    icon: ImageVector? = null,
    title: String,
    buttonText: String,
    onButtonClick: () -> Unit,
    titleColor: Color = Color.Black,
    content: @Composable ColumnScope.() -> Unit // CHANGED
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

            // CHANGED: Call the content lambda
            content()
        }
        // Push button to the bottom of the sheet
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onButtonClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(buttonText)
        }
    }
}

