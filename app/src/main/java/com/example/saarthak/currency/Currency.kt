import android.content.Context
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
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
fun CurrencyRecognitionScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var outputText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showRetryButton by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // TTS initialization
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    DisposableEffect(Unit) {
        var ttsInstance: TextToSpeech? = null
        ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInstance?.language = Locale.US
                tts = ttsInstance
            } else {
                Log.e("TTS", "Initialization failed with status: $status")
                errorMessage = "Text-to-speech initialization failed"
            }
        }
        onDispose {
            ttsInstance.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Camera Preview with Double Tap Detection
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { _ ->
                                if (!isLoading) {
                                    captureAndAnalyzeImage(
                                        imageCapture = imageCapture,
                                        context = context,
                                        scope = scope,
                                        onLoadingChange = { isLoading = it },
                                        onResult = { result ->
                                            outputText = result
                                            tts?.speak(result, TextToSpeech.QUEUE_FLUSH, null, null)
                                        },
                                        onError = { error ->
                                            errorMessage = error
                                            showRetryButton = true
                                        }
                                    )
                                }
                            }
                        )
                    }
            ) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    lifecycleOwner = lifecycleOwner,
                    onImageCapture = { capture ->
                        imageCapture = capture
                    }
                )

                // Camera overlay guide
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .border(
                                width = 2.dp,
                                color = Color.White.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(8.dp)
                            )
                    )
                }

                // Double tap instruction
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Double tap to capture",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Output Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Output Display
                AnimatedVisibility(
                    visible = outputText != null || errorMessage != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    errorMessage != null -> MaterialTheme.colorScheme.errorContainer
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                }
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            text = errorMessage ?: outputText ?: "",
                            color = when {
                                errorMessage != null -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Retry Button
                AnimatedVisibility(
                    visible = showRetryButton,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    TextButton(
                        onClick = {
                            errorMessage = null
                            showRetryButton = false
                            outputText = null
                        }
                    ) {
                        Text("Try Again")
                    }
                }
            }
        }

        // Loading Overlay
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun captureAndAnalyzeImage(
    imageCapture: ImageCapture?,
    context: Context,
    scope: CoroutineScope,
    onLoadingChange: (Boolean) -> Unit,
    onResult: (String) -> Unit,
    onError: (String) -> Unit
) {
    imageCapture?.let { capture ->
        val file = File(context.cacheDir, "temp_${System.currentTimeMillis()}.jpg")
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        onLoadingChange(true)
        capture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    onLoadingChange(false)
                    onError("Failed to capture image: ${exc.message}")
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    scope.launch {
                        try {
                            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                            bitmap?.let {
                                val base64Image = encodeImageToBase64(it)
                                val caption = analyzeImageWithGeminiVision(base64Image)
                                onLoadingChange(false)
                                onResult(caption)
                            } ?: run {
                                onLoadingChange(false)
                                onError("Failed to process image")
                            }
                            file.delete()
                        } catch (e: Exception) {
                            onLoadingChange(false)
                            onError("Error: ${e.message}")
                        }
                    }
                }
            }
        )
    }
}

// Helper function to encode bitmap image to Base64
private fun encodeImageToBase64(bitmap: Bitmap): String {
    val byteArrayOutputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream)
    return Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.NO_WRAP)
}

// Function to call Gemini Vision API and get image caption
private suspend fun analyzeImageWithGeminiVision(base64Image: String): String {
    val apiKey = "Add Your Own Api Key"  // Store your API key in local.properties
    val endpoint = "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent"

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
                            put("text", "Identify the currency in this image")
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

            val request = Request.Builder()
                .url("$endpoint?key=$apiKey")
                .post(RequestBody.create("application/json".toMediaTypeOrNull(), jsonBody.toString()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext "Error: Empty response"
            val jsonResponse = JSONObject(responseBody)

            // Parse the response
            jsonResponse.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

        } catch (e: Exception) {
            Log.e("API_ERROR", "Error calling Gemini API", e)
            "Error: ${e.localizedMessage}"
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
