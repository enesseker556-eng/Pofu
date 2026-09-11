package com.pofu.rider.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Mikrofonu kask Bluetooth kulakligina yonlendirir.
 * Motor ruzgar sesinde telefonun kendi mikrofonu ise yaramaz; bu kisim kritik.
 */
class AudioRoute(context: Context) {

    private val am = context.getSystemService(AudioManager::class.java)
    private var scoStarted = false

    @SuppressLint("MissingPermission")
    fun enableBluetoothMic(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val bt = am.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                } ?: return false
                am.setCommunicationDevice(bt)
            } else {
                @Suppress("DEPRECATION")
                if (!am.isBluetoothScoAvailableOffCall) return false
                @Suppress("DEPRECATION")
                am.startBluetoothSco()
                @Suppress("DEPRECATION")
                am.isBluetoothScoOn = true
                scoStarted = true
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bluetooth mikrofonu acilamadi", e)
            false
        }
    }

    fun release() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            } else if (scoStarted) {
                @Suppress("DEPRECATION")
                am.isBluetoothScoOn = false
                @Suppress("DEPRECATION")
                am.stopBluetoothSco()
                scoStarted = false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bluetooth mikrofonu kapatilamadi", e)
        }
    }

    private companion object { const val TAG = "PofuAudioRoute" }
}
