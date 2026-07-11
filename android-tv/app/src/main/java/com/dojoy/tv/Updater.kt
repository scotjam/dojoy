package com.dojoy.tv

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val VERSION_URL = "http://192.168.1.12:8090/dojoy-tv-version.json"
private const val CONNECT_TIMEOUT_MS = 6000

object Updater {

    fun checkForUpdate(activity: Activity) {
        Thread {
            try {
                val conn = (URL(VERSION_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = CONNECT_TIMEOUT_MS
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val remoteVersion = json.getInt("versionCode")
                val apkUrl = json.getString("apkUrl")
                if (remoteVersion > BuildConfig.VERSION_CODE) {
                    downloadAndInstall(activity, apkUrl)
                }
            } catch (e: Exception) {
                // offline or server unreachable this launch - app still loads normally, retry next time
            }
        }.start()
    }

    private fun downloadAndInstall(activity: Activity, apkUrl: String) {
        try {
            val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = CONNECT_TIMEOUT_MS
            }
            val file = File(activity.cacheDir, "dojoy-update.apk")
            conn.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            Handler(Looper.getMainLooper()).post { promptInstall(activity, file) }
        } catch (e: Exception) {
            // download failed - retry next launch
        }
    }

    private fun promptInstall(activity: Activity, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + activity.packageName)
            )
            activity.startActivity(settingsIntent)
            return
        }
        val uri = FileProvider.getUriForFile(activity, activity.packageName + ".fileprovider", file)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(installIntent)
    }
}
