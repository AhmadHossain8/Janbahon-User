package com.example.janbahonrider.Utils

import android.content.Context
import android.text.method.TextKeyListener.clear
import android.util.Log
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast
import com.example.janbahonrider.Common.Common
import com.example.janbahonrider.Model.DriverGeoModel
import com.example.janbahonrider.Model.EventBus.SelectPlaceEvent

import com.example.janbahonrider.Model.FCMSendData
import com.example.janbahonrider.Model.TokenModel
import com.example.janbahonrider.R
import com.example.janbahonrider.Remote.IFCMService
import com.example.janbahonrider.Remote.RetrofitClient
import com.example.janbahonrider.Remote.RetrofitFCMClient
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.core.RepoManager.clear
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlin.text.StringBuilder

object UserUtils {
    fun updateUser(
        view: View?,
        updateData:Map<String,Any>
    ){
        FirebaseDatabase.getInstance()
            .getReference(Common.RIDER_INFO_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .updateChildren(updateData)
            .addOnFailureListener { e ->
                Snackbar.make(view!!,e.message!!, Snackbar.LENGTH_LONG).show()
            }.addOnSuccessListener {
                Snackbar.make(view!!,"Update imformation Success", Snackbar.LENGTH_LONG).show()
            }
    }
    fun updateToken(context: Context, token: String) {
        val tokenModel = TokenModel()
        tokenModel.token = token;

        FirebaseDatabase.getInstance()
            .getReference(Common.TOKEN_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .setValue(tokenModel)
            .addOnFailureListener { e-> Toast.makeText(context,e.message, Toast.LENGTH_LONG).show() }
            .addOnSuccessListener {  }

    }

    fun sendRequestToDriver(context: Context, mainLayout: RelativeLayout?, foundDriver: DriverGeoModel?, selectPlaceEvent: SelectPlaceEvent) {
        Log.d("insidenoti","Sending notification to driver")
        val compositeDisposable = CompositeDisposable()
        val ifcmService = RetrofitFCMClient.instance!!.create(IFCMService::class.java)

        FirebaseDatabase.getInstance()
            .getReference(Common.TOKEN_REFERENCE)
            .child(foundDriver!!.key!!)
            .addListenerForSingleValueEvent(object :ValueEventListener{
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if(dataSnapshot.exists()){
                        val tokenModel = dataSnapshot.getValue(TokenModel::class.java)
                        val notificationData:MutableMap<String,String>  = HashMap()
                        notificationData.put(Common.NOTI_TITLE,Common.REQUEST_DRIVER_TITLE)
                        notificationData.put(Common.NOTI_BODY,"Requesting Driver Action")
                        notificationData.put(Common.RIDER_KEY,FirebaseAuth.getInstance().currentUser!!.uid)
                        notificationData.put(Common.PICKUP_LOCATION_STRING,selectPlaceEvent.originAddress  )
                        notificationData.put(Common.PICKUP_LOCATION,StringBuilder()
                            .append(selectPlaceEvent.origin.latitude)
                            .append(",")
                            .append(selectPlaceEvent.origin.longitude)
                            .toString())

                        notificationData[Common.DESTINATION_LOCATION_STRING] = selectPlaceEvent.destinationAddress
                        notificationData[Common.DESTINATION_LOCATION] = StringBuilder()
                            .append(selectPlaceEvent.destination.latitude)
                            .append(",")
                            .append(selectPlaceEvent.destination.longitude)
                            .toString()

                        //new info
                        notificationData[Common.RIDER_DISTANCE_TEXT] = selectPlaceEvent.distanceText!!
                        notificationData[Common.RIDER_DISTANCE_VALUE] = selectPlaceEvent.distanceValue.toString()
                        notificationData[Common.RIDER_DURATION_TEXT] = selectPlaceEvent.durationText!!
                        notificationData[Common.RIDER_DURATION_VALUE] = selectPlaceEvent.durationValue.toString()
                        notificationData[Common.RIDER_TOTAL_FEE] = selectPlaceEvent.totalFee.toString()


                        val fcmSendData = FCMSendData(tokenModel!!.token,notificationData)

                        compositeDisposable.add(ifcmService.sendNotification(fcmSendData)!!
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe({ fcmResponse ->
                                if(fcmResponse!!.success ==0){
                                    compositeDisposable.clear()
                                    Snackbar.make(mainLayout!!,context.getString(R.string.send_request_driver_failed),Snackbar.LENGTH_LONG).show()
                                }
                            }, {t:Throwable->
                                compositeDisposable.clear()
                                Snackbar.make(mainLayout!!,t!!.message!!,Snackbar.LENGTH_LONG).show()
                            }))
                    }else{
                        Snackbar.make(mainLayout!!,context.getString(R.string.token_not_found),Snackbar.LENGTH_LONG).show()
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Snackbar.make(mainLayout!!,databaseError.message,Snackbar.LENGTH_LONG).show()
                }
            })

    }
}