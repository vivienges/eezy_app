package com.example.android_project_bike

import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.FirebaseFirestore
import android.os.CountDownTimer
import android.widget.FrameLayout
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth


class BikeDetailsActivity : BaseActivity(), OnMapReadyCallback {

    lateinit var mMap: GoogleMap

    private var db = FirebaseFirestore.getInstance()
    private lateinit var auth: FirebaseAuth

    private lateinit var bike: Bike
    private lateinit var bikeId: String
    private lateinit var dialog: Dialog
    private lateinit var countDownTimer: CountDownTimer
    private var timeLeftInMilliSec = MAX_RESERVATION_TIME
    private var bikeReserved = false
    private lateinit var broadcastReceiver: BroadcastReceiver


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bike_details)

        broadcastReceiver = object : BroadcastReceiver() {

            override fun onReceive(arg0: Context, intent: Intent) {
                val action = intent.action
                if (action == FINISH_ACTIVITY_FLAG) {
                    finish()
                }
            }
        }
        registerReceiver(broadcastReceiver, IntentFilter(FINISH_ACTIVITY_FLAG))

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        Log.d("USER", "${currentUser!!.email}")

        bike = Bike()

        val intent = intent
        val bundle = intent.getBundleExtra("bundle")
        bikeId = bundle?.getString(BIKE_ID)!!

        val bikeTitle = findViewById<TextView>(R.id.bike_label)
        val bikeText = "Bike $bikeId"
        bikeTitle.text = bikeText

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_fragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        val rentBikeButton = findViewById<Button>(R.id.rent_bike_button)
        val reserveBikeButton = findViewById<Button>(R.id.reserve_bike_button)

        rentBikeButton.setOnClickListener {

            db.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { result ->
                    if (result.data != null) {
                        Log.d("DATA", "Current data: ${result.data}")
                        val payment = result["payment"]

                        if (payment == 0.toLong()) {
                            Log.d("PAYMENT", "$payment")

                            val intent = Intent(this, AddPaymentActivity::class.java)
                            intent.putExtra("bundle", bundle)
                            intent.putExtra("latitude", bike.position.latitude)
                            intent.putExtra("longitude", bike.position.longitude)
                            startActivity(intent)

                        } else {

                            val intent = Intent(this, EnterQrCodeActivity::class.java)
                            intent.putExtra("bundle", bundle)
                            intent.putExtra("latitude", bike.position.latitude)
                            intent.putExtra("longitude", bike.position.longitude)
                            startActivity(intent)
                        }
                    }
                }
        }

        dialog = Dialog(this)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setContentView(R.layout.reservation_popup)
        dialog.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val fragment = dialog.findViewById<FrameLayout>(R.id.timer_fragment)
        val timer = fragment.findViewById<TextView>(R.id.timer)

        reserveBikeButton.setOnClickListener {

            dialog.show()
            startStopTimer(timer)

            db.collection("bikes").document(bikeId)
                .update(
                    mapOf(
                        "available" to false,
                        "current_user" to currentUser.email
                    )
                )
                .addOnSuccessListener { result ->
                    Log.d("SUCCESS", "Added $result")
                }
                .addOnFailureListener { exception ->
                    Log.d("ERROR", "Adding data failed!")
                }

            bikeReserved = true


            //TODO: Scan Code --> Proceed to ScanActivity

            val cancelReservationButton = dialog.findViewById<Button>(R.id.cancel_reservation_button)

            cancelReservationButton.setOnClickListener {
                cancelReservation(dialog, timer)
                bikeReserved = false
            }

            val scanQRCodeButton = dialog.findViewById<Button>(R.id.scan_code_button)

            scanQRCodeButton.setOnClickListener {
                val intent = Intent(this, EnterQrCodeActivity::class.java)
                intent.putExtra("bundle", bundle)
                startActivity(intent)
            }


        }
    }

    override fun onBackPressed() {

        if (!bikeReserved) {
            super.onBackPressed()
        }
        else {
            Toast.makeText(
                this,
                "You can't leave this page if you have an ongoing reservation",
                Toast.LENGTH_LONG).show()
        }
    }


    override fun onMapReady(googleMap: GoogleMap) {

        mMap = googleMap
        db.collection("bikes").document(bikeId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("FAIL", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    Log.d("DATA", "Current data: ${snapshot.data}")
                    bike = snapshot.toObject(Bike::class.java)!!

                    val info = findViewById<TextView>(R.id.charge_val_label)
                    info.text = "${bike.charge}"


                    val position =
                        LatLng(bike.position.latitude, bike.position.longitude)
                    mMap.addMarker(MarkerOptions().position(position).title("Bike $bikeId"))
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 18F))

                } else {
                    Log.d("NULL", "Current data: null")
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }

    fun startStopTimer(timer: TextView) {

        countDownTimer = object : CountDownTimer(timeLeftInMilliSec, 1000) {
            override fun onTick(l: Long) {
                timeLeftInMilliSec = l
                timer.text = updateTimer()


            }

            override fun onFinish() {
                cancelReservation(dialog, timer)
            }
        }.start()

    }



    fun updateTimer(): String {
        val minutes = timeLeftInMilliSec / 60000
        val seconds = timeLeftInMilliSec % 60000 / 1000
        val timeLeft: String
        var min = minutes.toString()
        var sec = seconds.toString()

        if (seconds < 10) {
            sec = "0$sec"
        }

        if (minutes < 10) {
            min = "0$min"
        }

        timeLeft = "$min : $sec"
        return timeLeft

    }

    fun cancelReservation(dialog: Dialog, timer: TextView) {

        dialog.dismiss()
        countDownTimer.cancel()
        timeLeftInMilliSec = MAX_RESERVATION_TIME


        db.collection("bikes").document(bikeId)
            .update(
                mapOf(
                    "available" to true,
                    "current_user" to ""
                )
            )
            .addOnSuccessListener { result ->
                Log.d("SUCCESS", "Added $result")
            }
            .addOnFailureListener { exception ->
                Log.d("ERROR", "Adding data failed!")
            }
    }

    companion object {
        const val BIKE_ID = "BIKE_ID"
        const val FINISH_ACTIVITY_FLAG = "finish_activity"
        const val MAX_RESERVATION_TIME = 1800000.toLong()
    }

}
