package com.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.app.R
import com.app.database.CityDBModel
import com.app.models.CityModel

class CityAdapter(private val cities: List<CityDBModel>, private val onClickEvent: (model:CityDBModel) -> Unit) :
    RecyclerView.Adapter<CityAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.city_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val city = cities[position]
        holder.bind(city)
        holder.itemView.setOnClickListener {
            onClickEvent.invoke(city)
        }
    }


    override fun getItemCount(): Int {
        return cities.size
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cityNameTextView: TextView = itemView.findViewById(R.id.cityText)


        fun bind(city: CityDBModel) {
            cityNameTextView.text = city.name

        }
    }

    interface OnItemClickListener {
        fun onItemClick(cityName: String)
    }
}
