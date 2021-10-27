package com.cebeayes.freetour

import android.Manifest
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
import com.google.android.libraries.places.api.net.FetchPlaceResponse
import android.widget.Toast
import java.io.IOException
import java.util.Locale
import android.location.Geocoder
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.common.api.Status
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import kotlinx.android.synthetic.main.activity_maps.*
import org.json.JSONObject
import android.location.Location
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.annotation.RequiresApi

import com.google.android.gms.tasks.Task

import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import org.json.JSONArray

import android.location.LocationManager
import android.provider.Settings
import android.widget.ImageView
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*


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
    private var isLocationToggleOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Create an object textToSpeech and adding features into it
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

        // Initialize Places
        Places.initialize(applicationContext, getString(R.string.google_maps_key))
        placesClient = Places.createClient(this)

        // Initialize AutoComplete Search Bar
        initialiseAutoComplete()
    }


    /**
     * This callback is triggered when the map is ready to be used.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        // Add a marker in Sydney and move the camera
        val initialLocation = LatLng(52.46445379930646, 13.437549696475179)
        Thread.sleep(1000)
        moveCamera(initialLocation, 15f, "Default")
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    fun toggleLocation(v: View) {
        when(isLocationToggleOn) {
            true -> {
                locationToggleButton.setBackgroundColor(Color.WHITE)
                mMap.isMyLocationEnabled = false
                mMap.uiSettings.isMyLocationButtonEnabled = false
                isLocationToggleOn = false
            }
            false -> {
                if(!isLocationTurnedOn()) {
                    Log.i(TAG, "Location is not turned on!")
                    openLocationSettings()
                    return
                }
                locationToggleButton.setBackgroundColor(Color.GREEN)
                getDeviceLocation()

                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return;
                }
                mMap.isMyLocationEnabled = true
                mMap.uiSettings.isMyLocationButtonEnabled = true
                isLocationToggleOn = true
            }
        }



    }

    private fun openLocationSettings(){
        val locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
        task.addOnSuccessListener { locationSettingsResponse ->
            Log.i(TAG, "Location Settings is OK")
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException){
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    exception.startResolutionForResult(this@MapsActivity,
                        12)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private fun isLocationTurnedOn(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // This is a new method provided in API 28
            val lm = applicationContext.getSystemService(LOCATION_SERVICE) as LocationManager
            lm.isLocationEnabled
        } else {
            // This was deprecated in API 28
            val mode: Int = Settings.Secure.getInt(
                applicationContext.contentResolver, Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF
            )
            mode != Settings.Secure.LOCATION_MODE_OFF
        }
    }

    private fun moveCamera(latLng: LatLng, zoom: Float, title: String) {
        Log.d(
            TAG,
            "moveCamera: moving the camera to: lat: " + latLng.latitude + ", lng: " + latLng.longitude
        )
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom), 2000, null)
        if (title != "Default") {
            val options = MarkerOptions()
                .position(latLng)
                .title(title)
            mMap.addMarker(options)
        }
    }

    private fun getDeviceLocation() {
        Log.d(TAG, "getDeviceLocation: getting the devices current location")
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        try {
            val location: Task<*> = mFusedLocationProviderClient.getLastLocation()
            location.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "onComplete: found location!")
                    val currentLocation = task.result as Location
                    moveCamera(
                        LatLng(currentLocation.latitude, currentLocation.longitude),
                        DEFAULT_ZOOM,
                        "Default"
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
                openSearchWikipedia(place.name!!)
            }

            override fun onError(status: Status) {
                Log.i(TAG, "An error occurred: $status")
            }
        })

    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun playSpeech(text: String) {
        textToSpeech.speak(text,TextToSpeech.QUEUE_FLUSH,null, "id")
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun openSearchWikipedia(text: String) {
        val searchText = text.replace(" ", "%20")
        val url = "https://en.wikipedia.org/w/api.php?action=opensearch&search=$searchText&limit=1&namespace=0&format=json"
        Log.i(TAG, "OpenSearch URL: $url")
        val queue = Volley.newRequestQueue(this)

        val stringRequest = StringRequest(
            Request.Method.GET, url,
            { response ->
                val pageTitle = JSONArray(response).getJSONArray(1).getString(0)
                getWikiPage(pageTitle)
            },
            {
                Log.i(TAG,"OpenSearch failed", it)
            }
        )
        queue.add(stringRequest)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun getWikiPage(pageTitle: String) {
        val pageTitleWithoutSpaces = pageTitle.replace(" ", "%20")
        val url = "https://en.wikipedia.org/w/api.php?action=query&prop=extracts&exsentences=10&exlimit=1&titles=$pageTitleWithoutSpaces&explaintext=1&formatversion=2&format=json"
        val queue = Volley.newRequestQueue(this)
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            { res ->
                val jsonObject = JSONObject(res)
                val query = jsonObject.getJSONObject("query")
                val pages = query.getJSONArray("pages")
                val contentObject = pages.getJSONObject(0)
                val content = contentObject.getString("extract")
                playSpeech(content)
            },
            {
                Log.i(TAG,"getWikiPage failed", it)
            }
        )
        queue.add(stringRequest)
    }

    fun setMapOnTapListener() {
        mMap.setOnMapClickListener {
            // getAddress(this, it.latitude, it.longitude)
            // getPlaceByCoordinates(it.latitude, it.longitude)
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

    fun getPlaceByCoordinates(latitude: Double, longitude: Double) {
        val queue = Volley.newRequestQueue(this)
        val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=$latitude,$longitude&radius=100&key=${getString(R.string.google_maps_key)}\n"

        val stringRequest = StringRequest(
            Request.Method.GET, url,
            { response ->
                // Display the first 500 characters of the response string.
                parseHtmlResponse(response)
            },
            {
                Log.i(TAG,"FAILED")
            }
        )
        queue.add(stringRequest)
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
}