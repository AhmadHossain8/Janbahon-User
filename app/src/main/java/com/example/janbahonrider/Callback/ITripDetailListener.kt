package com.example.janbahonrider.Callback

import com.example.janbahonrider.Model.TripPlanModel

interface ITripDetailListener {
    fun onTripDetailLoadSuccess(tripPlanModel: TripPlanModel)
}