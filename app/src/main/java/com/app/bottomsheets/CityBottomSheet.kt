package com.app.bottomsheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.R
import com.app.database.CityDBModel
import com.app.models.CityModel
import com.app.ui.adapters.CityAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


class CityBottomSheet(var cityList : List<CityDBModel>) : BottomSheetDialogFragment() {


    lateinit var adapter: CityAdapter
    lateinit var recyclerView: RecyclerView
    private var citySelectedListener: CitySelectedListener? = null


    interface CitySelectedListener {
        fun onCitySelected(city: CityDBModel)
    }

    fun setCitySelectedListener(listener: CitySelectedListener) {
        this.citySelectedListener = listener
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.city_bottom_sheet_layout, container, false)
        recyclerView = view.findViewById(R.id.recyclerView)

        return view

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)



        adapter = CityAdapter(cityList) { city ->
            citySelectedListener?.onCitySelected(city)
            dismiss()
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter


    }


    override fun getTheme(): Int {
        return R.style.FullScreenDialog
    }


    private fun onItemClick(cityName: CityDBModel) {
        dismiss()

    }



}
