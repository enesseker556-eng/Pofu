package com.pofu.rider.voice

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
 * Dongu: wake word beklenir -> bip -> komut dinlenir -> komut uygulanir ->
 * sesli cevap verilir -> tekrar beklemeye doner.
 *
 * Wake word ile konusma tanima ayni mikrofonu paylasamaz; bu yuzden dinlemeye
 * gecerken wake word duraklatilir, is bitince geri acilir.
 */
class VoiceService : Service() {

    private val main = Handler(Looper.getMainLooper())

    private lateinit var executor: CommandExecutor
    private lateinit var speaker: Speaker
    private lateinit var audioRoute: AudioRoute
    private lateinit var wakeWord: WakeWordEngine

    private var recognizer: SpeechRecognizer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var tone: ToneGenerator? = null
    private var testReceiver: BroadcastReceiver? = null

    /** Ayni anda iki dinleme baslatmamak icin. */
    private var busy = false

    /** Her dinleme dongusu icin artan sayac: gecikmeli geri cagrilarin
     *  bir sonraki donguyu bozmasini engeller. */
    private var cycleId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        executor = CommandExecutor(this)
        speaker = Speaker(this)
        audioRoute = AudioRoute(this)
        wakeWord = WakeWordEngine(this) { main.post { onWakeWordDetected() } }
        tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull()
        Log.i(TAG, "onCreate; debug=${BuildConfig.DEBUG}")
        registerTestReceiver()
    }

    /**
     * Sadece debug derlemesinde: mikrofonu atlayip komut metnini dogrudan besler.
     * Emulatorde mikrofon olmadigi icin tum komutlari boyle deneyebiliyoruz.
     *
     *   adb shell am broadcast -a com.pofu.rider.TEST_COMMAND \
     *       -p com.pofu.rider --es text "ahmeti ara"
     */
    private fun registerTestReceiver() {
        if (!BuildConfig.DEBUG) return
        testReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val text = intent?.getStringExtra("text").orEmpty()
                if (text.isBlank()) return
                Log.i(TAG, "TEST komut: $text")
                main.post {
                    busy = true
                    cycleId++
                    handleResults(listOf(text))
                }
            }
        }
        Log.i(TAG, "test alicisi kaydediliyor")
        val filter = IntentFilter("com.pofu.rider.TEST_COMMAND")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(testReceiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(testReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_LISTEN -> {
                startForegroundSafely()
                main.post { onWakeWordDetected() }
                return START_STICKY
            }
        }

        startForegroundSafely()
        Prefs.serviceEnabled = true
        acquireWakeLock()

        if (Prefs.useBluetoothMic) audioRoute.enableBluetoothMic()

        val ok = wakeWord.start()
        update(
            state.value.copy(
                phase = Phase.WAITING,
                wakeWord = wakeWord.activeKeyword,
                error = if (ok) null else wakeWord.lastError
            )
        )
        notifyUpdate()
        return START_STICKY
    }

    // ---------------------------------------------------------------- dinleme

    private fun onWakeWordDetected() {
        if (busy) return
        busy = true
        cycleId++
        wakeWord.pause()
        beep()
        update(state.value.copy(phase = Phase.LISTENING, heard = "", reply = "", error = null))
        notifyUpdate()
        // Bip sesi bitene kadar kisa bir nefes, yoksa bip'i komut saniyor.
        main.postDelayed({ startRecognition() }, 250)
    }

    private fun startRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            finishCycle("Konusma tanima bu telefonda yok")
            return
        }
        recognizer?.let { runCatching { it.destroy() } }
        val sr = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = sr
        sr.setRecognitionListener(listener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }
        try {
            sr.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startListening", e)
            finishCycle("Mikrofon acilamadi")
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onEndOfSpeech() {
            update(state.value.copy(phase = Phase.WORKING))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) update(state.value.copy(heard = text))
        }

        override fun onResults(results: Bundle?) {
            val candidates = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.filter { it.isNotBlank() }
                .orEmpty()
            handleResults(candidates)
        }

        override fun onError(error: Int) {
            finishCycle(errorMessage(error))
        }
    }

    /**
     * Konusma tanima birden fazla aday dondurur. Anlasilir ilk adayi secmek,
     * en yuksek skorluya korce guvenmekten daha iyi sonuc veriyor: motorda
     * birinci aday sik sik bozuk geliyor ama ikincisi dogru cikiyor.
     */
    private fun handleResults(candidates: List<String>) {
        if (candidates.isEmpty()) {
            finishCycle("Anlamadim")
            return
        }
        update(state.value.copy(heard = candidates.first(), phase = Phase.WORKING))

        var chosen: Command = Command.Unknown(candidates.first())
        for (c in candidates) {
            val parsed = CommandParser.parse(c)
            if (parsed !is Command.Unknown) {
                chosen = parsed
                break
            }
        }

        val reply = try {
            executor.execute(chosen)
        } catch (e: Exception) {
            Log.e(TAG, "komut uygulanamadi", e)
            "Bir sorun cikti"
        }
        Log.i(TAG, "SONUC duyulan=\"${candidates.first()}\" komut=$chosen cevap=\"$reply\"")
        finishCycle(reply)
    }

    /** Cevabi soyler, kaynaklari birakir, wake word'u geri acar. */
    private fun finishCycle(reply: String) {
        recognizer?.let { runCatching { it.cancel(); it.destroy() } }
        recognizer = null

        update(state.value.copy(phase = Phase.SPEAKING, reply = reply))
        notifyUpdate()

        val thisCycle = cycleId
        val backToWaiting = Runnable {
            if (!busy || cycleId != thisCycle) return@Runnable
            busy = false
            update(state.value.copy(phase = Phase.WAITING))
            wakeWord.resume()
            notifyUpdate()
        }

        if (reply.isNotBlank() && Prefs.speakFeedback) {
            speaker.say(reply) { main.post(backToWaiting) }
            // TTS hic geri donmezse dinleme sonsuza kadar kapali kalmasin.
            main.postDelayed(backToWaiting, 6000)
        } else {
            main.postDelayed(backToWaiting, 300)
        }
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Anlamadim"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> ""
        SpeechRecognizer.ERROR_AUDIO -> "Mikrofon hatasi"
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Internet yok"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mikrofon izni yok"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> ""
        else -> "Anlamadim"
    }

    private fun beep() {
        runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
    }

    // -------------------------------------------------------------- bildirim

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
            Phase.LISTENING -> "Dinliyorum..."
            Phase.WORKING -> s.heard.ifBlank { "Isleniyor..." }
            Phase.SPEAKING -> s.reply.ifBlank { "..." }
            else -> s.wakeWord + " bekleniyor"
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
            .setContentTitle("Pofu surus modu")
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

    override fun onDestroy() {
        Prefs.serviceEnabled = false
        main.removeCallbacksAndMessages(null)
        recognizer?.let { runCatching { it.destroy() } }
        recognizer = null
        testReceiver?.let { runCatching { unregisterReceiver(it) } }
        testReceiver = null
        wakeWord.release()
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

        /** Wake word'u beklemeden dogrudan dinlemeye gecer (ekran veya kask butonu). */
        fun triggerListen(ctx: Context) {
            ctx.startForegroundService(
                Intent(ctx, VoiceService::class.java).setAction(ACTION_LISTEN)
            )
        }
    }
}
