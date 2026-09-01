package com.jagr.fridamusic.updates

import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal fun createFlavorAppUpdateController(activity: ComponentActivity): AppUpdateController =
    NoOpAppUpdateController

private object NoOpAppUpdateController : AppUpdateController {
    override val state: StateFlow<AppUpdateState> = MutableStateFlow(AppUpdateState.Idle)

    override fun checkForUpdates() = Unit
    override fun startFlexibleUpdate() = Unit
    override fun completeUpdate() = Unit
    override fun onResume() = Unit
    override fun close() = Unit
}
