package com.example.janbahonrider.Remote
import com.example.janbahonrider.Model.FCMResponse
import com.example.janbahonrider.Model.FCMSendData
import io.reactivex.Observable
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface IFCMService {
    @Headers(
        "Content-Type:application/json",
        "Authorization:key=AAAAyQ6Bouk:APA91bH8nKhg8aF2R7-n9SrwjCrcRN2BXBXng8oujDcaAONSB0kJwEEaltrZd0AUbS8iChf4xpAlTsKwXoYpajYrO5R__j3Yn3i43ayqLhtlY0PS5I1gfa6xTZiWrj7EQDFrwGB3u6r1"
    )
    @POST("fcm/send")
    fun sendNotification(@Body body: FCMSendData?):Observable<FCMResponse?>?
}