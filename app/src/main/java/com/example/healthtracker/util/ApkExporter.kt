package com.example.healthtracker.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

object ApkExporter {

    fun extractApkToDownloads(context: Context): Pair<Boolean, String> {
        return try {
            val sourceApkPath = context.applicationInfo.sourceDir
            val sourceApk = File(sourceApkPath)
            if (!sourceApk.exists()) {
                return false to "File APK sumber tidak ditemukan di sistem."
            }

            var savedPath = ""
            var copySuccess = false

            // 1. Try public Download folder
            try {
                val publicDownloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!publicDownloadDir.exists()) {
                    publicDownloadDir.mkdirs()
                }
                val destPublic = File(publicDownloadDir, "HealthTracker.apk")
                sourceApk.copyTo(destPublic, overwrite = true)
                savedPath = destPublic.absolutePath
                copySuccess = true
            } catch (e: Exception) {
                // Scoped storage fallback
            }

            // 2. Also copy to external files dir as fallback
            try {
                val appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                val destApp = File(appDownloads, "HealthTracker.apk")
                sourceApk.copyTo(destApp, overwrite = true)
                if (savedPath.isEmpty()) {
                    savedPath = destApp.absolutePath
                }
                copySuccess = true
            } catch (e: Exception) {
                // Ignored
            }

            // 3. Also copy to cache for FileProvider sharing
            val cacheApk = File(context.cacheDir, "HealthTracker.apk")
            sourceApk.copyTo(cacheApk, overwrite = true)

            if (copySuccess) {
                true to "File APK berhasil diekstrak ke:\n$savedPath"
            } else {
                false to "Gagal menulis file APK ke folder penyimpanan."
            }
        } catch (e: Exception) {
            false to "Terjadi kesalahan saat mengekstrak APK: ${e.localizedMessage}"
        }
    }

    fun shareApk(context: Context): Boolean {
        return try {
            val sourceApk = File(context.applicationInfo.sourceDir)
            val cacheDir = File(context.cacheDir, "exported").apply { mkdirs() }
            val destApk = File(cacheDir, "HealthTracker.apk")
            sourceApk.copyTo(destApk, overwrite = true)

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destApk
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "HealthTracker.apk")
                putExtra(Intent.EXTRA_TEXT, "File installer HealthTracker APK")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Bagikan / Simpan File APK").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun openBrowser(context: Context, url: String = "https://file.kiwi") {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
