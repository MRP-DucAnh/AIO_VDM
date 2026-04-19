package lib.device

import android.content.*
import android.content.Context.*
import android.net.*
import android.os.*
import android.telephony.*
import lib.process.*
import lib.texts.CommonTextUtils.capitalizeFirstLetter
import java.util.*

object DeviceUtility {

	@JvmStatic
	fun getDeviceScreenDensity(context: Context): Float {
		return context.resources.displayMetrics.density
	}

	@JvmStatic
	fun getDeviceScreenDensityInFormat(context: Context): String {
		val density = context.resources.displayMetrics.density
		return when {
			density >= 4.0 -> "xxxhdpi"
			density >= 3.0 -> "xxhdpi"
			density >= 2.0 -> "xhdpi"
			density >= 1.5 -> "hdpi"
			density >= 1.0 -> "mdpi"
			else -> "ldpi"
		}
	}

	@JvmStatic
	suspend fun getDeviceManufactureModelName(): String {
		val manufacturer = Build.MANUFACTURER
		val model = Build.MODEL
		val name = if (model.lowercase()
				.startsWith(manufacturer.lowercase())) {
			model
		} else {
			"$manufacturer $model"
		}
		return capitalizeFirstLetter(name) ?: ""
	}

	@JvmStatic
	suspend fun isDeviceConnectedToInternet(context: Context): Boolean = withIOContext {
		val applicationContext = context.applicationContext
		val systemService = applicationContext.getSystemService(CONNECTIVITY_SERVICE)
		val cm = systemService as? ConnectivityManager
		val network = cm?.activeNetwork ?: return@withIOContext false
		val nc = cm.getNetworkCapabilities(network) ?: return@withIOContext false

		nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
			nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
			nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
			nc.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)
	}

	@JvmStatic
	suspend fun getDeviceUserCountry(context: Context): String = withIOContext {
		val localeCountry = Locale.getDefault().country
		if (localeCountry.isNotEmpty()) return@withIOContext localeCountry

		val applicationContext = context.applicationContext
		val systemService = applicationContext.getSystemService(TELEPHONY_SERVICE)
		val tm = systemService as? TelephonyManager

		val simCountry = tm?.simCountryIso
		if (!simCountry.isNullOrEmpty()) {
			simCountry.uppercase(Locale.getDefault())
		} else {
			"Unknown"
		}
	}

	@JvmStatic
	suspend fun isUserFromIndia(context: Context): Boolean = withIOContext {
		val indiaCode = "in"
		val applicationContext = context.applicationContext
		val systemService = applicationContext.getSystemService(TELEPHONY_SERVICE)
		val tm = systemService as? TelephonyManager

		when {
			tm?.networkCountryIso?.equals(indiaCode, true) == true -> true
			tm?.simCountryIso?.equals(indiaCode, true) == true -> true
			Locale.getDefault().country.equals(indiaCode, true) -> true
			TimeZone.getDefault().id.equals("Asia/Kolkata", true) -> true
			else -> false
		}
	}

	@JvmStatic
	fun normalizeIndianNumber(raw: String): String {
		val digits = raw.filter { it.isDigit() }
		return when (digits.length) {
			12 if digits.startsWith("91") -> digits.substring(2)
			11 if digits.startsWith("0") -> digits.substring(1)
			10 -> digits
			else -> digits
		}
	}
}