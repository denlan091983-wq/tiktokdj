package de.laermbatz.app.freesound

import de.laermbatz.app.audio.LoopKeyAnalyzer
import kotlin.math.abs
import kotlin.math.roundToInt
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
    liveBpm: Float,
    liveKey: String?,
    categoryFilter: FreesoundLoopCategory?,
    onFilterChange: (FreesoundLoopCategory?) -> Unit,
    onChangeCategory: (FreesoundLibraryItem, FreesoundLoopCategory) -> Unit,
    onLoadIntoTrack: (FreesoundLibraryItem) -> Unit,
    onDelete: (FreesoundLibraryItem) -> Unit,
    onDismiss: () -> Unit
) {
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var genreMenuExpanded by remember { mutableStateOf(false) }
    var keyMenuExpanded by remember { mutableStateOf(false) }
    var genreFilter by remember(items) { mutableStateOf<String?>(null) }
    var keyFilter by remember(items) { mutableStateOf<String?>(null) }
    var deleteCandidate by remember { mutableStateOf<FreesoundLibraryItem?>(null) }
    val genres = items.map { it.genre }.filter(String::isNotBlank).distinct().sorted()
    val keys = items.mapNotNull { it.key }.distinct().sorted()
    val visibleItems = items
        .filter { categoryFilter == null || it.category == categoryFilter }
        .filter { genreFilter == null || it.genre == genreFilter }
        .filter { keyFilter == null || it.key == keyFilter }
        .sortedWith(
            compareBy<FreesoundLibraryItem> { keyFitRank(it, liveKey) }
                .thenBy { it.bpm?.let { source -> abs(source - liveBpm) } ?: Float.MAX_VALUE }
                .thenBy { it.category.label }
                .thenBy { it.genre }
                .thenBy { it.title }
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lokale Loop-Bibliothek (${items.size})") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Nur lokale WAVs. Sortierung bevorzugt Tonart-Treffer und BPM-Nähe zum aktuellen Mix.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Live-Ziel: ${liveBpm.roundToInt()} BPM · ${liveKey ?: "Tonart wird aus Bassline/Synth ermittelt"}. " +
                        "BPM folgt dem Mixer sofort; Bassline/Synth werden beim Laden tonal angeglichen, wenn sicher erkannt.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = { categoryMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Kategorie: ${categoryFilter?.label ?: "Alle"}")
                }
                DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Alle Kategorien") },
                        onClick = { onFilterChange(null); categoryMenuExpanded = false }
                    )
                    FreesoundLoopCategory.entries.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.label) },
                            onClick = { onFilterChange(category); categoryMenuExpanded = false }
                        )
                    }
                }
                OutlinedButton(onClick = { genreMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Genre: ${genreFilter ?: "Alle"}")
                }
                DropdownMenu(expanded = genreMenuExpanded, onDismissRequest = { genreMenuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Alle Genres") }, onClick = { genreFilter = null; genreMenuExpanded = false })
                    genres.forEach { genre ->
                        DropdownMenuItem(text = { Text(genre) }, onClick = { genreFilter = genre; genreMenuExpanded = false })
                    }
                }
                OutlinedButton(onClick = { keyMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Tonart: ${keyFilter ?: "Alle"}")
                }
                DropdownMenu(expanded = keyMenuExpanded, onDismissRequest = { keyMenuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Alle Tonarten") }, onClick = { keyFilter = null; keyMenuExpanded = false })
                    keys.forEach { key ->
                        DropdownMenuItem(text = { Text(key) }, onClick = { keyFilter = key; keyMenuExpanded = false })
                    }
                }
                if (visibleItems.isEmpty()) {
                    Text(
                        if (items.isEmpty()) "Noch keine Loops heruntergeladen oder lokal importiert."
                        else "Für diese Filterkombination sind keine Loops vorhanden.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                visibleItems.forEach { item ->
                    LibraryItemCard(
                        item = item,
                        selectedTrackLabel = selectedTrackLabel,
                        liveBpm = liveBpm,
                        liveKey = liveKey,
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
                Text("${item.title} sowie die zugehörigen lokalen Lizenz- und Analysemetadaten werden gelöscht. Ein bereits geladener Mixer-Track bleibt davon unberührt.")
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
    liveBpm: Float,
    liveKey: String?,
    canLoad: Boolean,
    onChangeCategory: (FreesoundLoopCategory) -> Unit,
    onLoadIntoTrack: () -> Unit,
    onDelete: () -> Unit
) {
    var categoryMenuExpanded by remember(item.file) { mutableStateOf(false) }
    val isLocalPack = SamplePackLicenseCatalog.isLocalPackLicense(item.license)
    val isUserZip = SamplePackLicenseCatalog.isUserSuppliedZipLicense(item.license)
    val canUseLocally = isLocalPack || FreesoundLicensePolicy.isAllowed(item.license, "wav", allowAttribution = true)
    val isTonalCategory = item.category == FreesoundLoopCategory.BASSLINE || item.category == FreesoundLoopCategory.SYNTH
    val shift = if (isTonalCategory && item.key != null && liveKey != null) {
        LoopKeyAnalyzer.semitoneShift(item.key, liveKey)
    } else null

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(item.title, style = MaterialTheme.typography.titleSmall)
            Text(
                if (isLocalPack) "${item.provider} · ${item.sourcePack}"
                else "${item.username.ifBlank { "Uploader unbekannt" }} · ${item.license.ifBlank { "Lizenz unbekannt" }}",
                style = MaterialTheme.typography.bodySmall
            )
            Text("${item.category.label} · ${item.genre}", style = MaterialTheme.typography.bodySmall)
            Text(
                "${item.bpm?.let { "Quelle ${it.roundToInt()} BPM → Live ${liveBpm.roundToInt()} BPM" } ?: "BPM nicht erkannt"} · " +
                    "${item.key ?: "Tonart nicht erkannt"}${if (item.key != null) " (Konfidenz ${(item.keyConfidence * 100).toInt()}%)" else ""}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                when {
                    !isTonalCategory -> "Drum/FX: keine automatische Tonartverschiebung."
                    liveKey == null -> "Live-Tonart noch nicht sicher erkannt; Originaltonhöhe bleibt erhalten."
                    shift == null -> "Modus passt nicht oder Tonart unklar; Originaltonhöhe bleibt erhalten."
                    shift == 0 -> "Tonart passt zum Live-Anker $liveKey."
                    else -> "Wird beim Laden auf $liveKey transponiert (${if (shift > 0) "+" else ""}$shift Halbtöne)."
                },
                style = MaterialTheme.typography.bodySmall
            )
            if (isUserZip) {
                Text(
                    "Nutzer-ZIP: Lizenz nicht unabhängig geprüft. Nur lokal importiert; Rechte vor Stream/Veröffentlichung selbst prüfen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (isLocalPack) {
                Text(
                    SamplePackLicenseCatalog.localLicenseDescription(item.license),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (item.archivePath.isNotBlank()) {
                Text("ZIP-Pfad: ${item.archivePath}", style = MaterialTheme.typography.bodySmall)
            }
            if (item.originalUrl.isNotBlank()) {
                Text(item.originalUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (!canUseLocally) {
                Text(
                    "Lizenz-/Herkunftsnachweis fehlt — dieser Loop kann nicht in den Mixer geladen werden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    OutlinedButton(onClick = { categoryMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Kategorie ändern")
                    }
                    DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                        FreesoundLoopCategory.entries.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.label) },
                                onClick = { onChangeCategory(category); categoryMenuExpanded = false }
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Button(onClick = onLoadIntoTrack, enabled = canLoad && canUseLocally, modifier = Modifier.fillMaxWidth()) {
                        Text("Laden → $selectedTrackLabel")
                    }
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Löschen") }
                }
            }
        }
    }
}

private fun keyFitRank(item: FreesoundLibraryItem, liveKey: String?): Int {
    if (liveKey == null) return 1
    val sourceKey = item.key ?: return 3
    val shift = LoopKeyAnalyzer.semitoneShift(sourceKey, liveKey) ?: return 2
    return if (shift == 0) 0 else 1
}
