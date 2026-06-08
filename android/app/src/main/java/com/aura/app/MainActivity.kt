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
        // biometricHelper.authenticate {
            setupContent()
        // }
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
    
    // Feature Toggles
    var hapticsEnabled by remember { mutableStateOf(true) }
    var biometricsEnabled by remember { mutableStateOf(false) }
    
    val view = LocalView.current

    // DEBUG: Force Local Engine Init
    LaunchedEffect(Unit) {
        val modelName = "GEMMA_2B"
        if (modelManager.isModelDownloaded(modelName)) {
            android.util.Log.d("AuraUI", "DEBUG: Forcing Local Engine Init")
            bridge.setLocalMode(true, modelManager.getModelFile(modelName).absolutePath, { chunk, _ ->
                messages = messages.dropLast(1) + "AURA: $chunk"
            }) { success ->
                 android.util.Log.d("AuraUI", "DEBUG: Engine Init Result: $success")
                 if (success) engineMode = "STANDALONE"
            }
        }
    }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding() 
            .navigationBarsPadding() 
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
                        val modelName = "GEMMA_2B"
                        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        if (modelManager.isModelDownloaded(modelName)) {
                            isDownloading = true
                            if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            bridge.setLocalMode(true, modelManager.getModelFile(modelName).absolutePath, { chunk, isComplete ->
                                messages = messages.dropLast(1) + "AURA: $chunk"
                            }) { success ->
                                isDownloading = false
                                if (success) {
                                    engineMode = "STANDALONE"
                                    if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                } else {
                                    if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                    messages = messages + "SYSTEM: Local Engine Initialization Failed"
                                }
                            }
                        } else if (modelManager.isModelInAssets(modelName)) {
                            isDownloading = true 
                            if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            modelManager.extractModelFromAssets(modelName) { success, error ->
                                isDownloading = false
                                if (success) {
                                    if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    engineMode = "STANDALONE"
                                    bridge.setLocalMode(true, modelManager.getModelFile(modelName).absolutePath, { chunk, _ ->
                                        messages = messages.dropLast(1) + "AURA: $chunk"
                                    })
                                } else {
                                    if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                    android.util.Log.e("AuraUI", "Extraction Error: $error")
                                    messages = messages + "SYSTEM: Local Engine Error - $error"
                                }
                            }
                        } else {
                            isDownloading = true
                            modelManager.downloadModel(modelName) { status, isComplete ->
                                if (isComplete) {
                                    isDownloading = false
                                    if (status == "READY") {
                                        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        engineMode = "STANDALONE"
                                        bridge.setLocalMode(true, modelManager.getModelFile(modelName).absolutePath, { chunk, _ ->
                                            messages = messages.dropLast(1) + "AURA: $chunk"
                                        })
                                    } else {
                                        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                        messages = messages + "SYSTEM: Download Failed - $status"
                                    }
                                } else {
                                    messages = messages.filter { !it.startsWith("SYSTEM: Download Progress") } + "SYSTEM: Download Progress: $status"
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
                        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        engineMode = "REMOTE"
                        bridge.setLocalMode(false)
                    }.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "ADV", 
                    color = if (engineMode == "ADVANCED") Color(0xFFD4AF37) else Color.Gray,
                    modifier = Modifier.clickable { 
                        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        engineMode = "ADVANCED"
                    }.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.labelSmall
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "SETTINGS",
                    color = if (showSettings) Color(0xFFD4AF37) else Color.Gray,
                    modifier = Modifier.clickable {
                        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        showSettings = !showSettings
                    }.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        if (isDownloading) {
            val progressMsg = messages.lastOrNull { it.startsWith("SYSTEM: Download Progress") }
            val progressText = progressMsg?.substringAfter(":")?.trim() ?: "Pulling Model..."
            val progressValue = try {
                val percentText = progressText.substringAfter("Progress: ").substringBefore("%")
                percentText.toFloat() / 100f
            } catch (e: Exception) {
                0.0f
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D0D0D))
                    .padding(vertical = 8.dp)
            ) {
                LinearProgressIndicator(
                    progress = progressValue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = Color(0xFFD4AF37),
                    trackColor = Color(0xFF1A1A1A)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = progressText,
                        color = Color(0xFFD4AF37),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "NETWORK LINK ACTIVE",
                        color = Color(0xFF44FF44),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (showSettings) {
            SettingsPanel(
                engineMode = engineMode,
                bridge = bridge,
                modelManager = modelManager,
                isDownloading = isDownloading,
                hapticsEnabled = hapticsEnabled,
                biometricsEnabled = biometricsEnabled,
                messages = messages,
                onMessagesChange = { messages = it },
                onHapticsToggle = { hapticsEnabled = it },
                onBiometricsToggle = { biometricsEnabled = it },
                onDownloadStart = { isDownloading = true },
                onDownloadEnd = { isDownloading = false },
                onClose = { showSettings = false }
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            reverseLayout = true 
        ) {
            items(messages.reversed()) { msg ->
                ChatMessage(msg)
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (engineMode == "ADVANCED") "Root Command..." else "Aura Command...", color = Color.Gray) },
                colors = TextFieldDefaults.textFieldColors(
                    containerColor = Color(0xFF1A1A1A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFFD4AF37)
                ),
                shape = MaterialTheme.shapes.medium
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
                        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
                            val serviceIntent = Intent(context, AuraService::class.java)
                            context.startForegroundService(serviceIntent)
                            
                            bridge.sendPrompt(prompt) { currentStream ->
                                if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                                messages = messages.dropLast(1) + "AURA: $currentStream"
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8833FF)),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.height(56.dp)
            ) {
                Text("VOID", color = Color.White)
            }
        }
    }
}

@Composable
fun ChatMessage(msg: String) {
    val isUser = msg.startsWith("USER:")
    val isSystem = msg.startsWith("SYSTEM:")
    val content = msg.substringAfter(":").trim()
    
    val alignment = if (isUser) androidx.compose.ui.Alignment.End else androidx.compose.ui.Alignment.Start
    val bgColor = when {
        isUser -> Color(0xFF2A2A2A)
        isSystem -> Color(0xFF441111)
        else -> Color(0xFF1A1A1A)
    }
    val textColor = if (isUser) Color(0xFFD4AF37) else Color(0xFFE0E0E0)

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = androidx.compose.ui.Modifier
                .background(bgColor, shape = MaterialTheme.shapes.medium)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .widthIn(max = 280.dp)
        ) {
            Text(text = content, color = textColor, style = MaterialTheme.typography.bodyMedium)
        }
        
        // 🖼️ COIL: Simple image detection in content
        if (content.contains("http") && (content.contains(".png") || content.contains(".jpg"))) {
            AsyncImage(
                model = content.substringAfter("http").substringBefore(" ").let { "http$it" },
                contentDescription = "Aura Rendered Asset",
                modifier = Modifier.fillMaxWidth().height(200.dp).padding(vertical = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPanel(
    engineMode: String,
    bridge: AuraBridge,
    modelManager: ModelManager,
    isDownloading: Boolean,
    hapticsEnabled: Boolean,
    biometricsEnabled: Boolean,
    messages: List<String>,
    onMessagesChange: (List<String>) -> Unit,
    onHapticsToggle: (Boolean) -> Unit,
    onBiometricsToggle: (Boolean) -> Unit,
    onDownloadStart: () -> Unit,
    onDownloadEnd: () -> Unit,
    onClose: () -> Unit
) {
    var urlText by remember { mutableStateOf("http://192.168.1.176:11435") }
    var connectionStatus by remember { mutableStateOf("IDLE") } // IDLE, TESTING, OK, FAIL
    val modelName = "GEMMA_2B"
    var modelStatus by remember { mutableStateOf(if (modelManager.isModelDownloaded(modelName)) "READY" else "MISSING") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(16.dp)
    ) {
        Text(
            "VOID // SETTINGS", 
            color = Color(0xFFD4AF37), 
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        SettingsSection("REMOTE ORCHESTRATOR") {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TextField(
                    value = urlText,
                    onValueChange = { 
                        urlText = it
                        bridge.setOrchestratorUrl(it)
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("http://...", color = Color.DarkGray) },
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = TextFieldDefaults.textFieldColors(
                        containerColor = Color(0xFF2A2A2A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        cursorColor = Color(0xFFD4AF37)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        connectionStatus = "TESTING"
                        bridge.testConnection(urlText) { success ->
                            connectionStatus = if (success) "OK" else "FAIL"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when(connectionStatus) {
                            "OK" -> Color(0xFF225522)
                            "FAIL" -> Color(0xFF552222)
                            else -> Color(0xFF2A2A2A)
                        }
                    ),
                    modifier = Modifier.height(56.dp)
                ) {
                    Text(if (connectionStatus == "TESTING") "..." else "TEST")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection("MODEL MANAGEMENT") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column {
                    Text("GEMMA 2B IT", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Text(modelStatus, color = if (modelStatus == "READY") Color.Green else Color.Red, style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = {
                        onDownloadStart()
                        modelStatus = "DOWNLOADING..."
                        modelManager.downloadModel(modelName) { status, isComplete ->
                            if (isComplete) {
                                modelStatus = status
                                onDownloadEnd()
                            } else {
                                modelStatus = status
                                onMessagesChange(messages.filter { !it.startsWith("SYSTEM: Download Progress") } + "SYSTEM: Download Progress: $status")
                            }
                        }
                    },
                    enabled = !isDownloading && modelStatus != "READY",
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8833FF)),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(if (isDownloading) "..." else "DOWNLOAD")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection("SYSTEM PREFERENCES") {
            ToggleRow("HAPTIC_FEEDBACK", hapticsEnabled, onHapticsToggle)
            ToggleRow("BIOMETRIC_VAULT", biometricsEnabled, onBiometricsToggle)
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection("ENGINE STATUS") {
            SettingRow("ACTIVE_MODE", engineMode)
            SettingRow("MODEL", "Gemma 2B IT")
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A))
        ) {
            Text("RETURN TO VOID", color = Color(0xFFD4AF37))
        }
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFD4AF37),
                checkedTrackColor = Color(0xFF8833FF)
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPanel(
    engineMode: String,
    bridge: AuraBridge,
    onClose: () -> Unit
) {
    var urlText by remember { mutableStateOf("http://192.168.1.176:11435") }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(16.dp)
    ) {
        Text(
            "VOID // SETTINGS", 
            color = Color(0xFFD4AF37), 
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        SettingsSection("REMOTE ORCHESTRATOR") {
            TextField(
                value = urlText,
                onValueChange = { 
                    urlText = it
                    bridge.setOrchestratorUrl(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("http://...", color = Color.DarkGray) },
                textStyle = MaterialTheme.typography.bodySmall,
                colors = TextFieldDefaults.textFieldColors(
                    containerColor = Color(0xFF2A2A2A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    cursorColor = Color(0xFFD4AF37)
                )
            )
            Text(
                "Point to an Ollama-compatible endpoint.", 
                color = Color.Gray, 
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsSection("ENGINE STATUS") {
            SettingRow("ACTIVE_MODE", engineMode)
            SettingRow("MODEL", "Gemma 2B IT")
            SettingRow("QUANT", "Q8_0 (MediaPipe)")
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A))
        ) {
            Text("RETURN TO VOID", color = Color(0xFFD4AF37))
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title, 
            color = Color(0xFF8833FF), 
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@Composable
fun SettingRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}
