package com.app.ui.sidenavigation.home


import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.app.bases.BaseViewModel
import com.app.database.CityDBModel
import com.app.models.prayer.PrayerResponse
import com.app.network.Resource
import com.app.respository.MainRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class HomeViewModel
@Inject
constructor(
    private val repository: MainRepository
) : BaseViewModel() {

    val prayerResponse: MutableLiveData<Resource<PrayerResponse>> = MutableLiveData()
    var citiesList: MutableLiveData<List<CityDBModel>> = MutableLiveData()

    fun callGetPrayerData(apiUrl: String) = viewModelScope.launch {
        prayerResponse.value = Resource.Loading
        prayerResponse.value = repository.callPrayerData(apiUrl)
    }


    fun insertCity(cityDBModel: CityDBModel) {
        viewModelScope.launch {
            repository.insertCity(cityDBModel)
        }
    }

    fun getAllCities() : LiveData<List<CityDBModel>>{
//        viewModelScope.launch {
//            repository.getAllCities().value.let {
//                citiesList.value = it
//
//            }
//        }

        return repository.getAllCities()

    }
}
