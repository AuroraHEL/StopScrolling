package com.example.stopscrolling.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.SystemClock
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * SP-02 (spike): a short text message between two phones with Classic Bluetooth (RFCOMM).
 *
 * The two phones must be PAIRED in the Android Bluetooth settings first. The pairing is done
 * by Android (it shows a code on both phones), the app only uses the paired devices.
 *
 * The message is one line of text. The partner answers with the line "ACK" so that the sender
 * knows that the message arrived.
 *
 * The permission BLUETOOTH_CONNECT is checked by the caller (see [BtSpikePermissions]).
 */
@SuppressLint("MissingPermission")
object Rfcomm {

    // Our own id for the service. Only an app that uses the same id answers.
    val APP_UUID: UUID = UUID.fromString("eade0f77-06c3-4b41-8b8a-ea2095ea233f")
    const val SERVICE_NAME = "StopScrollingSpike"

    private const val ACK = "ACK"

    // A connection that does nothing is closed after this time
    private const val CONNECTION_TIMEOUT_MS = 10_000L

    /* Result of [send]. [millis] is the time between the start and the answer (or the error). */
    data class SendResult(val ok: Boolean, val millis: Long, val error: String? = null)

    /*
     * Sends [text] to a paired [device] and waits for the ACK.
     * Call it from a coroutine: the work is done on the IO dispatcher.
     */
    suspend fun send(adapter: BluetoothAdapter, device: BluetoothDevice, text: String): SendResult =
        withContext(Dispatchers.IO) {
            val start = SystemClock.elapsedRealtime()
            var socket: BluetoothSocket? = null
            try {
                // A running discovery makes the connection slow. cancelDiscovery() needs
                // BLUETOOTH_SCAN (Android 12+) or BLUETOOTH_ADMIN (Android 11-), which we do not
                // request because the app never scans. Without it Android throws a
                // SecurityException (not an IOException) and the app crashes, so we ignore it.
                try {
                    adapter.cancelDiscovery()
                } catch (_: SecurityException) {
                    // No permission: our app never started a discovery, we just continue
                }
                socket = device.createRfcommSocketToServiceRecord(APP_UUID)
                socket.connect()

                // One line: we remove the new lines of the text
                val line = text.replace('\n', ' ').replace('\r', ' ')
                socket.outputStream.write((line + "\n").toByteArray(Charsets.UTF_8))
                socket.outputStream.flush()

                val answer = socket.inputStream.bufferedReader(Charsets.UTF_8).readLine()
                val elapsed = SystemClock.elapsedRealtime() - start
                if (answer == ACK) {
                    SendResult(ok = true, millis = elapsed)
                } else {
                    SendResult(ok = false, millis = elapsed, error = "No ACK (got: $answer)")
                }
            } catch (e: IOException) {
                // For example: the partner is not listening, Bluetooth is off, out of range
                SendResult(
                    ok = false,
                    millis = SystemClock.elapsedRealtime() - start,
                    error = e.message ?: e.javaClass.simpleName
                )
            } catch (e: SecurityException) {
                // A missing Bluetooth permission must show an error in the log, not crash the app
                SendResult(
                    ok = false,
                    millis = SystemClock.elapsedRealtime() - start,
                    error = "Permission problem: ${e.message}"
                )
            } finally {
                runCatching { socket?.close() }
            }
        }

    /*
     * The partner side: waits for connections and calls [onMessage] with the name of the
     * sender and the text. Start it from a foreground service, so that Android keeps the
     * process alive when the app is in the background.
     */
    class Server(
        private val adapter: BluetoothAdapter,
        private val onMessage: (from: String, text: String) -> Unit
    ) {
        @Volatile
        private var serverSocket: BluetoothServerSocket? = null
        private var job: Job? = null

        fun start(scope: CoroutineScope) {
            if (job != null) return
            job = scope.launch(Dispatchers.IO) {
                try {
                    // "Secure" socket: Android asks the pairing if the phones are not paired
                    val server = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, APP_UUID)
                    serverSocket = server
                    SpikeState.add("Listening for requests")

                    while (isActive) {
                        // accept() waits until a phone connects. It ends with an exception
                        // when stop() closes the server socket.
                        val socket = try {
                            server.accept()
                        } catch (e: IOException) {
                            break
                        }
                        // One coroutine for each connection: a slow phone does not block the others
                        launch { handle(socket) }
                    }
                } catch (e: IOException) {
                    SpikeState.add("Cannot listen: ${e.message}")
                } catch (e: SecurityException) {
                    SpikeState.add("Cannot listen, permission problem: ${e.message}")
                } finally {
                    closeServer()
                }
            }
        }

        fun stop() {
            closeServer()
            job?.cancel()
            job = null
        }

        private fun closeServer() {
            runCatching { serverSocket?.close() }
            serverSocket = null
        }

        private fun CoroutineScope.handle(socket: BluetoothSocket) {
            // Closes the connection if the sender does nothing (a blocking read cannot be
            // cancelled, but it ends with an exception when the socket is closed)
            val watchdog = launch {
                delay(CONNECTION_TIMEOUT_MS)
                runCatching { socket.close() }
            }
            try {
                val from = socket.remoteDevice.name ?: socket.remoteDevice.address
                val reader = socket.inputStream.bufferedReader(Charsets.UTF_8)
                val text = reader.readLine() ?: return

                socket.outputStream.write((ACK + "\n").toByteArray(Charsets.UTF_8))
                socket.outputStream.flush()
                onMessage(from, text)

                // Wait until the sender closes the connection: in this way the ACK is
                // surely delivered before we close our side
                reader.read()
            } catch (e: IOException) {
                SpikeState.add("Connection error: ${e.message}")
            } catch (e: SecurityException) {
                SpikeState.add("Connection error, permission problem: ${e.message}")
            } finally {
                watchdog.cancel()
                runCatching { socket.close() }
            }
        }
    }
}
