package com.example.janbahonrider.Callback

import com.example.janbahonrider.Model.DriverGeoModel

interface FirebaseDriverInfoListener {
    fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?)

}