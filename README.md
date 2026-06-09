# 📱 AURA-APK // THE SATELLITE INTERFACE

Aura is a high-performance Android application redesigned as a satellite interface for the **DA-HP Logic Hub**. It offloads heavy inference and complex logic tasks to a dedicated remote server (via Tailscale), providing a lightweight, secure, and family-friendly intelligence tool.

## 🌟 CORE MANDATES

Any development or operation within this repository is governed by the Aura mandates:

- **⚡ BOLT (Performance):** Zero-latency UI. Offload heavy lifting to the Hub. Optimized for Apple Silicon (Asahi) and AVX2 (DA-HP).
- **🛡️ SENTINEL (Security):** Strict command injection prevention and mandatory biometric gating for critical vault operations.
- **🎨 PALETTE (Aesthetics):** High-signal, low-noise Obsidian/Gold/Purple theme. Optimized for keyboard and controller-driven navigation.

## 🏗️ ARCHITECTURE: HUB-AND-SPOKE

Aura has transitioned from local-only inference to a distributed model:
- **Spoke (Aura-APK):** Native Android UI for prompt delivery, haptic feedback, and biometric security.
- **Hub (DA-HP):** Headless Arch Linux server running Ollama with Huge Pages and optimized CPU governors.
- **Link:** Secure communication over **Tailscale** using the Ollama compatible API.

## 🚀 GETTING STARTED

### 1. Configure the Hub
Ensure your DA-HP Logic Hub is active and reachable via Tailscale. The Hub should be running Ollama on port `11434`.

### 2. Install the APK
Download the latest `aura-pixel-debug.apk` or `aura-samsung-debug.apk` from the **[Releases](https://github.com/DaRipper91/Aura-APK/releases)** section.

### 3. Connect to Logic Hub
1. Open Aura on your Android device.
2. Navigate to **SETTINGS**.
3. Enter your DA-HP Tailscale IP (e.g., `http://100.x.y.z:11434`).
4. Tap **TEST** to verify the link.
5. **Return to Void** and start commanding.

## 🤖 MODEL MANAGEMENT

Models are managed server-side at the DA-HP Hub. The APK automatically utilizes whatever models are pre-pulled and configured on the remote server (defaulting to `qwen2.5-coder` and `deepseek-r1`).

## 🛠️ DEVELOPMENT

### Prerequisites
- Android Studio Ladybug+
- JDK 17
- Python 3.11+ (Chaquopy)

### Build
The project uses GitHub Actions for automated CI/CD. Pushing to the `master` branch automatically builds and releases new versions of the satellite APK.

## 📜 LOGIC HUB CONSTITUTION
- **SHUT UP AND COMPUTE:** High-signal, technical output only.
- **SATELLITE VIEW:** Analyze the entire workspace before acting.
- **NON-INTERACTIVE:** Use `--yes` and `yolo` modes for all automation.

---
*Built with 💜 for the Logic Hub // DA-HP.*
