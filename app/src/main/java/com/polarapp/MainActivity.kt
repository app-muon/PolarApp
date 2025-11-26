package com.polarapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polarapp.ui.PulseGraphView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable

class MainActivity : AppCompatActivity() {

    private lateinit var api: PolarBleApi
    private val disposables = CompositeDisposable()

    private lateinit var deviceIdField: TextView
    private lateinit var scanButton: Button
    private lateinit var connectButton: Button
    private lateinit var statusText: TextView
    private lateinit var hrText: TextView
    private lateinit var hrCurrent: TextView
    private lateinit var hrMax: TextView
    private lateinit var hrMin: TextView
    private lateinit var pulseGraph: PulseGraphView
    private lateinit var resetButton: Button
    private var isConnected: Boolean = false

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
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        deviceIdField = findViewById(R.id.deviceIdInput)
        scanButton = findViewById(R.id.scanButton)
        connectButton = findViewById(R.id.connectButton)
        statusText = findViewById(R.id.statusText)
        hrText = findViewById(R.id.hrText)
        hrCurrent = findViewById(R.id.hrCurrent)
        hrMax = findViewById(R.id.hrMax)
        hrMin = findViewById(R.id.hrMin)
        pulseGraph = findViewById(R.id.pulseGraph)
        resetButton = findViewById(R.id.resetButton)
        resetButton.setOnClickListener { resetSession() }

        api = PolarBleApiDefaultImpl.defaultImplementation(
            applicationContext,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO
            )
        )

        scanButton.setOnClickListener { ensurePermissionsAndScan() }
        connectButton.setOnClickListener {
            if (isConnected) {
                disconnectFromCurrentDevice()
            } else {
                ensurePermissionsAndConnect()
            }
        }
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

        statusText.text = getString(R.string.scanning_for_polar_devices)

        // Listen for the first broadcast and use its deviceId
        val d = api.startListenForPolarHrBroadcasts(null)
            .observeOn(AndroidSchedulers.mainThread())
            .firstOrError()
            .subscribe({ data ->
                val foundId = data.polarDeviceInfo.deviceId
                selectedDeviceId = foundId
                deviceIdField.text = foundId
                statusText.text =
                    getString(R.string.found_device_tap_connect_to_use_this_device, foundId)
            }, { error ->
                statusText.text = getString(R.string.scan_error, error.message)
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
                statusText.text =
                    getString(R.string.receiving_hr_from, selectedDeviceId ?: "device")
                isConnected = true
                connectButton.text = getString(R.string.disconnect)
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

            hrText.text = getString(R.string.bpm2, hr)
            updateStatsUi()
            pulseGraph.setData(hrBins.toList())
        }
    }

    private fun updateStatsUi() {
        hrCurrent.text = getString(R.string.stat_now, currentHr?.toString() ?: "—")
        hrMax.text = getString(R.string.stat_max, maxHr?.toString() ?: "—")
        hrMin.text = getString(R.string.stat_min, minHr?.toString() ?: "—")
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

        val id = selectedDeviceId

        if (id == null) {
            statusText.text = getString(R.string.scan_to_find_a_device_then_tap_connect)
            return
        }

        statusText.text = getString(R.string.connecting_to, id)

        runCatching { api.connectToDevice(id) }
            .onFailure { e ->
                runOnUiThread {
                    statusText.text = getString(R.string.error_connecting_to, id, e.message)
                }
            }

        disposables.clear()

        val targetId = id

        val d = api.startListenForPolarHrBroadcasts(null)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ data ->
                val broadcastId = data.polarDeviceInfo.deviceId
                if (broadcastId == targetId) {
                    onHeartRate(data.hr)
                }
            }, { error ->
                statusText.text = getString(R.string.hr_error, error.message)
            })
        disposables.add(d)
    }

    private fun disconnectFromCurrentDevice() {
        val id = selectedDeviceId
        if (id != null) {
            runCatching { api.disconnectFromDevice(id) }
        }
        isConnected = false
        selectedDeviceId = null
        connectButton.text = getString(R.string.connect)
        statusText.text = getString(R.string.disconnected)
        disposables.clear()
        resetSession()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
        runCatching { api.shutDown() }
    }
}
