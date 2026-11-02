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
        for (cls in listOf(LightWidget::class.java, CoverWidget::class.java)) {
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

private val HueColors = darkColorScheme(
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

@androidx.compose.runtime.Composable
private fun ConfigScreen(c: Context, onSaved: () -> Unit) {
    var url by remember { mutableStateOf(Ha.url(c)) }
    var token by remember { mutableStateOf(Ha.token(c)) }
    var entity by remember { mutableStateOf(Ha.entity(c)) }
    var cover by remember { mutableStateOf(Ha.coverEntity(c)) }
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
            Field(entity, "entity_id (ex: light.salon)", { entity = it }, top = false)
        }
        Section("Volet") {
            Field(cover, "entity_id (ex: cover.salon)", { cover = it }, top = false)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { Ha.save(c, url, token, entity); Ha.saveCover(c, cover); onSaved() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text("Enregistrer", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}
