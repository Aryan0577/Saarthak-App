package com.example.saarthak


import CurrencyRecognitionScreen
import ExploreScreen
import ObjectDetectionScreenG
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import com.example.saarthak.ObjectDetection.ObjectDetectionTfScreen
import com.example.saarthak.TextReco.TextRecognitionScreen
import com.google.mlkit.vision.objects.ObjectDetection


@Composable
fun SaarthakApp() {
    val navController = rememberNavController()

    AppDrawer(navController = navController) {
        NavHost(navController = navController, startDestination = "objectDetectionTf") {
            composable("textRecognition") { TextRecognitionScreen() }
            composable("objectDetectionTf") { ObjectDetectionTfScreen() }
            composable("currencyRecognition") { CurrencyRecognitionScreen() }
            composable("explore") { ExploreScreen() }
            composable("ObjDet"){ ObjectDetectionScreenG() }
        }
    }
}

