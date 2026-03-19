package com.ivor.openanime.presentation.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openanime.BuildConfig
import com.ivor.openanime.data.remote.GithubApi
import com.ivor.openanime.data.remote.GithubReleaseDto
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed class UpdateUiState {
    data object Loading : UpdateUiState()
    data class UpToDate(val currentVersion: String) : UpdateUiState()
    data class UpdateAvailable(val release: GithubReleaseDto, val currentVersion: String) : UpdateUiState()
    data class Downloading(val progress: Int) : UpdateUiState()
    data class ReadyToInstall(val apkFile: File) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val githubApi: GithubApi,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private var downloadId: Long = -1L

    init {
        checkForUpdate()
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Loading
            try {
                val release = githubApi.getLatestRelease()
                val current = BuildConfig.VERSION_NAME
                val latest = release.tagName.trimStart('v')
                if (isNewerVersion(latest, current)) {
                    _uiState.value = UpdateUiState.UpdateAvailable(release, current)
                } else {
                    _uiState.value = UpdateUiState.UpToDate(current)
                }
            } catch (e: Exception) {
                _uiState.value = UpdateUiState.Error(e.message ?: "Failed to check for updates")
            }
        }
    }

    fun downloadAndInstall(apkUrl: String, fileName: String) {
        _uiState.value = UpdateUiState.Downloading(0)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("OpenStream Update")
            setDescription("Downloading $fileName")
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setMimeType("application/vnd.android.package-archive")
            addRequestHeader("Accept", "application/octet-stream")
        }

        downloadId = dm.enqueue(request)

        // Register receiver for download completion
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    context.unregisterReceiver(this)
                    val apkFile = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        fileName
                    )
                    if (apkFile.exists()) {
                        _uiState.value = UpdateUiState.ReadyToInstall(apkFile)
                    } else {
                        _uiState.value = UpdateUiState.Error("Download failed — file not found")
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

    fun openInstaller(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        return try {
            val l = latest.split(".").map { it.toInt() }
            val c = current.split(".").map { it.toInt() }
            for (i in 0 until maxOf(l.size, c.size)) {
                val lv = l.getOrElse(i) { 0 }
                val cv = c.getOrElse(i) { 0 }
                if (lv > cv) return true
                if (lv < cv) return false
            }
            false
        } catch (e: Exception) {
            false
        }
    }
}
