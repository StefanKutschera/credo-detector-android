package science.credo.mobiledetector.detection

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.Camera
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.widget.Toast
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.instacart.library.truetime.TrueTime
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.runOnUiThread
import org.jetbrains.anko.uiThread
import science.credo.mobiledetector.CredoApplication
import science.credo.mobiledetector.database.ConfigurationWrapper
import science.credo.mobiledetector.database.DataManager
import science.credo.mobiledetector.database.DetectionStateWrapper
import science.credo.mobiledetector.info.ConfigurationInfo
import science.credo.mobiledetector.info.LocationInfo
import science.credo.mobiledetector.network.ServerInterface
import java.io.*
import java.nio.channels.FileChannel
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

const val MAX_HITS_ONE_FRAME = 5


class CameraPreviewCallbackNative(private val mContext: Context) : Camera.PreviewCallback {
    private val mServerInterface = ServerInterface.getDefault(mContext)
    private val mLocationInfo: LocationInfo = LocationInfo.getInstance(mContext)
    private var wasErrourous: Boolean = false


    companion object {
        val TAG = "CameraPreviewClbkNative"
        val aDataSize = 24
        var detectionStatsManager: DetectionStatsManager? = null

        private var benchmarkSumMs: AtomicInteger = AtomicInteger(0)
        private var benchmarkCount: AtomicInteger = AtomicInteger(0)
        var benchmark: Int = 0

        private var lastFpsSecond: Long = 0
        private var lastFps: Int = 0
        var fps: Int = 0
    }

    override fun onPreviewFrame(data: ByteArray, hCamera: Camera) {

        val timestamp = System.currentTimeMillis()
        var trueTime = 0L
        if (!wasErrourous){
            try {
                trueTime = TrueTime.now().time
            } catch (e: IllegalStateException){
                wasErrourous=true
                e.printStackTrace()
            }
        }
        if (timestamp / 1000 != lastFpsSecond) {
            lastFpsSecond = timestamp / 1000
            fps = lastFps
            lastFps = 0
        } else {
            lastFps++
        }

        if (detectionStatsManager == null) {
            detectionStatsManager = DetectionStatsManager()
        }

        val config = ConfigurationInfo(mContext)
        val sensorsState = (mContext.applicationContext as CredoApplication).detectorState
        val width: Int
        val height: Int

        try {
            val parameters = hCamera.parameters
            width = parameters.previewSize.width
            height = parameters.previewSize.height
        } catch (e: Exception) {
            Log.w(TAG, e)
            return
        }

        val analysisData = LongArray(aDataSize)

        doAsync {
            val benchmarkStart = System.currentTimeMillis()

            var loop = -1
            detectionStatsManager!!.frameAchieved(width, height)
            val hits = LinkedList<Hit>()
            var original: ByteArray? = null

            while (loop < MAX_HITS_ONE_FRAME) {
                loop++
                val (max, maxIndex, sum, zeroes) = calcHistogram(data, width, height, config.blackFactor)

                val average: Double = sum.toDouble() / (width * height).toDouble()
                val blacks: Double = zeroes * 1000 / (width * height).toDouble()

                if (loop == 0) {
                    detectionStatsManager!!.updateStats(max, average, blacks)
                }

                // frames not rejected conditions
                val averageBrightCondition = average < config.averageFactor
                val blackPixelsCondition = blacks >= config.blackCount

                // found Hit condition
                val brightestPixelCondition = max > config.maxFactor

                if (averageBrightCondition && blackPixelsCondition) {
                    if (loop == 0) {
                        detectionStatsManager!!.framePerformed(max, average, blacks)
                    }

                    if (brightestPixelCondition) {

                        val centerX = maxIndex.rem(width)
                        val centerY = (maxIndex / width)

                        val margin = config.cropSize / 2
                        var offsetX = max(0, centerX - margin)
                        var offsetY = max(0, centerY - margin)
                        var endX = min(width, centerX + margin)
                        var endY = min(height, centerY + margin)

                        // form hits on edges move the crop window from over edge to edge
                        if (offsetX == 0) {
                            endX = config.cropSize
                        }

                        if (endX == width) {
                            offsetX = width - config.cropSize
                        }

                        if (offsetY == 0) {
                            endY = config.cropSize
                        }

                        if (endY == height) {
                            offsetY = height - config.cropSize
                        }

                        // lazy copy original for mark extracted hits
                        if (original == null) {
                            original = data.clone()
                        }

                        val cropBitmap = ImageConversion.yuv2rgb(original, width, height, offsetX, offsetY, endX, endY)
                        detectionStatsManager!!.hitRegistered()
                        val cropDataPNG = bitmap2png(cropBitmap)
                        val dataString = Base64.encodeToString(cropDataPNG, Base64.DEFAULT)
                        val location = mLocationInfo.getLocationData()



                        fillHited(data, width, offsetX, offsetY, endX, endY)
                        val timeStampString = timestamp.toString()
                        Log.i("Hit-Detected", timeStampString)
                        try {
                            //Copy to CamerPreviewCallbackNative
                            //Information to store in the folder <timeStampString> on the moment of detection
                            retreiveInformation("https://services.swpc.noaa.gov/images/animations/ovation/north/latest.jpg", timeStampString)
                            retreiveInformation("https://services.swpc.noaa.gov/images/geospace/geospace_1_day.png", timeStampString)
                            retreiveInformation("https://services.swpc.noaa.gov/images/planetary-k-index.gif", timeStampString)
                            retreiveInformation("https://services.swpc.noaa.gov/images/swx-overview-large.gif", timeStampString)
                            retreiveInformation("https://services.swpc.noaa.gov/text/aurora-nowcast-hemi-power.txt", timeStampString)
                            retreiveInformation("https://services.swpc.noaa.gov/text/solar-geophysical-event-reports.txt", timeStampString)
                            retreiveInformation("https://services.swpc.noaa.gov/text/daily-geomagnetic-indices.txt", timeStampString)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        try {
                            // Store Hit-Bitmap onto device
                            val directory = File(
                                Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_DOWNLOADS
                                ), timeStampString
                            )
                            if (!directory.exists()) {
                                directory.mkdirs()
                            }

                            //save bitmap as PNG
                            val filepath = "/" + timeStampString + "_hit.png"
                            val file = File(directory, filepath)
                            val dst: FileOutputStream = FileOutputStream(file)
                            cropBitmap.compress(Bitmap.CompressFormat.PNG, 100, dst)
                            dst.flush();
                            dst.close();

                            // save bitmap as JSON
                            val bmpjsonFilepath = "/" + timeStampString + "_hit_bmp.json"
                            val mapper = jacksonObjectMapper()
                            val filebmp = File(directory,bmpjsonFilepath)
                            mapper.writeValue(filebmp, cropBitmap)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }



                        val epsilon = 21;
                        var rgbPx = ""
                        var potPixelError = false

                        val mrngRawP1Time = timeStampString.subSequence(timeStampString.length-5,timeStampString.length).toString().toInt()
                        val mrngP1Time = Integer.toBinaryString(mrngRawP1Time).toString()

                        val mrngRawP2Position = centerX.toString() + "," + centerY.toString()
                        val mrngP2Position = (centerX%2).toString() + (centerY%2).toString()

                        var mrngP3Color = ""
                        var mrngRawP3Color = ""

                        var mrngP4Outlier = ""
                        var mrngRawP4Outlier = ""


                        var prevRed = -1
                        var prevGreen = -1
                        var prevBlue = -1

                        var absDistRed = 0
                        var absDistGreen = 0
                        var absDistBlue = 0

                        var redArr : MutableList<Int> = ArrayList()
                        var greenArr : MutableList<Int> = ArrayList()
                        var blueArr : MutableList<Int> = ArrayList()

                        // Extract Randomness out of bit flipped pixels
                        for (x in 0 until cropBitmap.getWidth()) {
                            for (y in 0 until cropBitmap.getHeight()) {
                                var px = cropBitmap.getPixel(x, y)
                                var red = Color.red(px)
                                var green = Color.green(px)
                                var blue = Color.blue(px)
                                var alpha = Color.alpha(px)
                                if (red > epsilon || green > epsilon || blue > epsilon) {
                                    rgbPx = rgbPx + red + ";" + green + ";" + blue + ";" + alpha + ";" + x +";" + y + "\n"
                                    mrngRawP3Color = mrngRawP3Color + red + ";" + green + ";" + blue + ";" + alpha + ";" + x +";" + y +";\n"
                                    mrngP3Color = mrngP3Color + ((red%2) + (green%2) + (blue%2))%2
                                }
                                if (prevRed > 0 || prevGreen > 0 || prevBlue > 0) {
                                    absDistRed = absDistRed + abs(prevRed - red)
                                    absDistGreen = absDistGreen + abs(prevGreen - green)
                                    absDistBlue = absDistBlue + abs(prevBlue - blue)
                                }
                                redArr.add(red)
                                greenArr.add(green)
                                blueArr.add(blue)

                                prevRed = red
                                prevGreen = green
                                prevBlue = blue
                            }
                        }

                        redArr.sort()
                        greenArr.sort()
                        blueArr.sort()
                        val medRed = redArr[round(redArr.size/2.toDouble()).toInt()]
                        val medGreen = greenArr[round(redArr.size/2.toDouble()).toInt()]
                        val medBlue = blueArr[round(redArr.size/2.toDouble()).toInt()]

                        val pixelCount = cropBitmap.getWidth() * cropBitmap.getHeight()
                        val avgDistRed = absDistRed / pixelCount.toDouble()
                        val avgDistGreen = absDistGreen / pixelCount.toDouble()
                        val avgDistBlue = absDistBlue / pixelCount.toDouble()
                        val outlierMultiplier = 6

                        var locDistRed = -1
                        var locDistGreen = -1
                        var locDistBlue = -1

                        // 2nd Round Extract Outliers for Randomness out of bit flipped pixels
                        for (x in 0 until cropBitmap.getWidth()) {
                            for (y in 0 until cropBitmap.getHeight()) {
                                var px = cropBitmap.getPixel(x, y)
                                var red = Color.red(px)
                                var green = Color.green(px)
                                var blue = Color.blue(px)
                                var alpha = Color.alpha(px)

                                if (prevRed > 0 || prevGreen > 0 || prevBlue > 0) {
                                    locDistRed = abs(prevRed - red)
                                    locDistGreen = abs(prevGreen - green)
                                    locDistBlue = abs(prevBlue - blue)
                                }

                                if (red > epsilon || green > epsilon || blue > epsilon) {
                                    if (locDistRed > avgDistRed * outlierMultiplier || locDistGreen > avgDistGreen * outlierMultiplier || locDistBlue > avgDistBlue * outlierMultiplier) {
                                        mrngRawP4Outlier =
                                            mrngRawP4Outlier + red + ";" + green + ";" + blue + ";" + alpha + ";" + x + ";" + y + ";\n"
                                        mrngP4Outlier =
                                            mrngP4Outlier + ((red % 2) + (green % 2) + (blue % 2)) % 2
                                    }
                                }

                                prevRed = red
                                prevGreen = green
                                prevBlue = blue
                            }
                        }

                        val mrngBitString = mrngP1Time + mrngP2Position + mrngP3Color
                        if (mrngP3Color.length<=2)
                            potPixelError = true

                        val hit = Hit(
                            dataString,
                            timestamp,
                            location.latitude,
                            location.longitude,
                            location.altitude,
                            location.accuracy,
                            location.provider,
                            width,
                            height,
                            centerX,
                            centerY,
                            max,
                            average,
                            blacks,
                            config.blackFactor,
                            sensorsState.accX,
                            sensorsState.accY,
                            sensorsState.accZ,
                            sensorsState.orientation,
                            sensorsState.temperature,
                            trueTime,
                            mrngRawP1Time,
                            mrngRawP2Position,
                            mrngRawP3Color,
                            mrngRawP4Outlier,
                            mrngP1Time,
                            mrngP2Position,
                            mrngP3Color,
                            mrngP4Outlier,
                            mrngBitString,
                            potPixelError,
                            avgDistRed,
                            avgDistGreen,
                            avgDistBlue,
                            medRed,
                            medGreen,
                            medBlue
                        )
                        hits.add(hit)


                        // Write MRNG data to file
                        try {
                            val directory = File(
                                Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_DOWNLOADS
                                ), timeStampString
                            )
                            if (!directory.exists()) {
                                directory.mkdirs()
                            }
                            val mrngFile = "/" + timeStampString + "_mrng.txt"
                            val myfile = File(directory,mrngFile)

                            myfile.printWriter().use { out ->
                                out.println("MRNG Sequence:")
                                out.println(mrngBitString)
                                out.println("Timestamp:")
                                out.println(timeStampString)
                                out.println("Abs. Position:")
                                out.println("" + centerX + ";" + centerY)
                                out.println(rgbPx)
                                out.println("Outlier Stats:")
                                out.println("avgDistRed:" +avgDistRed)
                                out.println("avgDistGreen:" +avgDistGreen)
                                out.println("avgDistBlue:" +avgDistBlue)
                                out.println("medRed:" +medRed )
                                out.println("medGreen:" +medGreen )
                                out.println("medBlue:" +medBlue)
                                out.println("Outlier:")
                                out.println(mrngRawP4Outlier)
                                out.println(mrngP4Outlier)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }




                    } else {
                        break
                    }
                } else {
                    if (loop == 0) {
                        detectionStatsManager!!.activeDetect(false)
                    }
                    break
                }

            }

            uiThread {
                hCamera.addCallbackBuffer(data)
            }
            detectionStatsManager!!.flush(mContext, false)

            if (hits.size > 0) {
                val mDataManager: DataManager = DataManager.getDefault(mContext)

                for (hit in hits) {
                    try {
                        mDataManager.storeHit(hit)
                    } catch (e: Exception) {
                        Log.e(TAG, "Can't store hit", e)
                    }
                }
                mDataManager.closeDb()
                backupDb()
            }


            var bc = benchmarkCount.addAndGet(1)
            var bs = benchmarkSumMs.addAndGet((System.currentTimeMillis() - benchmarkStart).toInt())

            if (bc == 100) {
                benchmark = bs / 100;
                benchmarkCount = AtomicInteger(0)
                benchmarkSumMs = AtomicInteger(0)
            }
        }
        doAutoCallibrationIfNeed()
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

        val downloadManager = mContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

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
                    mContext.runOnUiThread {
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

    private fun doAutoCallibrationIfNeed() {
        val cw = ConfigurationWrapper(mContext)
        val config = ConfigurationInfo(mContext)
        if (!cw.autoCalibrationPerformed) {
            return
        }

        val ds = DetectionStateWrapper.getTotal(mContext)
        if (ds.performedFrames > 500) {
            Log.i("AutoCalibration", "average: ${ds.averageStats.average}")
            Log.i("AutoCalibration", "max: ${ds.maxStats.average}")
            config.averageFactor = minmax((ds.averageStats.average + 20).toInt(), 10, 60)
            config.blackFactor = minmax((ds.averageStats.average + 20).toInt(), 10, 60)
            config.maxFactor = minmax(max((ds.maxStats.average * 3).toInt(), 80), config.averageFactor, 160)
            cw.autoCalibrationPerformed = false
        }
    }

    fun minmax(v: Int, m: Int, a: Int): Int {
        return max(min(v, a), m)
    }

    fun flush() {
        detectionStatsManager?.flush(mContext, true)
    }

    fun Byte.toPositiveInt() = toInt() and 0xFF

    data class CalcHistogramResult(val max: Int, val maxIndex: Int, val sum: Long, val zeroes: Int)
    fun calcHistogram(
        data: ByteArray,
        width: Int,
        height: Int,
        black: Int
    ) : CalcHistogramResult {
        var sum: Long = 0
        var max: Int = 0
        var maxIndex: Int = 0
        var zeros: Int = 0
        //var histogram = ByteArray(256)

        for (i in 0 until width*height) {
            val byte = data[i].toPositiveInt()
            //histogram[byte]++
            if (byte > 0) {
                sum += byte
                if (byte > max) {
                    max = byte
                    maxIndex = i
                }
            }
            if (byte <= black) {
                zeros++
            }
        }

        return CalcHistogramResult(max, maxIndex, sum, zeros)
    }

    fun bitmap2png (bitmap: Bitmap) : ByteArray {
        val pngData = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, pngData)
        return pngData.toByteArray()
    }

    fun fillHited(data: ByteArray, width: Int, offsetX: Int, offsetY: Int, endX: Int, endY: Int){
        //Loops iterates from upper-left point sideLength times
        for (i in offsetY until endY) {
            for (j in offsetX until endX) {
                data[i * width + j] = 0
            }
        }
    }

    fun backupDb() {
        val db = DataManager.getDefault(mContext)
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
                    mContext.runOnUiThread {
                        Toast.makeText(this, "Backup Complete", Toast.LENGTH_SHORT).show()
                    }
                    bool = false
                }
            }
        } catch (e: java.lang.Exception) {
            Log.w("Automated Backup", e)
        }
    }
}
