package com.jagr.fridamusic.echomusic.updater.downloadmanager

import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jagr.fridamusic.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.ForegroundInfo
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import com.jagr.fridamusic.utils.ForegroundServiceLaunch
import java.util.zip.ZipInputStream
class UpdateDownloadWorker(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val apkUrl = inputData.getString("apk_url") ?: return@withContext Result.failure()
        val version = inputData.getString("version") ?: "unknown"
        val fileSize = inputData.getString("file_size") ?: ""

        DownloadNotificationManager.initialize(context.applicationContext)
        try {
            val startingNotification = DownloadNotificationManager.getDownloadStartingNotification(version, fileSize)
            val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(
                    DownloadNotificationManager.NOTIFICATION_ID, 
                    startingNotification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                ForegroundInfo(DownloadNotificationManager.NOTIFICATION_ID, startingNotification)
            }
            ForegroundServiceLaunch.log("update_worker", "promotion_attempt")
            setForeground(foregroundInfo)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: SecurityException) {
            ForegroundServiceLaunch.log("update_worker", "promotion_rejected", error)
            return@withContext foregroundUnavailable(version, retry = false)
        } catch (error: IllegalStateException) {
            ForegroundServiceLaunch.log("update_worker", "promotion_rejected", error)
            return@withContext foregroundUnavailable(version,
                retry = ForegroundServiceLaunch.isStartRestriction(error))
        }

        var connectionToClose: HttpURLConnection? = null
        var inputToClose: java.io.InputStream? = null
        var outputToClose: FileOutputStream? = null
        var temporaryFile: File? = null
        var completed = false
        try {
            val url = URL(apkUrl)
            val connection = url.openConnection() as HttpURLConnection
            connectionToClose = connection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                DownloadNotificationManager.showDownloadFailed(
                    version,
                    context.getString(R.string.server_error, connection.responseCode.toString())
                )
                if (connection.responseCode >= 500) {
                    return@withContext Result.retry()
                }
                return@withContext Result.failure()
            }

            val fileLength = connection.contentLength
            val inputStream = connection.inputStream
            inputToClose = inputStream

            val downloadDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "echo_updates"
            )
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            val isZip = apkUrl.contains("nightly.link") || apkUrl.endsWith(".zip")
            val downloadFile = if (isZip) File(downloadDir, "echo_temp.zip") else File(downloadDir, "echomusic.apk")
            temporaryFile = downloadFile
            val outputStream = FileOutputStream(downloadFile)
            outputToClose = outputStream

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead: Long = 0
            var lastProgress = -1
            var lastNotificationTime = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                coroutineContext.ensureActive()
                if (isStopped) {
                    outputStream.close()
                    inputStream.close()
                    connection.disconnect()
                    if (downloadFile.exists()) {
                        downloadFile.delete()
                    }
                    return@withContext Result.retry()
                }

                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                if (fileLength > 0) {
                    val progress = (totalBytesRead.toFloat() / fileLength.toFloat() * 100).toInt()
                    val currentTime = System.currentTimeMillis()
                    
                    if (progress > lastProgress && currentTime - lastNotificationTime >= 1000) {
                        lastProgress = progress
                        lastNotificationTime = currentTime
                        
                        val progressNotification = DownloadNotificationManager.getDownloadProgressNotification(progress, version)
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        notificationManager.notify(DownloadNotificationManager.NOTIFICATION_ID, progressNotification)
                        
                        setProgress(workDataOf("progress" to progress.toFloat() / 100f))
                    }
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()
            connection.disconnect()

            val finalFile = if (isZip) {
                val targetApkFile = File(downloadDir, "echomusic.apk")
                var extracted = false
                try {
                    ZipInputStream(downloadFile.inputStream()).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory && entry.name.endsWith(".apk")) {
                                FileOutputStream(targetApkFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                                extracted = true
                                break
                            }
                            entry = zis.nextEntry
                        }
                    }
                } catch (e: Exception) {
                    if (downloadFile.exists()) downloadFile.delete()
                    DownloadNotificationManager.showDownloadFailed(
                        version,
                        e.message ?: "Failed to extract zip file"
                    )
                    return@withContext Result.failure()
                } finally {
                    if (downloadFile.exists()) {
                        downloadFile.delete()
                    }
                }
                if (!extracted) {
                    DownloadNotificationManager.showDownloadFailed(
                        version,
                        "Could not find APK in zip"
                    )
                    return@withContext Result.failure()
                }
                targetApkFile
            } else {
                downloadFile
            }

            if (version.startsWith("nightly-r")) {
                val runNumberString = version.removePrefix("nightly-r")
                val runNumber = runNumberString.toIntOrNull()
                if (runNumber != null) {
                    val sharedPreferences = context.getSharedPreferences("update_settings", Context.MODE_PRIVATE)
                    sharedPreferences.edit().putInt("last_installed_nightly_run", runNumber).apply()
                }
            }

            completed = true
            DownloadNotificationManager.showDownloadComplete(version, finalFile.absolutePath)

            Result.success(workDataOf("file_path" to finalFile.absolutePath))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: IOException) {
            DownloadNotificationManager.showDownloadFailed(
                version,
                e.message ?: context.getString(R.string.download_failed)
            )
            return@withContext Result.retry()
        } catch (e: Exception) {
            DownloadNotificationManager.showDownloadFailed(
                version,
                e.message ?: context.getString(R.string.download_failed)
            )
            Result.failure()
        } finally {
            for (resource in listOfNotNull(outputToClose, inputToClose)) {
                try {
                    resource.close()
                } catch (error: IOException) {
                    ForegroundServiceLaunch.log("update_worker", "resource_close_failed", error)
                }
            }
            connectionToClose?.disconnect()
            if (!completed) temporaryFile?.delete()
        }
    }

    private fun foregroundUnavailable(version: String, retry: Boolean): Result {
        // Promotion precedes all network/file work, so this path owns no temporary resources.
        DownloadNotificationManager.showForegroundUnavailable(version)
        return if (retry && runAttemptCount < 3) Result.retry()
        else Result.failure(workDataOf("foreground_restricted" to true))
    }
}
