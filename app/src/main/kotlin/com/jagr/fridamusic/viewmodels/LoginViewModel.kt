package com.jagr.fridamusic.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagr.fridamusic.data.repository.AccountRepository
import com.music.innertube.YouTube
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.Application
import com.jagr.fridamusic.R
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val application: Application,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun onLoginSuccess(
        cookieString: String,
        onDone: () -> Unit,
    ) {
        if (cookieString.isBlank() || "SAPISID" !in cookieString) {
            _error.value = application.getString(R.string.login_failed_retry)
            return
        }

        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                YouTube.cookie = cookieString

                val info = YouTube.accountInfo().getOrThrow()
                val visitorData = YouTube.visitorData ?: ""
                val dataSyncId = YouTube.dataSyncId ?: ""

                accountRepository.saveTokenAndRestart(
                    cookie = cookieString,
                    visitorData = visitorData,
                    dataSyncId = dataSyncId,
                    accountName = info.name,
                    accountEmail = info.email ?: "",
                    accountChannelHandle = info.channelHandle ?: "",
                )

                withContext(Dispatchers.Main) { onDone() }
            } catch (e: Exception) {
                YouTube.cookie = null
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _error.value = application.getString(R.string.error_verifying_account, e.message)
                }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}