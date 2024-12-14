import android.content.ContentValues.TAG
import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

data class VisionObject(
    val name: String,
    val confidence: Float,
    val boundingBox: RectF,
    val timestamp: Long = System.currentTimeMillis(),
    val color: Int = android.graphics.Color.RED
)

@Composable
fun ObjectDetectionScreenG() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var detectedObjects by remember { mutableStateOf<List<VisionObject>>(emptyList()) }
    var outputText by remember { mutableStateOf("Point camera at objects and tap detect") }
    var isProcessing by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Loading state
    var isLoading by remember { mutableStateOf(false) }

    // Error handling state
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Initialize TTS with error handling
    var ttsInitialized by remember { mutableStateOf(false) }
    val textToSpeech = remember {
        TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInitialized = true
                 // Slightly slower for better clarity
            } else {
                errorMessage = "Text-to-speech initialization failed"
                Log.e(TAG, "TTS Initialization failed with status: $status")
            }
        }
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Enhanced auto-remove with fade effect
    LaunchedEffect(detectedObjects) {
        if (detectedObjects.isNotEmpty()) {
            delay(3000) // Increased to 3 seconds for better readability
            detectedObjects = emptyList()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            textToSpeech.shutdown()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                lifecycleOwner = lifecycleOwner,
                cameraProviderFuture = cameraProviderFuture,
                cameraExecutor = cameraExecutor,
                onImageCapture = { capture ->
                    imageCapture = capture
                },
                detectedObjects = detectedObjects,
                isFrontCamera = isFrontCamera,
                flashEnabled = flashEnabled
            )

            // Camera controls overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    onClick = { flashEnabled = !flashEnabled }
                ) {
                    Icon(
                        imageVector = if (flashEnabled) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn,
                        contentDescription = "Toggle flash",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = { isFrontCamera = !isFrontCamera }
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch camera",
                        tint = Color.White
                    )
                }
            }

            // Loading indicator
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Error message
        errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }

        // Detection button and results
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Button(
                onClick = {
                    if (!isProcessing) {
                        scope.launch {
                            isProcessing = true
                            isLoading = true
                            errorMessage = null
                            outputText = "Analyzing image..."

                            try {
                                val image = withContext(Dispatchers.IO) {
                                    captureImage(context, imageCapture, cameraExecutor)
                                }

                                image?.let {
                                    try {
                                        val base64Image = encodeImageToBase64i(it)
                                        val objects = analyzeImageWithGoogleVision(base64Image)
                                        val uniqueObjects = removeDuplicateObjects(objects)

                                        // Assign different colors to different objects
                                        detectedObjects = uniqueObjects.mapIndexed { index, obj ->
                                            obj.copy(color = when(index) {
                                                0 -> android.graphics.Color.RED
                                                1 -> android.graphics.Color.GREEN
                                                else -> android.graphics.Color.BLUE
                                            })
                                        }

                                        outputText = if (uniqueObjects.isNotEmpty()) {
                                            val topObjects = uniqueObjects.take(3)
                                            topObjects.joinToString("\n") { it.name }
                                            if (ttsInitialized) {
                                                val ttsText = "Detected: ${topObjects.joinToString(", ") { it.name }}"
                                                textToSpeech.speak(
                                                    ttsText,
                                                    TextToSpeech.QUEUE_FLUSH,
                                                    null,
                                                    null
                                                )
                                            }
                                            topObjects.joinToString("\n") { it.name }
                                        } else {
                                            "No objects detected. Try adjusting the camera angle or distance"
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = "Analysis failed: ${e.localizedMessage}"
                                        Log.e(TAG, "Error analyzing image", e)
                                    }
                                } ?: run {
                                    errorMessage = "Failed to capture image"
                                }
                            } catch (e: Exception) {
                                errorMessage = "Error: ${e.localizedMessage}"
                                Log.e(TAG, "Error in image capture process", e)
                            } finally {
                                isProcessing = false
                                isLoading = false
                            }
                        }
                    }
                },
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isProcessing) "Detecting..." else "Detect Objects")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Results card
            if (outputText.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Detection Results",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = outputText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
    cameraExecutor: ExecutorService,
    onImageCapture: (ImageCapture) -> Unit,
    detectedObjects: List<VisionObject>,
    isFrontCamera: Boolean = false,
    flashEnabled: Boolean = false
) {
    val context = LocalContext.current
    var preview by remember { mutableStateOf<Preview?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    LaunchedEffect(isFrontCamera, flashEnabled) {
        withContext(Dispatchers.Main) {
            try {
                val cameraProvider = cameraProviderFuture.get()
                preview = Preview.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .build()

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetResolution(Size(1280, 720))
                    .setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                    .build()

                imageCapture?.let { capture ->
                    onImageCapture(capture)
                }

                val cameraSelector = if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set up camera", e)
            }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    preview?.setSurfaceProvider(surfaceProvider)
                }
            },
            update = { previewView ->
                preview?.setSurfaceProvider(previewView.surfaceProvider)
            }
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            detectedObjects.forEach { obj ->
                drawContext.canvas.nativeCanvas.apply {
                    val paint = Paint().apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 4f
                        color = obj.color
                    }

                    val textPaint = Paint().apply {
                        style = Paint.Style.FILL
                        textSize = 36f
                        color = obj.color
                        setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                    }

                    val scaledRect = RectF(
                        obj.boundingBox.left * size.width,
                        obj.boundingBox.top * size.height,
                        obj.boundingBox.right * size.width,
                        obj.boundingBox.bottom * size.height
                    )

                    // Draw box with rounded corners
                    drawRoundRect(scaledRect, 10f, 10f, paint)

                    // Draw object name without confidence score
                    drawText(
                        obj.name,
                        scaledRect.left + 10f,
                        scaledRect.top - 10f,
                        textPaint
                    )
                }
            }
        }
    }
}
// Function to remove duplicate objects based on name and position
fun removeDuplicateObjects(objects: List<VisionObject>): List<VisionObject> {
    val result = mutableListOf<VisionObject>()

    for (obj in objects) {
        val isDuplicate = result.any { existing ->
            // Check if names match and bounding boxes are similar
            existing.name == obj.name && areBoxesSimilar(existing.boundingBox, obj.boundingBox)
        }

        if (!isDuplicate) {
            result.add(obj)
        }
    }

    return result
}

// Helper function to check if two bounding boxes are similar
fun areBoxesSimilar(box1: RectF, box2: RectF, threshold: Float = 0.1f): Boolean {
    return abs(box1.left - box2.left) < threshold &&
            abs(box1.top - box2.top) < threshold &&
            abs(box1.right - box2.right) < threshold &&
            abs(box1.bottom - box2.bottom) < threshold
}

suspend fun captureImage(
    context: Context,
    imageCapture: ImageCapture?,
    cameraExecutor: ExecutorService
): Bitmap? = withContext(Dispatchers.IO) {
    try {
        imageCapture?.let { capture ->
            val file = File(context.externalCacheDir, "${System.currentTimeMillis()}.jpg")
            val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

            return@withContext suspendCancellableCoroutine { continuation ->
                capture.takePicture(
                    outputOptions,
                    cameraExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                            continuation.resume(bitmap, null)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "Image capture failed", exception)
                            continuation.resume(null, null)
                        }
                    }
                )
            }
        }
        return@withContext null
    } catch (e: Exception) {
        Log.e(TAG, "Error capturing image", e)
        return@withContext null
    }
}
fun encodeImageToBase64i(bitmap: Bitmap): String {
    return try {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream)
        Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        Log.e(TAG, "Error encoding image to base64", e)
        throw e
    }
}

suspend fun analyzeImageWithGoogleVision(base64Image: String): List<VisionObject> = withContext(Dispatchers.IO) {
    val apiKey = "" // Replace with your API key
    val url = "https://vision.googleapis.com/v1/images:annotate?key=$apiKey"

    val requestBody = JSONObject().apply {
        put("requests", JSONArray().put(JSONObject().apply {
            put("image", JSONObject().apply {
                put("content", base64Image)
            })
            put("features", JSONArray().put(JSONObject().apply {
                put("type", "OBJECT_LOCALIZATION")
                put("maxResults", 3)
            }))
        }))
    }.toString()

    val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val request = Request.Builder()
        .url(url)
        .post(requestBody.toRequestBody("application/json".toMediaTypeOrNull()))
        .build()

    return@withContext try {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.e(TAG, "API request failed with code: ${response.code}")
            throw IOException("API request failed with code: ${response.code}")
        }

        val responseBody = response.body?.string() ?: throw IOException("Empty response body")
        Log.d(TAG, "API Response: $responseBody")

        val jsonObject = JSONObject(responseBody)
        val responses = jsonObject.getJSONArray("responses")
        if (responses.length() == 0) {
            return@withContext emptyList()
        }

        val localizedObjects = responses.getJSONObject(0)
            .optJSONArray("localizedObjectAnnotations")
            ?: return@withContext emptyList()

        List(localizedObjects.length()) { index ->
            val obj = localizedObjects.getJSONObject(index)
            val boundingPoly = obj.getJSONObject("boundingPoly")
            val normalizedVertices = boundingPoly.getJSONArray("normalizedVertices")

            // Calculate normalized bounding box
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE

            for (i in 0 until normalizedVertices.length()) {
                val vertex = normalizedVertices.getJSONObject(i)
                val x = vertex.getDouble("x").toFloat()
                val y = vertex.getDouble("y").toFloat()
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }

            VisionObject(
                name = obj.getString("name"),
                confidence = obj.getDouble("score").toFloat(),
                boundingBox = RectF(minX, minY, maxX, maxY)
            )
        }.sortedByDescending { it.confidence }
            .take(3)
    } catch (e: Exception) {
        Log.e(TAG, "Vision API request failed", e)
        throw e
    }
}
