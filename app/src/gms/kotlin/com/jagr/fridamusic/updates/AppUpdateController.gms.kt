package com.jagr.fridamusic.updates

import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun createFlavorAppUpdateController(activity: ComponentActivity): AppUpdateController =
    GooglePlayAppUpdateController(activity)

private class GooglePlayAppUpdateController(
    private val activity: ComponentActivity,
) : AppUpdateController {
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
    private val mutableState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    override val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()
    private var appUpdateInfo: AppUpdateInfo? = null

    private val updateLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { checkForUpdates() }

    private val installStateListener = InstallStateUpdatedListener { installState ->
        if (installState.installStatus() == InstallStatus.DOWNLOADED) {
            mutableState.value = AppUpdateState.ReadyToInstall
        }
    }

    init {
        appUpdateManager.registerListener(installStateListener)
    }

    override fun checkForUpdates() {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                appUpdateInfo = info
                mutableState.value = when {
                    info.installStatus() == InstallStatus.DOWNLOADED ->
                        AppUpdateState.ReadyToInstall
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) ->
                        AppUpdateState.Available
                    else -> AppUpdateState.Idle
                }
            }
            .addOnFailureListener {
                appUpdateInfo = null
                mutableState.value = AppUpdateState.Idle
            }
    }

    override fun startFlexibleUpdate() {
        val info = appUpdateInfo ?: return
        val options = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
        runCatching {
            appUpdateManager.startUpdateFlowForResult(info, updateLauncher, options)
        }.onFailure {
            mutableState.value = AppUpdateState.Idle
        }
    }

    override fun completeUpdate() {
        appUpdateManager.completeUpdate()
    }

    override fun onResume() {
        checkForUpdates()
    }

    override fun close() {
        appUpdateManager.unregisterListener(installStateListener)
    }
}
