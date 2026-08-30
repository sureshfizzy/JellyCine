package com.vela.shared.update

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vela.data.update.AppFlavor
import com.vela.data.update.AppUpdateAsset
import com.vela.data.update.AppUpdateCheckException
import com.vela.data.update.AppUpdateInstaller
import com.vela.data.update.AppUpdateRelease
import com.vela.data.update.AppUpdateRepository
import com.vela.data.update.BuiltinDownloadMirrors
import com.vela.data.update.CUSTOM_DOWNLOAD_MIRROR_ID
import com.vela.data.update.DownloadMirror
import com.vela.data.update.assetsForFlavor
import com.vela.data.update.isNewerVersion
import com.vela.data.update.pickRecommendedAsset
import com.vela.data.update.sanitizeMirrorPrefix
import com.vela.shared.R
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUpdateUiState(
    val currentVersion: String,
    val currentFlavor: AppFlavor,
    val deviceAbis: List<String>,
    val mirror: DownloadMirror,
    val customPrefix: String,
    val checking: Boolean = false,
    val checkError: String? = null,
    val release: AppUpdateRelease? = null,
    val isNewer: Boolean = false,
    val selectedFlavor: AppFlavor,
    val selectedAsset: AppUpdateAsset? = null,
    val downloading: Boolean = false,
    val receivedBytes: Long = 0,
    val totalBytes: Long? = null,
    val downloadedFile: File? = null,
    val downloadError: String? = null,
    val showReleaseDialog: Boolean = false,
    val showMirrorDialog: Boolean = false,
    val showCustomMirrorDialog: Boolean = false,
    val customPrefixDraft: String = "",
    val customPrefixError: Boolean = false,
    val needsInstallPermission: Boolean = false,
    val installError: String? = null
)

class AppUpdateViewModel(
    application: Application,
    currentVersion: String
) : AndroidViewModel(application) {
    private val repository = AppUpdateRepository(application)
    private var downloadJob: Job? = null

    private val _uiState = MutableStateFlow(
        AppUpdateUiState(
            currentVersion = currentVersion,
            currentFlavor = repository.currentFlavor(),
            deviceAbis = repository.deviceAbis(),
            mirror = repository.currentMirror(),
            customPrefix = repository.customPrefix(),
            selectedFlavor = repository.currentFlavor()
        )
    )
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    fun checkForUpdate() {
        if (_uiState.value.checking || _uiState.value.downloading) return
        _uiState.update {
            it.copy(
                checking = true,
                checkError = null,
                downloadError = null,
                installError = null
            )
        }
        viewModelScope.launch {
            try {
                val release = repository.checkLatest()
                val recommended = pickRecommendedAsset(
                    assets = release.assets,
                    flavor = _uiState.value.currentFlavor,
                    supportedAbis = _uiState.value.deviceAbis
                )
                _uiState.update { state ->
                    val flavor = recommended?.flavor ?: state.currentFlavor
                    state.copy(
                        checking = false,
                        release = release,
                        isNewer = isNewerVersion(release.versionName, state.currentVersion),
                        selectedFlavor = flavor,
                        selectedAsset = recommended ?: pickRecommendedAsset(
                            assets = release.assets,
                            flavor = flavor,
                            supportedAbis = state.deviceAbis
                        ),
                        downloadedFile = null,
                        showReleaseDialog = true
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: AppUpdateCheckException) {
                _uiState.update {
                    it.copy(
                        checking = false,
                        checkError = checkErrorMessage(error),
                        showReleaseDialog = false
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        checking = false,
                        checkError = context().getString(
                            R.string.about_update_error,
                            error.message ?: error.javaClass.simpleName
                        ),
                        showReleaseDialog = false
                    )
                }
            }
        }
    }

    fun dismissReleaseDialog() {
        if (_uiState.value.downloading) return
        _uiState.update { it.copy(showReleaseDialog = false) }
    }

    fun openMirrorPicker() {
        _uiState.update { it.copy(showMirrorDialog = true, mirror = repository.currentMirror()) }
    }

    fun dismissMirrorPicker() {
        _uiState.update { it.copy(showMirrorDialog = false, showCustomMirrorDialog = false) }
    }

    fun selectMirror(id: String) {
        if (id == CUSTOM_DOWNLOAD_MIRROR_ID) {
            _uiState.update {
                it.copy(
                    showCustomMirrorDialog = true,
                    customPrefixDraft = it.customPrefix.ifBlank { "" },
                    customPrefixError = false
                )
            }
            return
        }
        repository.setMirrorId(id)
        _uiState.update {
            it.copy(
                mirror = repository.currentMirror(),
                showMirrorDialog = false
            )
        }
    }

    fun updateCustomPrefixDraft(value: String) {
        _uiState.update { it.copy(customPrefixDraft = value, customPrefixError = false) }
    }

    fun saveCustomPrefix() {
        val prefix = sanitizeMirrorPrefix(_uiState.value.customPrefixDraft)
        if (prefix == null) {
            _uiState.update { it.copy(customPrefixError = true) }
            return
        }
        repository.setCustomPrefix(prefix)
        _uiState.update {
            it.copy(
                mirror = repository.currentMirror(),
                customPrefix = repository.customPrefix(),
                showCustomMirrorDialog = false,
                showMirrorDialog = false,
                customPrefixError = false
            )
        }
    }

    fun selectFlavor(flavor: AppFlavor) {
        _uiState.update { state ->
            val release = state.release ?: return@update state
            state.copy(
                selectedFlavor = flavor,
                selectedAsset = pickRecommendedAsset(release.assets, flavor, state.deviceAbis),
                downloadedFile = null,
                downloadError = null
            )
        }
    }

    fun selectAsset(asset: AppUpdateAsset) {
        _uiState.update {
            it.copy(selectedAsset = asset, downloadedFile = null, downloadError = null)
        }
    }

    fun downloadSelected() {
        val asset = _uiState.value.selectedAsset ?: return
        if (_uiState.value.downloading) return
        downloadJob?.cancel()
        _uiState.update {
            it.copy(
                downloading = true,
                receivedBytes = 0,
                totalBytes = asset.sizeBytes.takeIf { size -> size > 0 },
                downloadedFile = null,
                downloadError = null,
                installError = null,
                needsInstallPermission = false
            )
        }
        downloadJob = viewModelScope.launch {
            try {
                val file = repository.download(asset) { received, total ->
                    _uiState.update { state ->
                        state.copy(receivedBytes = received, totalBytes = total)
                    }
                }
                _uiState.update {
                    it.copy(
                        downloading = false,
                        downloadedFile = file,
                        receivedBytes = file.length()
                    )
                }
                installDownloaded()
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(downloading = false) }
                throw cancelled
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        downloading = false,
                        downloadError = context().getString(
                            R.string.about_update_download_failed,
                            error.message ?: error.javaClass.simpleName
                        )
                    )
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _uiState.update { it.copy(downloading = false) }
    }

    fun installDownloaded() {
        val file = _uiState.value.downloadedFile ?: return
        val appContext = context()
        if (!AppUpdateInstaller.canRequestInstall(appContext)) {
            _uiState.update { it.copy(needsInstallPermission = true, installError = null) }
            return
        }
        try {
            AppUpdateInstaller.install(appContext, file)
            _uiState.update { it.copy(needsInstallPermission = false, installError = null) }
        } catch (error: Exception) {
            _uiState.update {
                it.copy(installError = appContext.getString(R.string.about_update_install_failed))
            }
        }
    }

    fun openInstallPermissionSettings() {
        AppUpdateInstaller.openInstallPermissionSettings(context())
    }

    fun refreshInstallPermission() {
        val allowed = AppUpdateInstaller.canRequestInstall(context())
        _uiState.update { it.copy(needsInstallPermission = !allowed && it.downloadedFile != null) }
    }

    fun mirrors(): List<DownloadMirror> = BuiltinDownloadMirrors

    fun assetsForSelectedFlavor(): List<AppUpdateAsset> {
        val release = _uiState.value.release ?: return emptyList()
        return assetsForFlavor(release.assets, _uiState.value.selectedFlavor)
    }

    private fun checkErrorMessage(error: AppUpdateCheckException): String {
        val appContext = context()
        return when (error.message) {
            "NO_RELEASE" -> appContext.getString(R.string.about_update_none)
            else -> appContext.getString(R.string.about_update_error, error.message ?: "")
        }
    }

    private fun context(): Context = getApplication()
}
