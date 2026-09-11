package com.pofu.rider.voice

/** Surus ekraninin ve bildirimin gosterdigi durum. */
enum class Phase { STOPPED, WAITING, LISTENING, WORKING, SPEAKING }

data class VoiceState(
    val phase: Phase = Phase.STOPPED,
    val heard: String = "",
    val reply: String = "",
    val wakeWord: String = "yok",
    val error: String? = null
)
