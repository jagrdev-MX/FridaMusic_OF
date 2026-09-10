package com.jagr.fridamusic.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jagr.fridamusic.db.MusicDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NotificationHistoryViewModel @Inject constructor(
    private val database: MusicDatabase,
) : ViewModel() {
    val notifications = database.observeNotificationHistory().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val unreadCount = database.getUnreadNotificationCount().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        0,
    )

    val unreadRecapCount = database.getUnreadRecapNotificationCount().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        0,
    )

    fun markAsRead(id: String) {
        database.query { markNotificationAsRead(id, System.currentTimeMillis()) }
    }

    fun markAllAsRead() {
        database.query { markAllNotificationsAsRead(System.currentTimeMillis()) }
    }

    fun delete(id: String) {
        database.query { deleteNotification(id) }
    }

    fun clear() {
        database.query { clearNotificationHistory() }
    }
}
