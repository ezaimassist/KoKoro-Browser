package com.example.data

import com.example.engine.UrlUtils
import kotlinx.coroutines.flow.Flow

class PulseRepository(
    private val bookmarkDao: BookmarkDao,
    private val historyDao: HistoryDao,
    private val downloadDao: DownloadDao,
    private val siteProfileDao: SiteProfileDao,
    private val preferences: PulsePreferences
) {
    val bookmarksFlow: Flow<List<BookmarkEntity>> = bookmarkDao.observeAll()
    val historyFlow: Flow<List<HistoryEntity>> = historyDao.observeRecent()
    val downloadsFlow: Flow<List<DownloadEntity>> = downloadDao.observeAll()
    val siteProfilesFlow: Flow<List<SiteGamingProfileEntity>> = siteProfileDao.observeAll()
    val settingsFlow: Flow<PulseSettings> = preferences.settingsFlow

    suspend fun ensureDefaults() {
        if (bookmarkDao.count() == 0) {
            bookmarkDao.insertAll(defaultBookmarks)
        }
        if (siteProfileDao.findByHost("alt.lasthavens.com") == null) {
            siteProfileDao.upsert(
                SiteGamingProfileEntity(
                    host = "alt.lasthavens.com",
                    autoGamingMode = true,
                    lockLandscape = true,
                    hideBrowserUi = true,
                    keepScreenOn = true,
                    preferredRefreshHz = 0, // Auto highest supported by device
                    safeRenderScalePercent = 100, // Native untouched for WebGL2/Wasm compatibility
                    useDesktopUserAgent = false
                )
            )
        }
        if (siteProfileDao.findByHost("lasthavens.com") == null) {
            siteProfileDao.upsert(
                SiteGamingProfileEntity(
                    host = "lasthavens.com",
                    autoGamingMode = true,
                    lockLandscape = true,
                    hideBrowserUi = true,
                    keepScreenOn = true,
                    preferredRefreshHz = 0,
                    safeRenderScalePercent = 100,
                    useDesktopUserAgent = false
                )
            )
        }
    }

    suspend fun toggleBookmark(title: String, url: String, isGamingSite: Boolean = false): Boolean {
        val clean = url.trim()
        if (clean.isEmpty() || UrlUtils.isInternal(clean)) return false
        val existing = bookmarkDao.findByUrl(clean)
        return if (existing != null) {
            bookmarkDao.deleteByUrl(clean)
            false
        } else {
            val safeTitle = title.trim().ifEmpty { UrlUtils.extractHost(clean) ?: clean }.take(80)
            bookmarkDao.insert(BookmarkEntity(title = safeTitle, url = clean, isGamingSite = isGamingSite))
            true
        }
    }

    suspend fun addBookmark(title: String, rawUrl: String, isGamingSite: Boolean): Boolean {
        val normalized = UrlUtils.normalizeStrictUrlOrNull(rawUrl, httpsUpgrade = true) ?: return false
        val safeTitle = title.trim().ifEmpty { UrlUtils.extractHost(normalized) ?: normalized }.take(80)
        bookmarkDao.insert(BookmarkEntity(title = safeTitle, url = normalized, isGamingSite = isGamingSite))
        return true
    }

    suspend fun deleteBookmark(id: Long) = bookmarkDao.deleteById(id)

    suspend fun recordHistory(title: String, url: String) {
        if (url.isBlank() || UrlUtils.isInternal(url)) return
        val safeTitle = title.trim().ifEmpty { UrlUtils.extractHost(url) ?: url }.take(96)
        historyDao.insert(HistoryEntity(title = safeTitle, url = url))
    }

    suspend fun deleteHistoryItem(id: Long) = historyDao.deleteById(id)
    suspend fun clearHistory() = historyDao.clearAll()

    suspend fun recordDownload(
        systemDownloadId: Long,
        fileName: String,
        url: String,
        mimeType: String,
        contentLength: Long,
        status: String
    ) {
        downloadDao.insert(
            DownloadEntity(
                systemDownloadId = systemDownloadId,
                fileName = fileName,
                url = url,
                mimeType = mimeType,
                contentLength = contentLength,
                status = status
            )
        )
    }

    suspend fun deleteDownload(id: Long) = downloadDao.deleteById(id)
    suspend fun clearDownloads() = downloadDao.clearAll()

    suspend fun findSiteProfile(host: String): SiteGamingProfileEntity? =
        siteProfileDao.findByHost(host.lowercase())

    suspend fun saveSiteProfile(profile: SiteGamingProfileEntity) {
        siteProfileDao.upsert(
            profile.copy(
                host = profile.host.trim().lowercase(),
                safeRenderScalePercent = profile.safeRenderScalePercent.coerceIn(75, 100),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteSiteProfile(host: String) = siteProfileDao.deleteByHost(host.lowercase())

    suspend fun updateSettings(transform: (PulseSettings) -> PulseSettings) {
        preferences.update(transform)
    }

    companion object {
        const val LASTHAVENS_PLAY_URL = "https://alt.lasthavens.com/play?stats=1&log=1"

        private val defaultBookmarks = listOf(
            BookmarkEntity(
                title = "LastHavens (Play + Stats)",
                url = LASTHAVENS_PLAY_URL,
                isGamingSite = true
            ),
            BookmarkEntity(
                title = "WebGL Report (WebGL 2)",
                url = "https://webglreport.com/?v=2",
                isGamingSite = true
            ),
            BookmarkEntity(
                title = "UFO Refresh Rate Test",
                url = "https://www.testufo.com/",
                isGamingSite = true
            ),
            BookmarkEntity(
                title = "Three.js WebGL Samples",
                url = "https://threejs.org/examples/",
                isGamingSite = true
            ),
            BookmarkEntity(
                title = "DuckDuckGo Search",
                url = "https://duckduckgo.com/",
                isGamingSite = false
            )
        )
    }
}
