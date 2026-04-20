package com.polarapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiDefaultImpl
import io.reactivex.rxjava3.disposables.CompositeDisposable

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val api: PolarBleApi = PolarBleApiDefaultImpl.defaultImplementation(
        application,
        setOf(
            PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
            PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO
        )
    )
    val disposables = CompositeDisposable()

    var selectedDeviceId: String? = null
    var isConnected: Boolean = false
    var sessionStartMs: Long = 0L
    val hrBins: MutableList<Int> = mutableListOf()
    var currentHr: Int? = null
    var maxHr: Int? = null
    var minHr: Int? = null
    var lastStatus: String = ""

    override fun onCleared() {
        super.onCleared()
        disposables.clear()
        runCatching { api.shutDown() }
    }
}
