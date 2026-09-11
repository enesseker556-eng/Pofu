package com.pofu.rider.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.pofu.rider.core.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Release(
    val versionCode: Long,
    val versionName: String,
    val notes: String,
    /** Depodaki APK'nin ham indirme adresi. */
    val downloadUrl: String
)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val release: Release) : UpdateStatus
    data class Downloading(val percent: Int) : UpdateStatus
    data class Ready(val apk: File, val release: Release) : UpdateStatus
    data class Failed(val message: String) : UpdateStatus
}

/**
 * Uygulama kendini depodan gunceller.
 *
 * Uygulama her acildiginda dist/version.json okunur; oradaki versionCode
 * telefondakinden buyukse APK indirilir ve kurulum ekrani acilir. Son "Yukle"
 * dokunusunu kaldiramiyoruz: sessiz kurulum sadece sistem uygulamalarinin
 * alabildigi bir yetki.
 */
object Updater {

    private const val TAG = "PofuUpdater"

    /**
     * Surum bilgisi depodaki dist/version.json dosyasindan geliyor.
     *
     * Neden GitHub Releases degil: Actions faturalandirma kilidi yuzunden hic
     * release yayinlanamiyor. Depo public oldugu icin ham dosya adresi hem
     * token istemiyor hem de her push'ta kendiliginden guncel oluyor.
     */
    private fun manifestUrl(repo: String) =
        "https://raw.githubusercontent.com/$repo/main/dist/version.json"

    private fun apkUrl(repo: String, path: String) =
        "https://github.com/$repo/raw/main/$path"

    /** Telefonda kurulu olan surum. */
    fun currentVersionCode(ctx: Context): Long {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    fun currentVersionName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    /** Son surumu sorgular. Guncel ise null doner. */
    suspend fun check(ctx: Context): Result<Release?> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = Prefs.updateRepo
            if (repo.isBlank()) error("Guncelleme deposu ayarlanmamis")

            val obj = JSONObject(httpGet(manifestUrl(repo), "application/json"))
            val code = obj.optLong("versionCode", -1L)
            if (code < 0) error("version.json icinde versionCode yok")

            val release = Release(
                versionCode = code,
                versionName = obj.optString("versionName").ifBlank { "v$code" },
                notes = obj.optString("notes").take(500),
                downloadUrl = apkUrl(repo, obj.optString("apk").ifBlank { "dist/pofu.apk" })
            )
            Prefs.lastCheckAt = System.currentTimeMillis()
            if (release.versionCode > currentVersionCode(ctx)) release else null
        }
    }

    /** APK'yi indirir. [onProgress] 0..100 arasi. */
    suspend fun download(
        ctx: Context,
        release: Release,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            // Eski indirmeleri temizle, yer kaplamasin.
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "pofu-${release.versionCode}.apk")

            val conn = open(release.downloadUrl, "application/octet-stream")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    var lastPct = -1
                    while (input.read(buf).also { read = it } > 0) {
                        output.write(buf, 0, read)
                        done += read
                        if (total > 0) {
                            val pct = ((done * 100) / total).toInt()
                            if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                        }
                    }
                }
            }
            conn.disconnect()
            if (out.length() < 1024) {
                out.delete()
                error("Indirilen dosya bozuk")
            }
            out
        }
    }

    /** Android'in kurulum ekranini acar. Kurulumu kullanici onaylar. */
    fun install(ctx: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    /** Android 8+ "bilinmeyen kaynaklardan kuruluma izin" durumu. */
    fun canInstall(ctx: Context): Boolean =
        ctx.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(ctx: Context): Intent = Intent(
        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:" + ctx.packageName)
    )

    // ------------------------------------------------------------------ http

    private fun httpGet(url: String, accept: String): String {
        val conn = open(url, accept)
        return conn.inputStream.bufferedReader().use { it.readText() }
            .also { conn.disconnect() }
    }

    private fun open(url: String, accept: String): HttpURLConnection {
        var current = url
        // GitHub asset adresleri S3'e yonlendiriyor; HttpURLConnection
        // https->https yonlendirmesini otomatik izlemedigi icin elle takip ediyoruz.
        repeat(5) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 60_000
                setRequestProperty("Accept", accept)
                setRequestProperty("User-Agent", "Pofu-Updater")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                val token = Prefs.githubToken
                if (token.isNotBlank() && current.startsWith("https://api.github.com")) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }
            when (val code = conn.responseCode) {
                in 200..299 -> return conn
                301, 302, 303, 307, 308 -> {
                    val next = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (next.isNullOrBlank()) error("Yonlendirme adresi yok")
                    current = next
                }
                401, 403 -> {
                    val body = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    conn.disconnect()
                    Log.w(TAG, "yetki hatasi: $body")
                    error("Yetki reddedildi ($code) - repo private ise token gerekli")
                }
                404 -> {
                    conn.disconnect()
                    error("Bulunamadi (404) - depo adi yanlis veya henuz release yok")
                }
                else -> {
                    conn.disconnect()
                    error("Sunucu hatasi: $code")
                }
            }
        }
        error("Cok fazla yonlendirme")
    }
}
