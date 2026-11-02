package com.lashox.huewidget

/** SharedPreferences file and keys. Never rename: the user's saved config and cache live here. */
object Keys {
    const val PREFS = "ha"

    // config
    const val URL = "url"
    /** Legacy plaintext token: only read once, to migrate it into [TOKEN_ENC], then removed. */
    const val TOKEN = "token"
    /** Base64(iv + AES-256-GCM ciphertext) of the token; key in the Android Keystore. */
    const val TOKEN_ENC = "token_enc"
    const val LIGHT = "entity"
    const val COVER = "cover"
    const val TV = "tv"
    const val LED = "led"
    const val PC_ON = "pc"
    const val PC_OFF = "pc_off"
    const val PC_STATE = "pc_state"
    const val THERMO = "thermo"
    const val HUM = "hum"

    // cache (last known states)
    const val CACHE_LIGHT = "last_on"
    const val CACHE_TV = "tv_on"
    const val CACHE_LED = "led_on"
    const val CACHE_PC = "pc_on"
    const val CACHE_TEMP = "thermo_temp"
    const val CACHE_HUM = "thermo_hum"
    const val CACHE_LABEL = "thermo_label"
}

/** Widget colors (ARGB). */
object Colors {
    val AMBER = 0xFFFFC107.toInt()
    val GREY = 0xFF8A8D94.toInt()
    val COLD = 0xFF64B5F6.toInt()
    val WARM = 0xFFFF8A65.toInt()
    val WHITE = 0xFFFFFFFF.toInt()
    val COVER_ICON = 0xFFE6E6E9.toInt()
}

/** Temperature color thresholds (°C): below COLD_BELOW = cold, above WARM_ABOVE = warm. */
object Thresholds {
    const val COLD_BELOW = 18.0
    const val WARM_ABOVE = 26.0
}

/** Time budgets (ms). A broadcast with goAsync() must finish within ~10 s. */
object Budget {
    const val ACTION_MS = 8000L
    const val CONFIRM_STATE_MS = 4000L
    const val CONNECT_MAX_MS = 4000L
    const val READ_MAX_MS = 5000L
    /** Below this remaining time a request is not started. */
    const val MIN_REQUEST_MS = 300L
    /** The single connect retry only happens if at least this much time remains. */
    const val RETRY_MIN_MS = 1500L
    const val POLL_INTERVAL_MS = 500L
    const val POLL_MAX = 7
    const val DEBOUNCE_MS = 1000L
    const val TILE_REFETCH_MS = 5000L
}

/** Click actions, all handled by the non-exported [ActionReceiver]. */
object Act {
    const val LIGHT_TOGGLE = "com.lashox.huewidget.TOGGLE"
    const val COVER_OPEN = "com.lashox.huewidget.COVER_OPEN"
    const val COVER_CLOSE = "com.lashox.huewidget.COVER_CLOSE"
    const val COVER_STOP = "com.lashox.huewidget.COVER_STOP"
    const val TV_ALL = "com.lashox.huewidget.TV_ALL"
    const val TV_ONLY = "com.lashox.huewidget.TV_ONLY"
    const val LED_ONLY = "com.lashox.huewidget.LED_ONLY"
    const val THERMO_REFRESH = "com.lashox.huewidget.THERMO_REFRESH"
    const val DASH_REFRESH = "com.lashox.huewidget.DASH_REFRESH"
    const val PC_ON = "com.lashox.huewidget.PC_ON"
    const val PC_OFF = "com.lashox.huewidget.PC_OFF"
    const val TILE_LIGHT = "com.lashox.huewidget.TILE_LIGHT"
    const val TILE_TV = "com.lashox.huewidget.TILE_TV"
    const val TILE_REFRESH = "com.lashox.huewidget.TILE_REFRESH"

    val REFRESHES = setOf(THERMO_REFRESH, DASH_REFRESH, TILE_REFRESH)
    val ALL = setOf(
        LIGHT_TOGGLE, COVER_OPEN, COVER_CLOSE, COVER_STOP, TV_ALL, TV_ONLY, LED_ONLY,
        THERMO_REFRESH, DASH_REFRESH, PC_ON, PC_OFF, TILE_LIGHT, TILE_TV, TILE_REFRESH
    )
}
