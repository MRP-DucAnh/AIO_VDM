package lib.device

import android.content.*
import android.os.*
import android.telephony.*
import lib.device.AppVersionUtility.getVersionCode
import lib.device.AppVersionUtility.getVersionName
import lib.networks.NetworkUtility.*
import lib.process.*
import java.util.*

object DeviceInfoUtils {

	@JvmStatic
	suspend fun getDeviceInformation(context: Context): String = withIOContext {
		val appContext = context.applicationContext
		val sb = StringBuilder()
		val data = mutableMapOf<String, Any?>()

		try {
			val pm = appContext.packageManager
			val packageInfo = pm.getPackageInfo(appContext.packageName, 0)

			data["Device Id"] = DeviceIdProvider(appContext).generate()
			data["App Version"] = "${getVersionName(appContext)} (${getVersionCode(appContext)})"
			data["App Package Name"] = packageInfo.packageName
			data["Device Model"] = Build.MODEL
			data["Device Manufacturer"] = Build.MANUFACTURER
			data["Android Version"] = Build.VERSION.RELEASE
			data["API Level"] = Build.VERSION.SDK_INT

			val metrics = appContext.resources.displayMetrics
			data["Screen Resolution"] = "${metrics.widthPixels}x${metrics.heightPixels}"
			data["Screen Density"] = "${metrics.densityDpi} dpi"

			data["Available Storage"] = getDeviceAvailableStorage(appContext)
			data["Total Storage"] = getDeviceTotalStorage(appContext)

			val systemService = appContext.getSystemService(Context.TELEPHONY_SERVICE)
			val telephony = systemService as? TelephonyManager
			data["Network Operator"] = getServiceProvider()
			data["Network Country"] = telephony?.networkCountryIso ?: "Unknown"
			data["Sim Operator"] = telephony?.simOperatorName ?: "Unknown"

			val locale = Locale.getDefault()
			data["Locale"] = locale.displayName
			data["Language"] = locale.language

			getDeviceBatteryStatus(appContext)?.let { (status, level) ->
				data["Battery Status"] = status
				data["Battery Level"] = "$level%"
			}

		} catch (error: Exception) {
			error.printStackTrace()
			return@withIOContext "Error gathering device info: ${error.message}"
		}
		data.forEach { (key, value) ->
			sb.append("$key: $value\n")
		}

		sb.toString()
	}

	@JvmStatic
	private fun getDeviceTotalStorage(context: Context): Long {
		val path = context.getExternalFilesDir(null) ?: Environment.getDataDirectory()
		val stat = StatFs(path.absolutePath)
		return stat.blockCountLong * stat.blockSizeLong
	}

	@JvmStatic
	private fun getDeviceAvailableStorage(context: Context): Long {
		val path = context.getExternalFilesDir(null) ?: Environment.getDataDirectory()
		val stat = StatFs(path.absolutePath)
		return stat.availableBlocksLong * stat.blockSizeLong
	}

	@JvmStatic
	suspend fun getDeviceBatteryStatus(context: Context): Pair<String, Int>? {
		return withIOContext {
			val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
			val registerReceiver = context.registerReceiver(null, filter)
			val batteryStatus = registerReceiver ?: return@withIOContext null

			val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
			val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
			val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

			val batteryPct = if (scale > 0) (level * 100 / scale) else -1

			val statusString = when (status) {
				BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
				BatteryManager.BATTERY_STATUS_FULL -> "Full"
				BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
				BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
				else -> "Unknown"
			}

			Pair(statusString, batteryPct)
		}
	}
}