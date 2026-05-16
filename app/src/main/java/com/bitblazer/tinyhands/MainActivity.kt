package com.bitblazer.tinyhands

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bitblazer.tinyhands.ui.theme.TinyHandsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TinyHandsTheme {
                MainScreen()
            }
        }
    }
}

// ── Screen state ──────────────────────────────────────────────────────────────

private data class AppState(
    val permissionGranted: Boolean,
    val serviceRunning: Boolean,
    val locked: Boolean,
)

// ── Root composable ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── State ─────────────────────────────────────────────────────────────────

    var appState by remember {
        mutableStateOf(
            AppState(
                permissionGranted = isOverlayPermissionGranted(context),
                serviceRunning = isServiceRunning(context, OverlayService::class.java),
                locked = OverlayService.isLocked,
            )
        )
    }

    fun refreshState() {
        appState = AppState(
            permissionGranted = isOverlayPermissionGranted(context),
            serviceRunning = isServiceRunning(context, OverlayService::class.java),
            locked = OverlayService.isLocked,
        )
    }

    // ── Permission launcher ───────────────────────────────────────────────────

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshState() }

    // ── Lifecycle observer — re-check on resume ───────────────────────────────

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Broadcast receiver — real-time sync from service ──────────────────────

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) { refreshState() }
        }
        val filter = IntentFilter(Actions.STATE_CHANGED)
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    // ── Scroll behavior ───────────────────────────────────────────────────────

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // ── UI ────────────────────────────────────────────────────────────────────

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Touch Blocker",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Protect your screen during video calls",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission warning (shown only when needed)
            if (!appState.permissionGranted) {
                PermissionCard(
                    onGrantClick = {
                        permissionLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                "package:${context.packageName}".toUri()
                            )
                        )
                    }
                )
            }

            // Status card
            AnimatedContent(
                targetState = appState,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                },
                label = "StatusCard"
            ) { state ->
                StatusCard(state)
            }

            // Main toggle button
            ServiceToggleButton(
                state = appState,
                onEnable = {
                    context.startForegroundService(
                        Intent(context, OverlayService::class.java)
                    )
                    refreshState()
                },
                onDisable = {
                    context.stopService(Intent(context, OverlayService::class.java))
                    refreshState()
                }
            )

            Spacer(Modifier.height(8.dp))

            // Instructions
            InstructionsCard()

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Permission warning card ───────────────────────────────────────────────────

@Composable
private fun PermissionCard(onGrantClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Permission Required",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Draw over other apps permission is required",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        TextButton(
            onClick = onGrantClick,
            modifier = Modifier
                .align(Alignment.End)
                .padding(bottom = 8.dp, end = 8.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Text("Grant Permission", fontWeight = FontWeight.Bold)
        }
    }
}

// ── Status card ───────────────────────────────────────────────────────────────

@Composable
private fun StatusCard(state: AppState) {
    val (containerColor, icon, label, description) = when {
        !state.serviceRunning -> Quad(
            MaterialTheme.colorScheme.surfaceVariant,
            Icons.Default.LockOpen,
            "Overlay OFF",
            "Tap below to activate"
        )
        else -> Quad(
            MaterialTheme.colorScheme.errorContainer,
            Icons.Default.Lock,
            "LOCKED",
            "All touches blocked — triple-tap to deactivate"
        )
    }

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Service toggle button ─────────────────────────────────────────────────────

@Composable
private fun ServiceToggleButton(
    state: AppState,
    onEnable: () -> Unit,
    onDisable: () -> Unit
) {
    if (!state.serviceRunning) {
        Button(
            onClick = onEnable,
            enabled = state.permissionGranted,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Enable Protection", style = MaterialTheme.typography.titleSmall)
        }
    } else {
        OutlinedButton(
            onClick = onDisable,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Disable Protection", style = MaterialTheme.typography.titleSmall)
        }
    }
}

// ── Instructions card ─────────────────────────────────────────────────────────

@Composable
private fun InstructionsCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "How to use",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            InstructionRow("1.", "Tap \"Enable Protection\" below (or use the Quick Settings tile).")
            InstructionRow("2.", "Overlay activates instantly — amber border confirms it's ON.")
            InstructionRow("3.", "Hand the phone to the baby. All touches are blocked.")
            InstructionRow("4.", "To take the phone back: triple-tap anywhere on the screen.")
            InstructionRow("5.", "Overlay deactivates — phone returns to normal.")
        }
    }
}

@Composable
private fun InstructionRow(number: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

