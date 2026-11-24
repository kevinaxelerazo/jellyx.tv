package dev.jdtech.jellyfin.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class UpdateCheckViewModel
@Inject
constructor(
    private val appUpdateManager: AppUpdateManager,
) : ViewModel() {
    val isChecking: StateFlow<Boolean> = appUpdateManager.isCheckingForUpdates

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val currentVersionName: String = appUpdateManager.currentVersionName
    val updateRepo: String = appUpdateManager.updateRepo

    fun checkNow() {
        if (isChecking.value) return
        viewModelScope.launch {
            _statusMessage.value = "Buscando actualizaciones..."
            _statusMessage.value =
                when (val res = appUpdateManager.checkForUpdatesManually()) {
                    is ManualUpdateCheckResult.UpdateAvailable ->
                        "Nueva versión disponible: v${res.info.versionName}"
                    is ManualUpdateCheckResult.UpToDate ->
                        "Ya tienes la última versión (v${res.currentVersion})"
                    is ManualUpdateCheckResult.Error -> res.message
                }
        }
    }
}
