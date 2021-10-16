package com.cebeayes.freetour

import android.Manifest
import android.app.AlertDialog
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
import android.content.ContentValues.TAG
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.libraries.places.api.net.FetchPlaceResponse
import android.widget.Toast
import java.io.IOException
import java.util.Locale
import android.location.Geocoder
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.common.api.Status
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.PlaceLikelihood
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import kotlinx.android.synthetic.main.activity_maps.*
import org.json.JSONObject
import androidx.annotation.NonNull
import android.location.Location
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import androidx.annotation.RequiresApi

import com.google.android.gms.tasks.Task

import com.google.android.gms.tasks.OnCompleteListener

import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var placesClient: PlacesClient
    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var textToSpeech: TextToSpeech

    private val FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION
    private val COURSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION
    private val LOCATION_PERMISSION_REQUEST_CODE = 1234
    private var mLocationPermissionsGranted = false
    private val DEFAULT_ZOOM = 15f
    private val LAT_LNG_BOUNDS = LatLngBounds(
        LatLng(-40.0, -168.0), LatLng(71.0, 136.0)
    )



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // create an object textToSpeech and adding features into it
        textToSpeech = TextToSpeech(applicationContext) { i ->
            // if No error is found then only it will run
            if (i != TextToSpeech.ERROR) {
                // To Choose language of speech
                textToSpeech.language = Locale.US
            }
        }
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Initialize Places.
        Places.initialize(applicationContext, getString(R.string.google_maps_key))
        placesClient = Places.createClient(this)

        initialiseAutoComplete()
    }

    fun getMyLocation(v: View) {
        if (mLocationPermissionsGranted) {
            getDeviceLocation();

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return;
            }
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(false);
        } else {
            getLocationPermission()
        }

    }

    private fun moveCamera(latLng: LatLng, zoom: Float, title: String) {
        Log.d(
            TAG,
            "moveCamera: moving the camera to: lat: " + latLng.latitude + ", lng: " + latLng.longitude
        )
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom), 2000, null)
        if (title != "My Location") {
            val options = MarkerOptions()
                .position(latLng)
                .title(title)
            mMap.addMarker(options)
        }
    }

    private fun getPlaceById(placeId: String?) {
        if (placeId == null) {
            return
        }
        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.LAT_LNG
        )
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
                Toast.makeText(this, "Place found: " + exception.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getDeviceLocation() {
        Log.d(TAG, "getDeviceLocation: getting the devices current location")
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        try {
            if (mLocationPermissionsGranted) {
                Log.i(TAG, "HEREEE3")
                val location: Task<*> = mFusedLocationProviderClient.getLastLocation()
                location.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "onComplete: found location!")
                        val currentLocation = task.result as Location
                        moveCamera(
                            LatLng(currentLocation.latitude, currentLocation.longitude),
                            DEFAULT_ZOOM,
                            "My Location"
                        )
                    } else {
                        Log.d(TAG, "onComplete: current location is null")
                        Toast.makeText(
                            this,
                            "unable to get current location",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "getDeviceLocation: SecurityException: " + e.message)
        }
    }


    private fun initialiseAutoComplete() {
        // Initialize the AutocompleteSupportFragment.
        val autocompleteFragment =
            supportFragmentManager.findFragmentById(R.id.autocomplete_fragment)
                    as AutocompleteSupportFragment

        // Specify the types of place data to return.
        autocompleteFragment.setPlaceFields(
            listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG
            )
        )

        // Set up a PlaceSelectionListener to handle the response.
        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            override fun onPlaceSelected(place: Place) {
                Log.i(TAG, "Place: ${place.name}, ${place.id}")
                moveCamera(
                    LatLng(place.latLng!!.latitude, place.latLng!!.longitude),
                    DEFAULT_ZOOM,
                    place.name!!
                )
                val text = "The Brandenburg Gate (German: Brandenburger Tor [ˈbʁandn̩ˌbʊʁɡɐ ˈtoːɐ̯] (About this soundlisten)) is an 18th-century neoclassical monument in Berlin, built on the orders of Prussian king Frederick William II after the temporary restoration of order during the Batavian Revolution.[1] One of the best-known landmarks of Germany, it was built on the site of a former city gate that marked the start of the road from Berlin to the town of Brandenburg an der Havel, which used to be the capital of the Margraviate of Brandenburg.\n" +
                        "\n" +
                        "It is located in the western part of the city centre of Berlin within Mitte, at the junction of Unter den Linden and Ebertstraße, immediately west of the Pariser Platz. One block to the north stands the Reichstag building, which houses the German parliament (Bundestag). The gate is the monumental entry to Unter den Linden, a boulevard of linden trees which led directly to the royal City Palace of the Prussian monarchs.\n" +
                        "\n" +
                        "Throughout its existence, the Brandenburg Gate was often a site for major historical events and is today considered not only as a symbol of the tumultuous history of Europe and Germany, but also of European unity and peace.[2]"

                textToSpeech.speak(text,TextToSpeech.QUEUE_FLUSH,null, "id")


            }

            override fun onError(status: Status) {
                Log.i(TAG, "An error occurred: $status")
            }
        })

    }

    private fun geoLocate(search: String) {
        val geoCoder = Geocoder(this)

        try {
            val resultList = geoCoder.getFromLocationName(search, 1)
            if(resultList.isNotEmpty()) {
                val address = resultList[0]
                Log.i(TAG, address.toString())
            }
        } catch (e: IOException) {
            Log.e("IOException", e.message.toString())
        }

    }

    fun getAddress(context: Context?, lat: Double, lng: Double) {
        val geocoder = Geocoder(context, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            val obj = addresses[0]
            Toast.makeText(this, ""+obj.countryName+" "+obj.subAdminArea+" "+obj.locality+" "+obj.extras, Toast.LENGTH_SHORT).show()
            Log.i(TAG, obj.countryName)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
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
        val initialLocation = LatLng(52.46445379930646, 13.437549696475179)
        //mMap.addMarker(MarkerOptions().position(initialLocation).title("Hello There"))
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(initialLocation, 15f), 5000, null);

        mMap.setOnMapClickListener {
            getAddress(this, it.latitude, it.longitude)
            // Instantiate the RequestQueue.
            val queue = Volley.newRequestQueue(this)
            val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=${it.latitude},${it.longitude}&radius=100&key=${getString(R.string.google_maps_key)}\n"

            // Request a string response from the provided URL.
            val stringRequest = StringRequest(
                Request.Method.GET, url,
                { response ->
                    // Display the first 500 characters of the response string.
                    parseHtmlResponse(response)
                    /*
                    val placeFields = listOf(Place.Field.ID, Place.Field.NAME)
                    val request = FetchPlaceRequest.builder(placeId, placeFields)
                        .build()

                    // Add a listener to handle the response.
                    placesClient.fetchPlace(request).addOnSuccessListener { response: FetchPlaceResponse ->
                        val place = response.place
                        Log.i(TAG, "Place found: " + place.name)
                        Toast.makeText(this, "Place found: " + place.name, Toast.LENGTH_SHORT).show()

                    }.addOnFailureListener { exception: Exception ->
                        if (exception is ApiException) {
                            val apiException = exception as ApiException
                            val statusCode = apiException.statusCode
                            // Handle error with given status code.
                            Log.e(TAG, "Place not found: " + exception.message)
                            Toast.makeText(this, "Place found: " + exception.message, Toast.LENGTH_SHORT).show()

                        }
                    }
                    Log.i(TAG,"Response is: $response")

                     */
                },
                {
                    Log.i(TAG,"FAILED")
                }
            )

            queue.add(stringRequest)
        }
    }

    fun parseHtmlResponse(response: String) {
        val resultObject = JSONObject(response);
        val placesArray = resultObject.getJSONArray("results");
        //loop through places
        for (p in 0 until placesArray.length()) {
            val placeObject = placesArray.getJSONObject(p);
            val name = placeObject.getString("name")
            val placeId = placeObject.getString("place_id")
            Log.i(TAG, "name: $name, placeId: $placeId")
        }
    }

    private fun getLocationPermission() {
        Log.d(TAG, "getLocationPermission: getting location permissions")
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (ContextCompat.checkSelfPermission(
                this.applicationContext,
                FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (ContextCompat.checkSelfPermission(
                    this.applicationContext,
                    COURSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                mLocationPermissionsGranted = true
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    permissions,
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                permissions,
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionsResult: called.")
        mLocationPermissionsGranted = false
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.size > 0) {
                    var i = 0
                    while (i < grantResults.size) {
                        if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                            mLocationPermissionsGranted = false
                            Log.d(TAG, "onRequestPermissionsResult: permission failed")
                            return
                        }
                        i++
                    }
                    Log.d(TAG, "onRequestPermissionsResult: permission granted")
                    mLocationPermissionsGranted = true
                    getDeviceLocation()
                }
            }
        }
    }
}