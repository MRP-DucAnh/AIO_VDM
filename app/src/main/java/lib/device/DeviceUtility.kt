package lib.device

import android.content.*
import android.content.Context.*
import android.net.*
import android.net.NetworkCapabilities.*
import android.os.*
import android.telephony.*
import app.core.AIOApp.Companion.INSTANCE
import lib.texts.CommonTextUtils.capitalizeFirstLetter
import java.lang.ref.*
import java.util.*
import java.util.Locale.*

object DeviceUtility {

	@JvmStatic
	suspend fun getDeviceScreenDensity(context: Context?): Float {
		WeakReference(context).get()?.let { safeRef ->
			val displayMetrics = safeRef.resources.displayMetrics
			val density = displayMetrics.density
			return density
		}; return 0.0f
	}

	@JvmStatic
	suspend fun getDeviceScreenDensityInFormat(context: Context?): String {
		WeakReference(context).get()?.let { safeRef ->
			val displayMetrics = safeRef.resources.displayMetrics
			val density = displayMetrics.density
			val formattedDensity = when {
				density >= 4.0 -> "xxxhdpi"
				density >= 3.0 -> "xxhdpi"
				density >= 2.0 -> "xhdpi"
				density >= 1.5 -> "hdpi"
				density >= 1.0 -> "mdpi"
				else -> "ldpi"
			}
			return formattedDensity
		}; return ""
	}

	@JvmStatic
	suspend fun getDeviceManufactureModelName(): String? {
		val manufacturer = Build.MANUFACTURER
		val model: String = Build.MODEL
		val deviceName = if (model.startsWith(manufacturer)) {
			capitalizeFirstLetter(model)
		} else capitalizeFirstLetter("$manufacturer $model")
		return deviceName
	}

	@JvmStatic
	suspend fun isDeviceConnectedToInternet(): Boolean {
		val appContext = INSTANCE
		val connectivityService = appContext.getSystemService(CONNECTIVITY_SERVICE)
		val connectivityManager = connectivityService as ConnectivityManager
		val network = connectivityManager.activeNetwork
		val nc = connectivityManager.getNetworkCapabilities(network) ?: run {
			return false
		}
		val wifiChecked = nc.hasTransport(TRANSPORT_WIFI)
		val cellularChecked = nc.hasTransport(TRANSPORT_CELLULAR)
		val ethernetChecked = nc.hasTransport(TRANSPORT_ETHERNET)
		val bluetoothChecked = nc.hasTransport(TRANSPORT_BLUETOOTH)

		val isOnline = wifiChecked || cellularChecked || ethernetChecked || bluetoothChecked
		return isOnline
	}

	@JvmStatic
	suspend fun getDeviceUserCountry(): String {
		val country = getDefault().country
		if (country.isNotEmpty()) return country
		val telephoneService = INSTANCE.getSystemService(TELEPHONY_SERVICE)
		val telephonyManager = telephoneService as TelephonyManager
		val simCountryIso = telephonyManager.simCountryIso
		if (simCountryIso.isNotEmpty()) return simCountryIso.uppercase(getDefault())
		return "Unknown"
	}

	@JvmStatic
	suspend fun isUserFromIndia(context: Context?): Boolean {
		if (context == null) return false

		val indiaCountryCode = "in"
		val indianTimeZoneId = "Asia/Kolkata"
		val tm = context.getSystemService(TELEPHONY_SERVICE) as? TelephonyManager

		return when {
			tm?.networkCountryIso?.equals(indiaCountryCode, ignoreCase = true) == true -> true
			tm?.simCountryIso?.equals(indiaCountryCode, ignoreCase = true) == true -> true
			getDefault().country.equals(indiaCountryCode, ignoreCase = true) -> true
			TimeZone.getDefault().id.equals(indianTimeZoneId, ignoreCase = true) -> true

			else -> false
		}
	}

	@JvmStatic
	suspend fun normalizeIndianNumber(raw: String): String {
		val digits = raw.filter { it.isDigit() }
		return when (
			digits.length) {
			12 if digits.startsWith("91") -> digits.substring(2)
			11 if digits.startsWith("0") -> digits.substring(1)
			10 -> digits
			else -> digits
		}
	}

}