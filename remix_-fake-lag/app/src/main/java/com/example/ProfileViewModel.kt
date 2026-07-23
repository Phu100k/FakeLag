package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(private val repository: FakeLagProfileRepository) : ViewModel() {

    val allProfiles: StateFlow<List<FakeLagProfile>> = repository.allProfiles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun saveProfile(profile: FakeLagProfile) {
        viewModelScope.launch {
            repository.insert(profile)
        }
    }

    fun deleteProfile(profile: FakeLagProfile) {
        viewModelScope.launch {
            repository.delete(profile)
        }
    }

    fun applyProfile(profile: FakeLagProfile) {
        FakeLagSettings.simulatedPing.value = profile.simulatedPing
        FakeLagSettings.freezeDropRate.value = profile.freezeDropRate
        FakeLagSettings.freezeMinSize.value = profile.freezeMinSize
        FakeLagSettings.freezeMaxSize.value = profile.freezeMaxSize
        FakeLagSettings.ghostReplayCount.value = profile.ghostReplayCount
        FakeLagSettings.ghostBlockThreshold.value = profile.ghostBlockThreshold
        FakeLagSettings.posUpdateInterval.value = profile.posUpdateInterval
        FakeLagSettings.teleportReleaseWindow.value = profile.teleportReleaseWindow
        FakeLagSettings.telekillAutoDelay.value = profile.telekillAutoDelay
        FakeLagSettings.bwBlockUpload.value = profile.bwBlockUpload
        FakeLagSettings.bwBlockDownload.value = profile.bwBlockDownload
        FakeLagSettings.bwUploadLimit.value = profile.bwUploadLimit
        FakeLagSettings.bwDownloadLimit.value = profile.bwDownloadLimit
        
        FakeLagSettings.log("📁 Đã áp dụng hồ sơ: ${profile.name}", FakeLagSettings.LogType.SUCCESS)
    }
}

class ProfileViewModelFactory(private val repository: FakeLagProfileRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
