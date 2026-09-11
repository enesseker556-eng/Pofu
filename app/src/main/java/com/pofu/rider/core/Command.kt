package com.pofu.rider.core

/** Sesli komutun cozumlenmis hali. */
sealed interface Command {
    data class Call(val name: String) : Command
    data class Navigate(val destination: String) : Command
    data object Play : Command
    data object Pause : Command
    data object Next : Command
    data object Previous : Command
    data class Volume(val delta: Int) : Command
    data object HangUp : Command
    data object Cancel : Command
    data class Unknown(val raw: String) : Command
}
