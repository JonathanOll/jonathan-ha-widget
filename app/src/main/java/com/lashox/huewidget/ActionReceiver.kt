package com.lashox.huewidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Non-exported receiver for every widget / tile / confirm-dialog click.
 * Work runs on the shared [Actions] executor under goAsync() with an ~8 s budget.
 */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in Act.ALL) return
        // automatic tile refreshes are already throttled; user taps are debounced
        if (action != Act.TILE_REFRESH && Actions.debounced(action)) return
        val dl = Deadline.after(Budget.ACTION_MS)
        val pending = goAsync()
        if (action in Act.REFRESHES) Actions.requestRefresh(context, pending, dl)
        else Actions.enqueue(context, pending, dl) { app, d -> Actions.perform(app, action, d) }
    }
}
