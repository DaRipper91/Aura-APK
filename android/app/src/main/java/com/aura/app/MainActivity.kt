package com.aura.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage

class MainActivity : FragmentActivity() {
    private lateinit var auraBridge: AuraBridge
    private lateinit var biometricHelper: BiometricHelper
    private lateinit var modelManager: ModelManager
    private lateinit var shellBridge: ShellBridge
    private var speechCallback: ((String) -> Unit)? = null

    private val speechRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            data?.get(0)?.let { speechCallback?.invoke(it) }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startSpeechRecognition()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        auraBridge = AuraBridge(this)
        biometricHelper = BiometricHelper(this)
        modelManager = ModelManager(this)
        shellBridge = ShellBridge()

        // 🔐 SECURE: Require biometric unlock on launch
        biometricHelper.authenticate {
            setupContent()
        }
    }

    private fun setupContent() {
        val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        setContent {
            AuraTheme {
                ChatScreen(auraBridge, modelManager, shellBridge, sharedText, ::launchSpeechRecognition)
            }
        }
    }

    private fun launchSpeechRecognition(onResult: (String) -> Unit) {
        speechCallback = onResult
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startSpeechRecognition()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Aura Listening...")
        }
        speechRecognizerLauncher.launch(intent)
    }
}

@Composable
fun AuraTheme(content: @Composable () -> Unit) {
    val jetBrainsMono = FontFamily(
        Font(R.font.jetbrains_mono, FontWeight.Normal)
    )

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF0F0F0F), // Obsidian dark background
            surface = Color(0xFF1A1A1A),
            onPrimary = Color(0xFFD4AF37),  // Gold accent
            onSecondary = Color(0xFF8833FF) // Purple accent
        ),
        typography = Typography(
            bodyLarge = androidx.compose.ui.text.TextStyle(
                fontFamily = jetBrainsMono,
                fontWeight = FontWeight.Normal
            )
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    bridge: AuraBridge, 
    modelManager: ModelManager,
    shellBridge: ShellBridge,
    initialPrompt: String? = null, 
    onDictate: (((String) -> Unit) -> Unit)? = null
) {
    var inputText by remember { mutableStateOf(initialPrompt ?: "") }
    var messages by remember { mutableStateOf(listOf<String>()) }
    var engineMode by remember { mutableStateOf("REMOTE") } // REMOTE, STANDALONE, ADVANCED
    var isDownloading by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    
    val view = LocalView.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding() // Ensure text doesn't hide under the camera
            .navigationBarsPadding() // Space for gesture bar
            .imePadding() 
    ) {
        // 🌌 HEADER: Mode Switcher
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("AURA // ${engineMode}", color = Color(0xFFD4AF37), style = MaterialTheme.typography.labelSmall)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = "SW", 
                    color = if (engineMode == "STANDALONE") Color(0xFF8833FF) else Color.Gray,
                    modifier = Modifier.clickable { 
                        val modelName = "QWEN_1.5B"
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        if (modelManager.isModelDownloaded(modelName)) {
                            engineMode = "STANDALONE"
                            bridge.setLocalMode(true, modelManager.getModelFile(modelName).absolutePath)
                        } else if (modelManager.isModelInAssets(modelName)) {
                            isDownloading = true // Use same indicator for extraction
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            modelManager.extractModelFromAssets(modelName) { success, error ->
                                isDownloading = false
                                if (success) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    engineMode = "STANDALONE"
                                    bridge.setLocalMode(true, modelManager.getModelFile(modelName).absolutePath)
                                } else {
                                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                    android.util.Log.e("AuraUI", "Extraction Error: $error")
                                    messages = messages + "SYSTEM: Local Engine Error - $error"
                                }
                            }
                        } else {
                            isDownloading = true
                            modelManager.downloadModel(modelName) { success ->
                                isDownloading = false
                                if (success) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    engineMode = "STANDALONE"
                                    bridge.setLocalMode(true, modelManager.getModelFile(modelName).absolutePath)
                                } else {
                                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                }
                            }
                        }
                    }.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "RE", 
                    color = if (engineMode == "REMOTE") Color(0xFF8833FF) else Color.Gray,
                    modifier = Modifier.clickable { 
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        engineMode = "REMOTE"
                        bridge.setLocalMode(false)
                    }.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "ADV", 
                    color = if (engineMode == "ADVANCED") Color(0xFFD4AF37) else Color.Gray,
                    modifier = Modifier.clickable { 
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        engineMode = "ADVANCED"
                    }.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.labelSmall
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // ⚙️ VOID_SETTINGS Toggle
                Text(
                    text = "SETTINGS",
                    color = if (showSettings) Color(0xFFD4AF37) else Color.Gray,
                    modifier = Modifier.clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        showSettings = !showSettings
                    }.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        if (isDownloading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFFD4AF37))
        }

        if (showSettings) {
            // Simple Settings Panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Text("VOID_SETTINGS", color = Color(0xFFD4AF37), style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Engine: ${engineMode}", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                Text("Model: Qwen 2.5 1.5B (Quantized)", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                // Add more settings here as needed (Temperature, Context, etc.)
                Button(
                    onClick = { showSettings = false },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {
                    Text("CLOSE", color = Color(0xFFD4AF37))
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            reverseLayout = true 
        ) {
            items(messages.reversed()) { msg ->
                Column {
                    Text(
                        text = msg, 
                        color = if (msg.startsWith("USER:")) Color(0xFFD4AF37) else Color(0xFFB0B0B0), 
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    // 🖼️ COIL: Simple image detection
                    if (msg.contains("http") && (msg.contains(".png") || msg.contains(".jpg"))) {
                        AsyncImage(
                            model = msg.substringAfter("http").substringBefore(" ").let { "http$it" },
                            contentDescription = "Aura Rendered Asset",
                            modifier = Modifier.fillMaxWidth().height(200.dp).padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (engineMode == "ADVANCED") "Root Command..." else "Aura Command...", color = Color.Gray) },
                colors = TextFieldDefaults.textFieldColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFFD4AF37)
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (onDictate != null) {
                IconButton(onClick = { onDictate { result -> inputText = result } }) {
                    Text("🎙️", color = Color(0xFF8833FF))
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Button(
                onClick = {
                    val prompt = inputText
                    if (prompt.isNotBlank()) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        messages = messages + "USER: $prompt"
                        messages = messages + "AURA: ..." 
                        inputText = ""

                        if (engineMode == "ADVANCED") {
                            shellBridge.execute(prompt, object : ShellBridge.ShellCallback {
                                override fun onOutput(line: String) {
                                    messages = messages.dropLast(1) + "AURA: (SHELL) $line"
                                }
                                override fun onComplete(exitCode: Int) {
                                    messages = messages.dropLast(1) + "AURA: Execution Finished [Code: $exitCode]"
                                }
                            })
                        } else {
                            // 🔋 PERSISTENCE: Start background service during reasoning
                            val serviceIntent = Intent(context, AuraService::class.java)
                            context.startForegroundService(serviceIntent)
                            
                            bridge.sendPrompt(prompt) { currentStream ->
                                view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                                messages = messages.dropLast(1) + "AURA: $currentStream"
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSecondary)
            ) {
                Text("VOID", color = Color.White)
            }
        }
    }
}
