package com.example.janbahonrider

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.janbahonrider.Common.Common
import com.example.janbahonrider.Model.DriverGeoModel
import com.example.janbahonrider.Model.EventBus.*
import com.example.janbahonrider.Model.TripPlanModel
import com.example.janbahonrider.Remote.IGoogleAPI
import com.example.janbahonrider.Remote.RetrofitClient
import com.example.janbahonrider.Utils.UserUtils
import com.example.janbahonrider.databinding.ActivityRequestDriverBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.maps.android.ui.IconGenerator
import com.google.zxing.integration.android.IntentIntegrator
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_request_driver.*
import kotlinx.android.synthetic.main.bus_list.*
import kotlinx.android.synthetic.main.choose_type.*
import kotlinx.android.synthetic.main.layout_confirm_pickup.*
import kotlinx.android.synthetic.main.layout_confirm_pickup.txt_address_pickup
import kotlinx.android.synthetic.main.layout_confirm_uber.*
import kotlinx.android.synthetic.main.layout_driver_info.*
import kotlinx.android.synthetic.main.layout_driver_info.txt_driver_name
import kotlinx.android.synthetic.main.layout_driver_info.txt_driver_number
import kotlinx.android.synthetic.main.layout_driver_info.txt_driver_id
import kotlinx.android.synthetic.main.layout_driver_info.txt_rider_id
import kotlinx.android.synthetic.main.layout_driver_info.img_driver
import kotlinx.android.synthetic.main.layout_driver_info_bike.txt_driver_name1
import kotlinx.android.synthetic.main.layout_driver_info_bike.txt_driver_id1
import kotlinx.android.synthetic.main.layout_driver_info_bike.txt_rider_id1
import kotlinx.android.synthetic.main.layout_driver_info_bike.*
import kotlinx.android.synthetic.main.layout_finding_your_driver.*
import kotlinx.android.synthetic.main.layout_qrcode.*
import kotlinx.coroutines.Runnable
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONException
import org.json.JSONObject
import kotlin.random.Random



class RequestDriverActivity : AppCompatActivity(), OnMapReadyCallback,GoogleMap.OnMarkerClickListener {

    internal var number:String?=""
    internal var emergency:String?="999"

    internal var busDetails:String?=""
    private var tripId:String=""
    private  var driverOldPosition: String = ""
    private var handler:Handler ?= null
    private var v = 0f
    private var lat =0.0
    private var lng = 0.0
    private var index = 0
    private var next = 0
    private var start:LatLng?=null
    private var end:LatLng?=null
    var locationRequest: LocationRequest?=null
    var locationCallback: LocationCallback?=null
    var fusedLocationProviderClient: FusedLocationProviderClient?=null
    var previousLocation: Location? = null
    var currentLocation: Location? = null
    var firstTime = true
    var myloc:LatLng ?= null

    private var vehicle_type:String ?= null

    //Spinning animation
     var animator: ValueAnimator?=null
    private val DESIRED_NUM_OF_SPIN = 5
    private val DESIRED_SECONDS_PER_ONE_FULL_360_SPIN = 40
    //Effect
     var lastUserCircle: Circle?= null
    val duration = 1000
     var lastPulseAnimator: ValueAnimator?= null

    private lateinit var mMap: GoogleMap
    private lateinit var txt_origin:TextView

    private var selectPlaceEvent: SelectPlaceEvent?=null
    private lateinit var mapFragment:SupportMapFragment;

    //Rutes
    private val compositeDisposable =CompositeDisposable()
    private lateinit var iGoogleAPI: IGoogleAPI
    private var blackPolyLine:Polyline?=null
    private var grayPolyLine:Polyline?=null
    private var polyLineOptions:PolylineOptions?=null
    private var blackPolylineOptions:PolylineOptions?=null
    private var polylineList:ArrayList<LatLng?>?=null

    private var arrivalLatlng:MutableList<LatLng> = ArrayList()
    private var departureLatlng:MutableList<LatLng> = ArrayList()
    private var busList:MutableList<String> = ArrayList()
    private var busListDetails:MutableList<String> = ArrayList()

    private var originMarker:Marker?=null
    private var destinationMarker:Marker?=null

    private var lastDriverCall: DriverGeoModel?=null

    private var qrscanButton:String?=null

    override fun onStart() {
        super.onStart()
        if(!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this)
    }


    override fun onStop() {
        compositeDisposable.clear()
        if(EventBus.getDefault().hasSubscriberForEvent(SelectPlaceEvent::class.java))
            EventBus.getDefault().removeStickyEvent(SelectPlaceEvent::class.java)
        if(EventBus.getDefault().hasSubscriberForEvent(DeclineRequestFromDriver::class.java))
            EventBus.getDefault().removeStickyEvent(DeclineRequestFromDriver::class.java)
        if(EventBus.getDefault().hasSubscriberForEvent(DriverAcceptTripEvent::class.java))
            EventBus.getDefault().removeStickyEvent(DriverAcceptTripEvent::class.java)
        if(EventBus.getDefault().hasSubscriberForEvent(DeclineRequestAndRemoveTripFromDriver::class.java))
            EventBus.getDefault().removeStickyEvent(DeclineRequestAndRemoveTripFromDriver::class.java)
        if(EventBus.getDefault().hasSubscriberForEvent(DriverCompleteTripEvent::class.java))
            EventBus.getDefault().removeStickyEvent(DriverCompleteTripEvent::class.java)
        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onDriverCompleteTripEvent(event: DriverCompleteTripEvent){
        Common.showNotification(
            this,
            Random.nextInt(),
            "Thank you",
            "Your trip " + event.tripId + " has been completed.",
            null
        )
        startActivity(Intent(this,TripDetailActivity::class.java))
        EventBus.getDefault().postSticky(LoadTripDetailEvent(event.tripId))
        finish()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onDriverAcceptTripEvent(event: DriverAcceptTripEvent){
        tripId=event.tripId
        FirebaseDatabase.getInstance().getReference(Common.TRIP)
            .child(event.tripId)
            .addListenerForSingleValueEvent(object : ValueEventListener{
                override fun onDataChange(p0: DataSnapshot) {
                    if(p0.exists()){
                        val tripPlanModel = p0.getValue(TripPlanModel::class.java)
                        mMap.clear()
                        fill_maps.visibility = View.GONE
                        if(animator != null)animator!!.end()
                        val cameraPos = CameraPosition.Builder().target(mMap.cameraPosition.target)
                            .tilt(0f).zoom(mMap.cameraPosition.zoom).build()
                        mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPos))


                        //Get driver routes
                        val driverLocation = StringBuilder()
                            .append(tripPlanModel!!.currentLat)
                            .append(",")
                            .append(tripPlanModel!!.currentLng)
                            .toString()

                        compositeDisposable.add(
                            iGoogleAPI.getDirection("driving",
                                "less_driving",
                                driverLocation,tripPlanModel!!.origin,
                                "AIzaSyCDz6LWeUuWz7f4OYPeKkFIJy5QCgxyW84")!!
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe{ returnResult ->

                                    var blackPolylineOptions:PolylineOptions?= null
                                    var polylineList:List<LatLng?>?=null
                                    var blackPolyline:Polyline?=null

                                    try{
                                        val jsonObject = JSONObject(returnResult)
                                        val jsonArray=jsonObject.getJSONArray("routes");
                                        for(i in 0 until jsonArray.length())
                                        {
                                            val route=jsonArray.getJSONObject(i)
                                            val poly=route.getJSONObject("overview_polyline")
                                            val polyline=poly.getString("points")
                                            polylineList=Common.decodePoly(polyline)

                                        }
                                        blackPolylineOptions= PolylineOptions()
                                        blackPolylineOptions!!.color(Color.BLACK)
                                        blackPolylineOptions!!.width(5f)
                                        blackPolylineOptions!!.startCap(SquareCap())
                                        blackPolylineOptions!!.jointType(JointType.ROUND)
                                        blackPolylineOptions!!.addAll(polylineList!!)
                                        blackPolyline=mMap.addPolyline(blackPolylineOptions!!)


                                        val latLngBound=LatLngBounds.Builder().include(selectPlaceEvent!!.origin)
                                            .include(selectPlaceEvent!!.destination)
                                            .build()

                                        //Add car icon for origin
                                        val objects=jsonArray.getJSONObject(0)
                                        val legs=objects.getJSONArray("legs")
                                        val legsObject=legs.getJSONObject(0)

                                        val time=legsObject.getJSONObject("duration")
                                        val duration =time.getString("text")

                                        val origin = LatLng(
                                            tripPlanModel!!.origin!!.split(",").get(0).toDouble(),
                                            tripPlanModel!!.origin!!.split(",").get(1).toDouble())

                                        val destination = LatLng(tripPlanModel.currentLat,tripPlanModel.currentLng)

                                        val latLngBounds = LatLngBounds.Builder()
                                            .include(origin)
                                            .include(destination)
                                            .build()

                                        addPickupMarkerWithDuration(duration,origin)
                                        addDriverMarker(destination,tripPlanModel.driverInfoModel!!.vehicle_type)

                                        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(latLngBound,160))
                                        mMap.moveCamera(CameraUpdateFactory.zoomTo(mMap.cameraPosition!!.zoom-1))


                                        initDriverForMoving(event.tripId,tripPlanModel)

                                        //Driver avater
                                        Glide.with(this@RequestDriverActivity)
                                            .load(tripPlanModel!!.driverInfoModel!!.avatar)
                                            .into(img_driver)
                                        txt_driver_name1.setText(tripPlanModel!!.driverInfoModel!!.firstName)
                                        txt_driver_id1.setText(tripPlanModel!!.driverInfoModel!!.id)
                                        txt_driver_number1.setText(tripPlanModel!!.driverInfoModel!!.phoneNumber)
                                        txt_rider_id1.setText(tripPlanModel!!.riderModel!!.id)

                                        txt_driver_name.setText(tripPlanModel!!.driverInfoModel!!.firstName)
                                        txt_driver_id.setText(tripPlanModel!!.driverInfoModel!!.id)
                                        txt_driver_number.setText(tripPlanModel!!.driverInfoModel!!.phoneNumber)
                                        txt_rider_id.setText(tripPlanModel!!.riderModel!!.id)


                                        confirm_uber_layout.visibility = View.GONE
                                        confirm.visibility = View.GONE
                                        confirm_pickup_layout.visibility = View.GONE
                                        if(tripPlanModel!!.driverInfoModel!!.vehicle_type == "Car"){
                                            txt_car_type.setText(tripPlanModel!!.driverInfoModel!!.vehicle_name)
                                            txt_car_number.setText(tripPlanModel!!.driverInfoModel!!.vehicle_model)
                                            driver_info_layout.visibility = View.VISIBLE
                                        }
                                        else {
                                            txt_car_type_bike.setText(tripPlanModel!!.driverInfoModel!!.vehicle_name)
                                            txt_car_number_bike.setText(tripPlanModel!!.driverInfoModel!!.vehicle_model)
                                            driver_info_layout_bike.visibility = View.VISIBLE
                                        }


                                    }catch (e: java.lang.Exception){
                                        Toast.makeText(this@RequestDriverActivity,e.message!!,Toast.LENGTH_LONG).show()
                                    }
                                }
                        )

                    }else{
                        Snackbar.make(main_layout,getString(R.string.trip_not_found) + event.tripId,Snackbar.LENGTH_LONG).show()
                    }
                }

                override fun onCancelled(p0: DatabaseError) {
                    Snackbar.make(main_layout,p0.message,Snackbar.LENGTH_LONG).show()
                }
            })
    }

    private fun initDriverForMoving(tripId: String, tripPlanModel: TripPlanModel) {

        driverOldPosition = StringBuilder().append(tripPlanModel.currentLat).append(",")
            .append(tripPlanModel.currentLng).toString()

        FirebaseDatabase.getInstance().getReference(Common.TRIP)
            .child(tripId)
            .addValueEventListener(object : ValueEventListener{
                override fun onDataChange(p0: DataSnapshot) {
                    val newData = p0.getValue(TripPlanModel::class.java)
                    if(newData!=null) {
                        val driverNewPosition =
                            StringBuilder().append(newData!!.currentLat).append(",")
                                .append(newData!!.currentLng).toString()

                        if (!driverOldPosition.equals(driverNewPosition))
                            moveMarkerAnimation(
                                destinationMarker!!,
                                driverOldPosition,
                                driverNewPosition
                            )
                    }
                }

                override fun onCancelled(p0: DatabaseError) {
                    Snackbar.make(main_layout,p0.message,Snackbar.LENGTH_LONG).show()
                }
            })

    }

    private fun moveMarkerAnimation(marker: Marker, from: String, to: String) {
        compositeDisposable.add(
            iGoogleAPI.getDirection("driving",
                "less_driving",
                from,to,
                "AIzaSyCDz6LWeUuWz7f4OYPeKkFIJy5QCgxyW84")!!
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe{ returnResult ->

                    try{
                        val jsonObject = JSONObject(returnResult)
                        val jsonArray=jsonObject.getJSONArray("routes");
                        for(i in 0 until jsonArray.length())
                        {
                            val route=jsonArray.getJSONObject(i)
                            val poly=route.getJSONObject("overview_polyline")
                            val polyline=poly.getString("points")
                            polylineList=Common.decodePoly(polyline)

                        }
                        blackPolylineOptions= PolylineOptions()
                        blackPolylineOptions!!.color(Color.BLACK)
                        blackPolylineOptions!!.width(5f)
                        blackPolylineOptions!!.startCap(SquareCap())
                        blackPolylineOptions!!.jointType(JointType.ROUND)
                        blackPolylineOptions!!.addAll(polylineList!!)
                        blackPolyLine=mMap.addPolyline(blackPolylineOptions!!)


                        val latLngBound=LatLngBounds.Builder().include(selectPlaceEvent!!.origin)
                            .include(selectPlaceEvent!!.destination)
                            .build()

                        //Add car icon for origin
                        val objects=jsonArray.getJSONObject(0)
                        val legs=objects.getJSONArray("legs")
                        val legsObject=legs.getJSONObject(0)

                        val time=legsObject.getJSONObject("duration")
                        val duration =time.getString("text")

                        val bitmap = Common.createIconWithDuration(this@RequestDriverActivity,duration)
                        originMarker!!.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap!!))

                       val runnable = object:Runnable{
                           override fun run() {
                               if(index < polylineList!!.size - 2){
                                   index++
                                   next = index+1
                                   start = polylineList!![index]
                                   end = polylineList!![next]
                               }

                               val valueAnimator = ValueAnimator.ofInt(0,1)
                               valueAnimator.duration = 1500
                               valueAnimator.interpolator = LinearInterpolator()
                               valueAnimator.addUpdateListener { valueAnimatorNew ->
                                   v = valueAnimatorNew.animatedFraction
                                   lat = v*end!!.latitude + (1-v)*start!!.latitude
                                   lng =v*end!!.longitude + (1-v)*start!!.longitude

                                   val newPos = LatLng(lat,lng)
                                   marker.position = newPos
                                   marker.setAnchor(0.5f,0.5f)
                                   marker.rotation = Common.getBearing(start!!,newPos)
                                   mMap.moveCamera(CameraUpdateFactory.newLatLng(newPos))
                               }
                               valueAnimator.start()
                               if(index < polylineList!!.size - 2)handler!!.postDelayed(this,1500)
                           }
                       }

                        handler = Handler()
                        index = -1
                        next = 1
                        handler!!.postDelayed(runnable,1500)
                        driverOldPosition = to


                    }catch (e: java.lang.Exception){
                        Toast.makeText(this@RequestDriverActivity,e.message!!,Toast.LENGTH_LONG).show()
                    }
                }
        )
    }

    private fun addDriverMarker(destination: LatLng,Vehicle : String) {

        if(Vehicle == "Bike") {
            destinationMarker = mMap.addMarker(
                MarkerOptions().position(destination).flat(true)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.bike))
            )
        }else{
            destinationMarker = mMap.addMarker(
                MarkerOptions().position(destination).flat(true)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
            )
        }
    }

    private fun addPickupMarkerWithDuration(duration: String, origin: LatLng) {

        val icon = Common.createIconWithDuration(this@RequestDriverActivity,duration)!!
        originMarker = mMap.addMarker(MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(icon)).position(origin))
    }


    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onDeclineReceived(event: DeclineRequestFromDriver){
        if(lastDriverCall != null){
            Common.driversFound.get(lastDriverCall!!.key)!!.isDecline =  true
            findNearbyDriver(selectPlaceEvent!!)
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onDeclineAndRemoveTripReceived(event: DeclineRequestAndRemoveTripFromDriver){
        if(lastDriverCall != null){
            if(Common.driversFound.get(lastDriverCall!!.key)!=null) {
                Common.driversFound.get(lastDriverCall!!.key)!!.isDecline = true
            }
            finish()  //finish the activity
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onSelectPlaceEvent(event: SelectPlaceEvent){
        selectPlaceEvent=event
    }




    private lateinit var binding: ActivityRequestDriverBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityRequestDriverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        init()

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }
    private fun init() {
        iGoogleAPI=RetrofitClient.instance!!.create(IGoogleAPI::class.java)

        buildLocationRequest()
        buildLocationCallBack()
        updateLocation()

        btn_car.setOnClickListener {
            vehicle_type = "Car"
            confirm_uber_layout.visibility = View.VISIBLE
            confirm.visibility = View.GONE
            drawPath(selectPlaceEvent!!,"driving")
            mMap.uiSettings.isZoomControlsEnabled=true
            //setDataPickup()
        }
        btn_bike.setOnClickListener {
            vehicle_type = "Bike"
            confirm_uber_layout.visibility = View.VISIBLE
            confirm.visibility = View.GONE
            drawPath(selectPlaceEvent!!,"driving")
            mMap.uiSettings.isZoomControlsEnabled=true
            //setDataPickup()
        }

        btn_bus.setOnClickListener {
            vehicle_type = "Bus"
            confirm_uber_layout.visibility = View.VISIBLE
            confirm.visibility = View.GONE
            drawPath(selectPlaceEvent!!,"transit")
            mMap.uiSettings.isZoomControlsEnabled=true
            //setDataPickup()
        }

        btn_nsu_bus.setOnClickListener {
            confirm.visibility = View.GONE

            mMap.uiSettings.isZoomControlsEnabled=true
            vehicle_type = "NSU Bus"
            LoadNSUBus();
        }


        btn_confirm_uber.setOnClickListener {
            confirm_pickup_layout.visibility = View.VISIBLE
            confirm_uber_layout.visibility = View.GONE
            setDataPickup(vehicle_type!!)
        }


        btn_confirm_pickup.setOnClickListener {
            Log.d("vehicle" , vehicle_type!!)
            if(vehicle_type=="Bus")
            {
                val busAdapter:ArrayAdapter<String> = ArrayAdapter(
                    this,android.R.layout.simple_list_item_1,busListDetails
               )
                bus_list.adapter=busAdapter
                bus_layout.visibility=View.VISIBLE
                confirm_pickup_layout.visibility=View.GONE
                mMap.uiSettings.isZoomControlsEnabled=true
                mMap.clear()

                val cameraPos = CameraPosition.Builder().target(selectPlaceEvent!!.origin)
                    .tilt(45f)
                    .zoom(16f)
                    .build()
                mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPos))

                //start of animation
                addMarkerWithPulseAnimation();
            }
            else {
                if (mMap == null) return@setOnClickListener
                if (selectPlaceEvent == null) return@setOnClickListener

                //clear map
                mMap.clear()

                val cameraPos = CameraPosition.Builder().target(selectPlaceEvent!!.origin)
                    .tilt(45f)
                    .zoom(16f)
                    .build()
                mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPos))

                //start of animation
                addMarkerWithPulseAnimation();
            }

        }
        call_driver.setOnClickListener {
            number=txt_driver_number.text.toString().trim()
            val intent=Intent(Intent.ACTION_DIAL, Uri.parse("tel:"+ Uri.encode(number)))
            startActivity(intent)
        }
        call_driver1.setOnClickListener {
            number=txt_driver_number1.text.toString().trim()
            val intent=Intent(Intent.ACTION_DIAL, Uri.parse("tel:"+ Uri.encode(number)))
            startActivity(intent)
        }
        btn_emergency.setOnClickListener {
            val intent=Intent(Intent.ACTION_DIAL, Uri.parse("tel:"+ Uri.encode(emergency)))
            startActivity(intent)
        }
        btn_emergency1.setOnClickListener {
            val intent=Intent(Intent.ACTION_DIAL, Uri.parse("tel:"+ Uri.encode(emergency)))
            startActivity(intent)
        }

        btn_chat.setOnClickListener {
            val intent=Intent(this,ChatActivity::class.java)
            intent.putExtra("driverName",txt_driver_name.text.toString())
            intent.putExtra("senderUid",txt_rider_id.text.toString())
            intent.putExtra("receiverUid",txt_driver_id.text.toString())
            startActivity(intent)

        }
        btn_chat1.setOnClickListener {
            val intent=Intent(this,ChatActivity::class.java)
            intent.putExtra("driverName",txt_driver_name1.text.toString())
            intent.putExtra("senderUid",txt_rider_id1.text.toString())
            intent.putExtra("receiverUid",txt_driver_id1.text.toString())
            startActivity(intent)

        }

    }

    private fun LoadNSUBus() {
        if(Common.driversFound.size > 0){
            var min = 0f
            var foundDriver:DriverGeoModel?=null

            var busLocation:MutableList<DriverGeoModel> = ArrayList()

            for(key in Common.driversFound.keys){
                if(Common.driversFound[key]!!.driverInfoModel!!.vehicle_type != vehicle_type )continue
                busLocation.add(Common.driversFound[key]!!)
            }

            mMap.clear()
            fill_maps.visibility = View.GONE
            if(animator != null)animator!!.end()
            val cameraPos = CameraPosition.Builder().target(mMap.cameraPosition.target)
                .tilt(0f).zoom(mMap.cameraPosition.zoom).build()
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPos))

            if(busLocation.size == 0){
                Snackbar.make(main_layout,getString(R.string.drivers_not_found),Snackbar.LENGTH_LONG).show()
                lastDriverCall = null
                finish()
            }
            for(i in busLocation){
                Common.markerList.put(
                    i!!.key!!,//driverGeoModel!!.key!!,
                    mMap.addMarker(
                        MarkerOptions()
                            .position(
                                LatLng(
                                    i!!.geoLocation!!.latitude,
                                    i!!.geoLocation!!.longitude
                                )
                            )
                            .flat(true)
                            .title(
                                Common.vehicle_Name(
                                    i!!.driverInfoModel!!.vehicle_name
                                )
                            )
                            .snippet(i!!.driverInfoModel!!.vehicle_model)
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
                    )!!
                )
            }
            //mMap.setOnMarkerClickListener(this) NSU er bus er route paile erpor boshabo

            fusedLocationProviderClient!!
                .lastLocation
                .addOnSuccessListener { location->
                    myloc = LatLng(location.latitude,location.longitude) as LatLng
                    layout_qrcode.visibility = View.VISIBLE
                    enterTheBus()
                }
        }else{
            Snackbar.make(main_layout,getString(R.string.drivers_not_found),Snackbar.LENGTH_LONG).show()
            lastDriverCall = null
            finish()
        }
    }

    private fun enterTheBus() {

        btn_in.setOnClickListener {
            updateLocation()
            qrscanButton = "IN"
            val scanner = IntentIntegrator(this)
            scanner.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            scanner.initiateScan()
        }

        btn_out.setOnClickListener {
            updateLocation()
            qrscanButton = "OUT"
            val scanner = IntentIntegrator(this)
            scanner.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            scanner.initiateScan()
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(resultCode == Activity.RESULT_OK){
            val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
            if (result != null) {
                if (result.contents == null) {
                    Toast.makeText(this, "Failed", Toast.LENGTH_LONG).show()
                } else {
                    try {
                        val obj = JSONObject(result.contents)
                        var id : String = ""
                        var action : String = ""
                        if(obj.has("ID"))id = obj.getString("ID")
                        if(obj.has("TEXT"))action = obj.getString("TEXT")

                        if(qrscanButton != action){
                            Toast.makeText(this,"You have scanned the wrong QR code",Toast.LENGTH_LONG).show()
                        }else{
                            if(action == "IN"){
                                if(Common.currentRider!!.nsu_bus_id == ""){

                                    Common.currentRider!!.nsu_bus_id = id
                                    Common.currentRider!!.currentLatLng = myloc

                                    //Driver
                                    Common.driversFound[id]!!.driverInfoModel!!.total_user++

                                    //Updating value
                                    var temp = FirebaseDatabase.getInstance().getReference(Common.RIDER_INFO_REFERENCE)
                                    temp.child(Common.currentRider!!.id)
                                        .setValue(Common.currentRider)
                                        .addOnFailureListener{ e ->
                                            Toast.makeText(this,"Update failed", Toast.LENGTH_SHORT).show()
                                        }
                                        .addOnSuccessListener {
                                            Toast.makeText(this,"Update Success", Toast.LENGTH_SHORT).show()
                                        }

                                    temp = FirebaseDatabase.getInstance().getReference(Common.DRIVER_INFO_REFERENCE)
                                    temp.child(Common.driversFound[id]!!.driverInfoModel!!.id)
                                        .setValue(Common.driversFound[id]!!.driverInfoModel!!)
                                        .addOnFailureListener{ e ->
                                            Toast.makeText(this,"Update failed", Toast.LENGTH_SHORT).show()
                                        }
                                        .addOnSuccessListener {
                                            Toast.makeText(this,"Update Success*", Toast.LENGTH_SHORT).show()
                                        }


                                }else{
                                    Toast.makeText(this,"You are already inside a bus",Toast.LENGTH_LONG).show()
                                }
                            }else if(action == "OUT"){
                                //updateLocation()
                                if(Common.currentRider!!.nsu_bus_id == ""){
                                    Toast.makeText(this,"You are not inside a bus",Toast.LENGTH_LONG).show()
                                }else if(Common.currentRider!!.nsu_bus_id != id){
                                    Toast.makeText(this,"Wrong bus",Toast.LENGTH_LONG).show()
                                }else{
                                    //User
                                    Common.currentRider!!.nsu_bus_id = ""

                                    //Driver
                                    Common.driversFound[id]!!.driverInfoModel!!.total_user--
                                    Log.d("startLatLng",Common.currentRider!!.currentLatLng.toString())
                                    Log.d("endLatLng",myloc.toString())
                                    /*var org = Common.driversFound[id]!!.enter_latlng[Common.currentRider!!.id]
                                    Log.d("org_point",org.toString())
                                    Common.driversFound[id]!!.enter_latlng.remove(Common.currentRider!!.id)

                                    var des:LatLng?=null
                                    fusedLocationProviderClient!!
                                        .lastLocation.addOnSuccessListener { location->
                                            des = LatLng(location.latitude,location.longitude)
                                        }*/

                                    var org_string = Common.currentRider!!.currentLatLng!!.latitude.toString() + "," + Common.currentRider!!.currentLatLng!!.longitude.toString()
                                    var des_string = myloc!!.latitude.toString() + "," + myloc!!.longitude.toString()
                                    var cost = 0.0 as Double
                                    compositeDisposable.add(iGoogleAPI.getBusDirection("transit" ,
                                        des_string,des_string,
                                        "AIzaSyCDz6LWeUuWz7f4OYPeKkFIJy5QCgxyW84")
                                    !!.subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe{ returnResult->
                                            Log.d("API_RETURN",returnResult)
                                            try{
                                                val jsonObject = JSONObject(returnResult)
                                                val jsonArray=jsonObject.getJSONArray("routes");
                                                val objects=jsonArray.getJSONObject(0)
                                                val legs=objects.getJSONArray("legs")
                                                val legsObject=legs.getJSONObject(0)
                                                val distance=legsObject.getJSONObject("distance")
                                                val distanceText=distance.getString("text")
                                                val distanceValue=distance.getInt("value")
                                                cost = distanceText.dropLast(3).toDoubleOrNull()!!
                                                //Toast.makeText(this,"Your total distance " + distanceText.toString(),Toast.LENGTH_LONG).show()
                                            }catch (e: java.lang.Exception){
                                                Toast.makeText(this,e.message!!,Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    )

                                    Common.currentRider!!.currentLatLng = null
                                    //Updating value
                                    var temp = FirebaseDatabase.getInstance().getReference(Common.RIDER_INFO_REFERENCE)
                                    temp.child(Common.currentRider!!.id)
                                        .setValue(Common.currentRider)
                                        .addOnFailureListener{ e ->
                                            Toast.makeText(this,"Update failed", Toast.LENGTH_SHORT).show()
                                        }
                                        .addOnSuccessListener {
                                            //Toast.makeText(this,"Update Success", Toast.LENGTH_SHORT).show()
                                        }

                                    temp = FirebaseDatabase.getInstance().getReference(Common.DRIVER_INFO_REFERENCE)
                                    temp.child(Common.driversFound[id]!!.driverInfoModel!!.id)
                                        .setValue(Common.driversFound[id]!!.driverInfoModel!!)
                                        .addOnFailureListener{ e ->
                                            Toast.makeText(this,"Update failed", Toast.LENGTH_SHORT).show()
                                        }
                                        .addOnSuccessListener {
                                            cost = cost * 20.0
                                            Toast.makeText(this,"Your total distance " + cost.toString(),Toast.LENGTH_LONG).show()
                                        }

                                }

                            }else{
                                Toast.makeText(this,"Unknown QR Code",Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: JSONException) {
                        Toast.makeText(this, result.contents, Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                super.onActivityResult(requestCode, resultCode, data)
            }
        }
    }

    private fun addMarkerWithPulseAnimation() {
        confirm_pickup_layout.visibility = View.GONE
        fill_maps.visibility = View.VISIBLE
        finding_your_ride_layout.visibility = View.VISIBLE

        originMarker = mMap.addMarker(MarkerOptions().icon(BitmapDescriptorFactory.defaultMarker())
            .position(selectPlaceEvent!!.origin))
        addPulsatingEffect(selectPlaceEvent!!)

    }

    private fun addPulsatingEffect(selectPlaceEvent: SelectPlaceEvent) {
        if(lastPulseAnimator != null)lastPulseAnimator!!.cancel()
        if(lastUserCircle != null)lastUserCircle!!.center = selectPlaceEvent.origin
        lastPulseAnimator = Common.valueAnimate(duration,object :ValueAnimator.AnimatorUpdateListener{
            override fun onAnimationUpdate(p0: ValueAnimator?) {
                if(lastUserCircle != null)lastUserCircle!!.radius = p0!!.animatedValue.toString().toDouble() else{
                    lastUserCircle = mMap.addCircle(CircleOptions()
                        .center(selectPlaceEvent.origin )
                        .radius(p0!!.animatedValue.toString().toDouble())
                        .strokeColor(Color.WHITE)
                        .fillColor(ContextCompat.getColor(this@RequestDriverActivity,R.color.map_darkar)))
                }
            }
        })

        //start rotating camera
        startMapCameraSpinningAnimation(selectPlaceEvent)
    }

    private fun startMapCameraSpinningAnimation(selectPlaceEvent: SelectPlaceEvent?) {
        if(animator != null )animator!!.cancel()
        animator =ValueAnimator.ofFloat(0f,(DESIRED_NUM_OF_SPIN*360).toFloat())
        animator!!.duration = (DESIRED_NUM_OF_SPIN*DESIRED_SECONDS_PER_ONE_FULL_360_SPIN*1000).toLong()
        animator!!.interpolator = LinearInterpolator()
        animator!!.startDelay = (100)
        animator!!.addUpdateListener{ valueAnimator ->
            val newBearingValue = valueAnimator.animatedValue as Float
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder()
                .target(selectPlaceEvent!!.origin )
                .zoom(16f)
                .tilt(45f)
                .bearing(newBearingValue)
                .build()
            ))
        }
        animator!!.start()

        if(vehicle_type != "Bus")findNearbyDriver(selectPlaceEvent!!)
        else findNearbyBus(selectPlaceEvent!!)
    }

    private fun findNearbyBus(selectPlaceEvent: SelectPlaceEvent) {
        for(i in busList){
            Log.d("buslist",i)
        }
        finding_your_ride_layout.visibility = View.GONE

        if(Common.driversFound.size > 0){
            var min = 0f
            var foundDriver:DriverGeoModel?=null
            val currentRiderLocation = Location("")
            currentRiderLocation.latitude = selectPlaceEvent!!.origin!!.latitude
            currentRiderLocation.longitude = selectPlaceEvent!!.origin!!.longitude

            var busLocation:MutableList<DriverGeoModel> = ArrayList()
            Log.d("type",vehicle_type.toString())
            for(key in Common.driversFound.keys){

                if(Common.driversFound[key]!!.driverInfoModel!!.vehicle_type != vehicle_type )continue
                if(vehicle_type == "Bus" && !(Common.driversFound[key]!!.driverInfoModel!!.vehicle_name in busList))continue
                busLocation.add(Common.driversFound[key]!!)
            }

            mMap.clear()
            fill_maps.visibility = View.GONE
            if(animator != null)animator!!.end()
            val cameraPos = CameraPosition.Builder().target(mMap.cameraPosition.target)
                .tilt(0f).zoom(mMap.cameraPosition.zoom).build()
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPos))
            val busAdapter:ArrayAdapter<String> = ArrayAdapter(
                this,android.R.layout.simple_list_item_1,busListDetails
            )
            bus_list.adapter=busAdapter
            bus_layout.visibility=View.VISIBLE
            confirm_pickup_layout.visibility=View.GONE
            mMap.uiSettings.isZoomControlsEnabled=true
            if(busLocation.size == 0){
                Snackbar.make(main_layout,getString(R.string.drivers_not_found),Snackbar.LENGTH_LONG).show()
                lastDriverCall = null
                finish()
            }
            for(i in busLocation){
                if(vehicle_type == "Bus"){
                    Common.markerList.put(
                        i!!.key!!,//driverGeoModel!!.key!!,
                        mMap.addMarker(
                            MarkerOptions()
                                .position(
                                    LatLng(
                                        i!!.geoLocation!!.latitude,
                                        i!!.geoLocation!!.longitude
                                    )
                                )
                                .flat(true)
                                .title(
                                    Common.vehicle_Name(
                                        i!!.driverInfoModel!!.vehicle_name
                                    )
                                )
                                .snippet(i!!.driverInfoModel!!.vehicle_model)
                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
                        )!!
                    )
                }else if(vehicle_type == "NSU Bus"){
                    Common.markerList.put(
                        i!!.key!!,//driverGeoModel!!.key!!,
                        mMap.addMarker(
                            MarkerOptions()
                                .position(
                                    LatLng(
                                        i!!.geoLocation!!.latitude,
                                        i!!.geoLocation!!.longitude
                                    )
                                )
                                .flat(true)
                                .title(
                                    Common.vehicle_Name(
                                        i!!.driverInfoModel!!.vehicle_name
                                    )
                                )
                                .snippet(i!!.driverInfoModel!!.total_user.toString())
                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
                        )!!
                    )
                }
            }
            mMap.setOnMarkerClickListener(this)
        }else{
            Snackbar.make(main_layout,getString(R.string.drivers_not_found),Snackbar.LENGTH_LONG).show()
            lastDriverCall = null
            finish()
        }
    }


    override fun onMarkerClick(marker: Marker): Boolean {
        var newhashmap = Common.markerList.entries.associate { (String, Marker) -> Marker to String }
        var buskey = newhashmap[marker]
        var busname = Common.driversFound[buskey]!!.driverInfoModel!!.vehicle_name
        var idx = busList.indexOf(busname)
        var bus_stop_latlng = departureLatlng[idx]
        var current_latlng = marker.position

        var org = (current_latlng.latitude.toString() + "," + current_latlng.longitude.toString())
        var des = (bus_stop_latlng.latitude.toString() + "," + bus_stop_latlng.longitude.toString())


        compositeDisposable.add(iGoogleAPI.getBusDirection("transit" ,
            org,des,
            "AIzaSyCDz6LWeUuWz7f4OYPeKkFIJy5QCgxyW84")
        !!.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe{ returnResult->
                Log.d("API_RETURN",returnResult)
                try{
                    val jsonObject = JSONObject(returnResult)
                    val jsonArray=jsonObject.getJSONArray("routes");
                    var polylineList:ArrayList<LatLng?>?=null
                    for(i in 0 until jsonArray.length())
                    {
                        val route=jsonArray.getJSONObject(i)
                        val poly=route.getJSONObject("overview_polyline")
                        val polyline=poly.getString("points")
                        polylineList=Common.decodePoly(polyline)

                    }

                    var polyLineOptions= PolylineOptions()

                    polyLineOptions!!.color(Color.GRAY)
                    polyLineOptions!!.width(12f)

                    polyLineOptions!!.startCap(SquareCap())
                    polyLineOptions!!.jointType(JointType.ROUND)
                    polyLineOptions!!.addAll(polylineList!!)
                    var grayPolyLine=mMap.addPolyline(polyLineOptions!!)

                    var blackPolylineOptions= PolylineOptions()
                    blackPolylineOptions!!.color(Color.BLACK)
                    blackPolylineOptions!!.width(5f)
                    blackPolylineOptions!!.startCap(SquareCap())
                    blackPolylineOptions!!.jointType(JointType.ROUND)
                    blackPolylineOptions!!.addAll(polylineList!!)
                    var blackPolyLine=mMap.addPolyline(blackPolylineOptions!!)

                    //Animator
                    val valueAnimator=ValueAnimator.ofInt(0,100)
                    valueAnimator.duration=1100
                    valueAnimator.repeatCount=ValueAnimator.INFINITE
                    valueAnimator.interpolator=LinearInterpolator()
                    valueAnimator.addUpdateListener { value->
                        val points=grayPolyLine!!.points
                        val percentValue=value.animatedValue.toString().toInt()
                        val size=points.size
                        val newpoints=(size*(percentValue/100.0f)).toInt()
                        val p=points.subList(0,newpoints)
                        blackPolyLine!!.points=(p)
                    }
                    valueAnimator.start()
                    val latLngBound=LatLngBounds.Builder().include(current_latlng)
                        .include(bus_stop_latlng)
                        .build()

                    //Add car icon for origin
                    val objects=jsonArray.getJSONObject(0)
                    val legs=objects.getJSONArray("legs")
                    val legsObject=legs.getJSONObject(0)

                    val time=legsObject.getJSONObject("duration")

                    val duration =time.getString("text")
                    val durationValue=time.getInt("value")
                    val distance=legsObject.getJSONObject("distance")
                    val distanceText=distance.getString("text")
                    val distanceValue=distance.getInt("value")

                    val startAddress=legsObject.getString("start_address")
                    val endAddress=legsObject.getString("end_address")

                    val startLocation=legsObject.getJSONObject("start_location")
                    val endLocation=legsObject.getJSONObject("end_location")

                    Toast.makeText(this,"Approx time to reach the busstop  " + duration.toString(),Toast.LENGTH_LONG).show()

                    //set value
                    //txt_distance.text=(distanceText)
                    //txt_time.text=duration

                    //update here


                }catch (e: java.lang.Exception){
                    Toast.makeText(this,e.message!!,Toast.LENGTH_LONG).show()
                }
            }
        )

        return false
    }


    private fun findNearbyDriver(selectPlaceEvent: SelectPlaceEvent) {

        if(Common.driversFound.size > 0){
            var min = 0f
            var foundDriver:DriverGeoModel?=null
            val currentRiderLocation = Location("")
            currentRiderLocation.latitude = selectPlaceEvent!!.origin!!.latitude
            currentRiderLocation.longitude = selectPlaceEvent!!.origin!!.longitude


            for(key in Common.driversFound.keys){
                if(Common.driversFound[key]!!.driverInfoModel!!.vehicle_type != vehicle_type )continue
                val driverLocation = Location("")
                driverLocation.latitude = Common.driversFound[key]!!.geoLocation!!.latitude
                driverLocation.longitude = Common.driversFound[key]!!.geoLocation!!.longitude


                if(min == 0f){
                    min = driverLocation.distanceTo(currentRiderLocation)
                    if(!Common.driversFound[key]!!.isDecline){
                        foundDriver = Common.driversFound[key]
                        break;
                    }else
                        continue;

                }else if(driverLocation.distanceTo(currentRiderLocation) < min){
                    min = driverLocation.distanceTo(currentRiderLocation)
                    if(!Common.driversFound[key]!!.isDecline){
                        foundDriver = Common.driversFound[key]
                        break;
                    }else
                        continue;
                }
            }
            /*Snackbar.make(main_layout,StringBuilder("Found Driver: ")
                .append(foundDriver!!.driverInfoModel!!.phoneNumber),Snackbar.LENGTH_LONG).show()*/

            if(foundDriver != null){
                UserUtils.sendRequestToDriver(this@RequestDriverActivity,main_layout,foundDriver,selectPlaceEvent!!)
                lastDriverCall = foundDriver;
            }else{
                Toast.makeText(this,getString(R.string.no_driver_accept),Toast.LENGTH_LONG).show()
                lastDriverCall = null
                finish()
            }
        }else{
            Snackbar.make(main_layout,getString(R.string.drivers_not_found),Snackbar.LENGTH_LONG).show()
            lastDriverCall = null
            finish()
        }
    }

    override fun onDestroy() {
        if(animator != null)animator!!.end()
        super.onDestroy()
    }

    private fun setDataPickup(vehicle : String) {
        txt_address_pickup.text = if(txt_origin != null )txt_origin.text else "None"
        mMap.clear()

        if(vehicle != "Bus")addPickupMarker()
        else{
            for(i in departureLatlng){
                addPickupMarkerForBus(i)
            }
        }
    }

    private fun addPickupMarkerForBus(busmarker : LatLng) {
        val view = layoutInflater.inflate(R.layout.pickup_info_windows,null)

        val generator = IconGenerator(this)
        generator.setContentView(view)
        generator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon = generator.makeIcon()

        originMarker = mMap.addMarker(MarkerOptions()
            .icon(BitmapDescriptorFactory.fromBitmap(icon))
            .position(busmarker))
    }


    private fun addPickupMarker() {
        val view = layoutInflater.inflate(R.layout.pickup_info_windows,null)

        val generator = IconGenerator(this)
        generator.setContentView(view)
        generator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon = generator.makeIcon()

        originMarker = mMap.addMarker(MarkerOptions()
            .icon(BitmapDescriptorFactory.fromBitmap(icon))
            .position(selectPlaceEvent!!.origin))
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        try {
            val success=googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this,R.raw.uber_maps_style))
            if(!success)
            {
                Snackbar.make(mapFragment.requireView(),"Load map style failed ",Snackbar.LENGTH_LONG).show()
            }
        }catch (e:Exception)
        {
            Snackbar.make(mapFragment.requireView(),e.message!!,Snackbar.LENGTH_LONG).show()
        }

    }

    private fun drawPath(selectPlaceEvent: SelectPlaceEvent,mode : String) {
        //Request API
        if(mode != "transit"){
            compositeDisposable.add(iGoogleAPI.getDirection(mode ,
                "less_driving",
                selectPlaceEvent.originString,selectPlaceEvent.destinationString    ,
                "AIzaSyCDz6LWeUuWz7f4OYPeKkFIJy5QCgxyW84")
            !!.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe{ returnResult->
                    Log.d("API_RETURN",returnResult)
                    try{
                        val jsonObject = JSONObject(returnResult)
                        val jsonArray=jsonObject.getJSONArray("routes");
                        for(i in 0 until jsonArray.length())
                        {
                            val route=jsonArray.getJSONObject(i)
                            val poly=route.getJSONObject("overview_polyline")
                            val polyline=poly.getString("points")
                            polylineList=Common.decodePoly(polyline)

                        }

                        polyLineOptions= PolylineOptions()

                        polyLineOptions!!.color(Color.GRAY)
                        polyLineOptions!!.width(12f)

                        polyLineOptions!!.startCap(SquareCap())
                        polyLineOptions!!.jointType(JointType.ROUND)
                        polyLineOptions!!.addAll(polylineList!!)
                        grayPolyLine=mMap.addPolyline(polyLineOptions!!)

                        blackPolylineOptions= PolylineOptions()
                        blackPolylineOptions!!.color(Color.BLACK)
                        blackPolylineOptions!!.width(5f)
                        blackPolylineOptions!!.startCap(SquareCap())
                        blackPolylineOptions!!.jointType(JointType.ROUND)
                        blackPolylineOptions!!.addAll(polylineList!!)
                        blackPolyLine=mMap.addPolyline(blackPolylineOptions!!)

                        //Animator
                        val valueAnimator=ValueAnimator.ofInt(0,100)
                        valueAnimator.duration=1100
                        valueAnimator.repeatCount=ValueAnimator.INFINITE
                        valueAnimator.interpolator=LinearInterpolator()
                        valueAnimator.addUpdateListener { value->
                            val points=grayPolyLine!!.points
                            val percentValue=value.animatedValue.toString().toInt()
                            val size=points.size
                            val newpoints=(size*(percentValue/100.0f)).toInt()
                            val p=points.subList(0,newpoints)
                            blackPolyLine!!.points=(p)
                        }
                        valueAnimator.start()
                        val latLngBound=LatLngBounds.Builder().include(selectPlaceEvent.origin)
                            .include(selectPlaceEvent.destination)
                            .build()

                        //Add car icon for origin
                        val objects=jsonArray.getJSONObject(0)
                        val legs=objects.getJSONArray("legs")
                        val legsObject=legs.getJSONObject(0)

                        val time=legsObject.getJSONObject("duration")
                        val duration =time.getString("text")
                        val durationValue=time.getInt("value")
                        val distance=legsObject.getJSONObject("distance")
                        val distanceText=distance.getString("text")
                        val distanceValue=distance.getInt("value")

                        val startAddress=legsObject.getString("start_address")
                        val endAddress=legsObject.getString("end_address")

                        val startLocation=legsObject.getJSONObject("start_location")
                        val endLocation=legsObject.getJSONObject("end_location")

                        //set value
                        txt_distance.text=(distanceText)
                        //txt_time.text=duration

                        //update here
                        selectPlaceEvent.originAddress=startAddress
                        selectPlaceEvent.origin= LatLng(startLocation.getDouble("lat"),
                            startLocation.getDouble("lng"))
                        selectPlaceEvent.destinationAddress=endAddress
                        selectPlaceEvent.destination= LatLng(endLocation.getDouble("lat"),
                            endLocation.getDouble("lng"))
                        selectPlaceEvent.durationValue=durationValue
                        selectPlaceEvent.distanceValue=distanceValue
                        selectPlaceEvent.durationText=duration
                        selectPlaceEvent.distanceText=distanceText
                        //calculate fee
                        var fee=Common.calculateFeeBaseOnMetters(distanceValue)
                        if(vehicle_type=="Bike")
                        {
                            fee /= 2.0
                        }
                        selectPlaceEvent.totalFee=(fee)
                        txt_fee.text=StringBuilder("$").append(fee)

                        addOriginMarker(duration,startAddress)
                        addDestinationMarker(endAddress)

                        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(latLngBound,160))
                        mMap.moveCamera(CameraUpdateFactory.zoomTo(mMap.cameraPosition!!.zoom-1))


                    }catch (e: java.lang.Exception){
                        Toast.makeText(this,e.message!!,Toast.LENGTH_LONG).show()
                    }
                }
            )
        }else{
            compositeDisposable.add(iGoogleAPI.getBusDirection(mode ,
                selectPlaceEvent.originString,selectPlaceEvent.destinationString    ,
                "AIzaSyCDz6LWeUuWz7f4OYPeKkFIJy5QCgxyW84")
            !!.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe{ returnResult->
                    //Log.d("API_RETURN",returnResult)
                    try{
                        val jsonObject = JSONObject(returnResult)
                        val jsonArray=jsonObject.getJSONArray("routes");
                        for(i in 0 until jsonArray.length())
                        {
                            val route=jsonArray.getJSONObject(i)
                            val poly=route.getJSONObject("overview_polyline")
                            val polyline=poly.getString("points")
                            polylineList=Common.decodePoly(polyline)

                        }

                        polyLineOptions= PolylineOptions()

                        polyLineOptions!!.color(Color.GRAY)
                        polyLineOptions!!.width(12f)

                        polyLineOptions!!.startCap(SquareCap())
                        polyLineOptions!!.jointType(JointType.ROUND)
                        polyLineOptions!!.addAll(polylineList!!)
                        grayPolyLine=mMap.addPolyline(polyLineOptions!!)

                        blackPolylineOptions= PolylineOptions()
                        blackPolylineOptions!!.color(Color.BLACK)
                        blackPolylineOptions!!.width(5f)
                        blackPolylineOptions!!.startCap(SquareCap())
                        blackPolylineOptions!!.jointType(JointType.ROUND)
                        blackPolylineOptions!!.addAll(polylineList!!)
                        blackPolyLine=mMap.addPolyline(blackPolylineOptions!!)

                        //Animator
                        val valueAnimator=ValueAnimator.ofInt(0,100)
                        valueAnimator.duration=1100
                        valueAnimator.repeatCount=ValueAnimator.INFINITE
                        valueAnimator.interpolator=LinearInterpolator()
                        valueAnimator.addUpdateListener { value->
                            val points=grayPolyLine!!.points
                            val percentValue=value.animatedValue.toString().toInt()
                            val size=points.size
                            val newpoints=(size*(percentValue/100.0f)).toInt()
                            val p=points.subList(0,newpoints)
                            blackPolyLine!!.points=(p)
                        }
                        valueAnimator.start()
                        val latLngBound=LatLngBounds.Builder().include(selectPlaceEvent.origin)
                            .include(selectPlaceEvent.destination)
                            .build()

                        //Add car icon for origin
                        val objects=jsonArray.getJSONObject(0)
                        val legs=objects.getJSONArray("legs")
                        val legsObject=legs.getJSONObject(0)

                        val str = legsObject.getJSONArray("steps")
                        for(i in 0 until str.length()){
                            var temp = str.getJSONObject(i)
                            if(temp.has("transit_details")){

                                var temp2 = temp.getJSONObject("transit_details")
                                var line = temp2.getJSONObject("line")
                                var bus_name = line.getString("name").toString()
                                Log.d("bus_name",bus_name)
                                busList.add(bus_name)
                                busDetails= "$bus_name will departure from "

                                var arrival_stop = temp2.getJSONObject("arrival_stop")
                                var arrival_stop_string = arrival_stop.getString("name")
                                arrival_stop = arrival_stop.getJSONObject("location")
                                var arrival_stop_latlng = LatLng(arrival_stop.getString("lat").toDouble(),arrival_stop.getString("lng").toDouble())
                                Log.d("bus_name",arrival_stop_latlng.toString())
                                arrivalLatlng.add(arrival_stop_latlng)
                                busDetails= "$busDetails $arrival_stop_string and reach "

                                var departure_stop = temp2.getJSONObject("departure_stop")
                                var departure_stop_string = departure_stop.getString("name")
                                departure_stop = departure_stop.getJSONObject("location")
                                var departure_stop_latlng = LatLng(departure_stop.getString("lat").toDouble(),departure_stop.getString("lng").toDouble())
                                Log.d("bus_name",departure_stop_latlng.toString())
                                departureLatlng.add(departure_stop_latlng)
                                busDetails= "$busDetails $departure_stop_string"
                                busListDetails.add(busDetails!!)
                                busDetails=""

                            }
                        }
                        Log.d("BusTOT",busList!!.size.toString())
                        val time=legsObject.getJSONObject("duration")
                        val duration =time.getString("text")
                        val durationValue=time.getInt("value")
                        val distance=legsObject.getJSONObject("distance")
                        val distanceText=distance.getString("text")
                        val distanceValue=distance.getInt("value")

                        val startAddress=legsObject.getString("start_address")
                        val endAddress=legsObject.getString("end_address")

                        val startLocation=legsObject.getJSONObject("start_location")
                        val endLocation=legsObject.getJSONObject("end_location")

                        //set value
                        txt_distance.text=(distanceText)
                        //txt_time.text=duration

                        //update here
                        selectPlaceEvent.originAddress=startAddress
                        selectPlaceEvent.origin= LatLng(startLocation.getDouble("lat"),
                            startLocation.getDouble("lng"))
                        selectPlaceEvent.destinationAddress=endAddress
                        selectPlaceEvent.destination= LatLng(endLocation.getDouble("lat"),
                            endLocation.getDouble("lng"))
                        selectPlaceEvent.durationValue=durationValue
                        selectPlaceEvent.distanceValue=distanceValue
                        selectPlaceEvent.durationText=duration
                        selectPlaceEvent.distanceText=distanceText
                        //calculate fee
                        val fee=Common.calculateFeeBaseOnMetters(distanceValue)
                        selectPlaceEvent.totalFee=(fee)
                        txt_fee.text=StringBuilder("$").append(fee)

                        addOriginMarker(duration,startAddress)
                        addDestinationMarker(endAddress)

                        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(latLngBound,160))
                        mMap.moveCamera(CameraUpdateFactory.zoomTo(mMap.cameraPosition!!.zoom-1))


                    }catch (e: java.lang.Exception){
                        Toast.makeText(this,e.message!!,Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }

    private fun addDestinationMarker(endAddress: String) {
        val view=layoutInflater.inflate(R.layout.destination_info_windows,null)
        val txt_destination=view.findViewById<View>(R.id.txt_destination) as TextView
        txt_destination.text=Common.formatAddress(endAddress)

        val generator =IconGenerator(this)
        generator.setContentView(view)
        generator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon=generator.makeIcon()
        destinationMarker    =mMap.addMarker(MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(icon))
            .position(selectPlaceEvent!!.destination))

    }

    private fun addOriginMarker(duration: String, startAddress: String) {
        val view=layoutInflater.inflate(R.layout.origin_info_windows,null)
        val txt_time=view.findViewById<View >(R.id.txt_time) as TextView
        txt_origin=view.findViewById<View >(R.id.txt_origin) as TextView

        txt_time.text=Common.formatDuration(duration)
        txt_origin.text=Common.formatAddress(startAddress)

        val generator =IconGenerator(this)
        generator.setContentView(view)
        generator.setBackground(ColorDrawable(Color.TRANSPARENT))
        val icon=generator.makeIcon()
        originMarker    =mMap.addMarker(MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(icon))
            .position(selectPlaceEvent!!.origin))


    }

    private fun updateLocation() {
        if(fusedLocationProviderClient==null)
        {
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
            if(ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )!= PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_COARSE_LOCATION
                )!= PackageManager.PERMISSION_GRANTED)
            {
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
                    // If use has change location,calculate and load driver again
                    if(firstTime){
                        previousLocation = p0.lastLocation
                        currentLocation = p0.lastLocation
                        firstTime = false


                    }else{
                        previousLocation = currentLocation
                        currentLocation = p0.lastLocation
                    }
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
}



