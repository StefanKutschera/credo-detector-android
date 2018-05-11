package science.credo.credomobiledetektor.detection

import android.content.Context
import android.graphics.Bitmap
import android.hardware.Camera
import android.support.v8.renderscript.RenderScript
import android.util.Base64
import android.util.Log
import io.github.silvaren.easyrs.tools.Nv21Image
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import science.credo.credomobiledetektor.database.DataManager
import science.credo.credomobiledetektor.info.ConfigurationInfo
import science.credo.credomobiledetektor.info.HitInfo
import science.credo.credomobiledetektor.info.LocationInfo
import java.util.*
import science.credo.credomobiledetektor.info.IdentityInfo
import science.credo.credomobiledetektor.network.ServerInterface
import science.credo.credomobiledetektor.network.messages.DetectionRequest
import java.io.ByteArrayOutputStream
import java.util.*

class CameraPreviewCallbackNative(private val mContext: Context) : Camera.PreviewCallback {
    //private var detectionStatsManager = DetectionStatsManager()


    private val mDataManager: DataManager = DataManager.getInstance(mContext)
    private val mLocationInfo: LocationInfo = LocationInfo.getInstance(mContext)

    companion object {
        val TAG = "CameraPreviewClbkNative"
        val aDataSize = 24
        //val cropSize = 20
        var detectionStatsManager: DetectionStatsManager? = null
    }

    val rs = RenderScript.create(mContext)

    override fun onPreviewFrame(data: ByteArray, hCamera: Camera) {

        if (detectionStatsManager == null) {
            detectionStatsManager = DetectionStatsManager()
        }

        val config = ConfigurationInfo(mContext)

        doAsync {

            val parameters = hCamera.parameters
            val width = parameters.previewSize.width
            val height = parameters.previewSize.height
            val analysisData = LongArray(aDataSize)

            var loop = -1
            detectionStatsManager!!.frameAchieved(width, height)
            val hits = LinkedList<Hit>()

            while (true) {
                loop++
                calcHistogram(data, analysisData, width, height, config.blackFactor)

                val max = analysisData[aDataSize - 1]
                val maxIndex = analysisData[aDataSize - 2]
                val sum = analysisData[aDataSize - 3]
                val zeroes = analysisData[aDataSize - 4]
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
                        detectionStatsManager!!.framePerformed()
                    }

                    if (brightestPixelCondition) {
                        val bitmap = yuv2bitmap(data, width, height)
                        val cropBitmap = cropBitmap(bitmap, maxIndex.toInt(), config.cropSize)
                        detectionStatsManager!!.hitRegistered()
                        val cropDataPNG = bitmap2png(cropBitmap)
                        val dataString = Base64.encodeToString(cropDataPNG, Base64.DEFAULT)

                        val hit = Hit(
                            //@TODO add missing data
                            HitInfo.FrameData(dataString, width, height, 0, 0, 0, 0, 0),
                            mLocationInfo.getLocationData(),
                            HitInfo.FactorData(0, 0, 0, 0),
                            false
                        )
                        hits.add(hit)

                        fillHited(data, width, height, maxIndex.toInt(), config.cropSize)
                    } else {
                        break
                    }
                } else {
                    break
                }
            }

            uiThread {
                hCamera.addCallbackBuffer(data)
            }
            detectionStatsManager!!.flush(mContext, false)

            if (hits.size > 0) {
                val deviceInfo = IdentityInfo.getInstance(mContext).getIdentityData()
                for (hit in hits) {
                    mDataManager.storeHit(hit)
                }
                ServerInterface.getDefault(mContext)
                    .sendDetections(DetectionRequest(hits, deviceInfo))
            }
        }
    }

    external fun calcHistogram(
        data: ByteArray,
        analysisData: LongArray,
        width: Int,
        height: Int,
        black: Int
    )

    fun yuv2bitmap(data: ByteArray, width: Int, height: Int): Bitmap {
        return Nv21Image.nv21ToBitmap(rs, data, width, height)
    }

    fun bitmap2png(bitmap: Bitmap): ByteArray {
        val pngData = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, pngData)
        return pngData.toByteArray()
    }

    fun yuv2png(data: ByteArray, width: Int, height: Int): ByteArray {
        val bitmap = Nv21Image.nv21ToBitmap(rs, data, width, height)
        val pngData = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, pngData)
        return pngData.toByteArray()
    }

    fun cropBitmap(bitmap: Bitmap, maxPosition: Int, sideLength: Int): Bitmap {
        // position of max bright pixel
        val maxX = maxPosition.rem(bitmap.width)
        val maxY = maxPosition / bitmap.width

        var x = maxX - sideLength / 2
        var y = maxY - sideLength / 2

        when {
            x < 0 -> x = 0
            x >= (bitmap.width - sideLength) -> x = bitmap.width - sideLength
        }

        when {
            y < 0 -> y = 0
            y >= (bitmap.height - sideLength) -> y = bitmap.height - sideLength
        }

        return Bitmap.createBitmap(bitmap, x, y, sideLength, sideLength)
    }

    fun fillHited(data: ByteArray, width: Int, height: Int, maxPosition: Int, sideLength: Int) {

        //Point (maxX,maxY) is center(brightest pixel) of hit
        val maxX = maxPosition.rem(width)
        val maxY = maxPosition / width

        //Point (x,y) is upper-left corner of square with we want to fill
        var x = maxX - sideLength / 2
        var y = maxY - sideLength / 2


        when {
            x < 0 -> x = 0
            x >= width - sideLength -> x = width - sideLength
        }

        when {
        //We want to make sure that upper-left point of square is at least sideLength from bottom and right side of image
            y < 0 -> y = 0
            y >= height - sideLength -> y = height - sideLength
        }

        //Loops iterates from upper-left point sideLength times
        for (i in y..y + sideLength) {
            for (j in x..x + sideLength) {
                data[i * width + j] = 0
            }
        }

    }

}
