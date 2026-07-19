package com.jagr.fridamusic.data.repository

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.edit
import com.jagr.fridamusic.App
import com.jagr.fridamusic.constants.*
import com.jagr.fridamusic.utils.SyncUtils
import com.jagr.fridamusic.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncUtils: SyncUtils,
) {

    suspend fun logoutAndClearSyncedContent(onCookieChange: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            syncUtils.clearAllSyncedContent()
            App.forgetAccount(context)
            withContext(Dispatchers.Main) {
                onCookieChange("")
            }
        }
    }

    suspend fun logoutKeepData(onCookieChange: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            App.forgetAccount(context)
            withContext(Dispatchers.Main) {
                onCookieChange("")
            }
        }
    }

    suspend fun saveTokenAndRestart(
        cookie: String,
        visitorData: String,
        dataSyncId: String,
        accountName: String,
        accountEmail: String,
        accountChannelHandle: String,
    ) {
        withContext(Dispatchers.IO) {
            context.dataStore.edit { settings ->
                settings[InnerTubeCookieKey] = cookie
                settings[VisitorDataKey] = visitorData
                settings[DataSyncIdKey] = dataSyncId
                settings[AccountNameKey] = accountName
                settings[AccountEmailKey] = accountEmail
                settings[AccountChannelHandleKey] = accountChannelHandle
            }
            withContext(Dispatchers.Main) {
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(intent)
                Runtime.getRuntime().exit(0)
            }
        }
    }
}
