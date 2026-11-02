package com.lashox.huewidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

/**
 * Confirmation dialog shown over the home screen before touching the PC.
 * PC state known (state entity configured) -> proposes the matching action only.
 * PC state unknown -> proposes both "Allumer" and "Eteindre".
 */
class ConfirmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext
        val onId = Ha.pcEntity(app)
        val offId = Ha.pcOffEntity(app)
        val stateId = Ha.pcStateEntity(app)

        setContent {
            MaterialTheme(colorScheme = HueColors) {
                var loaded by remember { mutableStateOf(false) }
                var on by remember { mutableStateOf<Boolean?>(null) }
                LaunchedEffect(Unit) {
                    if (stateId.isNotBlank()) on = withContext(Dispatchers.IO) { Ha.isAlive(app, stateId) }
                    loaded = true
                }
                if (loaded) {
                    val canOn = onId.isNotBlank() && on != true
                    val canOff = offId.isNotBlank() && on != false
                    val title = when (on) {
                        true -> "\u00c9teindre le PC ?"
                        false -> "Allumer le PC ?"
                        null -> "PC"
                    }
                    AlertDialog(
                        onDismissRequest = { finish() },
                        title = { Text(title) },
                        text = {
                            if (onId.isBlank() && offId.isBlank()) Text("Aucune entit\u00e9 PC configur\u00e9e dans l'app.")
                            else if (on == null) Text("Que veux-tu faire ?")
                        },
                        confirmButton = {
                            Row {
                                if (canOff) TextButton(onClick = { act(app, offId); finish() }) { Text("\u00c9teindre") }
                                if (canOn) TextButton(onClick = { act(app, onId); finish() }) { Text("Allumer") }
                            }
                        },
                        dismissButton = { TextButton(onClick = { finish() }) { Text("Annuler") } }
                    )
                }
            }
        }
    }

    private fun act(c: Context, id: String) {
        thread {
            try {
                Ha.trigger(c, id)
            } catch (e: Exception) {
                Log.e("HueWidget", "pc action failed", e)
            }
            Thread.sleep(3000)
            val mgr = AppWidgetManager.getInstance(c)
            for (cls in listOf(PcWidget::class.java, DashWidget::class.java)) {
                c.sendBroadcast(
                    Intent(c, cls)
                        .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, mgr.getAppWidgetIds(ComponentName(c, cls)))
                )
            }
        }
    }
}
