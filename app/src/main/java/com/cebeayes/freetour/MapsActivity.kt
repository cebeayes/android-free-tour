package com.cebeayes.freetour

import android.R.attr
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.cebeayes.freetour.databinding.ActivityMapsBinding
import com.google.android.libraries.places.api.Places

import com.google.android.libraries.places.api.net.PlacesClient

import android.R.attr.apiKey
import android.content.ContentValues.TAG
import com.google.android.libraries.places.api.net.FetchPlaceRequest

import com.google.android.libraries.places.api.model.Place

import java.util.Arrays
import android.util.Log

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.libraries.places.api.net.FetchPlaceResponse


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Initialize Places.
        Places.initialize(applicationContext, getString(R.string.google_maps_key))
        // Create a new Places client instance.
        val placesClient = Places.createClient(this)

        val placeId = "INSERT_PLACE_ID_HERE"
        val placeFields = listOf(Place.Field.ID, Place.Field.NAME)
        val request = FetchPlaceRequest.builder(placeId, placeFields)
            .build()

        // Add a listener to handle the response.
        placesClient.fetchPlace(request).addOnSuccessListener { response: FetchPlaceResponse ->
            val place = response.place
            Log.i(TAG, "Place found: " + place.name)
        }.addOnFailureListener { exception: Exception ->
            if (exception is ApiException) {
                val apiException = exception as ApiException
                val statusCode = apiException.statusCode
                // Handle error with given status code.
                Log.e(TAG, "Place not found: " + exception.message)
            }
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Add a marker in Sydney and move the camera
        val sydney = LatLng(-34.0, 151.0)
        mMap.addMarker(MarkerOptions().position(sydney).title("Marker in Sydney"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney))
    }
}