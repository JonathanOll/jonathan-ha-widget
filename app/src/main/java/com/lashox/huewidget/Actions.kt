package com.lashox.huewidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single entry point for user actions and refreshes: one shared single-thread executor
 * (actions never overlap), 1 s debounce per action, then every widget/tile is redrawn from the cache.
 */
object Actions {
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "ha-actions").apply { isDaemon = true } }
    private val debouncer = Debouncer(Budget.DEBOUNCE_MS)
    private val refreshQueued = AtomicBoolean(false)
    @Volatile private var lastTileFetch = 0L

    /** true if the same action was accepted less than 1 s ago (the tap is then ignored). */
    fun debounced(action: String): Boolean = debouncer.hit(action, SystemClock.elapsedRealtime())

    /** Sends [action] to the non-exported [ActionReceiver] (used by tiles and the confirm dialog). */
    fun send(c: Context, action: String) {
        c.sendBroadcast(Intent(c, ActionReceiver::class.java).setAction(action))
    }

    /** Tiles: ask for a state refresh unless one was requested a few seconds ago. */
    fun requestTileRefresh(c: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTileFetch < Budget.TILE_REFETCH_MS) return
        lastTileFetch = now
        send(c, Act.TILE_REFRESH)
    }

    /**
     * Runs [work] on the shared executor, then redraws everything and finishes [pending].
     * The deadline is fixed by the caller at receive time, so time spent queued counts.
     */
    fun enqueue(c: Context, pending: BroadcastReceiver.PendingResult?, dl: Deadline, work: (Context, Deadline) -> Unit) {
        val app = c.applicationContext
        try {
            exec.execute {
                try {
                    work(app, dl)
                } catch (e: Exception) {
                    HaLog.w("action failed", e)
                } finally {
                    // states were just read: the tile redraw below must not trigger another fetch
                    lastTileFetch = SystemClock.elapsedRealtime()
                    try { Widgets.refreshAll(app) } catch (e: Exception) { HaLog.w("refreshAll failed", e) }
                    pending?.finish()
                }
            }
        } catch (e: RejectedExecutionException) {
            pending?.finish()
        }
    }

    /** Full state refresh (coalesced: at most one queued at a time). */
    fun requestRefresh(c: Context, pending: BroadcastReceiver.PendingResult?, dl: Deadline = Deadline.after(Budget.ACTION_MS)) {
        if (!refreshQueued.compareAndSet(false, true)) {
            pending?.finish()
            return
        }
        enqueue(c, pending, dl) { app, d ->
            refreshQueued.set(false)
            HaRepository.fetchAll(app, d)
        }
    }

    /** Executes one click action (on the executor thread). */
    fun perform(c: Context, action: String, dl: Deadline) {
        HaLog.d { "action $action" }
        when (action) {
            Act.LIGHT_TOGGLE, Act.TILE_LIGHT -> HaRepository.toggleLight(c, dl)
            Act.COVER_OPEN -> HaRepository.cover(c, "open_cover", dl)
            Act.COVER_STOP -> HaRepository.cover(c, "stop_cover", dl)
            Act.COVER_CLOSE -> HaRepository.cover(c, "close_cover", dl)
            Act.TV_ALL -> HaRepository.tv(c, TvAction.ALL, dl)
            Act.TV_ONLY -> HaRepository.tv(c, TvAction.TV, dl)
            Act.LED_ONLY -> HaRepository.tv(c, TvAction.LED, dl)
            Act.TILE_TV -> HaRepository.tv(c, TvAction.ALL, dl, requireTvState = true)
            Act.PC_ON -> HaRepository.pc(c, true, dl)
            Act.PC_OFF -> HaRepository.pc(c, false, dl)
        }
    }
}
