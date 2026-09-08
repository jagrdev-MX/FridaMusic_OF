package com.jagr.fridamusic.recognition

import android.app.Activity
import android.app.AlertDialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import com.jagr.fridamusic.R
import com.jagr.fridamusic.utils.ForegroundServiceLaunch

class RecognitionLaunchActivity : Activity() {
    private var resumed = false
    private var requested = false
    private var awaitingRetry = false
    private var retryDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(RecognitionServiceRequest.TOKEN)?.let {
            intent.putExtra(RecognitionServiceRequest.TOKEN, it)
        }
        RecognitionServiceRequest.token(this, intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(RecognitionServiceRequest.TOKEN, RecognitionServiceRequest.token(this, intent))
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            RecognitionServiceRequest.token(this, intent)
            requested = false
            if (resumed && hasWindowFocus()) launchRecognition()
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (awaitingRetry) showRetry()
        if (hasWindowFocus()) launchRecognition()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && resumed) launchRecognition()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    private fun launchRecognition() {
        if (requested || !resumed || isFinishing) return
        requested = true
        if (!MusicRecognitionService.hasRecordPermission(this)) {
            // This Activity is opened by an explicit recognition gesture, never at app startup.
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        val serviceIntent = RecognitionServiceRequest.serviceIntent(this, intent).apply {
            putExtra(RecognitionServiceRequest.REPLY, object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    if (isFinishing || isDestroyed) return
                    if (resultCode == RecognitionServiceRequest.ACCEPTED) finish() else showRetry()
                }
            })
        }
        if (!ForegroundServiceLaunch.start(this, serviceIntent, "recognition_activity")) showRetry()
        // Stay visible until promotion is acknowledged, not merely enqueued.
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 1 || isFinishing || isDestroyed) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            requested = false
            if (resumed && hasWindowFocus()) launchRecognition()
        } else {
            retryDialog = AlertDialog.Builder(this)
                .setTitle(R.string.music_recognition_permission_title)
                .setMessage(R.string.music_recognition_permission_permanent)
                .setPositiveButton(R.string.music_recognition_permission_action) { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")))
                    requested = false
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
        }
    }

    private fun showRetry() {
        awaitingRetry = true
        if (!resumed || isFinishing || isDestroyed) return
        if (retryDialog?.isShowing == true) return
        retryDialog = AlertDialog.Builder(this)
            .setTitle(R.string.recognize_music)
            .setMessage(R.string.recognition_fgs_retry)
            .setPositiveButton(R.string.retry) { _, _ ->
                awaitingRetry = false
                requested = false
                // Focus callback retries after dismissal, only while resumed.
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    override fun onDestroy() {
        retryDialog?.dismiss()
        super.onDestroy()
    }
}
