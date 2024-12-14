package com.example.saarthak

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.saarthak.ui.theme.SaarthakTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SaarthakTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

                    if (cameraPermissionState.status.isGranted) {
                        SaarthakApp()
                    } else {
                        PermissionRequest(
                            permissionState = cameraPermissionState,
                            onPermissionGranted = { SaarthakApp() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequest(
    permissionState: com.google.accompanist.permissions.PermissionState,
    onPermissionGranted: @Composable () -> Unit
) {
    if (permissionState.status.isGranted) {
        onPermissionGranted()
    } else {
        androidx.compose.material3.Button(onClick = { permissionState.launchPermissionRequest() }) {
            androidx.compose.material3.Text("Grant Camera Permission")
        }
    }
}