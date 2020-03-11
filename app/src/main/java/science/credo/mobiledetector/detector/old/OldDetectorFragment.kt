package science.credo.mobiledetector.detector.old

import android.annotation.SuppressLint
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.instacart.library.truetime.TrueTimeRx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import science.credo.mobiledetector.R
import science.credo.mobiledetector.utils.ConstantsNamesHelper
import science.credo.mobiledetector.settings.OldCameraSettings
import science.credo.mobiledetector.utils.Prefs
import java.lang.IllegalStateException
import science.credo.mobiledetector.detector.*


class OldDetectorFragment private constructor() : BaseDetectorFragment(),
    CameraInterface.FrameCallback {


    companion object {
        fun instance(): OldDetectorFragment {
            return OldDetectorFragment()
        }


    }


    lateinit var ivProgress: ImageView
    lateinit var tvExposure: TextView
    lateinit var tvFormat: TextView
    lateinit var tvFrameSize: TextView
    lateinit var tvState: TextView
    lateinit var tvDetectionCount: TextView
    lateinit var tvInterface : TextView

    var progressAnimation: AnimationDrawable? = null

    var calibrationResult: OldCalibrationResult? = null
    val calibrationFinder: OldCalibrationFinder = OldCalibrationFinder()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_detector, container, false)

        ivProgress = v.findViewById(R.id.ivProgress)
        tvExposure = v.findViewById(R.id.tvExposure)
        tvFormat = v.findViewById(R.id.tvFormat)
        tvFrameSize = v.findViewById(R.id.tvFrameSize)
        tvInterface = v.findViewById(R.id.tvInterface)
        tvState = v.findViewById(R.id.tvState)
        tvDetectionCount = v.findViewById(R.id.tvDetectionCount)
        ivProgress.setBackgroundResource(R.drawable.anim_progress)
        ivProgress.post {
            progressAnimation = ivProgress.background as AnimationDrawable
        }


        tvInterface.text = "Camera interface: Old"




        cameraInterface = OldCameraInterface(
            this,
            Prefs.get(context!!, OldCameraSettings::class.java)!!
        )
        cameraInterface?.start(context!!)
        return v
    }


    override fun onFrameReceived(frame: Frame) {
        GlobalScope.async {
            val ts = TrueTimeRx.now().time


//            val frameResult = frameAnalyzer.baseCalculation(calibrationResult)
            val stringDataResult = JniWrapper.calculateFrame(
                frame.byteArray,
                frame.width,
                frame.height,
                calibrationResult?.blackThreshold ?: 40
            )
            val frameResult = FrameResult.fromJniStringData(stringDataResult)
            println("===$this====t1 = ${TrueTimeRx.now().time - ts}  ${frameResult.avg}  ${frameResult.blacksPercentage}")

            if (frameResult.avg < calibrationResult?.avg ?: 40
                && frameResult.blacksPercentage >= 99.9
            ) {
                if (calibrationResult == null) {
                    calibrationResult = calibrationFinder.nextFrame(frameResult)
                    println("===$this====t2 = ${TrueTimeRx.now().time - ts}")
                    if (calibrationResult != null) {
                        Prefs.put(context!!, calibrationResult!!)
                    }
                    updateState(State.CALIBRATION, frame)
                } else {

                    val hit = OldFrameAnalyzer.checkHit(
                        frame,
                        frameResult,
                        calibrationResult!!
                    )
                    println("===$this====t3 = ${TrueTimeRx.now().time - ts} $hit")
                    hit?.send(context!!)
                    updateState(State.RUNNING, frame, hit)
                }
            } else {
                updateState(State.NOT_COVERED, frame)
            }
//

        }

    }


    fun updateState(state: State, frame: Frame?) {
        updateState(state, frame, null)
    }

    @SuppressLint("SetTextI18n")
    fun updateState(state: State, frame: Frame?, hit: Hit?) {
        GlobalScope.launch(Dispatchers.Main) {

            try {
                if (frame != null) {
                    tvFormat.text =
                        String.format(
                            "Format: %s",
                            ConstantsNamesHelper.getFormatName(frame.imageFormat)
                        )
                    tvFrameSize.text =
                        String.format("Frame size: %d x %d", frame.width, frame.height)
                    tvExposure.text = String.format("Exposure time %d ms", frame.exposureTime)
                }

                when (state) {
                    State.DISABLED -> {
                        tvState.text = getString(R.string.detector_state_disabled)

                    }
                    State.NOT_COVERED -> {
                        tvState.text = getString(R.string.detector_state_not_covered)
                        progressAnimation?.stop()

                    }
                    State.CALIBRATION -> {
                        tvState.text = getString(R.string.detector_state_calibration)
                        progressAnimation?.start()


                    }
                    State.RUNNING -> {
                        tvState.text = getString(R.string.detector_state_running)
                        tvDetectionCount.visibility = View.VISIBLE
                        if (hit != null) {
                            tvDetectionCount.text =
                                "Detections in this run : ${tvDetectionCount.tag}\nLast detection ${(TrueTimeRx.now().time - hit.timestamp!!) / 1000f / 60f} minutes ago"
                        }
                        progressAnimation?.start()

                    }
                }
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }

        }
    }





}