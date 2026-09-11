package com.pofu.rider.voice

/** Surus ekraninin ve bildirimin gosterdigi durum. */
enum class Phase { STOPPED, STARTING, WAITING, LISTENING, WORKING, SPEAKING }

data class VoiceState(
    val phase: Phase = Phase.STOPPED,
    val heard: String = "",
    val reply: String = "",
    val error: String? = null
)
