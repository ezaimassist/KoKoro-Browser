package com.example.data

import com.example.engine.UrlSecurityHelper
import kotlinx.coroutines.flow.Flow

class BrowserRepository(
    private val bookmarkDao: BookmarkDao,
    private val siteProfileDao: SiteProfileDao,
    private val preferences: BrowserPreferences
) {
    val bookmarksFlow: Flow<List<BookmarkEntity>> = bookmarkDao.observeAll()
    val siteProfilesFlow: Flow<List<SiteGamingProfileEntity>> = siteProfileDao.observeAll()
    val settingsFlow: Flow<BrowserSettings> = preferences.settingsFlow

    suspend fun ensureDefaultData() {
        if (bookmarkDao.count() == 0) {
            bookmarkDao.insertAll(defaultBookmarks)
        }
        defaultGamingProfiles.forEach { profile ->
            if (siteProfileDao.getByHost(profile.host) == null) {
                siteProfileDao.upsert(profile)
            }
        }
    }

    suspend fun toggleBookmark(title: String, url: String, isGamingPortal: Boolean = false): Boolean {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty() || cleanUrl == UrlSecurityHelper.INTERNAL_HOME) return false
        val existing = bookmarkDao.findByUrl(cleanUrl)
        return if (existing != null) {
            bookmarkDao.deleteByUrl(cleanUrl)
            false
        } else {
            val safeTitle = title.trim().ifEmpty { UrlSecurityHelper.extractHost(cleanUrl) ?: cleanUrl }
            bookmarkDao.insert(
                BookmarkEntity(
                    title = safeTitle.take(64),
                    url = cleanUrl,
                    category = if (isGamingPortal) "Gaming" else "Web",
                    isGamingPortal = isGamingPortal
                )
            )
            true
        }
    }

    suspend fun addBookmark(title: String, url: String, isGamingPortal: Boolean) {
        val normalized = UrlSecurityHelper.normalizeOrNull(url, httpsOnly = true) ?: return
        bookmarkDao.insert(
            BookmarkEntity(
                title = title.trim().ifEmpty { UrlSecurityHelper.extractHost(normalized) ?: normalized }.take(64),
                url = normalized,
                category = if (isGamingPortal) "Gaming" else "Web",
                isGamingPortal = isGamingPortal
            )
        )
    }

    suspend fun deleteBookmark(id: Long) = bookmarkDao.deleteById(id)

    suspend fun getSiteProfile(host: String): SiteGamingProfileEntity? = siteProfileDao.getByHost(host.lowercase())

    suspend fun saveSiteProfile(profile: SiteGamingProfileEntity) {
        siteProfileDao.upsert(profile.copy(host = profile.host.lowercase(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteSiteProfile(host: String) = siteProfileDao.deleteByHost(host.lowercase())

    suspend fun updateSettings(transform: (BrowserSettings) -> BrowserSettings) {
        preferences.update(transform)
    }

    companion object {
        private val defaultBookmarks = listOf(
            BookmarkEntity(
                title = "WebGL2 & Latency Lab",
                url = UrlSecurityHelper.INTERNAL_BENCHMARK,
                category = "Diagnostic",
                isGamingPortal = true
            ),
            BookmarkEntity(
                title = "Slow Roads (WebGL)",
                url = "https://slowroads.io/",
                category = "WebGL 3D",
                isGamingPortal = true
            ),
            BookmarkEntity(
                title = "Poki HTML5 Games",
                url = "https://poki.com/",
                category = "HTML5",
                isGamingPortal = true
            ),
            BookmarkEntity(
                title = "Three.js Examples",
                url = "https://threejs.org/examples/",
                category = "WebGL GPU",
                isGamingPortal = true
            ),
            BookmarkEntity(
                title = "UFO Refresh Test",
                url = "https://www.testufo.com/",
                category = "Display Hz",
                isGamingPortal = true
            ),
            BookmarkEntity(
                title = "Bare Browser GitHub",
                url = "https://github.com/BareBrowser/bare-browser",
                category = "Source",
                isGamingPortal = false
            )
        )

        private val defaultGamingProfiles = listOf(
            SiteGamingProfileEntity(
                host = "slowroads.io",
                autoGamingMode = true,
                renderScalePercent = 85,
                forceWebGlHighPerf = true,
                disableAntialias = true,
                aggressiveShield = true
            ),
            SiteGamingProfileEntity(
                host = "poki.com",
                autoGamingMode = true,
                renderScalePercent = 85,
                forceWebGlHighPerf = true,
                disableAntialias = false,
                aggressiveShield = true
            ),
            SiteGamingProfileEntity(
                host = "threejs.org",
                autoGamingMode = true,
                renderScalePercent = 100,
                forceWebGlHighPerf = true,
                disableAntialias = false,
                aggressiveShield = true
            )
        )
    }
}
