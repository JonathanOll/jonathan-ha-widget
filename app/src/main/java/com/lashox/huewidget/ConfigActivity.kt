package com.lashox.huewidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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

private enum class Screen { Home, Connection, Light, Cover, Tv, Thermo, Pc }

/**
 * Settings: a home screen with one card per device, and one page per device.
 * When opened from "add widget", it jumps straight to the page of that widget's device.
 */
open class ConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val widgetMode = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
        val target: Screen? = if (!widgetMode) null else
            when (AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider?.className) {
                LightWidget::class.java.name -> Screen.Light
                CoverWidget::class.java.name -> Screen.Cover
                TvWidget::class.java.name -> Screen.Tv
                ThermoWidget::class.java.name -> Screen.Thermo
                PcWidget::class.java.name -> Screen.Pc
                else -> null // dashboard: show everything
            }

        setContent {
            MaterialTheme(colorScheme = HueColors) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ConfigApp(this, widgetMode, target, onCommit = { refreshWidgets() }) {
                        if (widgetMode) setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                        finish()
                    }
                }
            }
        }
    }

    private fun refreshWidgets() {
        val mgr = AppWidgetManager.getInstance(this)
        val all = listOf(
            LightWidget::class.java, CoverWidget::class.java, TvWidget::class.java,
            ThermoWidget::class.java, PcWidget::class.java, DashWidget::class.java
        )
        for (cls in all) {
            sendBroadcast(
                Intent(this, cls)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, mgr.getAppWidgetIds(ComponentName(this, cls)))
            )
        }
    }
}

@Composable
private fun ConfigApp(
    c: Context, widgetMode: Boolean, target: Screen?, onCommit: () -> Unit, onFinish: () -> Unit
) {
    var url by remember { mutableStateOf(Ha.url(c)) }
    var token by remember { mutableStateOf(Ha.token(c)) }
    var light by remember { mutableStateOf(Ha.entity(c)) }
    var cover by remember { mutableStateOf(Ha.coverEntity(c)) }
    var tv by remember { mutableStateOf(Ha.tvEntity(c)) }
    var led by remember { mutableStateOf(Ha.ledEntity(c)) }
    var thermo by remember { mutableStateOf(Ha.thermoEntity(c)) }
    var hum by remember { mutableStateOf(Ha.humEntity(c)) }
    var pcOn by remember { mutableStateOf(Ha.pcEntity(c)) }
    var pcOff by remember { mutableStateOf(Ha.pcOffEntity(c)) }
    var pcState by remember { mutableStateOf(Ha.pcStateEntity(c)) }

    var entities by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var checking by remember { mutableStateOf(false) }
    LaunchedEffect(url, token) {
        entities = emptyList()
        if (url.isNotBlank() && token.isNotBlank()) {
            checking = true
            delay(700)
            entities = withContext(Dispatchers.IO) { Ha.fetchEntities(url, token) }
            checking = false
        }
    }
    val connected = entities.isNotEmpty()

    val needConnection = Ha.url(c).isBlank() || Ha.token(c).isBlank()
    var screen by remember { mutableStateOf(if (needConnection) Screen.Connection else target ?: Screen.Home) }

    fun commit() {
        Ha.save(c, url, token, light)
        Ha.saveCover(c, cover)
        Ha.saveTv(c, tv, led)
        Ha.saveMisc(c, pcOn, pcOff, pcState, thermo, hum)
        onCommit()
    }

    /** Save, then go on: next page in widget mode, otherwise back to the list. */
    fun done() {
        commit()
        when {
            widgetMode && target != null && screen == Screen.Connection -> screen = target
            widgetMode && target != null -> onFinish()
            else -> screen = Screen.Home
        }
    }

    BackHandler(enabled = screen != Screen.Home && (!widgetMode || target == null)) { commit(); screen = Screen.Home }

    fun ids(vararg d: String) = entities.filter { it.first.substringBefore('.') in d }

    when (screen) {
        Screen.Home -> Page("Jonathan HA Widget", "Contrôle tes appareils via Home Assistant", null) {
            val status = when {
                url.isBlank() || token.isBlank() -> "À configurer"
                checking -> "Vérification…"
                connected -> "Connecté · ${entities.size} entités"
                else -> "Injoignable"
            }
            NavCard(R.drawable.ic_power, "Home Assistant", status, connected) { screen = Screen.Connection }
            Label("Appareils")
            NavCard(R.drawable.ic_bulb, "Lumière", summary(light), light.isNotBlank()) { screen = Screen.Light }
            NavCard(R.drawable.ic_cover, "Volet", summary(cover), cover.isNotBlank()) { screen = Screen.Cover }
            NavCard(R.drawable.ic_tv, "Télé + LED", summary(tv, led), tv.isNotBlank() || led.isNotBlank()) { screen = Screen.Tv }
            NavCard(R.drawable.ic_thermo, "Thermomètre", summary(thermo, hum), thermo.isNotBlank()) { screen = Screen.Thermo }
            NavCard(R.drawable.ic_pc, "PC", summary(pcOn, pcOff), pcOn.isNotBlank() || pcOff.isNotBlank()) { screen = Screen.Pc }
            if (widgetMode) {
                Spacer(Modifier.height(24.dp))
                SaveButton("Terminer") { commit(); onFinish() }
            }
        }

        Screen.Connection -> Page("Home Assistant", "Adresse et accès", if (widgetMode && target != null) null else ({ commit(); screen = Screen.Home })) {
            Panel {
                Field(url, "URL (ex: http://192.168.1.10:8123)", { url = it }, KeyboardType.Uri, top = false)
                Field(token, "Token longue durée", { token = it }, secret = true)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(
                    when {
                        url.isBlank() || token.isBlank() || checking -> Color(0xFF8A8D94)
                        connected -> Color(0xFF66BB6A)
                        else -> Color(0xFFEF5350)
                    }
                ))
                Spacer(Modifier.size(8.dp))
                Text(
                    when {
                        url.isBlank() || token.isBlank() -> "Renseigne l'URL et le token"
                        checking -> "Vérification…"
                        connected -> "Connecté · ${entities.size} entités trouvées"
                        else -> "Injoignable (même Wi-Fi que HA ? token valide ?)"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))
            SaveButton("Enregistrer") { done() }
        }

        Screen.Light -> Page("Lumière", "Ampoule à allumer ou éteindre", if (widgetMode && target != null) null else ({ commit(); screen = Screen.Home })) {
            Panel { EntityField(light, "entity_id (ex: light.salon)", ids("light")) { light = it } }
            Spacer(Modifier.height(24.dp)); SaveButton("Enregistrer") { done() }
        }

        Screen.Cover -> Page("Volet", "Volet roulant (ouvrir, stop, fermer)", if (widgetMode && target != null) null else ({ commit(); screen = Screen.Home })) {
            Panel { EntityField(cover, "entity_id (ex: cover.salon)", ids("cover")) { cover = it } }
            Spacer(Modifier.height(24.dp)); SaveButton("Enregistrer") { done() }
        }

        Screen.Tv -> Page("Télé + LED", "Les deux prises de la télé", if (widgetMode && target != null) null else ({ commit(); screen = Screen.Home })) {
            Panel {
                EntityField(tv, "Prise de la télé (ex: switch.tv)", ids("switch", "light"), top = false) { tv = it }
                EntityField(led, "Prise des LED (ex: switch.led_tv)", ids("switch", "light")) { led = it }
            }
            Hint("Le gros bouton suit l'état de la télé. Les deux prises doivent être différentes.")
            Spacer(Modifier.height(24.dp)); SaveButton("Enregistrer") { done() }
        }

        Screen.Thermo -> Page("Thermomètre", "Capteurs de la pièce", if (widgetMode && target != null) null else ({ commit(); screen = Screen.Home })) {
            Panel {
                EntityField(thermo, "Température (ex: sensor.chambre_temperature)", ids("sensor"), top = false) { thermo = it }
                EntityField(hum, "Humidité (optionnel)", ids("sensor")) { hum = it }
            }
            Spacer(Modifier.height(24.dp)); SaveButton("Enregistrer") { done() }
        }

        Screen.Pc -> Page("PC", "Allumer ou éteindre, avec confirmation", if (widgetMode && target != null) null else ({ commit(); screen = Screen.Home })) {
            val actions = ids("switch", "button", "script", "scene", "input_boolean", "input_button", "light")
            Panel {
                EntityField(pcOn, "Pour allumer (ex: button.pc_on)", actions, top = false) { pcOn = it }
                EntityField(pcOff, "Pour éteindre (ex: button.pc_off)", actions) { pcOff = it }
                EntityField(
                    pcState, "Capteur d'état (ex: sensor.pc_cpu)",
                    ids("sensor", "binary_sensor", "switch", "device_tracker", "input_boolean", "light")
                ) { pcState = it }
            }
            Hint("Le capteur d'état sert à savoir si le PC est allumé : indisponible ou inconnu = éteint (ex: la charge CPU).")
            Spacer(Modifier.height(24.dp)); SaveButton("Enregistrer") { done() }
        }
    }
}

private fun summary(vararg ids: String): String =
    ids.filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { "À configurer" }

@Composable
private fun Page(title: String, subtitle: String, onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        if (onBack != null) {
            Text(
                "‹ Retour", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onBack() }.padding(vertical = 8.dp, horizontal = 4.dp)
            )
        }
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(), color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp)
    )
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp)
    ) { content() }
}

@Composable
private fun NavCard(icon: Int, title: String, summary: String, ok: Boolean, onClick: () -> Unit) {
    val amber = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(if (ok) Color(0x33FFC107) else Color(0x14FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painterResource(icon), null, Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(if (ok) amber else Color(0xFF8A8D94))
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(
                summary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFFF8A65)
            )
        }
        Text("›", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SaveButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun Field(
    value: String, label: String, onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text, secret: Boolean = false, top: Boolean = true
) {
    OutlinedTextField(
        value, onChange,
        Modifier.fillMaxWidth().padding(top = if (top) 12.dp else 0.dp),
        label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntityField(
    value: String, label: String, entities: List<Pair<String, String>>,
    top: Boolean = true, onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val q = value.trim().lowercase()
    val matches = entities.filter {
        q.isEmpty() || it.first.lowercase().contains(q) || it.second.lowercase().contains(q)
    }.take(30)
    ExposedDropdownMenuBox(
        expanded && matches.isNotEmpty(), { expanded = it },
        Modifier.padding(top = if (top) 12.dp else 0.dp)
    ) {
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
