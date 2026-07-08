package com.music.jiosaavn

import android.content.Context
import java.util.UUID
import kotlin.math.abs

object DeviceRouter {

    private const val PREFS_NAME = "echo"
    private const val KEY_DEVICE_ID = "device_id"

    private val SERVERS = listOf(
        "https://saavn.echomusic.fun",
        "https://jiosaavn-api.pc-adityadav9532.workers.dev",
        "https://jiosaavn-api.mac-adityadav9532.workers.dev"
    )

    private var deviceId: String? = null
    private var assignedServerIndex: Int = 0
    private var currentSessionServerIndex: Int = 0
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        val id = prefs.getString(KEY_DEVICE_ID, null) ?: run {
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
            newId
        }
        
        deviceId = id
        assignedServerIndex = abs(id.hashCode()) % SERVERS.size
        currentSessionServerIndex = assignedServerIndex
        isInitialized = true
    }

    fun getDeviceId(): String {
        check(isInitialized) { "DeviceRouter must be initialized first" }
        return deviceId!!
    }

    /**
     * Returns the full server URL for this device based on the current session's fallback state.
     */
    fun getCurrentServer(): String {
        check(isInitialized) { "DeviceRouter must be initialized first" }
        return SERVERS[currentSessionServerIndex]
    }

    /**
     * Returns the 0-based index of the currently active server.
     */
    fun getCurrentServerIndex(): Int {
        check(isInitialized) { "DeviceRouter must be initialized first" }
        return currentSessionServerIndex
    }

    /**
     * Fallback to the next server in the list for the duration of this session.
     */
    fun fallbackToNextServer() {
        check(isInitialized) { "DeviceRouter must be initialized first" }
        currentSessionServerIndex = (currentSessionServerIndex + 1) % SERVERS.size
    }
}
