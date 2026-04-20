package com.polarapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import com.polarapp.ui.PulseGraphView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    private lateinit var deviceIdText: TextView
    private lateinit var scanButton: Button
    private lateinit var connectButton: Button
    private lateinit var statusText: TextView
    private lateinit var hrText: TextView
    private lateinit var hrCurrent: TextView
    private lateinit var hrMax: TextView
    private lateinit var hrMin: TextView
    private lateinit var pulseGraph: PulseGraphView
    private lateinit var resetButton: Button

    private var pendingAction: (() -> Unit)? = null

    companion object {
        private const val MAX_HR_BINS = 720 // 1 hour at 5-second bins
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        pendingAction?.invoke()
        pendingAction = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val rootLayout = findViewById<android.view.View>(R.id.rootLayout)
        val basePadding = androidx.core.graphics.Insets.of(
            rootLayout.paddingLeft, rootLayout.paddingTop,
            rootLayout.paddingRight, rootLayout.paddingBottom
        )
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                left   = basePadding.left   + bars.left,
                top    = basePadding.top    + bars.top,
                right  = basePadding.right  + bars.right,
                bottom = basePadding.bottom + bars.bottom
            )
            insets
        }

        deviceIdText = findViewById(R.id.deviceIdText)
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

        // Restore all UI from ViewModel (works for both fresh launch and rotation)
        restoreUiFromViewModel()

        // If connected, the old subscription had callbacks into the destroyed Activity's
        // views. Clear it and start a fresh one pointing at the new views.
        if (viewModel.isConnected) {
            subscribeToHrBroadcasts()
        }

        scanButton.setOnClickListener { requirePermissions(::startScan) }
        connectButton.setOnClickListener {
            if (viewModel.isConnected) {
                disconnectFromCurrentDevice()
            } else {
                requirePermissions(::startConnect)
            }
        }
    }

    private fun restoreUiFromViewModel() {
        deviceIdText.text = viewModel.selectedDeviceId ?: ""
        statusText.text = viewModel.lastStatus.ifEmpty { getString(R.string.status_idle) }
        connectButton.text = getString(
            if (viewModel.isConnected) R.string.disconnect else R.string.connect
        )
        hrText.text = viewModel.currentHr
            ?.let { getString(R.string.bpm_format, it) }
            ?: getString(R.string.bpm)
        updateStatsUi()
        pulseGraph.setData(viewModel.hrBins.toList())
    }

    private fun buildRequiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun requirePermissions(action: () -> Unit) {
        val permissions = buildRequiredPermissions()
        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(permissions)
        }
    }

    private fun startScan() {
        if (viewModel.isConnected) return

        setStatus(getString(R.string.scanning_for_polar_devices))

        val d = viewModel.api.startListenForPolarHrBroadcasts(null)
            .observeOn(AndroidSchedulers.mainThread())
            .firstOrError()
            .subscribe({ data ->
                val foundId = data.polarDeviceInfo.deviceId
                viewModel.selectedDeviceId = foundId
                deviceIdText.text = foundId
                setStatus(getString(R.string.found_device_tap_connect_to_use_this_device, foundId))
            }, { error ->
                setStatus(getString(R.string.scan_error, error.message))
            })
        viewModel.disposables.add(d)
    }

    private fun resetSession() {
        viewModel.sessionStartMs = System.currentTimeMillis()
        viewModel.currentHr = null
        viewModel.maxHr = null
        viewModel.minHr = null
        viewModel.hrBins.clear()
        hrText.text = getString(R.string.bpm)
        updateStatsUi()
        pulseGraph.setData(emptyList())
    }

    private fun onHeartRate(hr: Int) {
        viewModel.currentHr = hr
        viewModel.maxHr = viewModel.maxHr?.let { kotlin.math.max(it, hr) } ?: hr
        viewModel.minHr = viewModel.minHr?.let { kotlin.math.min(it, hr) } ?: hr

        val elapsedMs = System.currentTimeMillis() - viewModel.sessionStartMs
        val binIndex = if (elapsedMs < 0) 0 else (elapsedMs / 5000L).toInt()

        if (binIndex < MAX_HR_BINS) {
            while (viewModel.hrBins.size <= binIndex) viewModel.hrBins.add(hr)
            viewModel.hrBins[binIndex] = hr
        }

        hrText.text = getString(R.string.bpm_format, hr)
        updateStatsUi()
        pulseGraph.setData(viewModel.hrBins.toList())
    }

    private fun updateStatsUi() {
        hrCurrent.text = getString(R.string.stat_now, viewModel.currentHr?.toString() ?: "—")
        hrMax.text = getString(R.string.stat_max, viewModel.maxHr?.toString() ?: "—")
        hrMin.text = getString(R.string.stat_min, viewModel.minHr?.toString() ?: "—")
    }

    private fun setStatus(text: String) {
        statusText.text = text
        viewModel.lastStatus = text
    }

    /** Starts a fresh HR broadcast subscription using the current Activity's view references. */
    private fun subscribeToHrBroadcasts() {
        val id = viewModel.selectedDeviceId ?: return
        viewModel.disposables.clear()
        val d = viewModel.api.startListenForPolarHrBroadcasts(null)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ data ->
                if (data.polarDeviceInfo.deviceId == id) {
                    if (viewModel.currentHr == null) {
                        setStatus(getString(R.string.receiving_hr_from, id))
                    }
                    onHeartRate(data.hr)
                }
            }, { error ->
                setStatus(getString(R.string.hr_error, error.message))
            })
        viewModel.disposables.add(d)
    }

    private fun startConnect() {
        val id = viewModel.selectedDeviceId

        if (id == null) {
            setStatus(getString(R.string.scan_to_find_a_device_then_tap_connect))
            return
        }

        setStatus(getString(R.string.connecting_to, id))
        viewModel.isConnected = true
        connectButton.text = getString(R.string.disconnect)
        resetSession()

        val connectResult = runCatching { viewModel.api.connectToDevice(id) }
        if (connectResult.isFailure) {
            viewModel.isConnected = false
            connectButton.text = getString(R.string.connect)
            setStatus(getString(R.string.error_connecting_to, id, connectResult.exceptionOrNull()?.message))
            return
        }

        subscribeToHrBroadcasts()
    }

    private fun disconnectFromCurrentDevice() {
        val id = viewModel.selectedDeviceId
        if (id != null) {
            runCatching { viewModel.api.disconnectFromDevice(id) }
        }
        viewModel.isConnected = false
        viewModel.selectedDeviceId = null
        connectButton.text = getString(R.string.connect)
        setStatus(getString(R.string.disconnected))
        viewModel.disposables.clear()
        resetSession()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Always clear subscriptions — their lambdas close over this Activity's views.
        // If rotating, onCreate will re-subscribe with fresh view references.
        // If genuinely finishing, ViewModel.onCleared() handles api.shutDown().
        viewModel.disposables.clear()
    }
}
