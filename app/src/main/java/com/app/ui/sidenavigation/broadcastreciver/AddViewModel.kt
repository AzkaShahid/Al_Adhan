package com.app.ui.sidenavigation.broadcastreciver

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.ads.AdRequest

class AdViewModel(application: Application) : AndroidViewModel(application) {
    val adRequest: MutableLiveData<AdRequest> = MutableLiveData()

    init {
        loadAd()
    }

    private fun loadAd() {
        val request = AdRequest.Builder().build()
        adRequest.postValue(request)
    }
}
