package com.example.saarthak.TextReco

import CameraPreview
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.*
import java.util.concurrent.Executors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
@Composable
fun TextRecognitionScreen() {
    var recognizedText by remember { mutableStateOf("") }
    val context = LocalContext.current
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var isFlashOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isTextExpanded by remember { mutableStateOf(false) }
    var speechSpeed by remember { mutableStateOf(1.0f) }
    var bottomSheetState by remember { mutableStateOf(false) }

    // Constants
    val maxTextLength = 700 // Maximum characters to store

    // Zoom state
    var scale by remember { mutableStateOf(1f) }
    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 3f)
    }

    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "The Language specified is not supported!")
                }

                // Set up TTS listener
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                    }

                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                    }
                })
            } else {
                Log.e("TTS", "Initialization failed!")
            }
        }
        onDispose {
            tts?.shutdown()
        }
    }

    // Effect to update speech rate when speed changes
    LaunchedEffect(speechSpeed) {
        tts?.setSpeechRate(speechSpeed)
    }

    // Function to handle speech
    val speakText: () -> Unit = {
        if (!isSpeaking) {
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }
            tts?.speak(recognizedText, TextToSpeech.QUEUE_FLUSH, params, "MANUAL_SPEAK")
        } else {
            tts?.stop()
            isSpeaking = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Status Bar
            AnimatedVisibility(
                visible = isAnalyzing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Analyzing text...",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Camera Preview
            Box(modifier = Modifier.weight(1f)) {
                CameraPreview(
                    onTextRecognized = { text ->
                        recognizedText = text.take(maxTextLength)
                    },
                    onCameraReady = { cam ->
                        camera = cam
                    },
                    scale = scale,
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(state = transformableState)
                )

                // Flash Toggle Button
                IconButton(
                    onClick = {
                        isFlashOn = !isFlashOn
                        camera?.cameraControl?.enableTorch(isFlashOn)
                    },
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = if (isFlashOn) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn,
                        contentDescription = "Toggle Flash",
                        tint = Color.White
                    )
                }
            }

            // Bottom Sheet
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { bottomSheetState = !bottomSheetState }
                        .animateContentSize(),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Handle indicator
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .align(Alignment.CenterHorizontally)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Speed Control
                        SpeedControl(
                            speed = speechSpeed,
                            onSpeedChange = { speechSpeed = it }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Recognized Text
                        if (bottomSheetState) {
                            SelectionContainer {
                                Text(
                                    text = recognizedText.ifEmpty { "No text detected" },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            Text(
                                text = if (recognizedText.isEmpty()) "No text detected"
                                else recognizedText.take(50) + "...",
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ActionButton(
                                icon = Icons.Default.ContentCopy,
                                label = "Copy",
                                enabled = recognizedText.isNotEmpty()
                            ) {
                                copyToClipboard(context, recognizedText)
                            }

                            ActionButton(
                                icon = if (isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow,
                                label = if (isSpeaking) "Stop" else "Speak",
                                enabled = recognizedText.isNotEmpty()
                            ) {
                                speakText()
                            }

                            ActionButton(
                                icon = Icons.Default.Refresh,
                                label = "Clear",
                                enabled = recognizedText.isNotEmpty()
                            ) {
                                recognizedText = ""
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedControl(
    speed: Float,
    onSpeedChange: (Float) -> Unit
) {
    Column {
        Text(
            text = "Speech Speed: ${String.format("%.1fx", speed)}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Slider(
            value = speed,
            onValueChange = onSpeedChange,
            valueRange = 0.5f..2f,
            steps = 5,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun CameraPreview(
    onTextRecognized: (String) -> Unit,
    onCameraReady: (Camera) -> Unit,
    scale: Float = 1f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                this.scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val executor = ContextCompat.getMainExecutor(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, TextAnalyzer(onTextRecognized))
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )
                    onCameraReady(camera)

                    // Set initial zoom
                    camera.cameraControl.setZoomRatio(scale)
                } catch (exc: Exception) {
                    Log.e("CameraPreview", "Use case binding failed", exc)
                }
            }, executor)

            previewView
        },
        modifier = modifier,
        update = { view ->
            // Update zoom when scale changes

        }
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Recognized Text", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
}


class TextAnalyzer(private val onTextRecognized: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastAnalyzedTimestamp = 0L
    private val analysisInterval = 4000L // Time between analyses in milliseconds

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        if (currentTimestamp - lastAnalyzedTimestamp >= analysisInterval) {
            lastAnalyzedTimestamp = currentTimestamp

            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        onTextRecognized(visionText.text)
                    }
                    .addOnFailureListener { e ->
                        Log.e("TextAnalyzer", "Text recognition failed", e)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        } else {
            imageProxy.close()
        }
    }
}


@Composable
fun ActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.padding(4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, fontSize = 12.sp)
        }
    }
}