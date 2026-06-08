# Aura APK 🌟

Aura is a high-performance Android application designed for secure, local-first intelligence. It leverages on-device LLMs (Large Language Models) to provide a seamless and private user experience, bypassing the need for constant cloud connectivity.

## 🚀 Features

- **Local Inference Engine:** Run advanced models (like Qwen 1.5B) directly on your device.
- **Biometric Security:** Integrated biometric authentication for sensitive operations.
- **Aura Bridge:** Seamless communication between the UI and low-level system services.
- **Background Service:** Persistent `AuraService` for handling long-running tasks and model management.
- **Shell Bridge:** Low-level system interaction capabilities.
- **Quick Settings Tile:** Fast access to Aura features directly from the Android status bar.

## 🛠️ Architecture

Aura is built with a focus on modularity and security:
- **Kotlin/Android:** Native UI and service lifecycle management.
- **Git LFS:** Efficient management of large model binary files.
- **Automated CI/CD:** Integrated GitHub Actions for building and releasing APKs.

## 📦 Getting Started

### Prerequisites
- Android Studio Ladybug or newer.
- JDK 17.
- Git LFS installed (`git lfs install`).

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/DaRipper91/Aura-APK.git
   ```
2. Fetch the models:
   ```bash
   git lfs pull
   ```
3. Open the `android` directory in Android Studio.
4. Build and run on your device.

## 🤖 Model Information

Aura currently supports the **Qwen 1.5B** model for local inference. Models are stored in `android/app/src/main/assets/models/` and managed via Git LFS.

## 🏗️ CI/CD

This project uses GitHub Actions for automated builds. Every push to the `master` branch triggers a build and automatically creates a new release with the generated APK.

## 🤝 Credits & Gratitude

This project wouldn't be where it is today without the incredible support and inspiration from:
- **Deanna, Nate, and Bray:** Thank you for the vibrant color palettes, the visionary platform ideas, and for being the anchor that pulled me back on track whenever the A.D.H.D. peaked the hardest. Your friendship and focus made Aura possible. 💜

---
*Built with 💜 for privacy and performance.*
