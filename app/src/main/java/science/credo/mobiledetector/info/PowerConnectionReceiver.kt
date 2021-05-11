package science.credo.mobiledetector.info

import android.os.BatteryManager
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import org.greenrobot.eventbus.EventBus
import science.credo.mobiledetector.events.BatteryEvent


class PowerConnectionReceiver : BroadcastReceiver() {

    companion object {
        val TAG = "PowerConnectionReceiver"

        fun parseIntent(intent: Intent) : BatteryEvent {
            val battery = BatteryEvent()
            battery.status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

            battery.chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            battery.usbCharge = battery.chargePlug == BatteryManager.BATTERY_PLUGGED_USB
            battery.acCharge = battery.chargePlug == BatteryManager.BATTERY_PLUGGED_AC

            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> battery.plugged = true;
                Intent.ACTION_POWER_DISCONNECTED -> battery.plugged = false
            }

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            battery.temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10
            Log.d(TAG, "temperature: ${battery.temperature}")

            battery.batteryPct = level * 100 / scale

            battery.isCharging = battery.status == BatteryManager.BATTERY_STATUS_CHARGING || battery.status == BatteryManager.BATTERY_STATUS_FULL || battery.usbCharge || battery.acCharge || battery.batteryPct > 90

            return battery
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive")
        EventBus.getDefault().post(parseIntent(intent))
    }
}
