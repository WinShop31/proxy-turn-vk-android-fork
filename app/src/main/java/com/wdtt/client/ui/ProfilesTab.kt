package com.wdtt.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.material.icons.filled.Check
import com.wdtt.client.ConnectionProfile
import com.wdtt.client.ProfilesStore
import android.widget.Toast
import com.wdtt.client.SettingsStore
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ButtonDefaults
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FileOpen

@Composable
fun ProfilesTab(
    onProfileApplied: () -> Unit = {},
    importFileUri: android.net.Uri? = null,
    onImportHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profilesStore = remember { ProfilesStore(context) }
    val settingsStore = remember { SettingsStore(context) }

    val profiles by profilesStore.profiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentPeer by settingsStore.peer.collectAsStateWithLifecycle(initialValue = "")
    val currentHashes by settingsStore.vkHashes.collectAsStateWithLifecycle(initialValue = "")
    val currentWorkers by settingsStore.workersPerHash.collectAsStateWithLifecycle(initialValue = 16)
    val currentPort by settingsStore.listenPort.collectAsStateWithLifecycle(initialValue = 9000)
    val currentPassword by settingsStore.connectionPassword.collectAsStateWithLifecycle(initialValue = "")

    val currentProfileId by settingsStore.currentProfileId.collectAsStateWithLifecycle(initialValue = "")

    var editorVisible by rememberSaveable { mutableStateOf(false) }
    var editingProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var nameInput by rememberSaveable { mutableStateOf("") }
    var peerInput by rememberSaveable { mutableStateOf("") }
    var hashesInput by rememberSaveable { mutableStateOf("") }
    var workersInput by rememberSaveable { mutableStateOf("16") }
    var portInput by rememberSaveable { mutableStateOf("9000") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var deleteTarget by rememberSaveable { mutableStateOf<ConnectionProfile?>(null) }
    var pingStates by remember { mutableStateOf<Map<String, PingState>>(emptyMap()) }
    var scannedProfile by remember { mutableStateOf<ConnectionProfile?>(null) }

    // Лаунчер системного выборщика файлов
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                val profile = parseQrConfig(text)
                if (profile != null) {
                    scannedProfile = profile
                } else {
                    Toast.makeText(context, "Неверный формат файла (.qwdtt или .netrkn)", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка чтения файла: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Автообработка URI если приложение открыли через файл
    LaunchedEffect(importFileUri) {
        val uri = importFileUri ?: return@LaunchedEffect
        try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
            val profile = parseQrConfig(text)
            if (profile != null) {
                scannedProfile = profile
            } else {
                Toast.makeText(context, "Неверный формат файла (.qwdtt или .netrkn)", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка чтения файла: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        onImportHandled()
    }

    fun openEditor(profile: ConnectionProfile? = null) {
        editingProfileId = profile?.id
        nameInput = profile?.name ?: ""
        peerInput = profile?.peer ?: currentPeer
        hashesInput = profile?.vkHashes ?: currentHashes
        workersInput = (profile?.workersPerHash ?: currentWorkers).toString()
        portInput = (profile?.listenPort ?: currentPort).toString()
        passwordInput = profile?.password ?: currentPassword
        editorVisible = true
    }

    fun saveEditor() {
        val name = nameInput.trim()
        val peer = peerInput.trim()
        val hashes = hashesInput.trim()
        val workers = workersInput.toIntOrNull()?.coerceIn(1, 128) ?: 16
        val port = portInput.toIntOrNull()?.coerceIn(1, 65535) ?: 9000
        val password = passwordInput
        if (name.isBlank() || peer.isBlank() || hashes.isBlank() || password.isBlank()) return

        scope.launch {
            if (editingProfileId == null) {
                profilesStore.createProfile(name, peer, hashes, workers, port, password)
            } else {
                profilesStore.saveProfile(
                    ConnectionProfile(
                        id = editingProfileId!!,
                        name = name,
                        peer = peer,
                        vkHashes = hashes,
                        workersPerHash = workers,
                        listenPort = port,
                        password = password
                    )
                )
            }
            editorVisible = false
        }
    }

    if (editorVisible) {
        AlertDialog(
            onDismissRequest = { editorVisible = false },
            title = { Text(if (editingProfileId == null) "Новый профиль" else "Редактировать профиль") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, label = { Text("Название") }, singleLine = true)
                    OutlinedTextField(value = peerInput, onValueChange = { peerInput = it }, label = { Text("Peer") }, singleLine = true)
                    OutlinedTextField(value = hashesInput, onValueChange = { hashesInput = it }, label = { Text("VK-хеши") }, minLines = 2)
                    OutlinedTextField(
                        value = workersInput,
                        onValueChange = { workersInput = it.filter(Char::isDigit) },
                        label = { Text("Потоки") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(value = passwordInput, onValueChange = { passwordInput = it }, label = { Text("Пароль") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { saveEditor() }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { editorVisible = false }) { Text("Отмена") }
            }
        )
    }

    if (deleteTarget != null) {
        val target = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить профиль?") },
            text = { Text("${target.name}\n\nЭтот профиль будет удалён из списка.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { profilesStore.deleteProfile(target.id) }
                    deleteTarget = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена") } }
        )
    }

    if (scannedProfile != null) {
        val profile = scannedProfile!!
        AlertDialog(
            onDismissRequest = { scannedProfile = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Импорт профиля", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Найден новый профиль в QR-коде!",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("📝 Название: ${profile.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("🌐 Сервер: ${profile.peer}", style = MaterialTheme.typography.bodyMedium)
                            Text("⚡ Потоков: ${profile.workersPerHash}", style = MaterialTheme.typography.bodyMedium)
                            val hashCount = if (profile.vkHashes.isBlank()) 0 else profile.vkHashes.trim().split(",").count { it.isNotBlank() }
                            Text("🔑 Хешей: $hashCount", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    if (profile.vkHashes.isBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text("⚠️", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "Хеш звонка ВКонтакте не указан. После сохранения добавьте его вручную в настройках профиля.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                    
                    Text(
                        text = "Вы хотите сохранить его в список профилей?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            profilesStore.saveProfile(profile)
                            Toast.makeText(context, "Профиль «${profile.name}» сохранен!", Toast.LENGTH_SHORT).show()
                            scannedProfile = null
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { scannedProfile = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Профили подключения",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        AppSectionCard(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Сохраняй часто используемые настройки и применяй их в один тап.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Кнопка "Создать" на всю ширину
            Button(
                onClick = { openEditor() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                androidx.compose.material3.Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Создать профиль", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }

            // Файл + QR рядом
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    androidx.compose.material3.Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Из файла", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                Button(
                    onClick = {
                        try {
                            val scanner = com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context)
                            scanner.startScan()
                                .addOnSuccessListener { barcode ->
                                    val rawText = barcode.rawValue ?: ""
                                    val profile = parseQrConfig(rawText)
                                    if (profile != null) {
                                        scannedProfile = profile
                                    } else {
                                        Toast.makeText(context, "Неверный формат QR-кода", Toast.LENGTH_LONG).show()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(context, "Ошибка сканирования: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Не удалось запустить сканер: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("QR-код", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }

        if (profiles.isEmpty()) {
            AppSectionCard(contentPadding = PaddingValues(20.dp)) {
                Text(
                    text = "Пока нет сохранённых профилей.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            profiles.forEach { profile ->
                val pingState = pingStates[profile.id] ?: PingState.Idle
                AppSectionCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val hasIssue = profile.vkHashes.isBlank() || profile.password.isBlank()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (hasIssue) {
                                        Text("⚠️", style = MaterialTheme.typography.titleMedium)
                                    }
                                    Text(
                                        profile.name,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                                        ),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (profile.id == currentProfileId) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            androidx.compose.material3.Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Активен",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .padding(horizontal = 5.dp, vertical = 3.dp)
                                                    .size(12.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                val profileHashCount = if (profile.vkHashes.isBlank()) 0 else profile.vkHashes.trim().split(",").count { it.isNotBlank() }
                                Text(
                                    text = "${profile.peer} · $profileHashCount хеш · ${profile.workersPerHash} потоков",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                            
                            Spacer(Modifier.width(8.dp))
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = pingState !is PingState.Loading) {
                                        pingStates = pingStates + (profile.id to PingState.Loading)
                                        scope.launch {
                                            val result = measurePing(profile.peer)
                                            pingStates = pingStates + (profile.id to result)
                                        }
                                    }
                                    .background(
                                        when (pingState) {
                                            is PingState.Idle -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            is PingState.Loading -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            is PingState.Success -> {
                                                val ms = pingState.ms
                                                if (ms < 100) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                                else if (ms < 250) Color(0xFFFFF3E0) // Light orange
                                                else Color(0xFFFFEBEE) // Light red
                                            }
                                            is PingState.Timeout -> Color(0xFFECEFF1) // Light gray
                                        }
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = when (pingState) {
                                            is PingState.Idle -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                            is PingState.Loading -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                            is PingState.Success -> {
                                                val ms = pingState.ms
                                                if (ms < 100) Color(0xFF4CAF50) // Green
                                                else if (ms < 250) Color(0xFFFF9800) // Orange
                                                else Color(0xFFF44336) // Red
                                            }
                                            is PingState.Timeout -> Color(0xFF90A4AE) // Gray
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    when (pingState) {
                                        is PingState.Idle -> {
                                            androidx.compose.material3.Icon(
                                                Icons.Filled.Bolt,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Тест",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        is PingState.Loading -> {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 1.5.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "...",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        is PingState.Success -> {
                                            val ms = pingState.ms
                                            val tintColor = if (ms < 100) Color(0xFF2E7D32)
                                                else if (ms < 250) Color(0xFFE65100)
                                                else Color(0xFFC62828)
                                            
                                            androidx.compose.material3.Icon(
                                                Icons.Filled.Bolt,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = tintColor
                                            )
                                            Text(
                                                text = "${ms} мс",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = tintColor
                                            )
                                        }
                                        is PingState.Timeout -> {
                                            Text(
                                                text = "Таймаут",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFF455A64)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (profile.vkHashes.isBlank() || profile.password.isBlank()) {
                            val warnings = mutableListOf<String>()
                            if (profile.vkHashes.isBlank()) warnings.add("хеш не указан")
                            if (profile.password.isBlank()) warnings.add("пароль не указан")
                            Text(
                                text = "⚠️ " + warnings.joinToString(", ").replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                text = "Пароль сохранён",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        profilesStore.applyProfile(context = context, id = profile.id, startImmediately = false)
                                        Toast.makeText(context, "Применено", Toast.LENGTH_SHORT).show()
                                        onProfileApplied()
                                    }
                                },
                                modifier = Modifier.weight(1.6f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Применить",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Применить",
                                    maxLines = 1,
                                    softWrap = false,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    ),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            OutlinedButton(
                                onClick = { openEditor(profile) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Править",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Править",
                                    maxLines = 1,
                                    softWrap = false,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    ),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            OutlinedButton(
                                onClick = { deleteTarget = profile },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                                )
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Удалить",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Удалить",
                                    maxLines = 1,
                                    softWrap = false,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    ),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

sealed class PingState {
    object Idle : PingState()
    object Loading : PingState()
    data class Success(val ms: Long) : PingState()
    object Timeout : PingState()
}

private suspend fun measurePing(peer: String): PingState = withContext(Dispatchers.IO) {
    val host = peer.split(":")[0].trim()
    if (host.isBlank()) return@withContext PingState.Timeout
    
    // 1. Try ICMP ping (works on most Android setups and doesn't require open TCP port)
    try {
        val process = Runtime.getRuntime().exec("ping -c 1 -w 2 $host")
        val exitValue = process.waitFor()
        if (exitValue == 0) {
            val reader = process.inputStream.bufferedReader()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line!!.contains("time=")) {
                    val parts = line!!.split("time=")
                    if (parts.size > 1) {
                        val timeStr = parts[1].split(" ")[0]
                        val ms = timeStr.toDoubleOrNull()?.toLong()
                        if (ms != null && ms > 0) return@withContext PingState.Success(ms)
                    }
                }
            }
        }
    } catch (e: Exception) {
        // Fallback to socket connect if ping command fails
    }

    // 2. Try socket connection fallback to standard ports (e.g. 443 then 80)
    val port = peer.split(":").getOrNull(1)?.toIntOrNull() ?: 443
    val startTime = System.currentTimeMillis()
    try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 2000)
            return@withContext PingState.Success(System.currentTimeMillis() - startTime)
        }
    } catch (e: Exception) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, 80), 1500)
                return@withContext PingState.Success(System.currentTimeMillis() - startTime)
            }
        } catch (e2: Exception) {
            // Both failed
        }
    }
    
    return@withContext PingState.Timeout
}

private fun parseQrConfig(rawText: String): ConnectionProfile? {
    val trimmed = rawText.trim()
    if (trimmed.isEmpty()) return null

    // 1. Try URL scheme
    if (trimmed.startsWith("netrkn://config") || trimmed.startsWith("netrkn:config") ||
        trimmed.startsWith("qwdtt://config") || trimmed.startsWith("qwdtt:config")) {
        try {
            val uri = android.net.Uri.parse(
                trimmed.replace("netrkn:config", "netrkn://config")
                       .replace("qwdtt:config", "qwdtt://config")
            )
            val name = uri.getQueryParameter("name") ?: "QR Профиль"
            val peer = uri.getQueryParameter("peer") ?: return null
            val hashes = uri.getQueryParameter("hashes") ?: ""
            val workers = uri.getQueryParameter("workers")?.toIntOrNull() ?: 18
            val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 9000
            val pass = uri.getQueryParameter("pass") ?: ""
            return ConnectionProfile(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                peer = peer,
                vkHashes = hashes,
                workersPerHash = workers,
                listenPort = port,
                password = pass
            )
        } catch (e: Exception) {
            // fallback
        }
    }

    // 2. Try JSON (raw or base64)
    var jsonStr = trimmed
    if (!trimmed.startsWith("{")) {
        try {
            val decodedBytes = android.util.Base64.decode(trimmed, android.util.Base64.DEFAULT)
            jsonStr = String(decodedBytes, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            // not base64
        }
    }

    if (jsonStr.startsWith("{")) {
        try {
            val jsonObj = org.json.JSONObject(jsonStr)
            val name = jsonObj.optString("name", "QR Профиль")
            val peer = jsonObj.getString("peer")
            val hashes = jsonObj.optString("hashes", jsonObj.optString("vkHashes", ""))
            val workers = jsonObj.optInt("workers", jsonObj.optInt("workersPerHash", 18))
            val port = jsonObj.optInt("port", jsonObj.optInt("listenPort", 9000))
            val pass = jsonObj.optString("password", jsonObj.optString("pass", ""))
            return ConnectionProfile(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                peer = peer,
                vkHashes = hashes,
                workersPerHash = workers,
                listenPort = port,
                password = pass
            )
        } catch (e: Exception) {
            // invalid json
        }
    }

    return null
}