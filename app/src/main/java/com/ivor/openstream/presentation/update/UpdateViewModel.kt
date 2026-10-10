package com.ivor.openstream.presentation.update

import android.app.DownloadManager
import com.ivor.openstream.R
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.BuildConfig
import com.ivor.openstream.data.remote.GithubApi
import com.ivor.openstream.data.remote.GithubAssetDto
import com.ivor.openstream.data.remote.GithubReleaseDto
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface UpdateUiState {
    data object Checking : UpdateUiState
    data class UpToDate(val latest: GithubReleaseDto?) : UpdateUiState
    data class Available(val release: GithubReleaseDto, val asset: GithubAssetDto?) : UpdateUiState
    data class Downloading(val release: GithubReleaseDto, val downloadedBytes: Long, val totalBytes: Long) : UpdateUiState {
        val progress: Float get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }
    data class ReadyToInstall(val release: GithubReleaseDto, val apk: File) : UpdateUiState
    data class Failed(val message: String, val release: GithubReleaseDto? = null) : UpdateUiState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val githubApi: GithubApi,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Checking)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME

    private val downloads = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var downloadId = -1L
    private var downloadJob: Job? = null

    init {
        checkForUpdate()
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Checking
            _uiState.value = runCatching { githubApi.getLatestRelease() }.fold(
                onSuccess = { release ->
                    if (isNewerVersion(release.tagName, currentVersion)) {
                        UpdateUiState.Available(release, pickAsset(release))
                    } else {
                        UpdateUiState.UpToDate(release)
                    }
                },
                onFailure = { UpdateUiState.Failed(context.getString(R.string.st_could_not_github)) }
            )
        }
    }

    fun download(release: GithubReleaseDto, asset: GithubAssetDto) {
        val target = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), asset.name)
        target.delete() // A stale copy would make DownloadManager pick a different name.

        // App-specific storage: readable by the installer through FileProvider with no permissions.
        val request = DownloadManager.Request(Uri.parse(asset.downloadUrl))
            .setTitle(context.getString(R.string.up_version_format, release.tagName))
            .setDescription(context.getString(R.string.dl_downloading_update))
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, asset.name)
        downloadId = downloads.enqueue(request)
        _uiState.value = UpdateUiState.Downloading(release, 0L, asset.size)

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch { trackDownload(release, target) }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        if (downloadId != -1L) downloads.remove(downloadId)
        downloadId = -1L
        checkForUpdate()
    }

    /** Whether Android will let this app open the package installer (Android 8+ asks once). */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    private suspend fun trackDownload(release: GithubReleaseDto, target: File) {
        while (true) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val snapshot = downloads.query(query)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                Triple(
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                )
            }
            when (snapshot?.first) {
                null, DownloadManager.STATUS_FAILED -> {
                    _uiState.value = UpdateUiState.Failed(context.getString(R.string.dl_not_finished), release)
                    return
                }
                DownloadManager.STATUS_SUCCESSFUL -> {
                    _uiState.value = UpdateUiState.ReadyToInstall(release, target)
                    return
                }
                else -> _uiState.value = UpdateUiState.Downloading(release, snapshot.second, snapshot.third)
            }
            delay(PROGRESS_POLL_MS)
        }
    }

    /** The build for this phone's CPU if the release has one, otherwise the universal APK. */
    private fun pickAsset(release: GithubReleaseDto): GithubAssetDto? {
        val apks = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return apks.firstOrNull { abi.isNotEmpty() && it.name.contains(abi, ignoreCase = true) }
            ?: apks.firstOrNull { it.name.contains("universal", ignoreCase = true) }
            ?: apks.firstOrNull { SPLIT_ABIS.none { split -> it.name.contains(split, ignoreCase = true) } }
            ?: apks.firstOrNull()
    }

    /** Compares the numbers in tags like `v2.0`, `2.0.1` or `ALPHA_0.4`; suffixes such as `-debug` are ignored. */
    private fun isNewerVersion(latest: String, current: String): Boolean {
        fun numbers(version: String): List<Int>? =
            Regex("""\d+(\.\d+)*""").find(version)?.value?.split(".")?.map { it.toInt() }
        val l = numbers(latest) ?: return false
        val c = numbers(current) ?: return false
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }

    override fun onCleared() {
        downloadJob?.cancel()
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val PROGRESS_POLL_MS = 400L
        val SPLIT_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    }
}
