package com.lashox.huewidget

import java.net.URLEncoder

// Pure decision logic: no Android dependencies, unit-testable on the JVM.

/** HA state string -> on/off. on/home = true, off/not_home = false, anything else = null (unknown). */
fun parseOnOff(state: String?): Boolean? = when (state) {
    "on", "home" -> true
    "off", "not_home" -> false
    else -> null
}

/** true if the state is a real value (not empty, unavailable or unknown). */
fun isRealState(state: String?): Boolean =
    !state.isNullOrEmpty() && state != "unavailable" && state != "unknown"

/**
 * "Is the device up?" from a sensor that only has a value while the device runs.
 * [httpCode] null = network error. 404 = entity missing = off. Other non-2xx (401/403/5xx) = unknown.
 * 2xx: real state = on, unavailable/unknown/empty = off.
 */
fun aliveFrom(httpCode: Int?, state: String?): Boolean? = when {
    httpCode == null -> null
    httpCode == 404 -> false
    httpCode !in 200..299 -> null
    else -> isRealState(state)
}

/**
 * Per-key debounce: [hit] returns true (= ignore) if the same key was accepted less than [windowMs]
 * ago. A rejected hit does not extend the window. The clock is passed in, so this is pure.
 */
class Debouncer(private val windowMs: Long) {
    private val last = HashMap<String, Long>()

    fun hit(key: String, nowMs: Long): Boolean = synchronized(last) {
        val prev = last[key]
        if (prev != null && nowMs - prev < windowMs) return true
        last[key] = nowMs
        false
    }
}

enum class TvAction { ALL, TV, LED }

/**
 * Entities to switch and their target state.
 * ALL: TV on -> everything off, otherwise everything on (follows the TV only).
 * TV / LED: flip that device. Unconfigured (blank) entities are skipped.
 */
fun tvTargets(action: TvAction, tvId: String, ledId: String, tv: Boolean?, led: Boolean?): List<Pair<String, Boolean>> =
    when (action) {
        TvAction.ALL -> {
            val on = tv != true
            listOf(tvId to on, ledId to on)
        }
        TvAction.TV -> listOf(tvId to (tv != true))
        TvAction.LED -> listOf(ledId to (led != true))
    }.filter { it.first.isNotBlank() }

enum class TempBucket { UNKNOWN, COLD, NORMAL, WARM }

fun tempBucket(temp: Double?): TempBucket = when {
    temp == null -> TempBucket.UNKNOWN
    temp < Thresholds.COLD_BELOW -> TempBucket.COLD
    temp > Thresholds.WARM_ABOVE -> TempBucket.WARM
    else -> TempBucket.NORMAL
}

/** URL-encode an entity id for use in a path segment ('.' and '_' are kept as is). */
fun encodeEntityId(id: String): String = URLEncoder.encode(id, "UTF-8").replace("+", "%20")

enum class UrlCheck {
    /** Can be used: https://, or http:// to a local host. */
    OK,
    /** Not a valid http(s) URL with a host. */
    INVALID,
    /** http:// to a non-local host: the token would travel in clear over the internet. */
    INSECURE_PUBLIC
}

/** Validates a HA base URL (scheme http/https, non-empty host) and applies the local-host rule for http://. No DNS. */
fun checkHaUrl(url: String): UrlCheck {
    val u = try {
        java.net.URL(url.trim())
    } catch (e: java.net.MalformedURLException) {
        return UrlCheck.INVALID
    }
    val host = u.host.orEmpty()
    if (host.isEmpty()) return UrlCheck.INVALID
    return when (u.protocol?.lowercase()) {
        "https" -> UrlCheck.OK
        "http" -> if (isLocalHost(host)) UrlCheck.OK else UrlCheck.INSECURE_PUBLIC
        else -> UrlCheck.INVALID
    }
}

/**
 * true if [host] (as found in a URL, IPv6 may be in brackets) is on the local network, decided from
 * the string only (no DNS lookup): private IPv4 (10/8, 172.16/12, 192.168/16), loopback 127/8,
 * link-local 169.254/16, IPv6 loopback / ULA fc00::/7 / link-local fe80::/10 (and IPv4-mapped
 * forms of the IPv4 ranges), "localhost", and names ending in .local, .lan or .home.arpa.
 * Anything not recognized is treated as public.
 */
fun isLocalHost(host: String): Boolean {
    var h = host.trim().lowercase()
    if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length - 1)
    if (h.isEmpty()) return false
    if (':' in h) return isLocalIpv6(h)
    ipv4Octets(h)?.let { return isLocalIpv4(it) }
    val name = h.removeSuffix(".")
    return name == "localhost" || name.endsWith(".localhost") ||
        name.endsWith(".local") || name.endsWith(".lan") || name.endsWith(".home.arpa")
}

/** Strict dotted-decimal IPv4 ("a.b.c.d", each 0-255), else null. */
private fun ipv4Octets(s: String): IntArray? {
    val parts = s.split('.')
    if (parts.size != 4) return null
    val out = IntArray(4)
    for ((i, p) in parts.withIndex()) {
        if (p.isEmpty() || p.length > 3 || !p.all { it in '0'..'9' }) return null
        val v = p.toInt()
        if (v > 255) return null
        out[i] = v
    }
    return out
}

private fun isLocalIpv4(o: IntArray): Boolean = when {
    o[0] == 10 -> true
    o[0] == 127 -> true
    o[0] == 172 && o[1] in 16..31 -> true
    o[0] == 192 && o[1] == 168 -> true
    o[0] == 169 && o[1] == 254 -> true
    else -> false
}

private fun isLocalIpv6(addr: String): Boolean {
    val g = ipv6Groups(addr.substringBefore('%')) ?: return false
    val first = g[0]
    if (g.all { it == 0 }) return false // unspecified "::"
    if (g.take(7).all { it == 0 } && g[7] == 1) return true // ::1
    if ((first and 0xfe00) == 0xfc00) return true // fc00::/7 ULA
    if ((first and 0xffc0) == 0xfe80) return true // fe80::/10 link-local
    // IPv4-mapped ::ffff:a.b.c.d
    if (g.take(5).all { it == 0 } && g[5] == 0xffff) {
        return isLocalIpv4(intArrayOf(g[6] shr 8, g[6] and 0xff, g[7] shr 8, g[7] and 0xff))
    }
    return false
}

/** Parses an IPv6 literal (with optional "::" and trailing dotted IPv4) into 8 groups, else null. */
private fun ipv6Groups(s: String): IntArray? {
    if (s.count { it == ':' } < 2) return null
    val halves = s.split("::")
    if (halves.size > 2) return null
    fun groups(part: String): List<Int>? {
        if (part.isEmpty()) return emptyList()
        val out = ArrayList<Int>()
        val items = part.split(':')
        for ((i, item) in items.withIndex()) {
            if (i == items.lastIndex && '.' in item) {
                val v4 = ipv4Octets(item) ?: return null
                out += (v4[0] shl 8) or v4[1]
                out += (v4[2] shl 8) or v4[3]
            } else {
                if (item.isEmpty() || item.length > 4 || !item.all { it in '0'..'9' || it in 'a'..'f' }) return null
                out += item.toInt(16)
            }
        }
        return out
    }
    val head = groups(halves[0]) ?: return null
    val tail = if (halves.size == 2) groups(halves[1]) ?: return null else emptyList()
    val result = if (halves.size == 2) {
        if (head.size + tail.size > 7) return null
        head + List(8 - head.size - tail.size) { 0 } + tail
    } else {
        if (head.size != 8) return null
        head
    }
    return result.toIntArray()
}
