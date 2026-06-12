package com.abhikjain360.abnormalarm.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.abhikjain360.abnormalarm.ui.theme.AbnormalarmTheme
import java.time.ZoneId
import java.util.Locale

class HomeClockWidgetConfigActivity : ComponentActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            AbnormalarmTheme {
                WidgetConfigScreen(
                    appWidgetId = appWidgetId,
                    onCancel = { finish() },
                    onSaved = {
                        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        setResult(Activity.RESULT_OK, result)
                        finish()
                    },
                )
            }
        }
    }
}

private data class ZoneChoice(val id: String?, val title: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigScreen(appWidgetId: Int, onCancel: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val current = remember(appWidgetId) { HomeClockWidgetPrefs.load(context, appWidgetId) }
    var zoneOne by remember { mutableStateOf(current.zoneOneId) }
    var zoneOneLabel by remember { mutableStateOf(current.zoneOneLabel) }
    var zoneTwo by remember { mutableStateOf(current.zoneTwoId) }
    var zoneTwoLabel by remember { mutableStateOf(current.zoneTwoLabel) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clock widget") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Cancel") }
                },
                actions = {
                    IconButton(
                        onClick = {
                            HomeClockWidgetPrefs.save(
                                context,
                                appWidgetId,
                                HomeClockWidgetConfig(
                                    zoneOneId = zoneOne,
                                    zoneOneLabel = zoneOne?.let {
                                        zoneOneLabel.trim().ifBlank { defaultZoneLabel(it) }
                                    }.orEmpty(),
                                    zoneTwoId = zoneTwo,
                                    zoneTwoLabel = zoneTwo?.let {
                                        zoneTwoLabel.trim().ifBlank { defaultZoneLabel(it) }
                                    }.orEmpty(),
                                ),
                            )
                            HomeClockWidget.update(context, appWidgetId)
                            onSaved()
                        },
                    ) { Icon(Icons.Default.Check, contentDescription = "Save") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "Choose up to two secondary clocks.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            ZoneEditor(
                title = "First time zone",
                zoneId = zoneOne,
                label = zoneOneLabel,
                onZoneChange = {
                    zoneOne = it
                    zoneOneLabel = it?.let(::defaultZoneLabel).orEmpty()
                },
                onLabelChange = { zoneOneLabel = it },
            )
            ZoneEditor(
                title = "Second time zone",
                zoneId = zoneTwo,
                label = zoneTwoLabel,
                onZoneChange = {
                    zoneTwo = it
                    zoneTwoLabel = it?.let(::defaultZoneLabel).orEmpty()
                },
                onLabelChange = { zoneTwoLabel = it },
            )
        }
    }
}

@Composable
private fun ZoneEditor(
    title: String,
    zoneId: String?,
    label: String,
    onZoneChange: (String?) -> Unit,
    onLabelChange: (String) -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Button(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth()) {
            Text(zoneId?.let { "$it (${defaultZoneLabel(it)})" } ?: "None")
        }
        if (zoneId != null) {
            OutlinedTextField(
                value = label,
                onValueChange = onLabelChange,
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (choosing) {
        ZoneChoiceDialog(
            selected = zoneId,
            onSelect = {
                onZoneChange(it)
                choosing = false
            },
            onDismiss = { choosing = false },
        )
    }
}

@Composable
private fun ZoneChoiceDialog(selected: String?, onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    val choices = remember {
        listOf(ZoneChoice(null, "None")) +
            ZoneId.getAvailableZoneIds()
                .sortedBy { defaultZoneLabel(it) }
                .map { ZoneChoice(it, "${defaultZoneLabel(it)} - $it") }
    }
    var query by remember { mutableStateOf("") }
    val filteredChoices = remember(query, choices) {
        val needle = query.trim().lowercase(Locale.getDefault())
        if (needle.isBlank()) {
            choices
        } else {
            choices.filter { choice ->
                choice.title.lowercase(Locale.getDefault()).contains(needle) ||
                    choice.id.orEmpty().lowercase(Locale.getDefault()).contains(needle)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text("Time zone") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(filteredChoices, key = { it.id ?: "none" }) { choice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(choice.id) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = choice.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (choice.id == selected) Text("Selected", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
