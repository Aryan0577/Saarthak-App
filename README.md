# SAARTHAK App

SAARTHAK is a feature-rich mobile application designed to assist visually impaired individuals. This app combines cutting-edge Machine Learning (ML) models and Google services to provide accessibility and empowerment through technology.

---

## Features

### 1. Text Recognition
- **Functionality**: Recognizes text using Google ML Kit.
- **Key Features**:
  - Flashlight toggle for better visibility.
  - Text-to-speech output with adjustable speed.
  - Pause and play controls.
  - Option to copy recognized text.
  

### 2. Object Detection
- **Functionality**: Detects objects in real-time using TensorFlow Lite (EfficientDetv4).
- **Key Features**:
  - Detects up to 90 common objects.
  - Frame-by-frame detection with bounding boxes.
  - Double-tap to freeze the camera and analyze the frame.
  - Audio output describing detected objects.


### 3. Explore Mode
- **Functionality**: Provides a comprehensive description of an image using Google Gemini API.
- **Key Features**:
  - Flashlight option for better image capturing.
  - Toggle to enable or disable audio descriptions.
  - Double-tap to capture and analyze the image.


### 4. Currency Detection
- **Functionality**: Identifies currency notes using Google Gemini. 
- **Key Features**:
  - Double-tap to capture and identify the currency.
  - Audio output for immediate feedback.


---

## How It Works
1. **Modes**: The app offers four modes: Text Mode, Object Detection, Explore Mode, and Currency Detection.
2. **Navigation**: Users can switch between modes using a side navigation drawer accessible via a hamburger menu icon.
3. **Camera Interface**: Each mode includes a live camera preview for real-time functionality.

---

## Technologies Used
- **Google ML Kit**: For text recognition.
- **TensorFlow Lite**: For object detection (EfficientDetv4 model).
- **Google Cloud Vision API**: For explore and currency modes.
- **Android Development**: Native development in Kotlin using Jetpack Compose.

---

## Installation
1. Clone the repository
2. Open the project in Android Studio.
3. Sync Gradle and build the project.
4. Run the app on an Android device or emulator.

---

## Future Enhancements
- Add support for more languages in text recognition.
- Include offline functionality for object and currency detection.
- Implement additional modes to assist with navigation and more daily activities.

---

## Contributing
We welcome contributions! To contribute:
1. Fork the repository.
2. Create a new branch: `git checkout -b feature-name`.
3. Commit your changes: `git commit -m "Added feature"`.
4. Push to the branch: `git push origin feature-name`.
5. Submit a pull request.

---

## License
This project is licensed under the [MIT License](LICENSE).

---


Empowering through technology. Together, let’s make the world more accessible for everyone!

