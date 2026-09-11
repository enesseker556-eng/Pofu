package com.pofu.rider.voice

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pofu.rider.BuildConfig
import com.pofu.rider.PofuApp
import com.pofu.rider.R
import com.pofu.rider.core.Command
import com.pofu.rider.core.CommandParser
import com.pofu.rider.core.Prefs
import com.pofu.rider.ui.RideActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Surus modunun motoru.
 *
 * Tek bir cihaz ustu tanima akisi calisiyor: "Hey Panda" duyulunca bip calip
 * komutu bekliyor, komut gelince uygulayip sesli cevap veriyor ve tekrar
 * uyandirma beklemeye doniyor.
 *
 * Konusurken mikrofon duraklatiliyor, yoksa kendi sesini komut saniyor.
 */
class VoiceService : Service() {

    private val main = Handler(Looper.getMainLooper())

    private lateinit var executor: CommandExecutor
    private lateinit var speaker: Speaker
    private lateinit var audioRoute: AudioRoute
    private lateinit var engine: VoskEngine

    private var wakeLock: PowerManager.WakeLock? = null
    private var tone: ToneGenerator? = null
    private var testReceiver: BroadcastReceiver? = null

    /** Bir komut islenirken ikincisine baslamamak icin. */
    private var busy = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        executor = CommandExecutor(this)
        speaker = Speaker(this)
        audioRoute = AudioRoute(this)
        tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull()

        engine = VoskEngine(
            ctx = this,
            onWake = { main.post { onWake() } },
            onCommand = { text -> main.post { onCommand(text) } },
            onPartial = { text -> main.post { update(state.value.copy(heard = text)) } },
            onState = { ready, error -> main.post { onEngineState(ready, error) } }
        )
        registerTestReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_LISTEN -> {
                startForegroundSafely()
                if (engine.isReady) engine.forceWake() else engine.start()
                return START_STICKY
            }
        }

        startForegroundSafely()
        Prefs.serviceEnabled = true
        acquireWakeLock()
        audioRoute.enableBluetoothMic()

        update(state.value.copy(phase = Phase.STARTING))
        notifyUpdate()
        engine.start()
        scheduleTick()
        return START_STICKY
    }

    // ----------------------------------------------------------------- akis

    private fun onEngineState(ready: Boolean, error: String?) {
        update(
            state.value.copy(
                phase = if (ready) Phase.WAITING else Phase.STARTING,
                error = error
            )
        )
        notifyUpdate()
    }

    private fun onWake() {
        if (busy) return
        beep()
        update(state.value.copy(phase = Phase.LISTENING, heard = "", reply = "", error = null))
        notifyUpdate()
    }

    private fun onCommand(text: String) {
        if (busy) return
        busy = true
        update(state.value.copy(phase = Phase.WORKING, heard = text))
        notifyUpdate()

        val cmd = CommandParser.parse(text)
        val reply = try {
            executor.execute(cmd)
        } catch (e: Exception) {
            Log.e(TAG, "komut uygulanamadi", e)
            "Bir sorun cikti"
        }
        Log.i(TAG, "SONUC duyulan=\"$text\" komut=$cmd cevap=\"$reply\"")
        finishCycle(reply)
    }

    /** Cevabi soyler, sonra tekrar uyandirma beklemeye doner. */
    private fun finishCycle(reply: String) {
        update(state.value.copy(phase = Phase.SPEAKING, reply = reply))
        notifyUpdate()

        val backToWaiting = Runnable {
            if (!busy) return@Runnable
            busy = false
            engine.pause(false)
            update(state.value.copy(phase = Phase.WAITING))
            notifyUpdate()
        }

        if (reply.isNotBlank() && Prefs.speakFeedback) {
            // Kendi sesimizi duymamak icin konusurken mikrofonu kapatiyoruz.
            engine.pause(true)
            speaker.say(reply) { main.postDelayed(backToWaiting, 250) }
            // TTS hic geri donmezse dinleme sonsuza kadar kapali kalmasin.
            main.postDelayed(backToWaiting, 8000)
        } else {
            main.postDelayed(backToWaiting, 250)
        }
    }

    /**
     * Uyandiktan sonra komut gelmezse motor komut dinleme modunda asili
     * kalmasin diye saniyede bir yokluyoruz.
     */
    private fun scheduleTick() {
        main.postDelayed(tickRunnable, 1000)
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            val before = state.value.phase
            engine.tick()
            if (before == Phase.LISTENING && !busy && !engine.isAwake) {
                update(state.value.copy(phase = Phase.WAITING))
                notifyUpdate()
            }
            main.postDelayed(this, 1000)
        }
    }

    private fun beep() {
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
    }

    // ------------------------------------------------------------ bildirim

    private fun startForegroundSafely() {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun buildNotification(): Notification {
        val s = state.value
        val text = when (s.phase) {
            Phase.STARTING -> s.error ?: "Hazırlanıyor..."
            Phase.LISTENING -> "Dinliyorum..."
            Phase.WORKING -> s.heard.ifBlank { "İşleniyor..." }
            Phase.SPEAKING -> s.reply.ifBlank { "..." }
            else -> "\"Hey Panda\" de"
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, RideActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, VoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, PofuApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Pofu sürüş modu")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Durdur", stop)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun notifyUpdate() {
        runCatching { NotificationManagerCompat.from(this).notify(NOTIF_ID, buildNotification()) }
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pofu:ride").apply {
            setReferenceCounted(false)
            acquire(8 * 60 * 60 * 1000L)
        }
    }

    /**
     * Sadece debug derlemesinde: mikrofonu atlayip komut metnini dogrudan besler.
     *
     *   adb shell am broadcast -a com.pofu.rider.TEST_COMMAND \
     *       -p com.pofu.rider --es text "ahmeti ara"
     */
    private fun registerTestReceiver() {
        if (!BuildConfig.DEBUG) return
        testReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.getStringExtra("grammar")?.let { g ->
                    // Sozluk testi: "pofu" modelin kelime dagarciginda var mi?
                    Thread {
                        val wav = intent.getStringExtra("wav") ?: "/data/local/tmp/wake.wav"
                        val heard = engine.transcribeFile(wav, g)
                        Log.i(TAG, "DILBILGISI g=$g duyulan=\"$heard\"")
                    }.start()
                    return
                }
                intent?.getStringExtra("wav")?.let { path ->
                    // Ses hatti testi: dosyayi modele verip ne duydugunu yaz.
                    Thread {
                        val heard = engine.transcribeFile(path)
                        val wake = com.pofu.rider.core.WakeWord.isMatch(heard)
                        val rest = com.pofu.rider.core.WakeWord.remainder(heard)
                        Log.i(TAG, "SES dosya=\"$path\" duyulan=\"$heard\" uyandi=$wake kalan=\"$rest\"")
                    }.start()
                    return
                }
                val text = intent?.getStringExtra("text").orEmpty()
                if (text.isBlank()) return
                main.post {
                    busy = false
                    onCommand(text)
                }
            }
        }
        val filter = IntentFilter("com.pofu.rider.TEST_COMMAND")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(testReceiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(testReceiver, filter)
        }
    }

    override fun onDestroy() {
        Prefs.serviceEnabled = false
        main.removeCallbacksAndMessages(null)
        testReceiver?.let { runCatching { unregisterReceiver(it) } }
        testReceiver = null
        engine.release()
        speaker.release()
        audioRoute.release()
        tone?.release()
        tone = null
        runCatching { wakeLock?.release() }
        wakeLock = null
        update(VoiceState())
        super.onDestroy()
    }

    private fun update(newState: VoiceState) {
        _state.value = newState
    }

    companion object {
        private const val TAG = "PofuVoice"
        private const val NOTIF_ID = 4242
        const val ACTION_STOP = "com.pofu.rider.STOP"
        const val ACTION_LISTEN = "com.pofu.rider.LISTEN"

        private val _state = MutableStateFlow(VoiceState())
        val state: StateFlow<VoiceState> = _state.asStateFlow()

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, VoiceService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, VoiceService::class.java).setAction(ACTION_STOP))
        }

        /** Uyandirmayi beklemeden dinlemeye gecer (ekrandaki mikrofon tusu). */
        fun triggerListen(ctx: Context) {
            ctx.startForegroundService(
                Intent(ctx, VoiceService::class.java).setAction(ACTION_LISTEN)
            )
        }
    }
}
