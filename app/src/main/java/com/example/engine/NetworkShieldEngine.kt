package com.example.engine

import android.net.Uri
import android.webkit.WebResourceResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class ShieldStats(
    val totalBlocked: Int = 0,
    val gamingModeBlocked: Int = 0,
    val estimatedSavedKb: Int = 0,
    val recentBlockedHosts: List<String> = emptyList()
)

class NetworkShieldEngine {

    private val totalBlockedCounter = AtomicInteger(0)
    private val gamingBlockedCounter = AtomicInteger(0)
    private val tabBlockedMap = ConcurrentHashMap<String, AtomicInteger>()
    private val recentHostsLock = Any()
    private val recentHosts = ArrayDeque<String>(24)

    private val _stats = MutableStateFlow(ShieldStats())
    val stats: StateFlow<ShieldStats> = _stats.asStateFlow()

    fun shouldBlock(uri: Uri?, aggressiveGamingFilter: Boolean): Boolean {
        val host = uri?.host?.lowercase() ?: return false
        val path = uri.path?.lowercase().orEmpty()

        if (matchesDomainSet(host, coreBlockedDomains)) return true
        if (aggressiveGamingFilter && matchesDomainSet(host, aggressiveBlockedDomains)) return true

        // Fast keyword checks for common telemetry beacons & ad scripts
        if (path.contains("/gtm.js") ||
            path.contains("/gtag/js") ||
            path.contains("/fbevents.js") ||
            path.contains("/pagead/") ||
            path.contains("/ads/js/") ||
            (aggressiveGamingFilter && (path.contains("analytics") || path.contains("telemetry") || path.contains("prebid")))
        ) {
            return true
        }
        return false
    }

    fun recordBlocked(tabId: String, host: String, inGamingMode: Boolean) {
        val total = totalBlockedCounter.incrementAndGet()
        val gamingTotal = if (inGamingMode) gamingBlockedCounter.incrementAndGet() else gamingBlockedCounter.get()
        tabBlockedMap.computeIfAbsent(tabId) { AtomicInteger(0) }.incrementAndGet()

        val snapshotRecent: List<String> = synchronized(recentHostsLock) {
            if (recentHosts.firstOrNull() != host) {
                recentHosts.addFirst(host)
                while (recentHosts.size > 16) {
                    recentHosts.removeLast()
                }
            }
            recentHosts.toList()
        }

        _stats.value = ShieldStats(
            totalBlocked = total,
            gamingModeBlocked = gamingTotal,
            estimatedSavedKb = total * 28,
            recentBlockedHosts = snapshotRecent
        )
    }

    fun getTabBlockedCount(tabId: String): Int = tabBlockedMap[tabId]?.get() ?: 0

    fun clearTabStats(tabId: String) {
        tabBlockedMap.remove(tabId)
    }

    fun resetSessionStats() {
        totalBlockedCounter.set(0)
        gamingBlockedCounter.set(0)
        tabBlockedMap.clear()
        synchronized(recentHostsLock) { recentHosts.clear() }
        _stats.value = ShieldStats()
    }

    fun createEmptyBlockedResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            204,
            "Blocked by Bare Turbo Shield",
            mapOf("X-Bare-Shield" to "blocked"),
            ByteArrayInputStream(ByteArray(0))
        )
    }

    private fun matchesDomainSet(host: String, domains: Set<String>): Boolean {
        if (host in domains) return true
        var dotIndex = host.indexOf('.')
        while (dotIndex >= 0 && dotIndex < host.length - 1) {
            val suffix = host.substring(dotIndex + 1)
            if (suffix in domains) return true
            dotIndex = host.indexOf('.', dotIndex + 1)
        }
        return false
    }

    companion object {
        // Core uBlock-style privacy, ad network, and Google telemetry blocklist
        private val coreBlockedDomains = setOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "google-analytics.com",
            "googletagmanager.com",
            "googletagservices.com",
            "adservice.google.com",
            "pagead2.googlesyndication.com",
            "facebook.net",
            "connect.facebook.net",
            "analytics.tiktok.com",
            "ads.twitter.com",
            "amazon-adsystem.com",
            "adnxs.com",
            "rubiconproject.com",
            "pubmatic.com",
            "openx.net",
            "criteo.com",
            "criteo.net",
            "taboola.com",
            "outbrain.com",
            "scorecardresearch.com",
            "quantserve.com",
            "hotjar.com",
            "fullstory.com",
            "mixpanel.com",
            "segment.io",
            "segment.com",
            "amplitude.com",
            "sentry.io",
            "bugsnag.com",
            "newrelic.com",
            "nr-data.net",
            "clarity.ms",
            "mc.yandex.ru",
            "coinhive.com",
            "coin-hive.com",
            "adcolony.com",
            "applovin.com",
            "unityads.unity3d.com",
            "vungle.com",
            "ironsrc.com",
            "chartboost.com",
            "moatads.com",
            "adsafeprotected.com",
            "doubleverify.com"
        )

        // Extra domains blocked during Gaming Mode to eliminate background JS & socket contention
        private val aggressiveBlockedDomains = setOf(
            "intercom.io",
            "intercomcdn.com",
            "zendesk.com",
            "drift.com",
            "crisp.chat",
            "onesignal.com",
            "pusher.com",
            "branch.io",
            "appsflyer.com",
            "adjust.com",
            "braze.com",
            "optimizely.com",
            "launchdarkly.com",
            "crazyegg.com",
            "mouseflow.com",
            "cookiebot.com",
            "onetrust.com",
            "cookielaw.org",
            "quantcast.com",
            "sharethis.com",
            "addthis.com",
            "disqus.com"
        )
    }
}
