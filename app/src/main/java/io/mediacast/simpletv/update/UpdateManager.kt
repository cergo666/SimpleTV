package io.mediacast.simpletv.update

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import io.mediacast.simpletv.BuildConfig
import io.mediacast.simpletv.data.Http
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

data class AppUpdate(
    val version: String,
    val assetName: String,
    val downloadUrl: String,
    val checksumsUrl: String?,
    val releaseUrl: String,
)

enum class UpdateInstallResult { OPENED, NEEDS_PERMISSION, FAILED }

/** Проверка и установка APK из GitHub Releases. */
object UpdateManager {
    private const val API = "https://api.github.com/repos/cergo666/SimpleTV/releases/latest"

    private fun userAgent() = "SimpleTV/${BuildConfig.VERSION_NAME}"

    fun check(currentVersion: String): AppUpdate? {
        val body = Http.getText(
            API,
            userAgent(),
            mapOf("Accept" to "application/vnd.github+json"),
        )
        val release = JSONObject(body)
        val version = release.optString("tag_name").removePrefix("v")
        if (version.isBlank() || !isNewer(version, currentVersion)) return null

        var apkName: String? = null
        var apkUrl: String? = null
        var checksumsUrl: String? = null
        val assets = release.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            when {
                name.endsWith(".apk", ignoreCase = true) -> {
                    apkName = name
                    apkUrl = asset.optString("browser_download_url")
                }
                name == "SHA256SUMS" -> checksumsUrl = asset.optString("browser_download_url")
            }
        }
        return AppUpdate(
            version = version,
            assetName = apkName ?: return null,
            downloadUrl = apkUrl ?: return null,
            checksumsUrl = checksumsUrl,
            releaseUrl = release.optString("html_url"),
        )
    }

    fun download(context: Context, update: AppUpdate, onProgress: (Float) -> Unit): File {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, update.assetName)
        val expected = update.checksumsUrl
            ?.let { runCatching { checksumFor(it, update.assetName) }.getOrNull() }
        if (target.isFile && (expected == null || sha256(target) == expected)) return target

        val temporary = File(directory, "${update.assetName}.part")
        Http.open(
            update.downloadUrl,
            userAgent(),
            headers = mapOf("Accept" to "application/octet-stream"),
        ).use { response ->
            if (!response.ok) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response")
            val total = response.contentLength
            var written = 0L
            temporary.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = body.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    written += read
                    if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }

        if (expected != null && sha256(temporary) != expected) {
            temporary.delete()
            throw IOException("Checksum mismatch")
        }
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        onProgress(1f)
        return target
    }

    fun install(context: Context, file: File): UpdateInstallResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:${context.packageName}".toUri(),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return UpdateInstallResult.NEEDS_PERMISSION
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return if (
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }.isSuccess
        ) {
            UpdateInstallResult.OPENED
        } else {
            UpdateInstallResult.FAILED
        }
    }

    private fun checksumFor(url: String, assetName: String): String? {
        val text = Http.getText(url, userAgent(), mapOf("Accept" to "text/plain"))
        return text.lineSequence()
            .map(String::trim)
            .firstOrNull { it.endsWith("  $assetName") || it.endsWith(" *$assetName") }
            ?.substringBefore(' ')
            ?.lowercase()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    internal fun isNewer(remote: String, current: String): Boolean {
        fun parts(value: String) = value
            .removePrefix("v")
            .substringBefore('-')
            .split('.')
            .map { it.toIntOrNull() ?: 0 }
        val remoteParts = parts(remote)
        val currentParts = parts(current)
        for (i in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }
}
