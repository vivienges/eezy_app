package com.example.android_project_bike

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.multidex.MultiDex
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.return_popup.*

class TourDetailsActivity : BaseActivity(), OnMapReadyCallback {

    lateinit var mMap: GoogleMap
    private var db = FirebaseFirestore.getInstance()

    lateinit var bike: Bike
    lateinit var bikeId: String
    lateinit var rideRefString: String
    lateinit var dialog: Dialog

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tour_details)

        val bundle = intent.getBundleExtra(BUNDLE)
        bikeId = bundle?.getString(BIKE_ID)!!
        rideRefString = bundle.getString(RIDE_DEF_STRING)!!
        val bikeTitle = findViewById<TextView>(R.id.title_label)
        val titleText = resources.getString(R.string.bike) + " " + bikeId
        bikeTitle.text = titleText
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_fragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)


        val pauseRideButton = findViewById<Button>(R.id.pause_bike_button)
        val returnBikeButton = findViewById<Button>(R.id.return_bike_button)

        returnBikeButton.setOnClickListener {

            //TODO Dialog if the user really wants to return the bike
            db.runBatch {
                db.collection(BIKES).document(bikeId)
                    .update(
                        mapOf(
                            AVAILABLE to true,
                            CURRENT_USER to "",
                            LOCKED to true
                        )
                    )
                db.collection(RIDES).document(rideRefString)
                    .update(END_TIME, FieldValue.serverTimestamp())
            }
            var totalPrice: Long
            db.collection(RIDES).document(rideRefString)
                .get()
                .addOnSuccessListener { result ->
                    totalPrice = result[TOTAL_PRICE] as Long
                    val confirmationText = "$totalPrice SEK"
                    dialog = Dialog(this)
                    dialog.setCanceledOnTouchOutside(false)
                    dialog.setCancelable(false)
                    dialog.setContentView(R.layout.return_popup)
                    dialog.final_price_label.text = confirmationText
                    dialog.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    dialog.show()
                    val okButton = dialog.ok_button
                    okButton.setOnClickListener{
                        dialog.dismiss()
                        finish()
                    }
                }

        }

        pauseRideButton.setOnClickListener {

            if (pauseRideButton.text == resources.getString(R.string.pause_ride)) {

                db.collection(BIKES).document(bikeId)
                    .update(
                        mapOf(
                            LOCKED to true
                        )
                    )
                    .addOnSuccessListener { result ->
                        Log.d("SUCCESS", "Added $result")
                    }
                    .addOnFailureListener { exception ->
                        Log.d("ERROR", "Adding data failed!")
                    }
                pauseRideButton.text = resources.getString(R.string.continue_ride)
                Toast.makeText(this, "Your ride was paused", Toast.LENGTH_LONG).show()
            }

            else {
                db.collection(BIKES).document(bikeId)
                    .update(
                        mapOf(
                            LOCKED to false
                        )
                    )
                    .addOnSuccessListener { result ->
                        Log.d("SUCCESS", "Added $result")
                    }
                    .addOnFailureListener { exception ->
                        Log.d("ERROR", "Adding data failed!")
                    }
                pauseRideButton.text = resources.getString(R.string.pause_ride)
                Toast.makeText(this, "Your ride was continued", Toast.LENGTH_LONG).show()

            }
        }

    }

    override fun onMapReady(googleMap: GoogleMap) {

        mMap = googleMap
        db.collection(BIKES).document(bikeId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("FAIL", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    Log.d("DATA", "Current data: ${snapshot.data}")
                    bike = snapshot.toObject(Bike::class.java)!!

                    val info = findViewById<TextView>(R.id.current_charge_val_label)
                    info.text = "${bike.charge}"


                    val position =
                        LatLng(bike.position.latitude, bike.position.longitude)
                    mMap.addMarker(MarkerOptions()
                        .position(position).title("Bike $bikeId")
                        .icon(BitmapDescriptorFactory.defaultMarker(82F)))
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 18F))

                } else {
                    Log.d("NULL", "Current data: null")
                }
            }

    }

    companion object {
            const val BIKE_ID = "BIKE_ID"
            const val BIKES = "bikes"
            const val RIDES = "rides"
            const val AVAILABLE = "available"
            const val CURRENT_USER = "current_user"
            const val LOCKED = "locked"
            const val BUNDLE = "bundle"
            const val END_TIME = "end_time"
            const val TOTAL_PRICE = "total_price"
            const val RIDE_DEF_STRING = "rideRefString"
    }
}
