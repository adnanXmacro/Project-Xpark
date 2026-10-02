package com.sparktube.app.update

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.FileProvider
import com.sparktube.app.BuildConfig
import java.io.File

/**
 * Tracks whether Open Tube has a started activity, holds a downloaded APK,
 * and launches the system installer (or the unknown-sources screen first).
 */
object UpdateInstaller {

    const val FILE_PROVIDER_AUTHORITY = "com.sparktube.app.fileprovider"
    const val DIR_NAME = "updates"

    private lateinit var app: Application

    @Volatile
    private var startedActivities = 0

    @Volatile
    private var pendingApk: File? = null

    /** True after we have opened the system installer for [pendingApk]. */
    @Volatile
    private var installPrompted = false

    @Volatile
    private var askedUnknownSources = false

    val isInForeground: Boolean
        get() = startedActivities > 0

    fun init(application: Application) {
        app = application
        application.registerActivityLifecycleCallbacks(Callbacks())
        restorePendingFromDisk()
    }

    fun updatesDir(): File = File(app.cacheDir, DIR_NAME).apply { mkdirs() }

    fun onReady(file: File) {
        if (!file.isFile) return
        pendingApk = file
        installPrompted = false
        askedUnknownSources = false
        if (isInForeground) {
            launchInstall(fromUserTap = false)
        }
    }

    fun launchInstall(fromUserTap: Boolean) {
        val file = pendingApk ?: return
        if (!file.isFile) {
            pendingApk = null
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !app.packageManager.canRequestPackageInstalls()
        ) {
            if (fromUserTap || !askedUnknownSources) {
                askedUnknownSources = true
                val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { app.startActivity(settings) }
            }
            return
        }
        askedUnknownSources = false
        installPrompted = true
        val uri = FileProvider.getUriForFile(app, FILE_PROVIDER_AUTHORITY, file)
        val install = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        install.clipData = ClipData.newRawUri("", uri)
        runCatching { app.startActivity(install) }
    }

    fun clearPending(file: File) {
        if (pendingApk == file) pendingApk = null
        if (file.exists()) runCatching { file.delete() }
    }

    private fun restorePendingFromDisk() {
        val dir = File(app.cacheDir, DIR_NAME)
        if (!dir.isDirectory) return
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".apk") } ?: return
        var newest: File? = null
        var newestTag: String? = null
        for (f in files) {
            val tag = tagFromName(f.name) ?: continue
            if (!isNewer(BuildConfig.VERSION_NAME, tag)) {
                runCatching { f.delete() }
                continue
            }
            if (newestTag == null || isNewer(newestTag, tag)) {
                newest = f
                newestTag = tag
            }
        }
        pendingApk = newest
    }

    private fun isNewer(current: String, tag: String): Boolean {
        val cur = current.removePrefix("v").removePrefix("V")
        val tg = tag.removePrefix("v").removePrefix("V")
        val curParts = cur.split('.').map { it.toIntOrNull() ?: 0 }
        val tgParts = tg.split('.').map { it.toIntOrNull() ?: 0 }
        val n = maxOf(curParts.size, tgParts.size)
        for (i in 0 until n) {
            val c = curParts.getOrElse(i) { 0 }
            val t = tgParts.getOrElse(i) { 0 }
            if (t != c) return t > c
        }
        return false
    }

    private fun tagFromName(name: String): String? {
        // OpenTube-1.2.1.apk or OpenTube-v1.2.1.apk
        val base = name.removeSuffix(".apk")
        val dash = base.indexOf('-')
        if (dash < 0 || dash == base.lastIndex) return null
        return base.substring(dash + 1)
    }

    private fun onStarted() {
        startedActivities++
        if (startedActivities == 1) {
            maybeAutoInstall()
        }
    }

    private fun onResumed() {
        if (pendingApk != null && !installPrompted) {
            maybeAutoInstall()
        }
    }

    private fun maybeAutoInstall() {
        val file = pendingApk
        if (file == null) return
        if (!file.isFile) {
            pendingApk = null
            return
        }
        if (!installPrompted) {
            launchInstall(fromUserTap = false)
        }
    }

    private fun onStopped() {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities == 0) {
            installPrompted = false
            askedUnknownSources = false
        }
    }

    private class Callbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = onStarted()
        override fun onActivityResumed(activity: Activity) = onResumed()
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = onStopped()
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
