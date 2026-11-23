package com.example.engine

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil

data class EnqueuedDownloadResult(
    val systemDownloadId: Long,
    val fileName: String,
    val status: String
)

object PulseDownloadHelper {

    fun enqueueDownload(
        context: Context,
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
        isPrivateTab: Boolean
    ): EnqueuedDownloadResult {
        val uri = Uri.parse(url)
        if (!UrlUtils.isAllowedNavigationUri(uri)) {
            return EnqueuedDownloadResult(-1L, "blocked", "Blocked unsafe scheme")
        }

        val rawName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val safeName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "download.bin" }

        return runCatching {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(uri).apply {
                setTitle(safeName)
                setDescription("Downloading via PulseBrowser 2.0")
                if (!mimeType.isNullOrBlank()) setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                if (!isPrivateTab) {
                    val cookies = CookieManager.getInstance().getCookie(url)
                    if (!cookies.isNullOrBlank()) {
                        addRequestHeader("Cookie", cookies)
                    }
                }
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
            }
            val id = dm.enqueue(request)
            EnqueuedDownloadResult(id, safeName, "Queued in Android Downloads")
        }.getOrElse { err ->
            EnqueuedDownloadResult(-1L, safeName, "Failed: ${err.message ?: err.javaClass.simpleName}")
        }
    }
}
