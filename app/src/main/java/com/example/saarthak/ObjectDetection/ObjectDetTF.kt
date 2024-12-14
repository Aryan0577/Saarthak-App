package com.example.saarthak.ObjectDetection

import android.content.Context
import android.graphics.*
import android.media.Image
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import android.speech.tts.TextToSpeech
import androidx.annotation.OptIn
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TensorFlowObjectDetector(context: Context) {
    private val options = ObjectDetector.ObjectDetectorOptions.builder().setMaxResults(10).setScoreThreshold(0.5f).build()
    private val detector: ObjectDetector = ObjectDetector.createFromFileAndOptions(context, "2.tflite", options)

    fun detect(image: Bitmap): List<Detection> {
        return try {
            val tensorImage = TensorImage.fromBitmap(image)
            detector.detect(tensorImage)
        } catch (e: Exception) {
            Log.e("TensorFlowObjectDetector", "Error during detection", e)
            emptyList()
        }
    }
}

data class DetectedObject(val label: String, val boundingBox: RectF)

class TextToSpeechManager(context: Context) : TextToSpeech.OnInitListener {
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private val speechQueue = mutableListOf<String>()

    init {
        textToSpeech = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isInitialized = true
                speakQueuedItems()
            } else {
                Log.e("TextToSpeechManager", "Language not supported")
            }
        } else {
            Log.e("TextToSpeechManager", "Initialization failed")
        }
    }

    fun speak(text: String) {
        if (isInitialized) textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
        else speechQueue.add(text)
    }

    private fun speakQueuedItems() {
        speechQueue.forEach { textToSpeech?.speak(it, TextToSpeech.QUEUE_ADD, null, null) }
        speechQueue.clear()
    }

    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}

object AppColors {
    val primaryBackground = Color(0xFF121212)
    val secondaryBackground = Color(0xFF1E1E1E)
    val accentColor = Color(0xFF3700B3)
    val textColor = Color(0xFFE0E0E0)
    val boundingBoxColor = Color(0xFF03DAC6)
    val errorColor = Color(0xFFCF6679)
}

@Composable
fun ObjectDetectionTfScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val objectDetector = remember { TensorFlowObjectDetector(context) }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val textToSpeechManager = remember { TextToSpeechManager(context) }
    var detectedObjects by remember { mutableStateOf<List<DetectedObject>>(emptyList()) }
    var imageSize by remember { mutableStateOf(Size.Zero) }
    var lastSpokenObjects by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isFrameFrozen by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition()
    val loadingAnimationScale by infiniteTransition.animateFloat(initialValue = 0.8f, targetValue = 1.2f, animationSpec = infiniteRepeatable(animation = tween(1000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse))

    DisposableEffect(Unit) {
        onDispose {
            textToSpeechManager.shutdown()
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(AppColors.primaryBackground)) {
        AndroidView(factory = { ctx ->
            val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            val preview = Preview.Builder().build()
            val imageAnalyzer = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().apply {
                setAnalyzer(cameraExecutor, TensorFlowImageAnalyzer(objectDetector) { objects, size ->
                    if (!isFrameFrozen) {
                        detectedObjects = objects
                        imageSize = size
                        isLoading = false
                        val currentObjects = objects.map { it.label }.toSet()
                        val newObjects = currentObjects - lastSpokenObjects
                        newObjects.forEach { textToSpeechManager.speak(it) }
                        lastSpokenObjects = currentObjects
                    }
                })
            }
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer)
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                } catch (exc: Exception) {
                    errorMessage = "Camera initialization failed"
                    Log.e("ObjectDetectionScreen", "Use case binding failed", exc)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }, modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    isFrameFrozen = !isFrameFrozen
                })
            })

        BoundingBoxOverlay(detectedObjects, imageSize)

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier
                .align(Alignment.Center)
                .size(64.dp)
                .scale(loadingAnimationScale), color = AppColors.accentColor, strokeWidth = 6.dp)
        }

        errorMessage?.let { message ->
            Surface(color = AppColors.errorColor.copy(alpha = 0.8f), modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)) {
                Text(text = message, color = Color.White, modifier = Modifier.padding(16.dp))
            }
        }

        Box(modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(16.dp)) {
            IconButton(onClick = { isFrameFrozen = !isFrameFrozen }, modifier = Modifier
                .size(56.dp)
                .background(AppColors.secondaryBackground, shape = CircleShape)) {
                Icon(imageVector = if (isFrameFrozen) Icons.Filled.PauseCircle else Icons.Default.PlayCircle, contentDescription = if (isFrameFrozen) "Unfreeze Frame" else "Freeze Frame", tint = AppColors.textColor)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .align(Alignment.BottomCenter)) {
            if (detectedObjects.isNotEmpty()) {
                Surface(color = AppColors.secondaryBackground.copy(alpha = 0.7f), shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Detected Objects:", color = AppColors.textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        detectedObjects.forEach { obj ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = obj.label, color = AppColors.textColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BoundingBoxOverlay(detectedObjects: List<DetectedObject>, imageSize: Size) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scaleX = size.width / imageSize.width
        val scaleY = size.height / imageSize.height
        detectedObjects.forEach { obj ->
            val scaledRect = RectF(obj.boundingBox.left * scaleX, obj.boundingBox.top * scaleY, obj.boundingBox.right * scaleX, obj.boundingBox.bottom * scaleY)
            drawRect(color = AppColors.boundingBoxColor, topLeft = Offset(scaledRect.left, scaledRect.top), size = Size(scaledRect.width(), scaledRect.height()), style = Stroke(width = 5f))
            drawContext.canvas.nativeCanvas.apply {
                drawText(obj.label, scaledRect.left + 10, scaledRect.top + 40, Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 40f
                    typeface = Typeface.DEFAULT_BOLD
                })
            }
        }
    }
}

class TensorFlowImageAnalyzer(private val objectDetector: TensorFlowObjectDetector, private val onObjectsDetected: (List<DetectedObject>, Size) -> Unit) : ImageAnalysis.Analyzer {
    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxy.image?.toBitmap()
            val objects = bitmap?.let { objectDetector.detect(it).map { DetectedObject(it.categories.first().label, it.boundingBox) } } ?: emptyList()
            onObjectsDetected(objects, Size(imageProxy.width.toFloat(), imageProxy.height.toFloat()))
        } catch (e: Exception) {
            Log.e("TensorFlowImageAnalyzer", "Error analyzing image", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun Image?.toBitmap(): Bitmap? {
        this ?: return null
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val yuvImage = android.graphics.YuvImage(bytes, android.graphics.ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 100, out)
        return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
    }
}
