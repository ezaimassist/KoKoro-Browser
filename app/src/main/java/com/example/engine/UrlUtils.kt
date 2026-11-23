package com.example.engine

import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object UrlUtils {
    const val INTERNAL_HOME = "pulse://home"

    private val ALLOWED_WEB_SCHEMES = setOf("https", "http")
    private val BLOCKED_SCHEMES = setOf("javascript", "file", "content", "data", "blob", "vbscript", "intent")

    fun isInternal(url: String): Boolean = url.startsWith("pulse://", ignoreCase = true)

    fun isAllowedNavigationUri(uri: Uri?): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme == "pulse") return true
        return scheme in ALLOWED_WEB_SCHEMES
    }

    fun isSecureUrl(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) || isInternal(url)

    fun extractHost(url: String): String? {
        if (isInternal(url)) return null
        return runCatching { Uri.parse(url).host?.lowercase() }.getOrNull()
    }

    fun normalizeUserInput(
        rawInput: String,
        httpsUpgrade: Boolean,
        searchEngine: String
    ): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return INTERNAL_HOME
        if (trimmed.equals(INTERNAL_HOME, ignoreCase = true)) return INTERNAL_HOME

        val lower = trimmed.lowercase()
        val colonIdx = lower.indexOf(':')
        if (colonIdx > 0) {
            val scheme = lower.substring(0, colonIdx)
            if (scheme in BLOCKED_SCHEMES) {
                return buildSearchUrl(trimmed, searchEngine)
            }
            if (scheme == "https" || scheme == "http") {
                return if (httpsUpgrade && scheme == "http") {
                    "https://" + trimmed.substring(7)
                } else {
                    trimmed
                }
            }
        }

        val hasWhitespace = trimmed.any { it.isWhitespace() }
        if (!hasWhitespace && trimmed.contains('.') && !trimmed.startsWith('.')) {
            return "https://$trimmed"
        }

        return buildSearchUrl(trimmed, searchEngine)
    }

    fun normalizeStrictUrlOrNull(rawUrl: String, httpsUpgrade: Boolean): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return null
        val candidate = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> {
                if (httpsUpgrade) "https://" + trimmed.substring(7) else trimmed
            }
            !trimmed.any { it.isWhitespace() } && trimmed.contains('.') -> "https://$trimmed"
            else -> return null
        }
        val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
        return if (isAllowedNavigationUri(uri) && !uri.host.isNullOrBlank()) candidate else null
    }

    private fun buildSearchUrl(query: String, engine: String): String {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        return when (engine.lowercase()) {
            "google" -> "https://www.google.com/search?q=$encoded"
            "startpage" -> "https://www.startpage.com/do/search?q=$encoded"
            else -> "https://duckduckgo.com/?q=$encoded"
        }
    }
}
