package com.app.respository

import androidx.lifecycle.LiveData
import com.app.bases.BaseRepository
import com.app.database.AppDao
import com.app.database.CityDBModel
import com.app.network.ApiClientInterface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * @author Muzzamil Saleem
 */
@Singleton
class MainRepository @Inject constructor(
    private val apiClientInterface: ApiClientInterface,
    private val appDao: AppDao
) :
    BaseRepository() {

    suspend fun callLogin(
        userName: String,
        password: String
    ) = safeApiCall {
        apiClientInterface.callLogin(userName, password)
    }
    suspend fun callPrayerData(apiUrl : String) = safeApiCall {
        apiClientInterface.getPrayerData(apiUrl)
    }

    suspend fun insertCity(city: CityDBModel) {
        appDao.insertCity(city)
    }

    fun getAllCities(): LiveData<List<CityDBModel>> {
        return appDao.getAllCities()
    }

}