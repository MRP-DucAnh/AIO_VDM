package lib.device

import android.content.*
import android.os.*
import android.telephony.*
import lib.device.AppVersionUtility.getVersionCode
import lib.device.AppVersionUtility.getVersionName
import lib.networks.NetworkUtility.*
import lib.process.*
import java.util.*
import kotlin.math.*

object DeviceInfoUtils {

	@JvmStatic
	suspend fun getDeviceInformation(context: Context): String = withIOContext {
		val appContext = context.applicationContext
		val data = mutableMapOf<String, Any?>()

		try {
			val pm = appContext.packageManager
			val packageInfo = pm.getPackageInfo(appContext.packageName, 0)

			data["Device Id"] = DeviceIdProvider(appContext).generate()
			data["App Version"] = "${getVersionName(appContext)} (${getVersionCode(appContext)})"
			data["App Package Name"] = packageInfo.packageName

			data["Device Model"] = Build.MODEL
			data["Manufacturer"] = Build.MANUFACTURER
			data["Android Version"] = Build.VERSION.RELEASE
			data["API Level"] = Build.VERSION.SDK_INT

			val metrics = appContext.resources.displayMetrics
			data["Resolution"] = "${metrics.widthPixels}x${metrics.heightPixels}"
			data["Density"] = "${metrics.densityDpi} dpi"

			data["Available Storage"] = formatSize(getStorage(appContext, available = true))
			data["Total Storage"] = formatSize(getStorage(appContext, available = false))

			val systemService = appContext.getSystemService(Context.TELEPHONY_SERVICE)
			val telephony = systemService as? TelephonyManager
			data["Network Operator"] = getServiceProvider()
			data["Network Country"] = telephony?.networkCountryIso?.uppercase() ?: "Unknown"
			data["Sim Operator"] = telephony?.simOperatorName ?: "Unknown"

			getDeviceBatteryStatus(appContext)?.let { (status, level) ->
				data["Battery Status"] = status
				data["Battery Level"] = "$level%"
			}

			val locale = Locale.getDefault()
			data["Locale"] = locale.displayName
			data["Language"] = locale.language

		} catch (error: Exception) {
			error.printStackTrace()
			return@withIOContext "Error: ${error.message}"
		}

		data.entries.joinToString("\n") { "${it.key}: ${it.value}" }
	}

	private fun getStorage(context: Context, available: Boolean): Long {
		val path = context.getExternalFilesDir(null) ?: Environment.getDataDirectory()
		val stat = StatFs(path.absolutePath)
		return if (available) stat.availableBlocksLong * stat.blockSizeLong
		else stat.blockCountLong * stat.blockSizeLong
	}

	private fun formatSize(bytes: Long): String {
		if (bytes <= 0) return "0 B"
		val units = arrayOf("B", "KB", "MB", "GB", "TB")
		val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
		return String.format(Locale.getDefault(), "%.2f %s",
		                     bytes / 1024.0.pow(digitGroups.toDouble()),
		                     units[digitGroups])
	}

	@JvmStatic
	fun getDeviceBatteryStatus(context: Context): Pair<String, Int>? {
		val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
		val batteryStatus = context.registerReceiver(null, filter) ?: return null

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

		return Pair(statusString, batteryPct)
	}
}