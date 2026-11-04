package de.laermbatz.app.freesound

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun FreesoundCredentialsDialog(
    initial: FreesoundCredentials,
    pendingOAuthState: String?,
    busy: Boolean,
    status: String,
    onDismiss: () -> Unit,
    onSave: (FreesoundCredentials) -> Unit,
    onStartOAuth: (FreesoundCredentials) -> Unit,
    onExchangeCode: (FreesoundCredentials, String) -> Unit
) {
    var clientId by remember(initial) { mutableStateOf(initial.clientId) }
    var clientSecret by remember(initial) { mutableStateOf(initial.clientSecretOrApiKey) }
    var redirectUri by remember(initial) { mutableStateOf(initial.registeredRedirectUri) }
    var apiUseConfirmed by remember(initial) { mutableStateOf(initial.apiUseConfirmed) }
    var allowAttribution by remember(initial) { mutableStateOf(initial.allowAttributionLicense) }
    var redirectUrl by remember(initial) { mutableStateOf("") }

    fun currentCredentials() = initial.copy(
        clientId = clientId.trim(),
        clientSecretOrApiKey = clientSecret.trim(),
        registeredRedirectUri = redirectUri.trim(),
        apiUseConfirmed = apiUseConfirmed,
        allowAttributionLicense = allowAttribution
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Freesound-API einrichten") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Beantrage ein eigenes API-Credential pro App bei Freesound. Suche nutzt den persönlichen API-Key; WAV-Downloads brauchen zusätzlich OAuth2.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text("Client-ID") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = clientSecret,
                    onValueChange = { clientSecret = it },
                    label = { Text("Client-Secret / API-Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = redirectUri,
                    onValueChange = { redirectUri = it },
                    label = { Text("Bei Freesound registrierte Redirect-URL") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = apiUseConfirmed, onCheckedChange = { apiUseConfirmed = it }, enabled = !busy)
                    Text(
                        "API-Nutzungsbedingungen und nötige kommerzielle Freigabe für meinen Einsatz sind geklärt.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allowAttribution, onCheckedChange = { allowAttribution = it }, enabled = !busy)
                    Text(
                        "CC BY zusätzlich suchen (Credits werden gespeichert; nötige Namensnennung muss auch beim Stream/der Veröffentlichung erfolgen). CC0 bleibt Standard.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    onClick = { onStartOAuth(currentCredentials()) },
                    enabled = !busy && apiUseConfirmed && clientId.isNotBlank() && clientSecret.isNotBlank() &&
                        redirectUri.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Freesound-OAuth2 im Browser öffnen")
                }
                if (pendingOAuthState != null) {
                    Text(
                        "Nach der Zustimmung die vollständige Weiterleitungs-URL dieser registrierten Redirect-URL " +
                            "mit Code und State hier einfügen. Host und Pfad werden geprüft; der OAuth-Code ist kurzlebig.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = redirectUrl,
                        onValueChange = { redirectUrl = it },
                        label = { Text("Vollständige Redirect-URL") },
                        enabled = !busy,
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onExchangeCode(currentCredentials(), redirectUrl.trim()) },
                        enabled = !busy && redirectUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (busy) "Verarbeite OAuth …" else "OAuth-Code sicher austauschen")
                    }
                }
                Text(
                    "Zugangsdaten werden mit Android Keystore verschlüsselt gespeichert. Teile oder veröffentliche keine APK mit deinen Zugangsdaten.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (status.isNotBlank()) {
                    Text(status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(currentCredentials()) },
                enabled = !busy
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Schließen") } }
    )
}
