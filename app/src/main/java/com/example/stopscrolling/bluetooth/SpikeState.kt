package com.example.stopscrolling.bluetooth

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/*
 * SP-02 (spike): what the test screen shows. The service and the screen run in the same
 * process, so a simple object is enough. This is test code, it is not for the final app.
 */
object SpikeState {

    private val _log = MutableStateFlow<List<String>>(emptyList())

    // The last lines of the log, the newest first
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _listening = MutableStateFlow(false)

    // True while the partner service is waiting for requests
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    fun add(text: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _log.update { old -> (listOf("$time  $text") + old).take(MAX_LINES) }
    }

    fun setListening(value: Boolean) {
        _listening.value = value
    }

    private const val MAX_LINES = 60
}
