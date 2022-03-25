package science.credo.mobiledetector.detection

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonGetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import ninja.sakib.pultusorm.annotations.AutoIncrement
import ninja.sakib.pultusorm.annotations.PrimaryKey
import science.credo.mobiledetector.CredoApplication

@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE
)
class Hit (frameContent: String,
           timestamp: Long,
           latitude: Double,
           longitude: Double,
           altitude: Double,
           accuracy: Float,
           provider: String,
           width: Int,
           height: Int,
           x: Int,
           y: Int,
           maxValue: Int,
           average: Double,
           blacks: Double,
           blackThreshold: Int,
           ax: Float,
           ay: Float,
           az: Float,
           orientation: Float,
           temperature: Int,
           trueTime: Long,
           mrngRawP1Time: Int,
           mrngRawP2Position: String,
           mrngRawP3Color: String,
           mrngRawP4Outlier: String,
           mrngP1Time: String,
           mrngP2Position: String,
           mrngP3Color: String,
           mrngP4Outlier: String,
           mrngBitString: String,
           potPixelError: Boolean?,
           avgDistRed: Double,
           avgDistGreen: Double,
           avgDistBlue: Double,
           medRed: Int,
           medGreen: Int,
           medBlue: Int
           ){
    @PrimaryKey
    @AutoIncrement
    @JsonProperty("id")
    var id: Int = 0
    @JsonProperty("frame_content")
    val mFrameContent: String = frameContent
    @JsonProperty("timestamp")
    val mTimestamp: Long = timestamp
    @JsonProperty("latitude")
    val mLatitude: Double = latitude
    @JsonProperty("longitude")
    val mLongitude: Double = longitude
    @JsonProperty("altitude")
    val mAltitude: Double = altitude
    @JsonProperty("accuracy")
    val mAccuracy: Float = accuracy
    @JsonProperty("provider")
    val mProvider: String = provider
    @JsonProperty("height")
    val mHeight: Int = height
    @JsonProperty("width")
    val mWidth: Int = width
    @JsonProperty("x")
    val mX: Int = x
    @JsonProperty("y")
    val mY: Int = y

    @JsonIgnore
    val mMaxValue: Int = maxValue

    @JsonIgnore
    val mAverage: Double = average

    @JsonIgnore
    val mBlacks: Double = blacks

    @JsonIgnore
    val mBlackThreshold: Int = blackThreshold

    @JsonIgnore
    val mAx: Float = ax

    @JsonIgnore
    val mAy: Float = ay

    @JsonIgnore
    val mAz: Float = az

    @JsonIgnore
    val mOrientation: Float = orientation

    @JsonIgnore
    val mTemperature: Int = temperature

    @JsonIgnore
    val mTrueTime: Long = trueTime

    @JsonGetter
    @JsonProperty("metadata")
    fun getMetadata(): String {
        return "{\"app\": \"cd_orig\", \"version\": ${CredoApplication.versionCode}, \"max\": $mMaxValue, \"average\": $mAverage, \"blacks\": $mBlacks, \"black_threshold\": $mBlackThreshold, \"ax\": $mAx, \"ay\": $mAy, \"az\": $mAz, \"orientation\": $mOrientation, \"temperature\": $mTemperature, \"true_time\": $mTrueTime}"
    }

    @JsonIgnore
    var toSent = true

    @JsonIgnore
    var serverId = 0L

    @JsonIgnore
    val detectionTimestamp = (System.currentTimeMillis() / 10000L).toInt() // PultusORM less condition walkaround

    @JsonIgnore
    val mMrngRawP1Time: Int = mrngRawP1Time

    @JsonIgnore
    val mMrngRawP2Position: String = mrngRawP2Position

    @JsonIgnore
    val mMrngRawP3Color: String = mrngRawP3Color

    @JsonIgnore
    val mMrngRawP4Outlier: String = mrngRawP4Outlier

    @JsonIgnore
    val mMrngP1Time: String = mrngP1Time

    @JsonIgnore
    val mMrngP2Position: String = mrngP2Position

    @JsonIgnore
    val mMrngP3Color: String = mrngP3Color

    @JsonIgnore
    val mMrngP4Outlier: String = mrngP4Outlier

    @JsonIgnore
    val mMrngBitString: String = mrngBitString

    @JsonIgnore
    val mPotPixelError: Boolean? = potPixelError

    @JsonIgnore
    val mAvgDistRed: Double = avgDistRed

    @JsonIgnore
    val mAvgDistGreen: Double = avgDistGreen

    @JsonIgnore
    val mAvgDistBlue: Double = avgDistBlue

    @JsonIgnore
    val mMedRed: Int = medRed

    @JsonIgnore
    val mMedGreen: Int = medGreen

    @JsonIgnore
    val mMedBlue: Int = medBlue

    constructor() : this("", 0, 0.0, 0.0, 0.0, 0.0f, "", 0, 0, 0, 0, 0, 0.0, 0.0, 0, 0f, 0f, 0f, 0f, 0, 0,0 ,"" ,"" , "", "", "", "", "", "", null, 0.0, 0.0, 0.0,0,0,0) {}
}
