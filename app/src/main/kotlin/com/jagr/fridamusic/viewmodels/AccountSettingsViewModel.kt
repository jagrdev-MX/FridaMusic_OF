package com.jagr.fridamusic.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagr.fridamusic.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountSettingsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    fun logoutAndClearSyncedContent(onCookieChange: (String) -> Unit) {
        viewModelScope.launch {
            accountRepository.logoutAndClearSyncedContent(onCookieChange)
        }
    }

    fun logoutKeepData(onCookieChange: (String) -> Unit) {
        viewModelScope.launch {
            accountRepository.logoutKeepData(onCookieChange)
        }
    }

    fun saveTokenAndRestart(
        cookie: String,
        visitorData: String,
        dataSyncId: String,
        accountName: String,
        accountEmail: String,
        accountChannelHandle: String,
    ) {
        viewModelScope.launch {
            accountRepository.saveTokenAndRestart(
                cookie = cookie,
                visitorData = visitorData,
                dataSyncId = dataSyncId,
                accountName = accountName,
                accountEmail = accountEmail,
                accountChannelHandle = accountChannelHandle
            )
        }
    }
}
