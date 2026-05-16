package com.radian0523.kulms_plus_for_android.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.radian0523.kulms_plus_for_android.R
import com.radian0523.kulms_plus_for_android.data.CredentialStore
import com.radian0523.kulms_plus_for_android.ui.QRCodeScannerScreen
import com.radian0523.kulms_plus_for_android.data.TOTPGenerator
import com.radian0523.kulms_plus_for_android.notification.NotificationHelper

private const val GITHUB_URL = "https://github.com/Radian0523/kulms-android-webview"
private val PRESET_OFFSETS = listOf(10, 30, 60, 180, 300, 720, 1440, 2880, 4320)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onLogout: () -> Unit) {
    val context = LocalContext.current
    val appVersion = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "-"
    } catch (_: Exception) {
        "-"
    }

    var offsets by remember { mutableStateOf(NotificationHelper.getNotificationOffsets(context)) }
    var notifyNewAssignment by remember { mutableStateOf(NotificationHelper.getNewAssignmentNotification(context)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var totpSecret by remember { mutableStateOf("") }
    var hasTotpSecret by remember { mutableStateOf(CredentialStore.loadTotpSecret(context) != null) }
    var showTotpInvalidAlert by remember { mutableStateOf(false) }
    var showQRScanner by remember { mutableStateOf(false) }

    val availablePresets = PRESET_OFFSETS.filter { it !in offsets }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Notification section
            SectionHeader(stringResource(R.string.notifications_section))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.notify_new_assignment),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = notifyNewAssignment,
                    onCheckedChange = {
                        notifyNewAssignment = it
                        NotificationHelper.saveNewAssignmentNotification(context, it)
                    }
                )
            }

            offsets.sortedDescending().forEach { offset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        NotificationHelper.formatOffsetLabel(offset, context),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        offsets = offsets - offset
                        NotificationHelper.saveNotificationOffsets(context, offsets)
                    }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (availablePresets.isNotEmpty()) {
                TextButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(
                        stringResource(R.string.add_timing),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            // TOTP section
            SectionHeader(stringResource(R.string.totp_section_title))

            if (hasTotpSecret) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.totp_configured),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        CredentialStore.clearTotpSecret(context)
                        hasTotpSecret = false
                        totpSecret = ""
                    }) {
                        Text(
                            stringResource(R.string.totp_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.totp_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = totpSecret,
                        onValueChange = { totpSecret = it },
                        label = { Text(stringResource(R.string.totp_placeholder)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val cleaned = totpSecret.replace(" ", "").replace("-", "")
                            if (TOTPGenerator.isValidBase32(cleaned)) {
                                CredentialStore.saveTotpSecret(context, cleaned)
                                hasTotpSecret = true
                                totpSecret = ""
                            } else {
                                showTotpInvalidAlert = true
                            }
                        },
                        enabled = totpSecret.isNotBlank()
                    ) {
                        Text(stringResource(R.string.totp_save))
                    }
                }
                TextButton(
                    onClick = { showQRScanner = true },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        stringResource(R.string.totp_scan_qr),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            // Security section
            SectionHeader(stringResource(R.string.security_section_title))
            Text(
                text = stringResource(R.string.security_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            // App info section
            SectionHeader(stringResource(R.string.app_info_section))

            SettingsRow(
                label = stringResource(R.string.version_label),
                value = appVersion
            )

            SettingsLinkRow(
                label = stringResource(R.string.source_code_label),
                value = "GitHub",
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            // Logout
            TextButton(
                onClick = { showLogoutConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    stringResource(R.string.logout),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // Add timing dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.add_timing)) },
            text = {
                Column {
                    availablePresets.forEach { offset ->
                        TextButton(
                            onClick = {
                                offsets = offsets + offset
                                NotificationHelper.saveNotificationOffsets(context, offsets)
                                showAddDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(NotificationHelper.formatOffsetLabel(offset, context))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // QR Scanner
    if (showQRScanner) {
        Dialog(
            onDismissRequest = { showQRScanner = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            QRCodeScannerScreen(
                onSecretFound = { secret ->
                    CredentialStore.saveTotpSecret(context, secret)
                    hasTotpSecret = true
                    totpSecret = ""
                },
                onBack = { showQRScanner = false }
            )
        }
    }

    // TOTP invalid alert
    if (showTotpInvalidAlert) {
        AlertDialog(
            onDismissRequest = { showTotpInvalidAlert = false },
            title = { Text(stringResource(R.string.totp_invalid_title)) },
            text = { Text(stringResource(R.string.totp_invalid_message)) },
            confirmButton = {
                TextButton(onClick = { showTotpInvalidAlert = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Logout confirm dialog
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text(stringResource(R.string.logout_confirm)) },
            text = { Text(stringResource(R.string.logout_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    onLogout()
                }) {
                    Text(stringResource(R.string.logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsLinkRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
