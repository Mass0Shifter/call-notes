package com.nacham.callnotes.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nacham.callnotes.ui.screens.DetailScreen
import com.nacham.callnotes.ui.screens.HomeScreen
import com.nacham.callnotes.ui.screens.SettingsScreen

class MainActivity : ComponentActivity() {

    private val vm: CallNotesViewModel by viewModels()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result handled reactively; onboarding can re-check */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestCorePermissions()
        handleShare(intent)
        setContent { App(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Accept audio shared from another app → import as a recording to transcribe. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("audio/") == true) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return
            // TODO(handoff): copy the uri content into AudioStore.dir(), create a RAW Recording row,
            // then route to Detail. See HANDOFF §4.3 / §5.
        }
    }

    private fun requestCorePermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
            perms += Manifest.permission.READ_MEDIA_AUDIO
        }
        permLauncher.launch(perms.toTypedArray())
    }
}

@Composable
private fun App(vm: CallNotesViewModel) {
    MaterialTheme {
        var route by remember { mutableStateOf<Route>(Route.Home) }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val r = route) {
                is Route.Home -> HomeScreen(
                    vm = vm,
                    onOpen = { route = Route.Detail(it) },
                    onSettings = { route = Route.Settings }
                )
                is Route.Detail -> DetailScreen(
                    vm = vm, id = r.id, onBack = { route = Route.Home }
                )
                is Route.Settings -> SettingsScreen(onBack = { route = Route.Home })
            }
        }
    }
}

private sealed interface Route {
    data object Home : Route
    data object Settings : Route
    data class Detail(val id: String) : Route
}
