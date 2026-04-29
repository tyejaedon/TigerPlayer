package com.example.tigerplayer.ui.library

import android.os.Build
import androidx.annotation.RequiresExtension
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect

@RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 15)
@Composable
fun NavidromeLoginScreen(
    viewModel: PlayerViewModel,
    onLoginSuccess: () -> Unit,
    onBackClick: () -> Unit
) {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // --- 1. Top Bar ---
        Box(modifier = Modifier.fillMaxSize()) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.padding(16.dp).statusBarsPadding()
            ) {
                Icon(WitcherIcons.Back, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
        }

        // --- 2. The Login "Terminal" Card ---
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .glassEffect(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f), MaterialTheme.shapes.extraLarge)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = WitcherIcons.Cloud, // Use a cloud or server icon
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )

            Text(
                text = "LINK ARCHIVE",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // Server URL Input
            LoginField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = "Server URL (http://...)",
                placeholder = "192.168.1.100:4533"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Username Input
            LoginField(
                value = username,
                onValueChange = { username = it },
                label = "Username"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password Input (Masked)
            LoginField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                isPassword = true
            )

            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- THE RITUAL BUTTON (Executes the MD5 Ping) ---
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null

                    scope.launch {
                        viewModel.connectToNavidrome(serverUrl, username, password)
                            .onSuccess {
                                // The library is already refreshing in the background!
                                onLoginSuccess()
                            }
                            .onFailure { error ->
                                errorMessage = error.message ?: "Authentication failed"
                            }
                        isLoading = false
                    }
                },
                enabled = !isLoading && serverUrl.isNotBlank() && username.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .bounceClick { },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = CircleShape
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Text("INITIATE SYNC", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    isPassword: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        singleLine = true
    )
}
