package com.ledapp.controller

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Kapselt die klassische Bluetooth-SPP-Verbindung zum HC-05/HC-06-Modul.
 * Verbindung und Schreibvorgänge laufen auf einem Hintergrundthread,
 * damit die UI nie blockiert.
 */
class BluetoothManagerHelper(
    private val onStatusChanged: (Connected: Boolean, message: String) -> Unit
) {
    companion object {
        private const val TAG = "BluetoothHelper"
        // Standard SPP UUID - funktioniert mit HC-05 / HC-06
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    val isConnected: Boolean
        get() = socket?.isConnected == true

    fun connect(device: BluetoothDevice) {
        executor.execute {
            try {
                // createRfcommSocketToServiceRecord ist der zuverlässigste Weg für SPP-Module
                val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
                sock.connect()
                socket = sock
                outputStream = sock.outputStream
                onStatusChanged(true, "Verbunden mit ${safeName(device)}")
            } catch (e: IOException) {
                Log.e(TAG, "Verbindung fehlgeschlagen", e)
                closeQuietly()
                onStatusChanged(false, "Verbindung fehlgeschlagen: ${e.message}")
            } catch (se: SecurityException) {
                Log.e(TAG, "Keine Berechtigung", se)
                onStatusChanged(false, "Bluetooth-Berechtigung fehlt")
            }
        }
    }

    private fun safeName(device: BluetoothDevice): String {
        return try { device.name ?: device.address } catch (se: SecurityException) { device.address }
    }

    /** Sendet einen Befehl. Das Endzeichen '~' wird von den Aufrufern bereits angehängt. */
    fun send(command: String) {
        val out = outputStream ?: run {
            onStatusChanged(false, "Nicht verbunden")
            return
        }
        executor.execute {
            try {
                out.write(command.toByteArray(Charsets.US_ASCII))
                out.flush()
            } catch (e: IOException) {
                Log.e(TAG, "Senden fehlgeschlagen", e)
                closeQuietly()
                onStatusChanged(false, "Verbindung verloren")
            }
        }
    }

    fun disconnect() {
        executor.execute {
            closeQuietly()
            onStatusChanged(false, "Getrennt")
        }
    }

    private fun closeQuietly() {
        try { outputStream?.close() } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        outputStream = null
        socket = null
    }

    fun shutdown() {
        closeQuietly()
        executor.shutdown()
    }
}
