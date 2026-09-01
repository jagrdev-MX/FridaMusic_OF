package com.jagr.fridamusic.updates

import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.StateFlow

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Available : AppUpdateState
    data object ReadyToInstall : AppUpdateState
}

interface AppUpdateController {
    val state: StateFlow<AppUpdateState>

    fun checkForUpdates()
    fun startFlexibleUpdate()
    fun completeUpdate()
    fun onResume()
    fun close()
}

fun createAppUpdateController(activity: ComponentActivity): AppUpdateController =
    createFlavorAppUpdateController(activity)
