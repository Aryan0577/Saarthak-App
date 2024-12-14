import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getString
import com.example.saarthak.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun ExploreScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var outputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var autoSpeak by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    var showError by remember { mutableStateOf<String?>(null) }

    // Initialize TextToSpeech
    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.apply {
                    setLanguage(Locale.US)
                    setPitch(1.0f)
                    setSpeechRate(1.0f)
                }
            }
        }
        onDispose {
            tts?.shutdown()
        }
    }

    fun speak(text: String, priority: Int = TextToSpeech.QUEUE_FLUSH) {
        if (autoSpeak) {
            tts?.speak(text, priority, null, null)
        }
    }

    val captureAndAnalyze = {
        if (!isLoading) {
            isLoading = true
            showError = null
            speak("Capturing image")

            imageCapture?.let { capture ->
                capture.flashMode = if (flashEnabled) {
                    ImageCapture.FLASH_MODE_ON
                } else {
                    ImageCapture.FLASH_MODE_OFF
                }

                val file = File(context.cacheDir, "temp_${System.currentTimeMillis()}.jpg")
                val outputFileOptions = ImageCapture.OutputFileOptions.Builder(file).build()

                capture.takePicture(
                    outputFileOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onError(exc: ImageCaptureException) {
                            isLoading = false
                            showError = "Failed to capture image: ${exc.message}"
                            speak("Failed to capture image")
                        }

                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            scope.launch {
                                try {
                                    speak("Processing image", TextToSpeech.QUEUE_ADD)
                                    val options = BitmapFactory.Options().apply {
                                        inSampleSize = 2
                                    }
                                    val newBitmap = BitmapFactory.decodeFile(file.absolutePath, options)

                                    newBitmap?.let {
                                        val base64Image = encodeImageToBase64(it)
                                        val description = analyzeImageWithGeminiVision(base64Image)
                                        withContext(Dispatchers.Main) {
                                            isLoading = false
                                            outputText = description
                                            speak("Analysis complete. $description")
                                        }
                                    }

                                    file.delete()
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        showError = e.message
                                        speak("Error while analyzing image")
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Camera Preview with Tutorial Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                showTutorial = false
                                captureAndAnalyze()
                            }
                        )
                    }
            ) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    lifecycleOwner = lifecycleOwner,
                    onImageCapture = { capture ->
                        imageCapture = capture
                        speak("Camera ready. Double tap screen to capture and analyze image.")
                    }
                )

                // Tutorial Overlay
                this@Column.AnimatedVisibility(
                    visible = showTutorial,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "Welcome to Image Analysis",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Double tap anywhere to capture and analyze an image\n" +
                                        "Use the flash button for low-light conditions\n" +
                                        "Toggle auto-speak in the top-right corner",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { showTutorial = false },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Got it!")
                            }
                        }
                    }
                }

                // Camera guide overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                ) {
                    // Center focus area
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(16.dp)
                            )
                    )
                    // Corner indicators
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.TopStart)
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(topStart = 8.dp)
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.TopEnd)
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(topEnd = 8.dp)
                            )
                    )
                }
            }

            // Results Bottom Sheet
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Output Text with improved styling
                    if (outputText.isNotEmpty()) {
                        Text(
                            text = outputText,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .padding(bottom = 16.dp)
                        )
                    }

                    // Capture Button with enhanced design
                    Button(
                        onClick = captureAndAnalyze,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Camera",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Capture & Analyze",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }

        // Control buttons row
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Flash Control Button
            FloatingActionButton(
                onClick = {
                    flashEnabled = !flashEnabled
                    speak(if (flashEnabled) "Flash enabled" else "Flash disabled")
                },
                containerColor = if (flashEnabled)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface
            ) {
                Icon(
                    imageVector = if (flashEnabled) Icons.Default.FlashlightOff else Icons.Default.FlashlightOn,
                    contentDescription = if (flashEnabled) "Disable Flash" else "Enable Flash",
                    tint = if (flashEnabled)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }

            // Auto-speak Toggle
            FloatingActionButton(
                onClick = {
                    autoSpeak = !autoSpeak
                    speak(if (autoSpeak) "Auto speak enabled" else "Auto speak disabled")
                },
                containerColor = if (autoSpeak)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface
            ) {
                Icon(
                    imageVector = if (autoSpeak) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = if (autoSpeak) "Disable Auto-Speak" else "Enable Auto-Speak",
                    tint = if (autoSpeak)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Loading Indicator
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Error Snackbar
        showError?.let { error ->
            Snackbar(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                action = {
                    TextButton(onClick = { showError = null }) {
                        Text(
                            "Dismiss",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            ) {
                Text(error)
            }
        }
    }
}
// Function to encode the bitmap image to Base64
private fun encodeImageToBase64(bitmap: Bitmap): String {
    val byteArrayOutputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
    val byteArray = byteArrayOutputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.NO_WRAP)
}

// Function to call Gemini Vision API and get image caption
private suspend fun analyzeImageWithGeminiVision(base64Image: String): String {
    val apiKey = ""  // Store your API key in local.properties
    val betterPrompt = "Analyze this image and provide a clear, concise description for a visually impaired person telling what this image contain "
     return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val jsonBody = JSONObject().apply {
                put("contents", JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                        put(JSONObject().apply {
                            put("text", betterPrompt)
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.4)
                    put("topK", 32)
                    put("topP", 1)
                    put("maxOutputTokens", 1024)
                })
            }

            val endpoint = "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent"

            val request = Request.Builder()
                .url("$endpoint?key=$apiKey")
                .post(RequestBody.create("application/json".toMediaTypeOrNull(), jsonBody.toString()))
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                throw Exception("API call failed: ${response.code} ${response.body?.string()}")
            }

            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            val jsonResponse = JSONObject(responseBody)

            // Parse the response
            val candidates = jsonResponse.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val content = candidates.getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                return@withContext content
            }

            return@withContext "Could not generate description"

        } catch (e: Exception) {
            Log.e("API_ERROR", "Error calling Gemini API", e)
            return@withContext "Error: ${e.localizedMessage}"
        }
    }
}
@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onImageCapture: (ImageCapture) -> Unit
) {
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }.also { previewView ->
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build()
                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                        onImageCapture(imageCapture)
                    } catch (e: Exception) {
                        Log.e("CameraPreview", "Binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        }
    )
}
