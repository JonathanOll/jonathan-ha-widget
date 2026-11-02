package com.lashox.huewidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Confirmation dialog shown over the home screen before touching the PC.
 * Shown immediately with a loading indicator while the PC state is read (short budget).
 * PC state known (state entity configured) -> proposes the matching action only.
 * PC state unknown -> proposes both "Allumer" and "Eteindre".
 * The action itself is sent to [ActionReceiver] (goAsync + shared executor), not run here.
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
                var loaded by remember { mutableStateOf(stateId.isBlank()) }
                var on by remember { mutableStateOf<Boolean?>(null) }
                LaunchedEffect(Unit) {
                    if (stateId.isNotBlank()) {
                        on = withContext(Dispatchers.IO) {
                            Ha.isAlive(app, stateId, Deadline.after(Budget.CONFIRM_STATE_MS))
                        }
                        loaded = true
                    }
                }
                val canOn = loaded && onId.isNotBlank() && on != true
                val canOff = loaded && offId.isNotBlank() && on != false
                val title = when {
                    !loaded -> stringResource(R.string.pc)
                    on == true -> stringResource(R.string.confirm_pc_off_title)
                    on == false -> stringResource(R.string.confirm_pc_on_title)
                    else -> stringResource(R.string.pc)
                }
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text(title) },
                    text = {
                        when {
                            onId.isBlank() && offId.isBlank() -> Text(stringResource(R.string.confirm_no_entity))
                            !loaded -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(R.string.confirm_loading))
                            }
                            on == null -> Text(stringResource(R.string.confirm_question))
                        }
                    },
                    confirmButton = {
                        Row {
                            if (canOff) TextButton(onClick = { act(Act.PC_OFF) }) { Text(stringResource(R.string.action_off)) }
                            if (canOn) TextButton(onClick = { act(Act.PC_ON) }) { Text(stringResource(R.string.action_on)) }
                        }
                    },
                    dismissButton = { TextButton(onClick = { finish() }) { Text(stringResource(R.string.action_cancel)) } }
                )
            }
        }
    }

    private fun act(action: String) {
        Actions.send(applicationContext, action)
        finish()
    }
}
