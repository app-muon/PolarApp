package com.polarapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiDefaultImpl
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import com.polarapp.ui.PulseGraphView

class MainActivity : AppCompatActivity() {

    private lateinit var api: PolarBleApi
    private val disposables = CompositeDisposable()

    private lateinit var deviceIdInput: EditText
    private lateinit var scanButton: Button
    private lateinit var connectButton: Button
    private lateinit var statusText: TextView
    private lateinit var hrText: TextView
    private lateinit var hrCurrent: TextView
    private lateinit var hrMax: TextView
    private lateinit var hrMin: TextView
    private lateinit var pulseGraph: PulseGraphView

    private var sessionStartMs: Long = 0L
    private var currentHr: Int? = null
    private var maxHr: Int? = null
    private var minHr: Int? = null
    private val hrBins: MutableList<Int> = mutableListOf()

    // Device the app is currently targeting
    private var selectedDeviceId: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        deviceIdInput = findViewById(R.id.deviceIdInput)
        scanButton = findViewById(R.id.scanButton)
        connectButton = findViewById(R.id.connectButton)
        statusText = findViewById(R.id.statusText)
        hrText = findViewById(R.id.hrText)
        hrCurrent = findViewById(R.id.hrCurrent)
        hrMax = findViewById(R.id.hrMax)
        hrMin = findViewById(R.id.hrMin)
        pulseGraph = findViewById(R.id.pulseGraph)

        api = PolarBleApiDefaultImpl.defaultImplementation(
            applicationContext,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO
            )
        )

        scanButton.setOnClickListener { ensurePermissionsAndScan() }
        connectButton.setOnClickListener { ensurePermissionsAndConnect() }
    }

    private fun ensurePermissionsAndScan() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }
        permissionLauncher.launch(permissions.toTypedArray())

        statusText.text = "Scanning for Polar devices..."

        // Listen for the first broadcast and use its deviceId
        val d = api.startListenForPolarHrBroadcasts(null)
            .observeOn(AndroidSchedulers.mainThread())
            .firstOrError()
            .subscribe({ data ->
                val foundId = data.polarDeviceInfo.deviceId
                selectedDeviceId = foundId
                deviceIdInput.setText(foundId)
                statusText.text = "Found device $foundId. Tap Connect to use this device."
            }, { error ->
                statusText.text = "Scan error: ${error.message}"
            })
        disposables.add(d)
    }

    private fun resetSession() {
        sessionStartMs = System.currentTimeMillis()
        currentHr = null
        maxHr = null
        minHr = null
        hrBins.clear()
        updateStatsUi()
        pulseGraph.setData(emptyList())
    }

    private fun onHeartRate(hr: Int) {
        runOnUiThread {
            if (currentHr == null) {
                statusText.text = "Receiving HR from ${selectedDeviceId ?: "device"}"
                resetSession()
            }
            currentHr = hr
            maxHr = maxHr?.let { kotlin.math.max(it, hr) } ?: hr
            minHr = minHr?.let { kotlin.math.min(it, hr) } ?: hr

            val elapsedMs = System.currentTimeMillis() - sessionStartMs
            val binIndex = if (elapsedMs < 0) 0 else (elapsedMs / 5000L).toInt()
            while (hrBins.size <= binIndex) {
                hrBins.add(currentHr ?: hr)
            }
            hrBins[binIndex] = hr

            hrText.text = "$hr bpm"
            updateStatsUi()
            pulseGraph.setData(hrBins.toList())
        }
    }

    private fun updateStatsUi() {
        hrCurrent.text = "Now: ${currentHr?.toString() ?: "--"}"
        hrMax.text = "Max: ${maxHr?.toString() ?: "--"}"
        hrMin.text = "Min: ${minHr?.toString() ?: "--"}"
    }

    private fun ensurePermissionsAndConnect() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }
        permissionLauncher.launch(permissions.toTypedArray())

        val idFromField = deviceIdInput.text.toString().trim()
        val id = when {
            selectedDeviceId != null -> selectedDeviceId
            idFromField.isNotEmpty() -> idFromField
            else -> null
        }

        if (id == null) {
            statusText.text = "Scan to find a device, then tap Connect."
            return
        }

        selectedDeviceId = id
        statusText.text = "Connecting to $id"

        runCatching { api.connectToDevice(id) }
            .onFailure { e ->
                runOnUiThread {
                    statusText.text = "Error connecting to $id: ${e.message}"
                }
            }

        // Clear previous HR subscriptions but keep api alive
        disposables.clear()

        val targetId = id

        // Listen to broadcasts from all devices, but accept only our selected one
        val d = api.startListenForPolarHrBroadcasts(null)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ data ->
                val broadcastId = data.polarDeviceInfo.deviceId
                if (broadcastId == targetId) {
                    onHeartRate(data.hr)
                }
            }, { error ->
                statusText.text = "HR error: ${error.message}"
            })
        disposables.add(d)
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
        runCatching { api.shutDown() }
    }
}
