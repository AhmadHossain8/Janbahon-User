package com.example.janbahonrider.ui.home

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.janbahonrider.Callback.FirebaseDriverInfoListener
import com.example.janbahonrider.Callback.FirebaseFailedListener
import com.example.janbahonrider.Common.Common
import com.example.janbahonrider.Model.AnimationModel
import com.example.janbahonrider.Model.DriverGeoModel
import com.example.janbahonrider.Model.DriverInfoModel
import com.example.janbahonrider.Model.EventBus.SelectPlaceEvent

import com.example.janbahonrider.Model.GeoQueryModel
import com.example.janbahonrider.R
import com.example.janbahonrider.Remote.IGoogleAPI
import com.example.janbahonrider.Remote.RetrofitClient
import com.example.janbahonrider.RequestDriverActivity
import com.example.janbahonrider.databinding.FragmentHomeBinding
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.json.JSONObject
import java.io.IOException
import java.util.*

class HomeFragment : Fragment(), OnMapReadyCallback, FirebaseDriverInfoListener {

    private var isNextLaunch: Boolean = false
    private var _binding: FragmentHomeBinding? = null
    private lateinit var mMap: GoogleMap
    private lateinit var mapFragment:SupportMapFragment

    var locationRequest:LocationRequest?=null
    var locationCallback: LocationCallback?=null
    var fusedLocationProviderClient: FusedLocationProviderClient?=null

    private lateinit var slidingUpPaneLayout: SlidingUpPanelLayout
    private lateinit var txt_welcome:TextView
    private lateinit var autocompleteSupportFragment : AutocompleteSupportFragment


    var distance = 1.0
    var LIMIT_RANGE = 10.0

    var previousLocation: Location? = null
    var currentLocation: Location? = null
    var firstTime = true

    lateinit var iFirebaseDriverInfoListener : FirebaseDriverInfoListener
    lateinit var iFirebaseFailedListener : FirebaseFailedListener

    var cityName = ""

    val compositeDisposable=CompositeDisposable()
    lateinit var iGoogleAPI: IGoogleAPI

    override fun onResume() {
        super.onResume()
        if(isNextLaunch)
            loadAvailableDrivers()
        else
            isNextLaunch = true
    }


    override fun onStop() {
        compositeDisposable.clear()
        super.onStop()
    }

    override fun onDestroy() {
        fusedLocationProviderClient!!.removeLocationUpdates(locationCallback!!)
        super.onDestroy()
    }
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)



        check(root)


        return root
    }
    fun check(root: View?){
        initViews(root)
        init()
    }

    private fun initViews(root: View?) {
        slidingUpPaneLayout=root!!.findViewById(R.id.activity_main) as SlidingUpPanelLayout
        txt_welcome=root!!.findViewById(R.id.txt_welcome) as TextView
        Common.setWelcomeMessage(txt_welcome)
    }

    private fun init() {
        Places.initialize(requireContext(),getString(R.string.google_maps_key))
        autocompleteSupportFragment = childFragmentManager.findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment
        autocompleteSupportFragment.setPlaceFields( Arrays.asList(Place.Field.ID,Place.Field.ADDRESS,Place.Field.LAT_LNG,
        Place.Field.NAME))
        autocompleteSupportFragment.setOnPlaceSelectedListener(object :PlaceSelectionListener{

            override fun onPlaceSelected(p0: Place) {
                if(ActivityCompat.checkSelfPermission(
                        context!!,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) !=PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        context!!,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )!= PackageManager.PERMISSION_GRANTED
                ) {
                    Snackbar.make(
                        requireView(),
                        getString(R.string.permission_require),
                        Snackbar.LENGTH_LONG
                    ).show()
                    return
                }
                fusedLocationProviderClient!!
                    .lastLocation.addOnSuccessListener { location->
                        val origin =LatLng(location.latitude,location.longitude)
                        val destination =LatLng(p0.latLng!!.latitude,p0.latLng!!.longitude)
                        startActivity(Intent(requireContext(),RequestDriverActivity::class.java))
                        EventBus.getDefault().postSticky(SelectPlaceEvent(origin,destination,"",p0!!.address!!))
                    }
            }
            override fun onError(p0: Status) {
                Snackbar.make(requireView(),""+p0.statusMessage!!,Snackbar.LENGTH_LONG).show()
            }

        })

        iGoogleAPI=RetrofitClient.instance!!.create(IGoogleAPI::class.java)


        Log.d("init","inside init")
        Log.d("prev1",previousLocation.toString())
        Log.d("cur1",currentLocation.toString())
        iFirebaseDriverInfoListener = this

        if(ActivityCompat.checkSelfPermission(
                context!!,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) !=PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context!!,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )!= PackageManager.PERMISSION_GRANTED
        ) {
            Snackbar.make(
                mapFragment.requireView(),
                getString(R.string.permission_require),
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        buildLocationRequest()
        buildLocationCallBack()
        updateLocation()

        Log.d("onLocationResult","after")
        Log.d("prev2",previousLocation.toString())
        Log.d("cur2",currentLocation.toString())



        loadAvailableDrivers();
    }

    private fun updateLocation() {
        if(fusedLocationProviderClient==null)
        {
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext())
            if(ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                )!= PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    requireContext(),Manifest.permission.ACCESS_COARSE_LOCATION
                )!= PackageManager.PERMISSION_GRANTED)
            {
                //Snackbar.make(requireView(),getString(R.string.permission_require),Snackbar.LENGTH_SHORT).show()
                //Log.d("gone","gone")
                return
            }
            fusedLocationProviderClient!!.requestLocationUpdates(locationRequest!!,locationCallback!!, Looper.myLooper())
        }
    }

    private fun buildLocationCallBack() {
        if(locationCallback==null)
        {
            locationCallback = object: LocationCallback() {
                override fun onLocationResult(p0: LocationResult) {
                    super.onLocationResult(p0)
                    val newPos = LatLng(p0!!.lastLocation!!.latitude,p0!!.lastLocation!!.longitude)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos,18f))

                    // If use has change location,calculate and load driver again
                    if(firstTime){
                        previousLocation = p0.lastLocation
                        currentLocation = p0.lastLocation

                        setRestrictPlacesInCountry(p0!!.lastLocation)

                        firstTime = false


                    }else{
                        previousLocation = currentLocation
                        currentLocation = p0.lastLocation
                    }

                    if(previousLocation!!.distanceTo(currentLocation)/1000 <= LIMIT_RANGE)
                        loadAvailableDrivers();

                }
            }
        }
    }

    private fun buildLocationRequest() {
        if(locationRequest==null)
        {
            locationRequest = LocationRequest.create() // LocationRequest() was deprecated
            locationRequest!!.setPriority(Priority.PRIORITY_HIGH_ACCURACY) //LocationRequest.PRIORITY_HIGH_ACCURACY was deprecated
            locationRequest!!.setFastestInterval(2000)
            locationRequest!!.setSmallestDisplacement(10f)
            locationRequest!!.interval = 4000
        }
    }

    private fun setRestrictPlacesInCountry(location: Location?) {
        try {
            val geoCoder = Geocoder(requireContext(), Locale.getDefault())
            var addressList = geoCoder.getFromLocation(location!!.latitude, location!!.longitude, 1)
            if(addressList.size>0)
            {
                autocompleteSupportFragment.setCountry(addressList[0].countryCode)
            }
        }catch (e:IOException){
            e.printStackTrace()
        }
    }
    private fun loadAvailableDrivers() {
        Log.d("loadAvailableDrivers","inside loadAvailableDrivers")
        if(ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED){
            Snackbar.make(requireView(),getString(R.string.permission_require),Snackbar.LENGTH_SHORT).show()
        }
        fusedLocationProviderClient!!.lastLocation
            .addOnFailureListener { e->
                Snackbar.make(requireView(),e.message!!,Snackbar.LENGTH_SHORT).show()
            }
            .addOnSuccessListener { location ->
                //Load all drivers in city
                val geoCoder = Geocoder(requireContext(), Locale.getDefault())
                var addressList : List<Address> = ArrayList()
                try {
                    //User's location
                    addressList = geoCoder.getFromLocation(location.latitude,location.longitude,1)
                    if(addressList.isNotEmpty())
                        cityName = addressList[0].locality
                    Log.d("city",cityName)
                    //Query
                    if(!TextUtils.isEmpty(cityName)) {
                        val driver_location_ref = FirebaseDatabase.getInstance()
                            .getReference(Common.DRIVERS_LOCATION_REFERENCE)
                            .child(cityName)

                        val gf = GeoFire(driver_location_ref)
                        //gf.queryAtLocation(GeoLocation(location.latitude,location.longitude),distance)
                        Log.d("lt", location.latitude.toString())
                        Log.d("lg", location.longitude.toString())

                        val geoQuery = gf.queryAtLocation(
                            GeoLocation(location.latitude, location.longitude),
                            distance
                        )
                        geoQuery.removeAllListeners()

                        geoQuery.addGeoQueryEventListener(object : GeoQueryEventListener {
                            override fun onKeyEntered(key: String?, location: GeoLocation?) {
                                Log.d("addDriver", "Adding drivers")
                                //Common.driversFound.add(DriverGeoModel(key!!, location!!))
                                if(!Common.driversFound.containsKey(key))
                                    Common.driversFound[key!!] = DriverGeoModel(key,location)
                            }

                            override fun onKeyExited(key: String?) {}
                            override fun onKeyMoved(key: String?, location: GeoLocation?) {}
                            override fun onGeoQueryReady() {
                                if (distance <= LIMIT_RANGE) {
                                    distance++
                                    Log.d("callload", "Calling to load again")
                                    Log.d("dist", distance.toString())
                                    loadAvailableDrivers()
                                } else {
                                    Log.d("calladd", "Calling to add")
                                    Log.d("dist1", distance.toString())
                                    distance = 0.0
                                    addDriverMarker()
                                }
                            }

                            override fun onGeoQueryError(error: DatabaseError?) {
                                Snackbar.make(requireView(), error!!.message, Snackbar.LENGTH_SHORT)
                                    .show()
                            }
                        })

                        driver_location_ref.addChildEventListener(object : ChildEventListener {
                            override fun onChildAdded(p0: DataSnapshot, p1: String?) {
                                // Have new driver
                                Log.d("yo", "idk")
                                val geoQueryModel = p0.getValue(GeoQueryModel::class.java)
                                val geoLocation =
                                    GeoLocation(geoQueryModel!!.l!![0], geoQueryModel!!.l!![1])
                                val driverGeoModel = DriverGeoModel(p0.key, geoLocation)
                                val newDriverLocation = Location("")
                                newDriverLocation.latitude = geoLocation.latitude
                                newDriverLocation.longitude = geoLocation.longitude
                                val newDistance = location.distanceTo(newDriverLocation) / 1000
                                if (newDistance <= LIMIT_RANGE)
                                    findDriverByKey(driverGeoModel)
                            }

                            override fun onChildChanged(p0: DataSnapshot, p1: String?) {}
                            override fun onChildRemoved(p0: DataSnapshot) {}
                            override fun onChildMoved(p0: DataSnapshot, p1: String?) {}
                            override fun onCancelled(p0: DatabaseError) {
                                Snackbar.make(requireView(), p0.message, Snackbar.LENGTH_SHORT)
                                    .show()
                            }
                        })
                    }
                    else
                    {
                        Snackbar.make(requireView(),getString(R.string.city_name_not_found),Snackbar.LENGTH_SHORT).show()
                    }
                }catch (e:IOException){
                    Snackbar.make(requireView(),getString(R.string.permission_require),Snackbar.LENGTH_SHORT).show()
                }
            }
    }

    private fun addDriverMarker() {
        Log.d("addDriverMarker","inside addDriverMarker")
        if(Common.driversFound.size > 0){
            Observable.fromIterable(Common.driversFound.keys)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe (
                    { key : String? ->
                        findDriverByKey(Common.driversFound[key!!])
                    },
                    {
                        t: Throwable? ->  Snackbar.make(requireView(),t!!.message!!,Snackbar.LENGTH_SHORT).show()

                    }
                )
        }else{
            Snackbar.make(requireView(),getString(R.string.drivers_not_found),Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun findDriverByKey(driverGeoModel: DriverGeoModel?) {
        Log.d("findDriverByKey","inside findDriverByKey")
        FirebaseDatabase.getInstance()
            .getReference(Common.DRIVER_INFO_REFERENCE)
            .child(driverGeoModel!!.key!!)
            .addListenerForSingleValueEvent(object :ValueEventListener{
                override fun onDataChange(p0: DataSnapshot) {
                    if(p0.hasChildren()){
                        driverGeoModel.driverInfoModel = (p0.getValue(DriverInfoModel::class.java))
                        Common.driversFound[driverGeoModel.key!!]!!.driverInfoModel = (p0.getValue(DriverInfoModel::class.java))
                        iFirebaseDriverInfoListener.onDriverInfoLoadSuccess(driverGeoModel)
                    }else
                        iFirebaseFailedListener.onFirebaseFailed(getString(R.string.key_not_found)+driverGeoModel.key)
                }

                override fun onCancelled(p0: DatabaseError) {
                    iFirebaseFailedListener.onFirebaseFailed(p0.message)
                }

            })
    }

    override fun onDestroyView() {
        Log.d("onDestroyView","inside onDestroyView")
        super.onDestroyView()
        _binding = null
    }

    override fun onMapReady(p0: GoogleMap) {
        Log.d("onMapReady","inside onMapReady")
        mMap = p0!!
        Dexter.withContext(requireContext())
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object:PermissionListener{
                override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
                    Log.d("onPermissionGranted","inside onPermissionGranted")
                    if(ActivityCompat.checkSelfPermission(
                            requireContext(),Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    ){
                        return
                    }
                    mMap.isMyLocationEnabled = true
                    mMap.uiSettings.isMyLocationButtonEnabled = true
                    mMap.setOnMyLocationButtonClickListener {
                        fusedLocationProviderClient!!.lastLocation
                            .addOnFailureListener { e ->
                                Snackbar.make(requireView(),e.message!!,Snackbar.LENGTH_LONG).show()
                            }
                            .addOnSuccessListener { location ->
                                val userLatLng = LatLng(location.latitude,location.longitude)
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng,18f))

                            }

                        true
                    }
                    val locationButton = (mapFragment.requireView()!!.findViewById<View>("1".toInt())!!.parent!!as View)
                        .findViewById<View>("2".toInt())
                    val params = locationButton.layoutParams as RelativeLayout.LayoutParams
                    params.addRule(RelativeLayout.ALIGN_PARENT_TOP,0)
                    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM,RelativeLayout.TRUE)
                    params.bottomMargin = 250

                    buildLocationRequest()
                    buildLocationCallBack()
                    updateLocation()


                }

                override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                    Snackbar.make(requireView(),p0!!.permissionName + " needed for running app",
                    Snackbar.LENGTH_LONG).show()
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: PermissionRequest?,
                    p1: PermissionToken?
                ) {

                }

            })
            .check() //Dont forget it

        //Enabl zoom
        mMap.uiSettings.isZoomControlsEnabled = true

        try{
            val success = p0!!.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(),R.raw.uber_maps_style))
            if(!success)
                Snackbar.make(requireView(),"Load map style failed",
                    Snackbar.LENGTH_LONG).show()
        }catch (e:Exception){
            Snackbar.make(requireView(),""+e.message,
                Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?) {
        Log.d("onDriverInfoLoadSuccess","inside onDriverInfoLoadSuccess")
        //if already with have marker with this key dont set it again
        if(!Common.markerList.containsKey(driverGeoModel!!.key)){
            if(driverGeoModel.driverInfoModel!!.vehicle_type == "Car" || driverGeoModel.driverInfoModel!!.vehicle_type == "Bus"){
                Common.markerList.put(driverGeoModel!!.key!!,
                mMap.addMarker(MarkerOptions()
                    .position(LatLng(driverGeoModel!!.geoLocation!!.latitude,driverGeoModel!!.geoLocation!!.longitude))
                    .flat(true)
                    .title(Common.buildName(driverGeoModel.driverInfoModel!!.firstName,driverGeoModel.driverInfoModel!!.lastName))
                    .snippet(driverGeoModel.driverInfoModel!!.phoneNumber)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.car)))!!)
            }else if(driverGeoModel.driverInfoModel!!.vehicle_type == "Bike") {
                Common.markerList.put(
                    driverGeoModel!!.key!!,
                    mMap.addMarker(
                        MarkerOptions()
                            .position(
                                LatLng(
                                    driverGeoModel!!.geoLocation!!.latitude,
                                    driverGeoModel!!.geoLocation!!.longitude
                                )
                            )
                            .flat(true)
                            .title(
                                Common.buildName(
                                    driverGeoModel.driverInfoModel!!.firstName,
                                    driverGeoModel.driverInfoModel!!.lastName
                                )
                            )
                            .snippet(driverGeoModel.driverInfoModel!!.phoneNumber)
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.bike))
                    )!! // !! in this line is from autosuggestion not from the code
                )
            }

        }
        if(!TextUtils.isEmpty(cityName)){
            val driverLocation = FirebaseDatabase.getInstance()
                .getReference(Common.DRIVERS_LOCATION_REFERENCE)
                .child(cityName)
                .child(driverGeoModel!!.key!!)
            driverLocation.addValueEventListener(object :ValueEventListener{
                override fun onDataChange(p0: DataSnapshot) {
                    if(!p0.hasChildren()){
                        if(Common.markerList.get(driverGeoModel!!.key!!) != null){
                            val marker = Common.markerList.get(driverGeoModel!!.key!!)
                            marker!!.remove() // remove marker
                            Common.markerList.remove(driverGeoModel!!.key!!)
                            Common.driversSubscribe.remove(driverGeoModel.key!!)
                            //when driver declines they can again accept if the restart the app
                            if(Common.driversFound != null && Common.driversFound[driverGeoModel.key!!] != null)
                                Common.driversFound.remove(driverGeoModel.key!!)
                            driverLocation.removeEventListener(this)
                        }
                    }
                    else{
                        if(Common.markerList.get(driverGeoModel!!.key!!) != null){
                            val geoQueryModel=p0!!.getValue(GeoQueryModel::class.java)
                            val animationModel=AnimationModel(false,geoQueryModel!!)
                            if(Common.driversSubscribe.get(driverGeoModel.key!!)!=null)
                            {
                                val marker=Common.markerList.get(driverGeoModel!!.key!!)
                                val oldposition=Common.driversSubscribe.get(driverGeoModel.key!!)
                                val from =StringBuilder()
                                    .append(oldposition!!.geoQueryModel!!.l?.get(0))
                                    .append(",")
                                    .append(oldposition!!.geoQueryModel!!.l?.get(1))
                                    .toString()

                                val to =StringBuilder()
                                    .append(animationModel.geoQueryModel!!.l?.get(0))
                                    .append(",")
                                    .append(animationModel.geoQueryModel!!.l?.get(1))
                                    .toString()
                                moveMarkerAnimation(driverGeoModel.key!!,animationModel,marker,from,to)
                            }
                            else
                            {
                                Common.driversSubscribe.put(driverGeoModel.key!!,animationModel)
                            }

                        }
                    }
                }

                override fun onCancelled(p0: DatabaseError) {
                    Snackbar.make(requireView(),p0.message,Snackbar.LENGTH_SHORT).show()
                }

            })
        }

    }

    private fun moveMarkerAnimation(key: String, newData: AnimationModel, marker: Marker?, from:String, to: String) {
        if(!newData.isRun)
        {
            //Request API
            compositeDisposable.add(iGoogleAPI.getDirection("driving" ,
            "less_driving",
            from,to,
            getString(R.string.google_api_key))
                !!.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe{ returnResult->
                    Log.d("API_RETURN",returnResult)
                    try{
                        val jsonObject =JSONObject(returnResult)
                        val jsonArray=jsonObject.getJSONArray("routes");
                        for(i in 0 until jsonArray.length())
                        {
                            val route=jsonArray.getJSONObject(i)
                            val poly=route.getJSONObject("overview_polyline")
                            val polyline=poly.getString("points")
                            //polylineList=Common.decodePoly(polyline)
                            newData.polylineList = Common.decodePoly(polyline)
                        }


                        //index=-1
                        newData.index = -1
                        //next=1
                        newData.next = 1

                        val runnable=object:Runnable{
                            override fun run() {
                               if(newData.polylineList != null && newData.polylineList!!.size>1)
                               {
                                   if(newData.index<newData.polylineList!!.size -2)
                                   {
                                       newData.index++
                                       newData.next=newData.index+1
                                       newData.start=newData.polylineList!![newData.index]!!
                                       newData.end=newData.polylineList!![newData.next]!!
                                   }
                                   val valueAnimator=ValueAnimator.ofInt(0,1)
                                   valueAnimator.duration=3000
                                   valueAnimator.interpolator=LinearInterpolator()
                                   valueAnimator.addUpdateListener { value->
                                       newData.v = value.animatedFraction
                                       newData.lat = newData.v * newData.end!!.latitude+(1 - newData.v) * newData.start!!.latitude
                                       newData.lng = newData.v * newData.end!!.longitude+(1 - newData.v) * newData.start!!.longitude
                                       val newPos = LatLng(newData.lat,newData.lng)
                                       marker!!.position=newPos
                                       marker!!.setAnchor(0.5f,0.5f)
                                       marker!!.rotation=Common.getBearing(newData.start!!,newPos)

                                   }
                                   valueAnimator.start()
                                   if(newData.index<newData.polylineList!!.size -2)
                                   {
                                       newData.handler!!.postDelayed(this,1500)
                                   }
                                   else if(newData.index<newData.polylineList!!.size -1)
                                   {
                                       newData.isRun=false
                                       Common.driversSubscribe.put(key,newData)
                                   }

                               }
                            }

                        }
                        newData.handler!!.postDelayed(runnable,1500)
                    }catch (e:java.lang.Exception){
                        Snackbar.make(requireView(),e.message!!,Snackbar.LENGTH_LONG).show()
                    }
                }
            )
        }

    }
}


