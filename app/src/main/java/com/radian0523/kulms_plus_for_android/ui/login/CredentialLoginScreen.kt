package com.radian0523.kulms_plus_for_android.ui.login

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.radian0523.kulms_plus_for_android.R
import com.radian0523.kulms_plus_for_android.data.CredentialStore
import com.radian0523.kulms_plus_for_android.ui.QRCodeScannerScreen
import com.radian0523.kulms_plus_for_android.data.LoginResult
import com.radian0523.kulms_plus_for_android.data.TOTPGenerator
import com.radian0523.kulms_plus_for_android.data.WebViewManager
import kotlinx.coroutines.launch

/**
 * ECS-ID/パスワードを入力する独自ログイン画面。
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CredentialLoginScreen(
    onRequireWebViewLogin: () -> Unit,
    didAutoLogin: Boolean,
    onDidAutoLogin: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val autofill = LocalAutofill.current
    val autofillTree = LocalAutofillTree.current

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var savePassword by remember { mutableStateOf(true) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // TOTP settings
    var totpSecret by remember { mutableStateOf("") }
    var hasTotpSecret by remember { mutableStateOf(CredentialStore.loadTotpSecret(context) != null) }
    var showTotpInvalidAlert by remember { mutableStateOf(false) }
    var showQRScanner by remember { mutableStateOf(false) }

    // TOTP debug display
    var debugTotpCode by remember { mutableStateOf<String?>(null) }
    var debugTotpSecret by remember { mutableStateOf<String?>(null) }
    var totpSecondsRemaining by remember { mutableIntStateOf(0) }
    var showDebugTotp by remember { mutableStateOf(false) }

    val usernameAutofillNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.Username, AutofillType.EmailAddress),
            onFill = { value -> username = value }
        )
    }
    val passwordAutofillNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.Password),
            onFill = { value -> password = value }
        )
    }
    autofillTree += usernameAutofillNode
    autofillTree += passwordAutofillNode

    // 起動時に保存済み認証情報を読み込み、自動ログインを試みる
    LaunchedEffect(Unit) {
        if (!didAutoLogin) {
            onDidAutoLogin(true)
            val stored = CredentialStore.load(context)
            if (stored != null) {
                username = stored.first
                password = stored.second
                performLogin(
                    context = context,
                    username = stored.first,
                    password = stored.second,
                    savePassword = true,
                    onProgress = { isSubmitting = it },
                    onError = { errorText = it },
                    onRequireWebViewLogin = onRequireWebViewLogin
                )
            }
        }
    }

    // TOTP debug: 1秒ごとにコードとカウントダウンを更新
    if (showDebugTotp) {
        LaunchedEffect(showDebugTotp) {
            while (true) {
                val secret = CredentialStore.loadTotpSecret(context)
                if (secret != null) {
                    debugTotpSecret = secret
                    debugTotpCode = TOTPGenerator.generate(secret)
                    totpSecondsRemaining = 30 - ((System.currentTimeMillis() / 1000) % 30).toInt()
                }
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 60.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "KULMS+",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(40.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it; errorText = null },
            label = { Text(stringResource(R.string.label_username)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                autoCorrectEnabled = false
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    usernameAutofillNode.boundingBox = coords.boundsInWindow()
                }
                .onFocusChanged { state ->
                    autofill?.run {
                        if (state.isFocused) requestAutofillForNode(usernameAutofillNode)
                        else cancelAutofillForNode(usernameAutofillNode)
                    }
                },
            enabled = !isSubmitting
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorText = null },
            label = { Text(stringResource(R.string.label_password)) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false
            ),
            keyboardActions = KeyboardActions(onDone = {
                keyboard?.hide()
                if (!isSubmitting && username.isNotBlank() && password.isNotBlank()) {
                    scope.launch {
                        performLogin(
                            context, username, password, savePassword,
                            onProgress = { isSubmitting = it },
                            onError = { errorText = it },
                            onRequireWebViewLogin = onRequireWebViewLogin
                        )
                    }
                }
            }),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) stringResource(R.string.hide_password) else stringResource(R.string.show_password)
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    passwordAutofillNode.boundingBox = coords.boundsInWindow()
                }
                .onFocusChanged { state ->
                    autofill?.run {
                        if (state.isFocused) requestAutofillForNode(passwordAutofillNode)
                        else cancelAutofillForNode(passwordAutofillNode)
                    }
                },
            enabled = !isSubmitting
        )
        Spacer(modifier = Modifier.height(12.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = savePassword,
                    onCheckedChange = { savePassword = it },
                    enabled = !isSubmitting
                )
                Text(
                    stringResource(R.string.save_password),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (errorText != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                errorText!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                keyboard?.hide()
                scope.launch {
                    performLogin(
                        context, username, password, savePassword,
                        onProgress = { isSubmitting = it },
                        onError = { errorText = it },
                        onRequireWebViewLogin = onRequireWebViewLogin
                    )
                }
            },
            enabled = !isSubmitting && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.height(0.dp))
                Text("  " + stringResource(R.string.logging_in))
            } else {
                Text(stringResource(R.string.login))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onRequireWebViewLogin, enabled = !isSubmitting) {
            Text(stringResource(R.string.login_browser))
        }
        Text(
            stringResource(R.string.login_browser_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // TOTP settings
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                stringResource(R.string.totp_section_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (hasTotpSecret) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.totp_configured),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = {
                        CredentialStore.clearTotpSecret(context)
                        hasTotpSecret = false
                        totpSecret = ""
                        debugTotpCode = null
                        debugTotpSecret = null
                        showDebugTotp = false
                    }) {
                        Text(
                            stringResource(R.string.totp_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Debug TOTP display
                if (showDebugTotp && debugTotpCode != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                debugTotpCode ?: "",
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "(${totpSecondsRemaining}s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (debugTotpSecret != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            SelectionContainer {
                                Text(
                                    "Secret: ${debugTotpSecret}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                TextButton(onClick = {
                    showDebugTotp = !showDebugTotp
                    if (showDebugTotp) {
                        val secret = CredentialStore.loadTotpSecret(context)
                        if (secret != null) {
                            debugTotpSecret = secret
                            debugTotpCode = TOTPGenerator.generate(secret)
                            totpSecondsRemaining = 30 - ((System.currentTimeMillis() / 1000) % 30).toInt()
                        }
                    }
                }) {
                    Text(stringResource(R.string.totp_show_code))
                }
            } else {
                Text(
                    stringResource(R.string.totp_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = totpSecret,
                        onValueChange = { totpSecret = it },
                        label = { Text(stringResource(R.string.totp_placeholder)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val cleaned = totpSecret
                                .replace(" ", "")
                                .replace("-", "")
                            if (TOTPGenerator.isValidBase32(cleaned)) {
                                CredentialStore.saveTotpSecret(context, cleaned)
                                hasTotpSecret = true
                                totpSecret = ""
                            } else {
                                showTotpInvalidAlert = true
                            }
                        },
                        enabled = totpSecret.trim().isNotEmpty()
                    ) {
                        Text(stringResource(R.string.totp_save))
                    }
                }
                TextButton(onClick = { showQRScanner = true }) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.height(0.dp))
                    Text("  " + stringResource(R.string.totp_scan_qr))
                }
            }
        }
    }

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

    if (showTotpInvalidAlert) {
        AlertDialog(
            onDismissRequest = { showTotpInvalidAlert = false },
            title = { Text(stringResource(R.string.totp_invalid_title)) },
            text = { Text(stringResource(R.string.totp_invalid_message)) },
            confirmButton = {
                TextButton(onClick = { showTotpInvalidAlert = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

private suspend fun performLogin(
    context: Context,
    username: String,
    password: String,
    savePassword: Boolean,
    onProgress: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    onRequireWebViewLogin: () -> Unit
) {
    onProgress(true)
    onError(null)

    when (val result = WebViewManager.loginWithCredentials(username.trim(), password)) {
        is LoginResult.Success -> {
            if (savePassword) {
                CredentialStore.save(context, username.trim(), password)
            }
            WebViewManager.setLoggedIn(true)
            onProgress(false)
        }
        is LoginResult.OtpRequired -> {
            if (savePassword) {
                CredentialStore.save(context, username.trim(), password)
            }
            onProgress(false)
            onRequireWebViewLogin()
        }
        is LoginResult.Failed -> {
            onError(result.message)
            onProgress(false)
        }
    }
}
