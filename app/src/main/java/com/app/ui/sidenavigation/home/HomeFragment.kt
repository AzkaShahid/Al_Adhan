package com.app.ui.sidenavigation.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.DatePickerDialog.OnDateSetListener
import android.app.PendingIntent
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Outline
import android.location.Geocoder
import android.location.Location
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.app.R
import com.app.databinding.FragmentHomeBinding
import com.app.bases.BaseFragment
import com.app.bottomsheets.CityBottomSheet
import com.app.broadcast.NetworkCallbackImpl
import com.app.database.CityDBModel
import com.app.listeners.AlarmReceiver
import com.app.models.CityModel
import com.app.models.prayer.Data
import com.app.models.prayer.Timings
import com.app.network.Resource
import com.app.utils.NetworkUtils
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.time.Duration

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding, HomeViewModel>(),
    CityBottomSheet.CitySelectedListener {


    var year: Int = 2024
    var month: Int = 2
    var dayOfMonth = 12
    var locationLat: Double = 29.3544
    var locationLng: Double = 71.6911
    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: NetworkCallbackImpl? = null
    private var datePickerDialog: DatePickerDialog? = null
    private var cityList = ArrayList<CityModel>()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var adView: AdView
    private var mInterstitialAd: InterstitialAd? = null

//    private lateinit var alarmManager: AlarmManager
//    private val alarmReceiver = AlarmReceiver()





    override val mViewModel: HomeViewModel by viewModels()


    override fun getBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentHomeBinding {
        return FragmentHomeBinding.inflate(inflater, container, false)
    }

    override fun getToolbarBinding() = null

    override fun getToolbarTitle() = R.string.menu_home

    override fun isMenuButton() = true

    @RequiresApi(Build.VERSION_CODES.O)
    override fun setupUI(savedInstanceState: Bundle?) {
        addCities()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        val calendar = Calendar.getInstance()
        year = calendar.get(Calendar.YEAR) // getCurrentYear
        month = calendar.get(Calendar.MONTH)+1 // getCurrentMnoth
        locationLat = cityList[0].latitude
        locationLng = cityList[0].longitude
        mViewBinding.cityName.text = cityList[0].cityName

        if (NetworkUtils.isNetworkAvailable(requireContext())) {
            fetchData()
            callPrayerTiming()
            setUpNetworkCallback()
        } else {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
        }
        mViewBinding.getCurrentLocation.setOnClickListener{
            getCurrentLocation(it)
        }

        MobileAds.initialize(requireContext()) {}

        adView = mViewBinding.adView
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)


        InterstitialAd.load(requireContext(),"ca-app-pub-3940256099942544/1033173712", adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                adError?.toString()?.let { Log.d(TAG, it) }
                mInterstitialAd = null
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.d(TAG, "Ad was loaded.")
                mInterstitialAd = interstitialAd
            }
        })
    }


    fun getCurrentLocation(view: View) {
        if (hasLocationPermission()) {
            requestLocation()
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }
    @SuppressLint("MissingPermission")
    private fun requestLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    fetchCityName(it.latitude, it.longitude)
                    fetchPrayerTimeData(it.latitude, it.longitude)
                } ?: run {
                    Toast.makeText(requireContext(), "Failed to get current location.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to get current location: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchCityName(latitude: Double, longitude: Double) {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        val addresses = geocoder.getFromLocation(latitude, longitude, 1)
        if (addresses != null) {
            if (addresses.isNotEmpty()) {
                val cityName = addresses[0].locality
                mViewBinding.cityName.text = cityName // Display city name in the cityName TextView
            } else {
                Toast.makeText(requireContext(), "Failed to fetch city name.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchPrayerTimeData(latitude: Double, longitude: Double) {
        val apiUrl =
            "https://api.aladhan.com/v1/calendar/$year/$month?latitude=$latitude&longitude=$longitude"
        mViewModel.callGetPrayerData(apiUrl)
    }

    private fun hasLocationPermission(): Boolean {

        return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }


    private fun setUpNetworkCallback() {
        connectivityManager =
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = NetworkCallbackImpl { isConnected ->
            if (isConnected) {
                Toast.makeText(requireContext(), "Network connected", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Network disconnected", Toast.LENGTH_SHORT).show()
            }
        }

        registerNetworkCallback()

    }


    private fun registerNetworkCallback() {
        networkCallback?.let { connectivityManager.registerDefaultNetworkCallback(it) }
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
    }


    private fun fetchData() {
        val apiUrl =
            "https://api.aladhan.com/v1/calendar/$year/$month?latitude=$locationLat&longitude=$locationLng"
        mViewModel.callGetPrayerData(apiUrl)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun callPrayerTiming() {
        mViewModel.prayerResponse.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    showProgressDialog()
                }

                is Resource.Success -> {
                    hideProgressDialog()
                    val response = resource.value

                    val timeList = response.data
                    //add null check here
                    if (!timeList.isNullOrEmpty()) {
                        //Current date day
                        val currentDate = LocalDate.now().dayOfMonth
                        val todayTimeDates = timeList.find {
                            it.date?.gregorian?.day?.toIntOrNull() == currentDate
                        }
                        todayTimeDates?.let { populateTodayTimes(it) }
                       // todayTimeDates?.let { setPrayerAlarms(it.timings) }

                        //val todayTimeDates = timeList.get(current-1)
                        // populateTodayTimes(todayTimeDates)
                    }


                    // adapter.updateAdapter(response.data as ArrayList<Data>)
                }

                is Resource.Failure -> {
                    hideProgressDialog()
                    Toast.makeText(mainActivity, resource.errorString, Toast.LENGTH_LONG).show()
                }

                else -> {}
            }

        }
    }




    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.O)
    private fun populateTodayTimes(todayTimeDates: Data) {
        val prayerTimings = todayTimeDates.timings
        val date = todayTimeDates.date
        prayerTimings?.let { model ->
            with(model) {
                mViewBinding.fajrtiming.text = formatTime(fajr)
                mViewBinding.sunrisetiming.text = formatTime(sunrise)
                mViewBinding.dhuhrtiming.text = formatTime(dhuhr)
                mViewBinding.asrtiming.text = formatTime(asr)
                mViewBinding.maghribtiming.text = formatTime(maghrib)
                mViewBinding.ishaTiming.text = formatTime(isha)
                mViewBinding.sunsetTiming.text = formatTime(sunset)
                mViewBinding.midnightTiming.text = formatTime(midnight)


                val currentTime = LocalTime.now()

                // Set colors based on prayer times
                setPrayerLayoutColor(mViewBinding.Fajr, fajr, currentTime)
                setPrayerLayoutColor(mViewBinding.dhuhr, dhuhr, currentTime)
                setPrayerLayoutColor(mViewBinding.Asr, asr, currentTime)
                setPrayerLayoutColor(mViewBinding.Maghrib, maghrib, currentTime)
                setPrayerLayoutColor(mViewBinding.Isha, isha, currentTime)

                //displayRemainingTimeForNextPrayer(model, currentTime)
               // setPrayerAlarm(prayerTimings)

            }


        }

        date?.let { model ->
            with(model) {
                mViewBinding.weekDay.text = gregorian?.weekday?.en
                mViewBinding.dateText.text = gregorian?.day
                mViewBinding.monthText.text = gregorian?.month?.en
                mViewBinding.yearText.text = gregorian?.year
                mViewBinding.weekDayAr.text = hijri?.weekday?.ar
                mViewBinding.dateTextAr.text = hijri?.day
                mViewBinding.monthTextAr.text = hijri?.month?.ar
                mViewBinding.yearTextAr.text = hijri?.year

            }
        }

        prayerTimings?.let { timings ->
            val prayerDateTimeMap = mapOf(
                "Fajr" to timings.fajr,
                "Dhuhr" to timings.dhuhr,
                "Asr" to timings.asr,
                "Maghrib" to timings.maghrib,
                "Isha" to timings.isha
            )

            val currentDateTime = LocalDateTime.now()
            var nextPrayerName: String? = null
            var nextPrayerTime: String? = null
            var minRemainingTime = Long.MAX_VALUE

            // Find the next upcoming prayer
            for ((prayerName, prayerTime) in prayerDateTimeMap) {
                prayerTime?.let {
                    val timePart = it.substringBefore(" ")
                    val prayerDateTime = LocalDateTime.of(
                        LocalDate.now(),
                        LocalTime.parse(timePart, DateTimeFormatter.ofPattern("HH:mm"))
                    )

                    val remainingTime =
                        Duration.between(currentDateTime, prayerDateTime).toMinutes()

                    if (remainingTime > 0 && remainingTime < minRemainingTime) {
                        nextPrayerName = prayerName
                        nextPrayerTime = it
                        minRemainingTime = remainingTime
                    }
                }
            }



            mViewBinding.fajRemainingTime.visibility =
                if (nextPrayerName == "Fajr") View.VISIBLE else View.INVISIBLE
            mViewBinding.dhuhrRemainingTime.visibility =
                if (nextPrayerName == "Dhuhr") View.VISIBLE else View.INVISIBLE
            mViewBinding.asrRemainingTime.visibility =
                if (nextPrayerName == "Asr") View.VISIBLE else View.INVISIBLE
            mViewBinding.maghribRemainingTime.visibility =
                if (nextPrayerName == "Maghrib") View.VISIBLE else View.INVISIBLE
            mViewBinding.ishaRemainingTime.visibility =
                if (nextPrayerName == "Isha") View.VISIBLE else View.INVISIBLE


            nextPrayerName?.let { prayerName ->
                nextPrayerTime?.let { prayerTime ->
                    val timePart = prayerTime.substringBefore(" ")
                    val prayerDateTime = LocalDateTime.of(
                        LocalDate.now(),
                        LocalTime.parse(timePart, DateTimeFormatter.ofPattern("HH:mm"))
                    )


                    // HH:mm -> hh:mm:aa
                    val remainingTime =
                        Duration.between(currentDateTime, prayerDateTime).toMinutes()

                    when (prayerName) {
                        "Fajr" -> mViewBinding.fajRemainingTime.text =
                            formatRemainingTime(remainingTime)

                        "Dhuhr" -> mViewBinding.dhuhrRemainingTime.text =
                            formatRemainingTime(remainingTime)

                        "Asr" -> mViewBinding.asrRemainingTime.text =
                            formatRemainingTime(remainingTime)

                        "Maghrib" -> mViewBinding.maghribRemainingTime.text =
                            formatRemainingTime(remainingTime)

                        "Isha" -> mViewBinding.ishaRemainingTime.text =
                            formatRemainingTime(remainingTime)
                    }
                }
            }
        }
    }

//    @RequiresApi(Build.VERSION_CODES.O)
//    private fun setPrayerAlarm(prayerTimings: Timings?) {
//
//        val prayerDateTimeMap = mapOf(
//            "Fajr" to prayerTimings?.fajr,
//            "Dhuhr" to prayerTimings?.dhuhr,
//            "Asr" to prayerTimings?.asr,
//            "Maghrib" to prayerTimings?.maghrib,
//            "Isha" to prayerTimings?.isha
//        )
//
//        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
//        val currentDateTime = LocalDateTime.now()
//
//        prayerDateTimeMap.forEach { (prayerName, prayerTime) ->
//            prayerTime?.let {
//                val timePart = it.substringBefore(" ")
//                val prayerDateTime = LocalDateTime.of(
//                    LocalDate.now(),
//                    LocalTime.parse(timePart, DateTimeFormatter.ofPattern("HH:mm"))
//                )
//
//                val alarmTime = prayerDateTime.minusMinutes(5)
//
//                // Set the alarm for the calculated time
//                setAlarm(requireContext(), alarmManager, prayerName, alarmTime)
//            }
//        }
//
//
//    }

//    @RequiresApi(Build.VERSION_CODES.O)
//    private fun setAlarm(context: Context, alarmManager: AlarmManager, prayerName: String, alarmTime: LocalDateTime) {
//        val intent = Intent(context, AlarmReceiver::class.java).apply {
//            action = "ACTION_ALARM"
//            putExtra("prayerName", prayerName)
//        }
//        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
//
//        val alarmTimeMillis = alarmTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            alarmManager.setExactAndAllowWhileIdle(
//                AlarmManager.RTC_WAKEUP,
//                alarmTimeMillis,
//                pendingIntent
//            )
//        } else {
//            alarmManager.setExact(
//                AlarmManager.RTC_WAKEUP,
//                alarmTimeMillis,
//                pendingIntent
//            )
//        }
//
//        Log.d("Alarm", "Alarm set for $prayerName at $alarmTime")
//    }

    private fun formatTime(time: String?): String {
        time?.let {
            val parser = SimpleDateFormat("HH:mm", Locale.getDefault())
            val formatter = SimpleDateFormat("hh:mm aa", Locale.getDefault())
            return formatter.format(parser.parse(it)!!)
        }
        return ""
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun setPrayerLayoutColor(layout: View, prayerTime: String?, currentTime: LocalTime) {
        val cornerRadius = dpToPx(10).toFloat()
        layout.clipToOutline = true

        prayerTime?.let {
            val timePart = it.substringBefore(" ")
            val prayerDateTime = LocalTime.parse(timePart, DateTimeFormatter.ofPattern("HH:mm"))

            if (currentTime.isAfter(prayerDateTime)) {
                layout.setBackgroundColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.greyColor
                    )
                )
            } else {
                layout.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brown))
            }
        }

        layout.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View?, outline: Outline?) {
                outline?.setRoundRect(0, 0, view!!.width, view.height, cornerRadius)
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        val scale = Resources.getSystem().displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }


    @SuppressLint("SetTextI18n")
    private fun formatRemainingTime(remainingMinutes: Long): String {
        if (remainingMinutes <= 0) {
            return ""
        }

        val hours = (remainingMinutes + 80) / 60
        val minutes = remainingMinutes % 60

        var format = ""
        if (hours > 0)
            format = "$hours hours and"
        format = "$format$minutes minutes"


        return "( $format )"
//        return "(${"%02d:%02d".format(hours, minutes)})"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun attachListener() {
        mViewBinding.cityName.setOnClickListener {
            mViewModel.getAllCities()
            showList()
        }
        mViewBinding.date.setOnClickListener {

            showDatePickerDialog()
        }
        mViewBinding.setAlarmButton.setOnClickListener{
            setPrayerAlarms()
        }
        mViewBinding.showAdButton.setOnClickListener {

            if (mInterstitialAd != null) {
                mInterstitialAd?.show(requireActivity())
            } else {
                Log.d("TAG", "The interstitial ad wasn't ready yet.")
            }
        }


    }

    private fun setPrayerAlarms() {
        val prayerTimes = getPrayerTimes() // Retrieve prayer times

        prayerTimes.forEach { (prayerName, prayerTime) ->
            val alarmTime = prayerTime.minusMinutes(5) // Alarm 5 minutes before prayer time
            setAlarm(requireContext(), prayerName, alarmTime)
        }
    }

    private fun setAlarm(context: Context, prayerName: String, alarmTime: LocalDateTime) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = "ACTION_ALARM"
                putExtra("prayerName", prayerName)
            }
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            val alarmTimeMillis = alarmTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarmTimeMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    alarmTimeMillis,
                    pendingIntent
                )
            }

            Toast.makeText(context, "Alarm set for $prayerName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("setAlarm", "Error setting alarm: ${e.message}")
            Toast.makeText(context, "Error setting alarm: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    private fun getPrayerTimes(): Map<String, LocalDateTime> {
        // Implement your logic to retrieve prayer times here
        return mapOf(
            "Fajr" to LocalDateTime.now().plusHours(1),
            "Dhuhr" to LocalDateTime.now().plusHours(2),
            "Asr" to LocalDateTime.now().plusHours(3),
            "Maghrib" to LocalDateTime.now().plusHours(4),
            "Isha" to LocalDateTime.now().plusHours(5)
        )
    }

    private fun showList() {
        mViewModel.getAllCities().observe(viewLifecycleOwner) { cities ->
            if (cities.isNullOrEmpty()) {
                Toast.makeText(mainActivity, "DB list is empty...", Toast.LENGTH_LONG).show()
            } else {
                showCityDialog(cities)
            }
        }

    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        datePickerDialog = DatePickerDialog(requireContext(), dateSetListener, year, month, day)
        datePickerDialog?.show()
    }


    @RequiresApi(Build.VERSION_CODES.O)

    private val dateSetListener = OnDateSetListener { _, year, month, dayOfMonth: Int ->
        val selectedYear = year
        val selectedMonth = month + 1
        val selectedDayOfMonth = dayOfMonth

        callPrayerTimeData(selectedYear, selectedMonth, selectedDayOfMonth)

        datePickerDialog?.dismiss()
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun callPrayerTimeData(selectedYear: Int, selectedMonth: Int, selectedDayOfMonth: Int) {

        showProgressDialog()

        lifecycleScope.launch {
            delay(1000)

            mViewModel.prayerResponse.observe(viewLifecycleOwner) { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        showProgressDialog()
                    }

                    is Resource.Success -> {
                        hideProgressDialog()
                        val response = resource.value

                        val timeList = response.data
                        // Add null check here
                        if (!timeList.isNullOrEmpty()) {
                            val selectedPrayerTimeData = timeList.find { prayerData ->
                                prayerData.date?.gregorian?.day?.toIntOrNull() == selectedDayOfMonth
                            }
                            selectedPrayerTimeData?.let { populateTodayTimes(it) }
                        }
                    }

                    is Resource.Failure -> {
                        hideProgressDialog()
                        Toast.makeText(mainActivity, resource.errorString, Toast.LENGTH_LONG)
                            .show()
                    }

                    else -> {

                    }
                }
            }
        }
    }


    override fun observeViewModel() {
//        mViewModel.citiesList.observe(viewLifecycleOwner) {
//            if (it.isNullOrEmpty()) {
//                Toast.makeText(mainActivity, "DB list is empty...", Toast.LENGTH_LONG).show()
//                return@observe
//            }
//           showCityDialog(it as java.util.ArrayList<CityDBModel>)
//        }
    }

    private fun showCityDialog(list: List<CityDBModel>?) {
        // val bottomSheetFragment = CityBottomSheet(list as ArrayList<CityDBModel>)
//        bottomSheetFragment.setCitySelectedListener(this)
//        bottomSheetFragment.show(childFragmentManager, bottomSheetFragment.tag)

        list?.let { cities ->
            val bottomSheetFragment = CityBottomSheet(cities)
            bottomSheetFragment.setCitySelectedListener(this)
            bottomSheetFragment.show(childFragmentManager, bottomSheetFragment.tag)
        }
    }

    override fun onCitySelected(city: CityDBModel) {
        mViewBinding.cityName.text = city.name
        callCityPrayerTime(city)

    }

    private fun callCityPrayerTime(city: CityDBModel) {
        val apiUrl =
            "https://api.aladhan.com/v1/calendar/$year/$month?latitude=${city.lat}&longitude=${city.lng}"
        mViewModel.callGetPrayerData(apiUrl)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        unregisterNetworkCallback()
        if (::adView.isInitialized) {
            adView.destroy()
        }
    }

    private fun addCities() {
        cityList.clear()
        cityList.add(CityModel("Islamabad", 33.693812, 73.065151))
        cityList.add(CityModel("Lahore", 31.582045, 74.329376))
        cityList.add(CityModel("Karachi", 24.854684, 67.020706))
        cityList.add(CityModel("Istanbul", 41.006381, 28.975872))
        cityList.add(CityModel("Shanghai", 31.232344, 121.469102))
        cityList.add(CityModel("Dhaka", 23.764403, 90.389015))
        cityList.add(CityModel("Mumbai", 19.081577, 72.886628))
        cityList.add(CityModel("Dubai", 25.074282, 55.188539))
        cityList.add(CityModel("Delhi", 28.627393, 77.171695))
        cityList.add(CityModel("Jakarta", -6.175247, 106.827049))
        var i = 99
        cityList.forEach {
            i += 1
            mViewModel.insertCity(CityDBModel(i.toLong(), it.cityName, it.latitude, it.longitude))
        }

    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestLocation()
            } else {
                Toast.makeText(requireContext(), "Location permission denied.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 100
    }


}
