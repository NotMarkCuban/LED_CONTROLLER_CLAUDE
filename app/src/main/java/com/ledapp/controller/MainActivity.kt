package com.ledapp.controller

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ledapp.controller.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var btHelper: BluetoothManagerHelper

    private var pairedDevices: List<BluetoothDevice> = emptyList()
    private var selectedDevice: BluetoothDevice? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    // Throttling, damit wir während des Ziehens am Regler/Farbrad nicht
    // das Bluetooth-Modul mit Befehlen fluten (Arduino-Puffer ist 32 Byte)
    private var lastColorSend = 0L
    private var lastBrightnessSend = 0L
    private var lastSpeedSend = 0L
    private val throttleMs = 120L

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            loadPairedDevices()
        } else {
            Toast.makeText(this, "Bluetooth-Berechtigungen werden benötigt", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val btManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = btManager.adapter

        btHelper = BluetoothManagerHelper { connected, message ->
            mainHandler.post {
                binding.tvStatus.text = message
                binding.btnConnect.text = if (connected) "Trennen" else "Verbinden"
            }
        }

        setupColorWheel()
        setupModeButtons()
        setupBrightnessSlider()
        setupSpeedSlider()
        setupConnectButton()

        ensurePermissionsAndLoadDevices()
    }

    // ── Berechtigungen & Geräteliste ──────────────────────────────
    private fun ensurePermissionsAndLoadDevices() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed += Manifest.permission.BLUETOOTH_CONNECT
            needed += Manifest.permission.BLUETOOTH_SCAN
        } else {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            loadPairedDevices()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun loadPairedDevices() {
        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Bitte Bluetooth aktivieren", Toast.LENGTH_LONG).show()
            return
        }
        try {
            pairedDevices = bluetoothAdapter.bondedDevices.toList()
        } catch (se: SecurityException) {
            Toast.makeText(this, "Keine Bluetooth-Berechtigung", Toast.LENGTH_LONG).show()
            return
        }
        val names = pairedDevices.map { device ->
            try { device.name ?: device.address } catch (se: SecurityException) { device.address }
        }
        if (names.isEmpty()) {
            Toast.makeText(
                this,
                "Keine gekoppelten Geräte gefunden. Bitte HC-05/HC-06 zuerst in den Android-Bluetooth-Einstellungen koppeln.",
                Toast.LENGTH_LONG
            ).show()
        }
        binding.spinnerDevices.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, names
        )
    }

    // ── Verbindung ────────────────────────────────────────────────
    private fun setupConnectButton() {
        binding.btnConnect.setOnClickListener {
            if (btHelper.isConnected) {
                btHelper.disconnect()
                return@setOnClickListener
            }
            val index = binding.spinnerDevices.selectedItemPosition
            if (index < 0 || index >= pairedDevices.size) {
                Toast.makeText(this, "Kein Gerät ausgewählt", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            selectedDevice = pairedDevices[index]
            binding.tvStatus.text = "Verbinde…"
            selectedDevice?.let { btHelper.connect(it) }
        }
    }

    // ── Farbrad ──────────────────────────────────────────────────
    private fun setupColorWheel() {
        binding.colorWheel.setColor(255, 0, 0)
        binding.colorWheel.listener = object : ColorWheelView.OnColorChangeListener {
            override fun onColorChanged(r: Int, g: Int, b: Int, finalValue: Boolean) {
                val now = System.currentTimeMillis()
                if (finalValue || now - lastColorSend >= throttleMs) {
                    lastColorSend = now
                    btHelper.send("N$r,$g,$b~")
                }
            }
        }
    }

    // ── Modus-Buttons ────────────────────────────────────────────
    private fun setupModeButtons() {
        binding.btnModeColor.setOnClickListener {
            // Aktuelle Farbe erneut senden -> Arduino setzt mode=0 automatisch
            val c = binding.colorWheel.currentColor
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            btHelper.send("N$r,$g,$b~")
        }
        binding.btnModeRainbow.setOnClickListener {
            btHelper.send("M1~")
        }
        binding.btnModeRainbowSlow.setOnClickListener {
            btHelper.send("M2~")
        }
    }

    // ── Helligkeit ───────────────────────────────────────────────
    private fun setupBrightnessSlider() {
        binding.seekBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvBrightnessLabel.text = "Helligkeit: $progress"
                if (!fromUser) return
                val now = System.currentTimeMillis()
                if (now - lastBrightnessSend >= throttleMs) {
                    lastBrightnessSend = now
                    btHelper.send("B$progress~")
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                btHelper.send("B${seekBar?.progress ?: 150}~")
            }
        })
    }

    // ── Rainbow-Geschwindigkeit ──────────────────────────────────
    private fun setupSpeedSlider() {
        binding.seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvSpeedLabel.text = "Rainbow-Geschwindigkeit: $progress"
                if (!fromUser) return
                val now = System.currentTimeMillis()
                if (now - lastSpeedSend >= throttleMs) {
                    lastSpeedSend = now
                    btHelper.send("R$progress~")
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                btHelper.send("R${seekBar?.progress ?: 25}~")
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        btHelper.shutdown()
    }
}
