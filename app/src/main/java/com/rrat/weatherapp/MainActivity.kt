package com.rrat.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.google.android.gms.location.*

import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.rrat.weatherapp.databinding.ActivityMainBinding
import com.rrat.weatherapp.models.WeatherResponse
import com.rrat.weatherapp.network.WeatherService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    private var mLatitude : Double = 0.0
    private var mLongitude : Double = 0.0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if(!isLocationEnable()){
            Toast.makeText(
                    this,
                    "Your Location Provider is Turned Off",
                    Toast.LENGTH_SHORT
            ).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            // Check Permissions
            Dexter.withContext(this).withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).withListener(object: MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if(report!!.areAllPermissionsGranted()){
                        //Get Localization
                        Log.i("Response Result","INIT")
                        requestNewLocationData()
                    }
                }
                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    p1: PermissionToken?
                ) {
                    showRationalDialogForPermissions()
                }
            }).onSameThread().check()
        }

    }

    private fun isLocationEnable(): Boolean{
        // This provides access to the system location services.
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun getLocationWeatherDetails(){
        if(Constants.isNetworkAvailable(this)){
            val retrofit : Retrofit = Retrofit.Builder()
                    .baseUrl(Constants.BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

            val service: WeatherService = retrofit.create(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(mLatitude, mLongitude, Constants.METRIC_UNIT, Constants.APP_ID)

            listCall.enqueue(object: Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if(response.isSuccessful){
                        val weatherList: WeatherResponse? = response.body()
                        Log.i("Response Result", "$weatherList")
                    }else{
                        when(response.code()){
                            400 -> {
                                Log.i("Error 400", "Bad Connection")
                            }
                            404 ->{
                                Log.i("Error 404", "Not Found")
                            }else ->{
                            Log.i("Error Generic", "Generic  Error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.i("Error onFailure", t.message.toString())
                }

            })

        }else{
            Toast.makeText(
                this,
                "No internet connection available",
                Toast.LENGTH_SHORT
            ).show()
            Log.i("Network", "Internet No Available")
        }
    }

    private fun showRationalDialogForPermissions(){
        AlertDialog.Builder(this).setMessage("It looks like you have turned off permission" +
                " required for this feature. It can be enable" +
                " under Application Settings")
            .setPositiveButton("GO TO SETTINGS"){
                    _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                }
            }
            .setNegativeButton("CANCEL"){
                    dialog, _ ->
                dialog.dismiss()
            }.show()

    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocationData(){

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        //mLocationRequest.interval = 1000
        //mLocationRequest.numUpdates = 1
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        Log.i("Response Result","Latitude Longitude")
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallBack, Looper.myLooper())

    }

    private val mLocationCallBack = object: LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult?) {
            val mLastLocation: Location = locationResult!!.lastLocation
            mLatitude = mLastLocation.latitude
            Log.i("Current Latitude", "$mLatitude")
            mLongitude = mLastLocation.longitude
            Log.i("Current Longitude", "$mLongitude")

            getLocationWeatherDetails()
        }
    }

}