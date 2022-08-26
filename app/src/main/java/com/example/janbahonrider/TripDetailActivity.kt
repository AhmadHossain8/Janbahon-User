package com.example.janbahonrider

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.example.janbahonrider.Callback.FirebaseFailedListener
import com.example.janbahonrider.Callback.ITripDetailListener
import com.example.janbahonrider.Common.Common
import com.example.janbahonrider.Model.AnimationModel
import com.example.janbahonrider.Model.EventBus.*
import com.example.janbahonrider.Model.TripPlanModel
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.activity_request_driver.*
import kotlinx.android.synthetic.main.activity_request_driver.main_layout
import kotlinx.android.synthetic.main.activity_splash_screen.*
import kotlinx.android.synthetic.main.activity_trip_detail.*
import kotlinx.android.synthetic.main.activity_trip_detail.txt_distance
import kotlinx.android.synthetic.main.layout_confirm_uber.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.StringBuilder
import kotlin.random.Random

class TripDetailActivity : AppCompatActivity(), ITripDetailListener, FirebaseFailedListener {

    lateinit var tripDetailListener:ITripDetailListener
    lateinit var firebaseFailedListener: FirebaseFailedListener
    private lateinit var driver: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip_detail)

        init()
    }
    private fun init() {
        tripDetailListener=this
        firebaseFailedListener=this
    }

    override fun onTripDetailLoadSuccess(tripPlanModel: TripPlanModel) {
        txt_date.text=tripPlanModel.timeText
        txt_price.text=StringBuilder("$").append(tripPlanModel.totalFee)
        txt_origin.text=tripPlanModel.originString
        txt_destination.text=tripPlanModel.destinationString
        if(tripPlanModel.driverInfoModel!!.vehicle_type=="Bike")
        {
            txt_base_fare.text=StringBuilder("$").append(Common.BASE_FARE_BIKE)
            txt_vehicle_type.text="Janbahon Bike"
        }
        else
        {
            txt_base_fare.text=StringBuilder("$").append(Common.BASE_FARE)
            txt_vehicle_type.text="Janbahon Car"
        }

        txt_distance.text=tripPlanModel.distanceText
        txt_time.text=tripPlanModel.durationText
        //show layout
        layout_details.visibility=View.VISIBLE
        progress_ring.visibility=View.GONE

        ratingBar2.setOnRatingBarChangeListener{ ratingBar,f1,b ->
            tripPlanModel.driverInfoModel!!.total_trips++
            tripPlanModel.driverInfoModel!!.stars += f1.toInt()
            tripPlanModel.driverInfoModel!!.rating = (tripPlanModel.driverInfoModel!!.stars / tripPlanModel.driverInfoModel!!.total_trips).toDouble()


            driver = FirebaseDatabase.getInstance().getReference(Common.DRIVER_INFO_REFERENCE)
            driver.child(tripPlanModel.driver!!)
                .setValue(tripPlanModel.driverInfoModel)
                .addOnFailureListener{ e ->
                    Toast.makeText(this@TripDetailActivity,"Update failed", Toast.LENGTH_SHORT).show()
                }
                .addOnSuccessListener {
                    startActivity(Intent(this,HomeActivity::class.java))
                }
            startActivity(Intent(this,HomeActivity::class.java))
        }
    }

    override fun onFirebaseFailed(message: String) {
        Snackbar.make(main_layout,message,Snackbar.LENGTH_LONG).show()
    }

    override fun onStart() {
        super.onStart()
        if(!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this)
    }


    override fun onStop() {

        if(EventBus.getDefault().hasSubscriberForEvent(LoadTripDetailEvent::class.java))
            EventBus.getDefault().removeStickyEvent(LoadTripDetailEvent::class.java)

        EventBus.getDefault().unregister(this)
        super.onStop()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onLoadTripDetailEvent(event: LoadTripDetailEvent){
        FirebaseDatabase.getInstance()
            .getReference(Common.TRIP)
            .child(event.tripKey)
            .addListenerForSingleValueEvent(object :ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    if(snapshot.exists()){
                        val model=snapshot.getValue(TripPlanModel::class.java)
                        tripDetailListener.onTripDetailLoadSuccess(model!!)
                    }
                    else
                    {
                        firebaseFailedListener.onFirebaseFailed("Can't find trip key ")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    firebaseFailedListener.onFirebaseFailed(error.message)
                }

            })
    }
}