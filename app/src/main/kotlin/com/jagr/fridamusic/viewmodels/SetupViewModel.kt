package com.jagr.fridamusic.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagr.fridamusic.data.repository.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SetupStep { WELCOME, QUALITY, PRIVACY, SERVICES, LIBRARY, FEATURES, DONE }

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
) : ViewModel() {

    private val _step = MutableStateFlow(SetupStep.WELCOME)
    val step = _step.asStateFlow()

    fun next() {
        val all = SetupStep.entries
        val idx = all.indexOf(_step.value)
        if (idx < all.size - 1) _step.value = all[idx + 1]
    }

    fun back() {
        val all = SetupStep.entries
        val idx = all.indexOf(_step.value)
        if (idx > 0) _step.value = all[idx - 1]
    }

    fun complete() {
        viewModelScope.launch {
            settingsManager.setOnboardingCompleted(true)
        }
    }
}
