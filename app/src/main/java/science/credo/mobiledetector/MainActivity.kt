package science.credo.mobiledetector

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.*
import android.preference.PreferenceManager
import android.provider.Settings
import android.support.annotation.RequiresApi
import android.support.design.widget.NavigationView
import android.support.v4.app.Fragment
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import kotlinx.android.synthetic.main.nav_header_status.*
import kotlinx.android.synthetic.main.nav_header_status.view.*
import org.jetbrains.anko.doAsync
import science.credo.mobiledetector.database.ConfigurationWrapper
import science.credo.mobiledetector.database.DataManager
import science.credo.mobiledetector.database.DetectionStateWrapper
import science.credo.mobiledetector.database.UserInfoWrapper
import science.credo.mobiledetector.fragment.*
import science.credo.mobiledetector.fragment.detections.DetectionContent
import science.credo.mobiledetector.info.ConfigurationInfo
import science.credo.mobiledetector.network.ServerInterface
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.util.*


const val REQUEST_SIGNUP = 1

class MainActivity : AppCompatActivity(),
        StatusFragment.OnFragmentInteractionListener,
        CredoFragment.OnFragmentInteractionListener,
        SettingsFragment.OnFragmentInteractionListener,
        DetectionFragment.OnListFragmentInteractionListener {

    override fun onStartDetection() {
        ConfigurationInfo(this).isDetectionOn = true
        credoApplication().turnOnDetection()
        DetectionStateWrapper.getLatestSession(this).clear()
        DetectionStateWrapper.getLatestSession(this).startDetectionTimestamp = System.currentTimeMillis()
    }

    override fun onStopDetection() {
        ConfigurationInfo(this).isDetectionOn = false
        credoApplication().turnOffDetection()
    }

    override fun onListFragmentInteraction(item: DetectionContent.HitItem?) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

//    var mHitDataManager : HitDataManager? = null

    var mSettingsFlag = false;
    //val mReceiver: PowerConnectionReceiver = PowerConnectionReceiver()

    //private var mFusedLocationClient: FusedLocationProviderClient? = null
    //private var mLocationRequest: LocationRequest? = null
    //private var mLocationSettingsRequest: LocationSettingsRequest? = null
    //private var mLocationCallback: LocationCallback? = null
    //private var mCurrentLocation: Location? = null

    companion object {
        val TAG = "MainActivity"
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        if (!ConfigurationWrapper(this).autoRun) {
            onStopDetection()
        }
        super.onDestroy()
    }

    override fun onFragmentInteraction(uri: Uri) {
        Log.d(TAG, "onFragmentInteraction: " + uri.toString())
        when (uri.fragment) {
            "StatusFragment" -> {
                when (uri.path) {
                    "start" -> {
                        onStartDetection()
                    }
                    "stop" -> {
                        onStopDetection()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG,"onCreate")

        if (UserInfoWrapper(this).token.isEmpty()) {
            setResult(Activity.RESULT_OK)
            finish()
            return
        }

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        val toggle = ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer.addDrawerListener(toggle)

        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener {
            Log.d(TAG,"onNavigationItemSelected")
            val id = it.itemId
            switchFragment(id)
            drawer.closeDrawer(GravityCompat.START)
             true
        }

        /*navigationView.view().imageView.onClick {
            val href = "https://api.credo.science/"
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(href))
            startActivity(browserIntent)
        }*/

        if (credoApplication().detectorRunning.get()) {
            switchFragment(R.id.nav_status)
        } else {
            switchFragment(R.id.nav_information)
        }

        toggle.syncState()

        if (credoApplication().cameraSettings == null) {
            credoApplication().turnOnDetection(CredoApplication.DetectorMode.CHECK)
        }

        //mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        //createLocationCallback();
        //createLocationRequest();
        //buildLocationSettingsRequest();
    }

    override fun onBackPressed() {
        Log.d(TAG,"onBackPressed")
        val drawer = findViewById(R.id.drawer_layout) as DrawerLayout
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        Log.d(TAG,"onCreateOptionsMenu")
        menuInflater.inflate(R.menu.main, menu)
        if (!isExternalStorageAvailable() || isExternalStorageReadOnly()) {
            menu.findItem(R.id.action_export).setEnabled(false);
        }
        return true
    }
    //--------------------------------------START---------------------------------------------------
    //Copy to CamerPreviewCallbackNative
    var msg: String? = ""
    var lastMsg = ""

    private fun retreiveInformation(url: String, timeStampString: String) {

        val directory = File(Environment.DIRECTORY_DOWNLOADS)

        if (!directory.exists()) {
            directory.mkdirs()
        }

        val downloadManager = this.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val downloadUri = Uri.parse(url)
        val filename = timeStampString + url.substring(url.lastIndexOf("gov") + 3).replace("/", "_")
        val request = DownloadManager.Request(downloadUri).apply {
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                .setAllowedOverRoaming(false)
                .setTitle(url.substring(url.lastIndexOf("gov") + 3))
                .setDescription("")
                .setDestinationInExternalPublicDir(
                    directory.toString(),
                    "/" + timeStampString+"/"+filename
                )
        }
        val downloadId = downloadManager.enqueue(request)
        val query = DownloadManager.Query().setFilterById(downloadId)
        Thread(Runnable {
            var downloading = true
            while (downloading) {
                val cursor: Cursor = downloadManager.query(query)
                cursor.moveToFirst()
                if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
                    downloading = false
                }
                val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                msg = statusMessage(url, directory, status)
                if (msg != lastMsg) {
                    this.runOnUiThread {
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                    lastMsg = msg ?: ""
                }
                cursor.close()
            }
        }).start()
    }
    private fun statusMessage(url: String, directory: File, status: Int): String? {
        var msg = ""
        msg = when (status) {
            DownloadManager.STATUS_FAILED -> "Download has been failed, please try again"
            DownloadManager.STATUS_PAUSED -> "Paused"
            DownloadManager.STATUS_PENDING -> "Pending"
            DownloadManager.STATUS_RUNNING -> "Downloading..."
            DownloadManager.STATUS_SUCCESSFUL -> "Image downloaded successfully in $directory" + File.separator + url.substring(
                url.lastIndexOf("/") + 1
            )
            else -> "There's nothing to download"
        }
        return msg
    }
    //--------------------------------------END-----------------------------------------------------
    //Copy to CamerPreviewCallbackNative

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG,"onOptionsItemSelected")

        return when (item.itemId) {
            R.id.action_settings -> {
                startSettingsActivity()
                true
            }
            R.id.action_logout -> {
                //startRegisterActivity()
                UserInfoWrapper(this).token = ""
                UserInfoWrapper(this).email = ""
                UserInfoWrapper(this).password = ""
                UserInfoWrapper(this).login = ""
                UserInfoWrapper(this).displayName = ""
                UserInfoWrapper(this).team = ""
                setResult(Activity.RESULT_OK)
                ConfigurationWrapper(this).endpoint = ConfigurationWrapper.defaultEndpoint
                finish()
                true
            }
            R.id.action_export -> {
               /* //--------------------------------------START---------------------------------------------------
                //Copy to CamerPreviewCallbackNative
                val timeStampString = "Stefan0123"
                //Information to store in the folder <timeStampString> on the moment of detection
                retreiveInformation("https://services.swpc.noaa.gov/images/animations/ovation/north/latest.jpg", timeStampString)
                retreiveInformation("https://services.swpc.noaa.gov/images/animations/ovation/north/latest.jpg", timeStampString)
                retreiveInformation("https://services.swpc.noaa.gov/images/geospace/geospace_1_day.png", timeStampString)
                retreiveInformation("https://services.swpc.noaa.gov/images/planetary-k-index.gif", timeStampString)
                retreiveInformation("https://services.swpc.noaa.gov/images/swx-overview-large.gif", timeStampString)
                retreiveInformation("https://services.swpc.noaa.gov/text/aurora-nowcast-hemi-power.txt", timeStampString)
                retreiveInformation("https://services.swpc.noaa.gov/text/solar-geophysical-event-reports.txt", timeStampString)
                retreiveInformation("https://services.swpc.noaa.gov/text/daily-geomagnetic-indices.txt", timeStampString)


                //--------------------------------------END-----------------------------------------------------
                //Copy to CamerPreviewCallbackNative
                */
                val db = DataManager.getDefault(this@MainActivity)
                try {
                    val sd = File(
                        Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS
                        ), "backup_db"
                    )
                    val data = db.mAppPath
                    var bool = false
                    if(!sd.exists()) {
                        sd.mkdir()
                    }
                    if (sd.canWrite()) {
                        val currentDBPath = "cache.db"
                        val backupDBPath = "backup_db.txt"
                        val currentDB = File(data, currentDBPath)
                        val backupDB = File(sd, backupDBPath)
                        if (currentDB.exists()) {
                            val src: FileChannel = FileInputStream(currentDB).getChannel()
                            val dst: FileChannel = FileOutputStream(backupDB).channel
                            dst.transferFrom(src, 0, src.size())
                            src.close()
                            dst.close()
                            bool = true
                        }
                        if (bool === true) {
                            Toast.makeText(this, "Backup Complete", Toast.LENGTH_SHORT)
                                .show()
                            bool = false
                        }
                    }
                } catch (e: java.lang.Exception) {
                    Log.w("Settings Backup", e)
                }
                true
            }
           /* R.id.action_account -> {
                startActivity(Intent(this@MainActivity, UserActivity::class.java))
                true
            }*/
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_SIGNUP) {
            if (UserInfoWrapper(this).token.isEmpty()) {
                this.finish()
            }
        }
    }

    fun switchFragment(id: Int): Boolean {

        var fragment: Fragment? = null
        var title: String? = null;
        var lead = "${getString(R.string.app_name_short)}:"
        when (id) {
            R.id.nav_status -> {
                title = "$lead ${getString(R.string.title_status)}"
                fragment = StatusFragment.newInstance()
            }

           /* R.id.nav_debug -> {
                title = "$lead ${getString(R.string.title_statistics)}"
                fragment = DebugFragment.newInstance(getString(R.string.title_statistics), "")
            }*/
            R.id.nav_settings -> {
                startSettingsActivity()
                mSettingsFlag = true
            }
            R.id.nav_register -> {
                //startRegisterActivity()
                mSettingsFlag = true
           }
            R.id.nav_statistics -> {
                title = getString(R.string.preview_title_activity, DataManager.TRIM_PERIOD_HITS_DAYS)
                fragment = DetectionFragment.newInstance(1)
            }
            R.id.nav_information -> {
                title = "$lead ${getString(R.string.title_information)}"
                fragment = CredoFragment.newInstance()

            }
        }

        if (fragment != null) {
            val ft = supportFragmentManager.beginTransaction()
            ft.replace(R.id.content_frame, fragment)
            ft.commit()
        }

        // set the toolbar title
        if (supportActionBar != null && title != null) supportActionBar!!.title = title

        val drawer = findViewById(R.id.drawer_layout) as DrawerLayout
        drawer.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onResume() {
        Log.d(TAG,"onResume")
        super.onResume()
        mSettingsFlag = false;
        disableDoze()

        LauncherActivity.hasAllPermissionsGranted(this)

        doAsync {
            val db = DataManager.getDefault(this@MainActivity)
            val si = ServerInterface.getDefault(this@MainActivity)

            try {
                // TODO: detect internet connection and try flush when is
//                db.trimHitsDb()
//                db.sendHitsToNetwork(si)
//                db.flushCachedPings(si)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to sent to server", e)
            }
            db.closeDb()
        }

        //startLocationUpdates()
    }

    override fun onPause() {
        Log.d(TAG,"onPause")
//        restartService()
        super.onPause()
    }

    fun disableDoze() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent()
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }

    private fun startSettingsActivity() = startActivity(Intent(this, SettingsActivity::class.java))

    override fun onGoToExperiment() {
        switchFragment(R.id.nav_status)
    }

    private fun credoApplication() : CredoApplication = application as CredoApplication

    /*private fun createLocationRequest() {
        mLocationRequest = LocationRequest()
        mLocationRequest!!.interval = 60000
        mLocationRequest!!.fastestInterval = 10000
        mLocationRequest!!.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }*/

    /*private fun createLocationCallback() {
        val a = this
        val cw = ConfigurationWrapper(this)
        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                mCurrentLocation = locationResult.lastLocation

                if (mCurrentLocation != null) {
                    cw.localizationLatitude = mCurrentLocation!!.latitude
                    cw.localizationLongitude = mCurrentLocation!!.longitude
                    cw.localizationAltitude = mCurrentLocation!!.altitude
                    cw.localizationAccuracy = mCurrentLocation!!.accuracy
                    cw.localizationProvider = mCurrentLocation!!.provider
                    cw.localizationTimestamp = mCurrentLocation!!.time

                    if (mCurrentLocation!!.latitude != 0.0 && mCurrentLocation!!.longitude != 0.0) {
                        cw.localizationNeedUpdate = 0
                    }
                }

                EventBus.getDefault().post(UiUpdateEvent(0))
            }
        }
    }*/

    /*private fun buildLocationSettingsRequest() {
        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(mLocationRequest!!)
        mLocationSettingsRequest = builder.build()
    }*/

    /*private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {} else {
            mFusedLocationClient!!.requestLocationUpdates(mLocationRequest,
                mLocationCallback,
                Looper.getMainLooper())
        }
    }*/

    fun isExternalStorageReadOnly() : Boolean {
        val extStorageState = Environment.getExternalStorageState()
        if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(extStorageState)) {
            return true
        }
        return false
    }

    fun isExternalStorageAvailable() : Boolean {
        val extStorageState = Environment.getExternalStorageState()
        if (Environment.MEDIA_MOUNTED.equals(extStorageState)) {
            return true
        }
        return false
    }
}
