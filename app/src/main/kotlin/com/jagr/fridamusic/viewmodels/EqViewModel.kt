package com.jagr.fridamusic.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagr.fridamusic.eq.EqualizerService
import com.jagr.fridamusic.eq.data.EQProfileRepository
import com.jagr.fridamusic.eq.data.FilterType
import com.jagr.fridamusic.eq.data.ParametricEQ
import com.jagr.fridamusic.eq.data.ParametricEQBand
import com.jagr.fridamusic.eq.data.SavedEQProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class BandUiState(
    val id: String = UUID.randomUUID().toString(),
    val frequency: Double = 1000.0,
    val gain: Double = 0.0,
    val q: Double = 1.41,
    val filterType: FilterType = FilterType.PK,
    val enabled: Boolean = true,
)

data class EQUiState(
    val isEnabled: Boolean = false,
    val preamp: Double = 0.0,
    val bands: List<BandUiState> = defaultBands(),
    val activeProfileId: String? = null,
    val editingProfileName: String = "",
    val showSaveDialog: Boolean = false,
    val showImportDialog: Boolean = false,
    val importText: String = "",
    val importError: String? = null,
)

private fun defaultBands(): List<BandUiState> = listOf(
    BandUiState(frequency = 32.0,    gain = 0.0, filterType = FilterType.LSC),
    BandUiState(frequency = 64.0,    gain = 0.0),
    BandUiState(frequency = 125.0,   gain = 0.0),
    BandUiState(frequency = 250.0,   gain = 0.0),
    BandUiState(frequency = 500.0,   gain = 0.0),
    BandUiState(frequency = 1000.0,  gain = 0.0),
    BandUiState(frequency = 2000.0,  gain = 0.0),
    BandUiState(frequency = 4000.0,  gain = 0.0),
    BandUiState(frequency = 8000.0,  gain = 0.0),
    BandUiState(frequency = 16000.0, gain = 0.0, filterType = FilterType.HSC),
)

@HiltViewModel
class EQViewModel @Inject constructor(
    private val repository: EQProfileRepository,
    private val equalizerService: EqualizerService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EQUiState())
    val uiState: StateFlow<EQUiState> = _uiState.asStateFlow()

    val profiles: StateFlow<List<SavedEQProfile>> = repository.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        val active = repository.getActiveProfile()
        if (active != null) {
            _uiState.update { state ->
                state.copy(
                    isEnabled = true,
                    activeProfileId = active.id,
                    preamp = active.preamp,
                    bands = active.bands.map { it.toBandUiState() },
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isEnabled = enabled) }
        if (!enabled) {
            equalizerService.disable()
            viewModelScope.launch { repository.setActiveProfile(null) }
        } else {
            val state = _uiState.value
            applyCurrentState(state)
        }
    }

    fun setPreamp(value: Double) {
        _uiState.update { it.copy(preamp = value) }
        if (_uiState.value.isEnabled) applyCurrentState(_uiState.value)
    }

    fun setBandGain(index: Int, gain: Double) {
        _uiState.update { state ->
            val updated = state.bands.toMutableList().also { it[index] = it[index].copy(gain = gain) }
            state.copy(bands = updated)
        }
        if (_uiState.value.isEnabled) applyCurrentState(_uiState.value)
    }

    fun setBandEnabled(index: Int, enabled: Boolean) {
        _uiState.update { state ->
            val updated = state.bands.toMutableList().also { it[index] = it[index].copy(enabled = enabled) }
            state.copy(bands = updated)
        }
        if (_uiState.value.isEnabled) applyCurrentState(_uiState.value)
    }

    fun resetBands() {
        _uiState.update { it.copy(bands = defaultBands(), preamp = 0.0, activeProfileId = null) }
        if (_uiState.value.isEnabled) applyCurrentState(_uiState.value)
    }

    fun loadProfile(profile: SavedEQProfile) {
        _uiState.update { state ->
            state.copy(
                isEnabled = true,
                activeProfileId = profile.id,
                preamp = profile.preamp,
                bands = profile.bands.map { it.toBandUiState() },
            )
        }
        equalizerService.applyProfile(profile)
        viewModelScope.launch { repository.setActiveProfile(profile.id) }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            if (_uiState.value.activeProfileId == profileId) {
                equalizerService.disable()
                _uiState.update { it.copy(isEnabled = false, activeProfileId = null, bands = defaultBands(), preamp = 0.0) }
                repository.setActiveProfile(null)
            }
            repository.deleteProfile(profileId)
        }
    }

    fun showSaveDialog(name: String = "") {
        _uiState.update { it.copy(showSaveDialog = true, editingProfileName = name) }
    }

    fun dismissSaveDialog() {
        _uiState.update { it.copy(showSaveDialog = false, editingProfileName = "") }
    }

    fun setProfileName(name: String) {
        _uiState.update { it.copy(editingProfileName = name) }
    }

    fun saveCurrentAsProfile() {
        val state = _uiState.value
        val name = state.editingProfileName.trim().ifEmpty { return }
        viewModelScope.launch {
            val profile = SavedEQProfile(
                id = "custom_${System.currentTimeMillis()}",
                name = name,
                deviceModel = name,
                bands = state.bands.map { it.toParametricEQBand() },
                preamp = state.preamp,
                isCustom = true,
                isActive = true,
            )
            repository.saveProfile(profile)
            repository.setActiveProfile(profile.id)
            _uiState.update { it.copy(showSaveDialog = false, editingProfileName = "", activeProfileId = profile.id) }
        }
    }

    fun showImportDialog() {
        _uiState.update { it.copy(showImportDialog = true, importText = "", importError = null) }
    }

    fun dismissImportDialog() {
        _uiState.update { it.copy(showImportDialog = false, importText = "", importError = null) }
    }

    fun setImportText(text: String) {
        _uiState.update { it.copy(importText = text, importError = null) }
    }

    fun importProfile() {
        val text = _uiState.value.importText
        try {
            val eq = com.jagr.fridamusic.eq.data.ParametricEQParser.parseText(text)
            val errors = com.jagr.fridamusic.eq.data.ParametricEQParser.validate(eq)
            if (errors.isNotEmpty()) {
                _uiState.update { it.copy(importError = errors.first()) }
                return
            }
            viewModelScope.launch {
                val name = "Imported ${System.currentTimeMillis()}"
                repository.importCustomProfile(name, eq)
                _uiState.update { it.copy(showImportDialog = false, importText = "", importError = null) }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(importError = "Invalid AutoEQ format") }
        }
    }

    private fun applyCurrentState(state: EQUiState) {
        val tempProfile = SavedEQProfile(
            id = "_live_",
            name = "_live_",
            deviceModel = "_live_",
            bands = state.bands.map { it.toParametricEQBand() },
            preamp = state.preamp,
            isCustom = true,
        )
        equalizerService.applyProfile(tempProfile)
    }

    private fun ParametricEQBand.toBandUiState() = BandUiState(
        frequency = frequency,
        gain = gain,
        q = q,
        filterType = filterType,
        enabled = enabled,
    )

    private fun BandUiState.toParametricEQBand() = ParametricEQBand(
        frequency = frequency,
        gain = gain,
        q = q,
        filterType = filterType,
        enabled = enabled,
    )
}