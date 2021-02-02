package com.rrat.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.google.android.gms.location.*
import com.google.gson.Gson

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
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    private lateinit var mSharedPreferences : SharedPreferences

    private var mProgressDialog: Dialog? = null

    private var mLatitude : Double = 0.0
    private var mLongitude : Double = 0.0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setupUI()

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



    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh ->{
                requestUpdateLocationData()
                true
            }else -> super.onOptionsItemSelected(item)
        }
    }


    private fun isLocationEnable(): Boolean{
        // This provides access to the system location services.
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }


    private fun showCustomProgressDialog(){
        mProgressDialog = Dialog(this)

        mProgressDialog!!.setContentView(R.layout.dialog_progress_bar)

        mProgressDialog!!.show()
    }

    private fun hideProgressDialog(){
        mProgressDialog?.dismiss()
    }

    private fun getLocationWeatherDetails(){
        if(Constants.isNetworkAvailable(this)){
            val retrofit : Retrofit = Retrofit.Builder()
                    .baseUrl(Constants.BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

            val service: WeatherService = retrofit.create(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(mLatitude, mLongitude, Constants.METRIC_UNIT, Constants.APP_ID)

            showCustomProgressDialog()

            listCall.enqueue(object: Callback<WeatherResponse>{
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>?
                ) {
                    hideProgressDialog()
                    if(response!!.isSuccessful){
                        val weatherList: WeatherResponse? = response.body()

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        setupUI()


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
                    hideProgressDialog()
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

    @SuppressLint("MissingPermission")
    private fun requestUpdateLocationData(){

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mLocationRequest.interval = 500
        mLocationRequest.numUpdates = 1
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        Log.i("Response Result","Latitude Longitude")
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallBack, Looper.getMainLooper())

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

    private fun setupUI() {

        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")

        if(!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)

            for(i in weatherList.weather.indices){
                Log.i("Weather Name", weatherList.weather.toString())
                binding.textViewMain.text = weatherList.weather[i].main
                binding.textViewMainDescription.text = weatherList.weather[i].description

                binding.textViewHumidity.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
                binding.textViewHumidityDescription.text = weatherList.main.humidity.toString() + " per cent"


                binding.textViewTempMax.text = weatherList.main.temp_max.toString() + " max"
                binding.textViewTempMin.text = weatherList.main.temp_min.toString() + " min"

                binding.textViewWind.text = weatherList.wind.speed.toString()

                binding.textViewName.text = weatherList.name
                binding.textViewCountry.text = weatherList.sys.country

                binding.textViewSunrise.text = unixTime(weatherList.sys.sunrise)
                binding.textViewSunset.text = unixTime(weatherList.sys.sunset)

                when(weatherList.weather[i].icon){
                    "01d" -> binding.imageViewMain.setImageResource(R.drawable.sunny)
                    "02d" -> binding.imageViewMain.setImageResource(R.drawable.cloud)
                    "03d" -> binding.imageViewMain.setImageResource(R.drawable.cloud)
                    "04d" -> binding.imageViewMain.setImageResource(R.drawable.cloud)
                    "04n" -> binding.imageViewMain.setImageResource(R.drawable.cloud)
                    "10d" -> binding.imageViewMain.setImageResource(R.drawable.rain)
                    "11d" -> binding.imageViewMain.setImageResource(R.drawable.storm)
                    "13d" -> binding.imageViewMain.setImageResource(R.drawable.snowflake)
                    "01n" -> binding.imageViewMain.setImageResource(R.drawable.cloud)
                    "02n" -> binding.imageViewMain.setImageResource(R.drawable.cloud)
                    "03n" -> binding.imageViewMain.setImageResource(R.drawable.cloud)
                    "10n" -> binding.imageViewMain.setImageResource(R.drawable.cloud)
                    "11n" -> binding.imageViewMain.setImageResource(R.drawable.rain)
                    "13N" -> binding.imageViewMain.setImageResource(R.drawable.snowflake)

                }
            }
        }



    }

    private fun getUnit(value: String): String?{
        var value = "°C"
        if("US" == value || "LR" == value || "MM" == value ){
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex: Long): String?{
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)

    }

}