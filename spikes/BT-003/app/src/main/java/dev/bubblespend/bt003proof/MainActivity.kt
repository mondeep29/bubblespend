package dev.bubblespend.bt003proof

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private const val LOG_TAG = "BT003Proof"

class MainActivity : ComponentActivity() {
    private var showAnalytics by mutableStateOf(false)
    private var returningFromPermission by mutableStateOf(false)
    private val overlaySettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshOverlayState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showAnalytics = intent.getBooleanExtra("analytics", false)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    if (showAnalytics) AnalyticsScreen(onBack = { showAnalytics = false })
                    else ControlScreen(
                        onEnable = ::beginEnable,
                        onDisable = ::disableOverlay,
                        onAnalytics = { showAnalytics = true },
                        overlayGranted = rememberOverlayPermission()
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (returningFromPermission) refreshOverlayState()
    }

    private fun beginEnable() {
        if (Settings.canDrawOverlays(this)) {
            startOverlay()
        } else {
            returningFromPermission = true
            Log.i(LOG_TAG, "overlay permission explanation shown")
            overlaySettings.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
    }

    private fun refreshOverlayState() {
        returningFromPermission = false
        if (Settings.canDrawOverlays(this)) startOverlay()
        else {
            stopService(Intent(this, OverlayService::class.java))
            Log.i(LOG_TAG, "overlay permission not granted")
        }
    }

    private fun startOverlay() {
        Log.i(LOG_TAG, "overlay enabled")
        startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_SHOW))
    }

    private fun disableOverlay() {
        stopService(Intent(this, OverlayService::class.java))
        Log.i(LOG_TAG, "overlay disabled")
    }
}

@Composable
private fun rememberOverlayPermission(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    var granted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    androidx.compose.runtime.LaunchedEffect(context) {
        while (true) {
            granted = Settings.canDrawOverlays(context)
            kotlinx.coroutines.delay(500)
        }
    }
    return granted
}

@Composable
private fun ControlScreen(
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onAnalytics: () -> Unit,
    overlayGranted: Boolean
) {
    var explain by remember { mutableStateOf(false) }
    var inAppEntry by rememberSaveable { mutableStateOf(false) }
    var inAppSaved by rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    if (inAppEntry) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("In-app synthetic entry", style = MaterialTheme.typography.headlineSmall)
            Text("This path remains available with overlay permission denied or revoked.")
            Button(enabled = !inAppSaved, onClick = {
                inAppSaved = OverlayService.markSavedOnce(context, "in-app")
            }) { Text(if (inAppSaved) "Saved once" else "Save synthetic entry") }
            TextButton(onClick = { inAppEntry = false }) { Text("Back") }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("BT-003 platform proof", style = MaterialTheme.typography.headlineSmall) }
        item { Text("This disposable spike uses synthetic values only. In-app controls remain available when overlay access is off.") }
        item { Button(onClick = { explain = true }) { Text("Enable floating shortcut") } }
        item { OutlinedButton(onClick = onDisable) { Text("Disable and remove shortcut") } }
        item { Text(if (overlayGranted) "Overlay permission: granted" else "Overlay permission: not granted") }
        item { Button(onClick = { inAppEntry = true }) { Text("Open in-app synthetic entry") } }
        item { OutlinedButton(onClick = onAnalytics) { Text("Open accessible chart proof") } }
        item { Text("Save counter: ${OverlayService.savedCount(androidx.compose.ui.platform.LocalContext.current)}") }
    }
    if (explain) AlertDialog(
        onDismissRequest = { explain = false },
        title = { Text("Allow a floating shortcut?") },
        text = { Text("Android will open its special overlay access screen. You can deny access and continue using the in-app controls.") },
        confirmButton = { TextButton(onClick = { explain = false; onEnable() }) { Text("Continue to Android settings") } },
        dismissButton = { TextButton(onClick = { explain = false }) { Text("Not now") } }
    )
}

private data class Fixture(val id: String, val category: String, val minor: Int, val day: String)
private val fixtures = listOf(
    Fixture("synthetic-001", "Food", 1250, "Mon"),
    Fixture("synthetic-002", "Travel", 800, "Tue"),
    Fixture("synthetic-003", "Food", 500, "Thu"),
    Fixture("synthetic-004", "Home", 2100, "Fri")
)

@Composable
private fun AnalyticsScreen(onBack: () -> Unit) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedDay by remember { mutableStateOf<String?>(null) }
    var showEmpty by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Synthetic analytics", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { showEmpty = !showEmpty; selectedCategory = null; selectedDay = null }) {
                Text(if (showEmpty) "Show populated fixture" else "Show empty state")
            }
        }
        item { Text("Period: 2026-01-01 inclusive to 2026-02-01 exclusive · synthetic fixture") }
        if (showEmpty) {
            item { Text("No synthetic entries for this period") }
        } else {
        item { Text("Category share (exact synthetic minor units)") }
        items(listOf("Food" to 1750, "Travel" to 800, "Home" to 2100)) { (category, amount) ->
            val share = amount * 100f / 4650f
            Row(Modifier.fillMaxWidth().clickable { selectedCategory = category }
                .semantics { contentDescription = "$category category share, $amount synthetic minor units, ${"%.1f".format(share)} percent. Open matching synthetic entries." }
                .padding(8.dp)) {
                Canvas(Modifier.size((amount / 50f).coerceIn(28f, 50f).dp)) {
                    drawCircle(if (category == "Food") Color(0xFF008577) else if (category == "Travel") Color(0xFF6750A4) else Color(0xFFB3261E))
                }
                Text("  $category: $amount (${"%.1f".format(share)}%)", modifier = Modifier.padding(start = 8.dp))
            }
        }
        item { Text("Trend (zero baseline; blank buckets are zero)") }
        item {
            Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Mon" to 1250, "Tue" to 800, "Wed" to 0, "Thu" to 500, "Fri" to 2100, "Sat" to 0, "Sun" to 0).forEach { (day, amount) ->
                    Column(Modifier.weight(1f).fillMaxHeight().clickable { selectedDay = day }.semantics {
                        contentDescription = "$day trend bucket, $amount synthetic minor units; baseline zero; open matching entries"
                    }, verticalArrangement = Arrangement.Bottom) {
                    Canvas(Modifier.fillMaxWidth().height((amount / 25f).coerceAtLeast(0f).dp)) { drawRect(Color(0xFF386A20)) }
                        Text(day)
                    }
                }
            }
        }
        item { Text("Equivalent ordered list · amounts in synthetic minor units") }
        item { Text("Trend values: Mon 1250, Tue 800, Wed 0, Thu 500, Fri 2100, Sat 0, Sun 0") }
        items(fixtures) { fixture ->
            Text("${fixture.day} · ${fixture.category} · ${fixture.minor} · ${fixture.id}",
                Modifier.fillMaxWidth().clickable { selectedCategory = fixture.category }
                    .semantics { contentDescription = "${fixture.category}, ${fixture.minor} synthetic minor units, ${fixture.id}; show matching entries" }
                    .padding(10.dp))
        }
        item { Text("Comparison bars · zero baseline: Food 1750, Travel 800, Home 2100") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Food" to 1750, "Travel" to 800, "Home" to 2100).forEach { (name, value) ->
                    Box(Modifier.weight(1f).height(52.dp).clickable { selectedCategory = name }
                        .semantics { contentDescription = "$name comparison bar, $value synthetic minor units, baseline zero; open matching entries" }) {
                        Canvas(Modifier.fillMaxSize()) { drawRect(Color(0xFF386A20), size = androidx.compose.ui.geometry.Size(size.width, size.height * value / 2200f)) }
                    }
                }
            }
        }
        if (selectedCategory != null || selectedDay != null) {
            val matches = fixtures.filter { (selectedCategory == null || it.category == selectedCategory) && (selectedDay == null || it.day == selectedDay) }
            item { Text("Drill-down: ${matches.size} matching synthetic entries") }
            items(matches) { Text("${it.id} · ${it.day} · ${it.category} · ${it.minor}") }
            item { TextButton(onClick = { selectedCategory = null; selectedDay = null }) { Text("Clear selection") } }
        }
        }
    }
}
