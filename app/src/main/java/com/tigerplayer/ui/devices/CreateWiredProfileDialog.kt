package com.tigerplayer.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tigerplayer.ui.theme.TigerCyberCyan

@Composable
fun CreateWiredProfileDialog(
    onConfirm: (name: String, formFactor: String) -> Unit,
    onDismiss: () -> Unit
) {
    var profileName by remember { mutableStateOf("") }
    var selectedFormFactor by remember { mutableStateOf("In-Ear") }
    val formFactors = listOf("In-Ear", "Over-Ear", "Earbud")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "New Wired Headphone Profile",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Create a custom profile for 3.5mm headphones or USB DAC endpoints to save personalized AutoEq curves.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Headphone Name / Model") },
                    placeholder = { Text("e.g. Sennheiser HD 600") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Form Factor",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    formFactors.forEach { form ->
                        FilterChip(
                            selected = selectedFormFactor == form,
                            onClick = { selectedFormFactor = form },
                            label = { Text(form) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TigerCyberCyan.copy(alpha = 0.2f),
                                selectedLabelColor = TigerCyberCyan
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (profileName.isNotBlank()) {
                                onConfirm(profileName.trim(), selectedFormFactor)
                            }
                        },
                        enabled = profileName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TigerCyberCyan,
                            contentColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text("Save Profile", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
