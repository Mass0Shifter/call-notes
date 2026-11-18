package com.nacham.callnotes.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
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
fun HomeScreen(
    vm: CallNotesViewModel,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    onSetup: () -> Unit
) {
    val items by vm.items.collectAsState()
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CallNotes") },
                actions = {
                    IconButton(onClick = {
                        vm.importSamsungRecordings { n ->
                            Toast.makeText(
                                ctx,
                                if (n > 0) "Imported $n call recording(s)"
                                else "No new phone-call recordings found",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }) { Icon(Icons.Default.Download, contentDescription = "Import call recordings") }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Setup") }, onClick = { menu = false; onSetup() })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onSettings() })
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as? android.app.Activity
    // Bumping this re-reads all the live status checks (call it after returning from system settings).
    var tick by remember { mutableIntStateOf(0) }

    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { tick++ }

    val mic = remember(tick) { com.nacham.callnotes.util.Setup.hasMic(ctx) }
    val notif = remember(tick) { com.nacham.callnotes.util.Setup.hasNotifications(ctx) }
    val apiKey = remember(tick) { com.nacham.callnotes.util.Setup.hasApiKey(ctx) }
    val access = remember(tick) { com.nacham.callnotes.util.Setup.isAccessibilityOn(ctx) }
    val core = mic && apiKey

    Scaffold(topBar = { TopAppBar(title = { Text("Set up CallNotes") }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Finish these to start recording and summarizing calls.",
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))

            StepRow("Microphone access", mic, if (mic) null else "Grant") {
                permLauncher.launch(buildList {
                    add(android.Manifest.permission.RECORD_AUDIO)
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        add(android.Manifest.permission.POST_NOTIFICATIONS)
                        add(android.Manifest.permission.READ_MEDIA_AUDIO)
                    }
                }.toTypedArray())
            }
            StepRow("Notifications", notif, if (notif) null else "Grant") {
                permLauncher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS))
            }
            StepRow("Transcription API key", apiKey, if (apiKey) null else "Open Settings", onOpenSettings)
            StepRow("VoIP call detection (WhatsApp/Telegram)", access,
                if (access) null else "Enable") {
                com.nacham.callnotes.util.Setup.openAccessibilitySettings(ctx)
            }
            StepRow("Add the \"Record call\" Quick Settings tile", false, "Add tile") {
                activity?.let { com.nacham.callnotes.util.Setup.requestAddTile(it) }
            }

            Spacer(Modifier.height(16.dp))
            Text("Tip: for WhatsApp/Telegram, put the call on speakerphone so the other person is " +
                "captured. For normal phone calls, turn on Samsung's built-in call recording, then " +
                "use the download icon on the home screen to import those recordings.",
                style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(24.dp))
            Row {
                OutlinedButton(onClick = { tick++ }) { Text("Re-check") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onDone, enabled = core) {
                    Text(if (core) "Start using CallNotes" else "Grant mic + API key first")
                }
            }
        }
    }
}

@Composable
private fun StepRow(label: String, done: Boolean, action: String?, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(if (done) "✓" else "○", Modifier.padding(end = 12.dp),
            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}
