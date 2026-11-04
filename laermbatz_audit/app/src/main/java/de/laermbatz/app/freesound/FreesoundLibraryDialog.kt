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
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FreesoundLibraryDialog(
    items: List<FreesoundLibraryItem>,
    selectedTrackLabel: String,
    canLoad: Boolean,
    categoryFilter: FreesoundLoopCategory?,
    onFilterChange: (FreesoundLoopCategory?) -> Unit,
    onChangeCategory: (FreesoundLibraryItem, FreesoundLoopCategory) -> Unit,
    onLoadIntoTrack: (FreesoundLibraryItem) -> Unit,
    onDelete: (FreesoundLibraryItem) -> Unit,
    onDismiss: () -> Unit
) {
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<FreesoundLibraryItem?>(null) }
    val visibleItems = items.filter { categoryFilter == null || it.category == categoryFilter }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lokale Loop-Bibliothek (${items.size})") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Nur lokal gespeicherte Freesound-WAVs. Kategorie und Herkunft bleiben auf diesem Gerät erhalten.",
                    style = MaterialTheme.typography.bodySmall
                )
                Column {
                    OutlinedButton(onClick = { filterMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Filter: ${categoryFilter?.label ?: "Alle Kategorien"}")
                    }
                    DropdownMenu(
                        expanded = filterMenuExpanded,
                        onDismissRequest = { filterMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Alle Kategorien") },
                            onClick = {
                                onFilterChange(null)
                                filterMenuExpanded = false
                            }
                        )
                        FreesoundLoopCategory.values().forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.label) },
                                onClick = {
                                    onFilterChange(category)
                                    filterMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                if (visibleItems.isEmpty()) {
                    Text(
                        if (items.isEmpty()) "Noch keine Freesound-Loops heruntergeladen." else "In dieser Kategorie sind keine Loops.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                visibleItems.forEach { item ->
                    LibraryItemCard(
                        item = item,
                        selectedTrackLabel = selectedTrackLabel,
                        canLoad = canLoad,
                        onChangeCategory = { category -> onChangeCategory(item, category) },
                        onLoadIntoTrack = { onLoadIntoTrack(item) },
                        onDelete = { deleteCandidate = item }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } }
    )

    deleteCandidate?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Loop lokal löschen?") },
            text = {
                Text("${item.title} sowie die zugehörigen lokalen Lizenzmetadaten werden gelöscht. Ein bereits geladener Mixer-Track bleibt davon unberührt.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(item)
                    deleteCandidate = null
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun LibraryItemCard(
    item: FreesoundLibraryItem,
    selectedTrackLabel: String,
    canLoad: Boolean,
    onChangeCategory: (FreesoundLoopCategory) -> Unit,
    onLoadIntoTrack: () -> Unit,
    onDelete: () -> Unit
) {
    var categoryMenuExpanded by remember(item.file) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(item.title, style = MaterialTheme.typography.titleSmall)
            Text(
                "${item.username.ifBlank { "Uploader unbekannt" }} · ${item.license.ifBlank { "Lizenz unbekannt" }}",
                style = MaterialTheme.typography.bodySmall
            )
            Text("Kategorie: ${item.category.label}", style = MaterialTheme.typography.bodySmall)
            if (item.originalUrl.isNotBlank()) {
                Text(item.originalUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            val licenseVerified = FreesoundLicensePolicy.isAllowed(
                item.license,
                "wav",
                allowAttribution = true
            )
            if (!licenseVerified) {
                Text(
                    "Lizenz-/Herkunftsnachweis fehlt oder ist unbekannt — dieser Loop kann nicht in den Mixer geladen werden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { categoryMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Kategorie ändern") }
                    DropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        FreesoundLoopCategory.values().forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.label) },
                                onClick = {
                                    onChangeCategory(category)
                                    categoryMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Button(onClick = onLoadIntoTrack, enabled = canLoad && licenseVerified, modifier = Modifier.fillMaxWidth()) {
                        Text("Laden → $selectedTrackLabel")
                    }
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Löschen") }
                }
            }
        }
    }
}
