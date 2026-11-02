package com.lashox.huewidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

open class ConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setContent {
            MaterialTheme(colorScheme = HueColors) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ConfigScreen(this) { save(widgetId) }
                }
            }
        }
    }

    private fun save(widgetId: Int) {
        val mgr = AppWidgetManager.getInstance(this)
        for (cls in listOf(LightWidget::class.java, CoverWidget::class.java, TvWidget::class.java,
            ThermoWidget::class.java, PcWidget::class.java, DashWidget::class.java)) {
            val ids = mgr.getAppWidgetIds(ComponentName(this, cls))
            sendBroadcast(
                Intent(this, cls)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        }
        finish()
    }
}

internal val HueColors = darkColorScheme(
    primary = Color(0xFFFFC107),
    onPrimary = Color(0xFF1A1400),
    background = Color(0xFF121316),
    surface = Color(0xFF121316),
    surfaceVariant = Color(0xFF1E2024),
    onSurface = Color(0xFFE6E6E9),
    onSurfaceVariant = Color(0xFF9A9CA3),
    outline = Color(0xFF3A3C42)
)

@androidx.compose.runtime.Composable
private fun Section(title: String, content: @androidx.compose.runtime.Composable () -> Unit) {
    Text(
        title.uppercase(), color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
    )
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) { Column(Modifier.padding(16.dp)) { content() } }
}

@androidx.compose.runtime.Composable
private fun Field(
    value: String, label: String, onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text, secret: Boolean = false, top: Boolean = true
) {
    OutlinedTextField(
        value, onChange,
        Modifier.fillMaxWidth().padding(top = if (top) 12.dp else 0.dp),
        label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun EntityField(
    value: String, label: String, entities: List<Pair<String, String>>, onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val q = value.trim().lowercase()
    val matches = entities.filter {
        q.isEmpty() || it.first.lowercase().contains(q) || it.second.lowercase().contains(q)
    }.take(30)
    ExposedDropdownMenuBox(expanded && matches.isNotEmpty(), { expanded = it }) {
        OutlinedTextField(
            value, { onChange(it); expanded = true },
            Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
            label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(16.dp)
        )
        ExposedDropdownMenu(expanded && matches.isNotEmpty(), { expanded = false }) {
            matches.forEach { (id, name) ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(id)
                            if (name.isNotEmpty()) Text(name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = { onChange(id); expanded = false }
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ConfigScreen(c: Context, onSaved: () -> Unit) {
    var url by remember { mutableStateOf(Ha.url(c)) }
    var token by remember { mutableStateOf(Ha.token(c)) }
    var entity by remember { mutableStateOf(Ha.entity(c)) }
    var cover by remember { mutableStateOf(Ha.coverEntity(c)) }
    var tv by remember { mutableStateOf(Ha.tvEntity(c)) }
    var led by remember { mutableStateOf(Ha.ledEntity(c)) }
    var pc by remember { mutableStateOf(Ha.pcEntity(c)) }
    var pcOff by remember { mutableStateOf(Ha.pcOffEntity(c)) }
    var pcState by remember { mutableStateOf(Ha.pcStateEntity(c)) }
    var thermo by remember { mutableStateOf(Ha.thermoEntity(c)) }
    var hum by remember { mutableStateOf(Ha.humEntity(c)) }
    var entities by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    LaunchedEffect(url, token) {
        delay(700)
        if (url.isNotBlank() && token.isNotBlank()) {
            entities = withContext(Dispatchers.IO) { Ha.fetchEntities(url, token) }
        }
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("Jonathan HA Widget", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text("Contrôle tes appareils via Home Assistant", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Section("Home Assistant") {
            Field(url, "URL (ex: http://192.168.1.10:8123)", { url = it }, KeyboardType.Uri, top = false)
            Field(token, "Token longue durée", { token = it }, secret = true)
        }
        Section("Lumière") {
            EntityField(entity, "entity_id (ex: light.salon)", entities.filter { it.first.startsWith("light.") }) { entity = it }
        }
        Section("Volet") {
            EntityField(cover, "entity_id (ex: cover.salon)", entities.filter { it.first.startsWith("cover.") }) { cover = it }
        }
        Section("Télé") {
            val plugs = entities.filter { it.first.startsWith("switch.") || it.first.startsWith("light.") }
            EntityField(tv, "Prise TV (ex: switch.tv)", plugs) { tv = it }
            Spacer(Modifier.height(12.dp))
            EntityField(led, "Prise LED (ex: switch.led_tv)", plugs) { led = it }
        }
        Section("Thermomètre") {
            val sensors = entities.filter { it.first.startsWith("sensor.") }
            EntityField(thermo, "Température (ex: sensor.chambre_temperature)", sensors) { thermo = it }
            Spacer(Modifier.height(12.dp))
            EntityField(hum, "Humidité (optionnel)", sensors) { hum = it }
        }
        Section("PC") {
            val actions = entities.filter {
                it.first.substringBefore('.') in setOf("switch", "button", "script", "scene", "input_boolean", "input_button", "light")
            }
            EntityField(pc, "Entit\u00e9 pour allumer (ex: button.pc_on)", actions) { pc = it }
            Spacer(Modifier.height(12.dp))
            EntityField(pcOff, "Entit\u00e9 pour \u00e9teindre (ex: button.pc_off)", actions) { pcOff = it }
            Spacer(Modifier.height(12.dp))
            EntityField(
                pcState, "Capteur d\u2019\u00e9tat du PC (ex: sensor.pc_cpu, indisponible = \u00e9teint)",
                entities.filter { it.first.substringBefore('.') in setOf("sensor", "binary_sensor", "switch", "device_tracker", "input_boolean", "light") }
            ) { pcState = it }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { Ha.save(c, url, token, entity); Ha.saveCover(c, cover); Ha.saveTv(c, tv, led); Ha.saveMisc(c, pc, pcOff, pcState, thermo, hum); onSaved() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text("Enregistrer", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}
