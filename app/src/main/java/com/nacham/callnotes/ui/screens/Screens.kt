package com.nacham.callnotes.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nacham.callnotes.data.ProcessStatus
import com.nacham.callnotes.ui.CallNotesViewModel
import com.nacham.callnotes.util.SettingsStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: CallNotesViewModel, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val items by vm.items.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CallNotes") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { pad ->
        if (items.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No recordings yet.\nStart a call, or use the Record Quick Settings tile.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize()) {
                items(items, key = { it.id }) { rec ->
                    ListItem(
                        headlineContent = { Text(rec.contact ?: rec.source) },
                        supportingContent = {
                            Text(SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
                                .format(Date(rec.startedAt)) + " · " + statusLabel(rec.status))
                        },
                        modifier = Modifier.clickable { onOpen(rec.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun statusLabel(s: ProcessStatus) = when (s) {
    ProcessStatus.RAW -> "Not processed"
    ProcessStatus.TRANSCRIBING -> "Transcribing…"
    ProcessStatus.TRANSCRIBED -> "Transcribed"
    ProcessStatus.SUMMARIZING -> "Summarizing…"
    ProcessStatus.DONE -> "Done"
    ProcessStatus.ERROR -> "Error"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: CallNotesViewModel, id: String, onBack: () -> Unit) {
    val items by vm.items.collectAsState()
    val rec = items.firstOrNull { it.id == id }
    val ctx = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(rec?.contact ?: "Recording") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        if (rec == null) { Box(Modifier.padding(pad)) { Text("Deleted") }; return@Scaffold }
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            Row {
                Button(onClick = { vm.process(id) },
                    enabled = rec.status != ProcessStatus.TRANSCRIBING && rec.status != ProcessStatus.SUMMARIZING) {
                    Text(if (rec.status == ProcessStatus.DONE) "Re-process" else "Transcribe & summarize")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { shareText(ctx, vm.exportMarkdown(rec)) },
                    enabled = rec.summary != null || rec.transcript != null) { Text("Export") }
            }
            rec.error?.let { Spacer(Modifier.height(8.dp)); Text("Error: $it", color = MaterialTheme.colorScheme.error) }
            rec.summary?.let { Section("Summary", it) }
            if (rec.actionItems.isNotEmpty()) {
                Spacer(Modifier.height(16.dp)); Text("Action items", style = MaterialTheme.typography.titleMedium)
                rec.actionItems.forEach { Text("• $it", Modifier.padding(top = 4.dp)) }
            }
            rec.notes?.takeIf { it.isNotBlank() }?.let { Section("Notes", it) }
            rec.transcript?.let { Section("Transcript", it) }
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(body, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
}

private fun shareText(ctx: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(i, "Export to…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var provider by rememberSaveable { mutableStateOf(SettingsStore.provider(ctx)) }
    var apiKey by rememberSaveable { mutableStateOf(SettingsStore.apiKey(ctx)) }
    var announce by rememberSaveable { mutableStateOf(SettingsStore.announceRecording(ctx)) }
    var cellularMic by rememberSaveable { mutableStateOf(SettingsStore.recordCellularMic(ctx)) }
    var auto by rememberSaveable { mutableStateOf(SettingsStore.autoProcess(ctx)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Transcription provider", style = MaterialTheme.typography.titleMedium)
            Row {
                FilterChip(selected = provider == "GROQ", onClick = {
                    provider = "GROQ"; SettingsStore.setProvider(ctx, provider)
                }, label = { Text("Groq") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = provider == "OPENAI", onClick = {
                    provider = "OPENAI"; SettingsStore.setProvider(ctx, provider)
                }, label = { Text("OpenAI") })
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; SettingsStore.setApiKey(ctx, it) },
                label = { Text("API key") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth()
            )
            SwitchRow("Announce recording out loud at start", announce) {
                announce = it; SettingsStore.setAnnounceRecording(ctx, it)
            }
            SwitchRow("Record my side of normal phone calls (mic)", cellularMic) {
                cellularMic = it; SettingsStore.setRecordCellularMic(ctx, it)
            }
            SwitchRow("Auto transcribe + summarize after each call", auto) {
                auto = it; SettingsStore.setAutoProcess(ctx, it)
            }
            Spacer(Modifier.height(24.dp))
            Text("Tip: for WhatsApp/Telegram, use speakerphone so the other person is captured. " +
                "For normal calls, turn on Samsung's built-in call recording and import those files.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
