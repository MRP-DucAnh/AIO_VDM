package lib.device

import android.content.*
import android.content.Context.*
import android.content.Intent.*
import android.content.pm.*
import android.os.*
import android.os.BatteryManager.*
import android.os.Environment.*
import android.telephony.*
import app.core.*
import lib.networks.NetworkUtility.*
import lib.process.*
import java.lang.ref.*
import java.util.*

object DeviceInfoUtils {

	@JvmStatic
	suspend fun getDeviceInformation(context: Context?): String {
		return withIOContext {
			WeakReference(context).get()?.let { contextRef ->
				val sb = StringBuilder()
				val pm = contextRef.packageManager
				val packageInfo: PackageInfo = pm.getPackageInfo(contextRef.packageName, 0)
				sb.append("Device Id: ${DeviceIdProvider(AIOApp.INSTANCE).generate()}")
				sb.append("App Version: ${getApplicationVersionName()} (${getApplicationVersionCode()})\n")
				sb.append("App Package Name: ${packageInfo.packageName}\n")
				sb.append("Device Model: ${Build.MODEL}\n")
				sb.append("Device Manufacturer: ${Build.MANUFACTURER}\n")
				sb.append("Android Version: ${Build.VERSION.RELEASE}\n")
				sb.append("API Level: ${Build.VERSION.SDK_INT}\n")
				sb.append("Device Hardware: ${Build.HARDWARE}\n")
				sb.append("Device Brand: ${Build.BRAND}\n")
				sb.append("Device Board: ${Build.BOARD}\n")
				sb.append("Device Bootloader: ${Build.BOOTLOADER}\n")
				sb.append("Device Host: ${Build.HOST}\n")
				sb.append("Device Tags: ${Build.TAGS}\n")
				sb.append("Device Type: ${Build.TYPE}\n")
				sb.append("Device User: ${Build.USER}\n")
				val metrics = contextRef.resources.displayMetrics
				sb.append("Screen Resolution: ${metrics.widthPixels}x${metrics.heightPixels}\n")
				sb.append("Screen Density: ${metrics.densityDpi} dpi\n")
				sb.append("Available Storage: ${getDeviceAvailableStorage()} bytes\n")
				sb.append("Total Storage: ${getDeviceTotalStorage()} bytes\n")
				sb.append("Network Operator: ${getServiceProvider()}\n")
				sb.append("Network Country: ${getDeviceNetworkCountry(contextRef)}\n")
				sb.append("Sim Country: ${getDeviceSimCountry(contextRef)}\n")
				sb.append("Sim Operator: ${getDeviceSimOperator(contextRef)}\n")
				sb.append("Locale: ${Locale.getDefault().displayName}\n")
				sb.append("Language: ${Locale.getDefault().language}\n")
				sb.append("Country: ${Locale.getDefault().country}\n")
				getDeviceBatteryStatus(contextRef)?.let {
					sb.append("Battery Status: ${it.first}\n")
					sb.append("Battery Level: ${it.second}%\n")
				}

				return@withIOContext sb.toString()
			}
			return@withIOContext ""
		}
	}

	@JvmStatic
	private fun getApplicationVersionName(): String? {
		return AppVersionUtility.versionName
	}

	@JvmStatic
	private fun getApplicationVersionCode(): String {
		return AppVersionUtility.versionCode.toString()
	}

	@JvmStatic
	private fun getDeviceTotalStorage(): Long {
		val stat = StatFs(getExternalStorageDirectory().absolutePath)
		return stat.blockCountLong * stat.blockSizeLong
	}

	@JvmStatic
	private fun getDeviceAvailableStorage(): Long {
		val stat = StatFs(getExternalStorageDirectory().absolutePath)
		return stat.availableBlocksLong * stat.blockSizeLong
	}

	@JvmStatic
	private fun getDeviceNetworkCountry(context: Context?): String {
		WeakReference(context).get()?.let { safeRes ->
			val telephonyService = safeRes.getSystemService(TELEPHONY_SERVICE)
			val telephonyManager = telephonyService as TelephonyManager
			return telephonyManager.networkCountryIso
		}; return ""
	}

	@JvmStatic
	private fun getDeviceSimCountry(context: Context?): String {
		WeakReference(context).get()?.let { safeRes ->
			val telephonyService = safeRes.getSystemService(TELEPHONY_SERVICE)
			val telephonyManager = telephonyService as TelephonyManager
			return telephonyManager.simCountryIso
		}; return ""
	}

	@JvmStatic
	private fun getDeviceSimOperator(context: Context?): String {
		WeakReference(context).get()?.let { safeRes ->
			val telephonyService = safeRes.getSystemService(TELEPHONY_SERVICE)
			val telephonyManager = telephonyService as TelephonyManager
			return telephonyManager.simOperatorName
		}; return ""
	}

	@JvmStatic
	fun getDeviceBatteryStatus(context: Context?): Pair<String, Int>? {
		WeakReference(context).get()?.let { safeRes ->
			val batteryStatus: Intent? = IntentFilter(ACTION_BATTERY_CHANGED).let { filter ->
				safeRes.registerReceiver(null, filter)
			}
			val status = batteryStatus?.getIntExtra(EXTRA_STATUS, -1) ?: -1
			val batteryPct = batteryStatus?.let {
				it.getIntExtra(EXTRA_LEVEL, -1) * 100 / it.getIntExtra(EXTRA_SCALE, -1)
			} ?: -1
			val statusString = when (status) {
				BATTERY_STATUS_CHARGING -> "Charging"
				BATTERY_STATUS_FULL -> "Full"
				BATTERY_STATUS_DISCHARGING -> "Discharging"
				BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
				else -> "Unknown"
			}
			return Pair(statusString, batteryPct)
		}; return null
	}
}
