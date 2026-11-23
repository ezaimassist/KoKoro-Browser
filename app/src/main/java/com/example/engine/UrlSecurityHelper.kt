package com.example.engine

import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object UrlSecurityHelper {
    const val INTERNAL_HOME = "bare://home"
    const val INTERNAL_BENCHMARK = "bare://benchmark"
    const val INTERNAL_GPU = "bare://gpu"
    const val INTERNAL_FLAGS = "bare://flags"

    private val ALLOWED_WEB_SCHEMES = setOf("https", "http")
    private val BLOCKED_SCHEMES = setOf("javascript", "file", "content", "data", "blob", "vbscript", "intent")

    fun isInternalUrl(url: String): Boolean {
        return url.startsWith("bare://", ignoreCase = true)
    }

    fun isAllowedNavigationUri(uri: Uri?): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme == "bare") return true
        return scheme in ALLOWED_WEB_SCHEMES
    }

    fun normalizeUserInput(rawInput: String, httpsOnly: Boolean): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return INTERNAL_HOME

        val lower = trimmed.lowercase()
        if (lower.startsWith("bare://")) {
            return when (lower) {
                INTERNAL_HOME, INTERNAL_BENCHMARK, INTERNAL_GPU, INTERNAL_FLAGS -> lower
                else -> INTERNAL_HOME
            }
        }

        // Explicitly reject dangerous schemes typed into the address bar
        val colonIdx = lower.indexOf(':')
        if (colonIdx > 0) {
            val candidateScheme = lower.substring(0, colonIdx)
            if (candidateScheme in BLOCKED_SCHEMES) {
                return buildSearchUrl(trimmed)
            }
            if (candidateScheme == "http" || candidateScheme == "https") {
                val upgraded = if (httpsOnly && candidateScheme == "http") {
                    "https://" + trimmed.substring(7)
                } else {
                    trimmed
                }
                return upgraded
            }
        }

        // Check if input looks like a domain name (contains dot, no spaces)
        val hasSpaces = trimmed.any { it.isWhitespace() }
        if (!hasSpaces && trimmed.contains('.') && !trimmed.startsWith(".")) {
            val scheme = "https://"
            return "$scheme$trimmed"
        }

        return buildSearchUrl(trimmed)
    }

    fun normalizeOrNull(rawUrl: String, httpsOnly: Boolean): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("bare://", ignoreCase = true)) return trimmed.lowercase()
        val candidate = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            if (httpsOnly && trimmed.startsWith("http://", ignoreCase = true)) {
                "https://" + trimmed.substring(7)
            } else {
                trimmed
            }
        } else if (!trimmed.any { it.isWhitespace() } && trimmed.contains('.')) {
            "https://$trimmed"
        } else {
            return null
        }
        val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
        return if (isAllowedNavigationUri(uri) && !uri.host.isNullOrBlank()) candidate else null
    }

    fun extractHost(url: String): String? {
        if (isInternalUrl(url)) return url.removePrefix("bare://")
        return runCatching { Uri.parse(url).host?.lowercase()?.removePrefix("www.") }.getOrNull()
    }

    fun isSecureUrl(url: String): Boolean {
        return url.startsWith("https://", ignoreCase = true) || isInternalUrl(url)
    }

    private fun buildSearchUrl(query: String): String {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        return "https://duckduckgo.com/?q=$encoded"
    }
}
