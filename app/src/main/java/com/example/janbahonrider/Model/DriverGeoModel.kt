package com.example.janbahonrider.Model
import com.firebase.geofire.GeoLocation
import com.google.android.gms.maps.model.LatLng

class DriverGeoModel {
    var key:String?=null
    var geoLocation:GeoLocation?=null
    var driverInfoModel:DriverInfoModel?=null
    var isDecline:Boolean = false
    constructor(key : String?,geoLocation: GeoLocation?){
        this.key = key
        this.geoLocation = geoLocation!!
    }


}