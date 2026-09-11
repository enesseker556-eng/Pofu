package com.pofu.rider.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

data class PermissionItem(
    val key: String,
    val label: String,
    val why: String,
    val granted: Boolean,
    val required: Boolean
)

/** Uygulamanin calismasi icin gereken izinlerin tek yerden durumu. */
object Permissions {

    /** runtime izin dialoguyla istenebilenler. */
    fun runtimePermissions(): Array<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()

    fun has(ctx: Context, perm: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    /** Kilitli ekranin ustune cikabilmek ve arka plandan arama baslatabilmek icin sart. */
    fun canDrawOverlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun isBatteryUnrestricted(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun status(ctx: Context): List<PermissionItem> = buildList {
        add(
            PermissionItem(
                Manifest.permission.RECORD_AUDIO, "Mikrofon",
                "Wake word ve sesli komut icin. Bu olmadan hicbir sey calismaz.",
                has(ctx, Manifest.permission.RECORD_AUDIO), required = true
            )
        )
        add(
            PermissionItem(
                Manifest.permission.CALL_PHONE, "Telefon etme",
                "Komutla dogrudan arama baslatmak icin. Kapaliysa cevirici ekrani acilir.",
                has(ctx, Manifest.permission.CALL_PHONE), required = true
            )
        )
        add(
            PermissionItem(
                Manifest.permission.READ_CONTACTS, "Rehber",
                "Soyledigin ismi numaraya cevirmek icin.",
                has(ctx, Manifest.permission.READ_CONTACTS), required = true
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(
                PermissionItem(
                    Manifest.permission.POST_NOTIFICATIONS, "Bildirim",
                    "Surus modu kalici bildirimle calisir; izin yoksa Android servisi oldurur.",
                    has(ctx, Manifest.permission.POST_NOTIFICATIONS), required = true
                )
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(
                PermissionItem(
                    Manifest.permission.BLUETOOTH_CONNECT, "Bluetooth",
                    "Kask kulakliginin mikrofonuna gecmek icin.",
                    has(ctx, Manifest.permission.BLUETOOTH_CONNECT), required = false
                )
            )
        }
        add(
            PermissionItem(
                "overlay", "Diger uygulamalarin ustunde goster",
                "Telefon kilitliyken arama baslatabilmek ve surus ekranini acabilmek icin SART.",
                canDrawOverlay(ctx), required = true
            )
        )
        add(
            PermissionItem(
                "battery", "Pil optimizasyonundan muaf",
                "Olmazsa Android bir sure sonra dinlemeyi kapatir.",
                isBatteryUnrestricted(ctx), required = true
            )
        )
    }

    fun overlayIntent(ctx: Context) = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:" + ctx.packageName)
    )

    @android.annotation.SuppressLint("BatteryLife")
    fun batteryIntent(ctx: Context) = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:" + ctx.packageName)
    )
}
