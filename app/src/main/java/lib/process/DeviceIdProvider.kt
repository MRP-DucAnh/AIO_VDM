package lib.process

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

/**
 * A utility class responsible for generating a unique, stable device identifier that persists
 * across application reinstall.
 *
 * This generator creates a SHA-256 fingerprint by combining hardware-specific metadata
 * ([Build.MANUFACTURER], [Build.MODEL], [Build.BOARD]), the [Settings.Secure.ANDROID_ID],
 * the application's package name, and a predefined internal salt.
 *
 * Note: While this ID remains stable across reinstall, it may change if the device
 * undergoes a factory reset or if the [Settings.Secure.ANDROID_ID] is modified (e.g., on rooted devices).
 *
 * @property context The application context used to retrieve the Android ID and package name.
 */
class DeviceIdProvider(private val context: Context) {

	private val appContext = context.applicationContext

	companion object {
		/**
		 * A hardcoded string used as a cryptographic salt to ensure the generated device ID
		 * is unique to this specific application and remains consistent across different versions.
		 */
		private const val APP_SALT = "com.aio.video_downloader"

		/**
		 * Stores the computed device identifier in memory to avoid redundant SHA-256
		 * hashing operations on subsequent calls.
		 */
		@Volatile private var cachedId: String? = null
	}

	/**
	 * Generates a unique SHA-256 hash representing the device's identity.
	 *
	 * The identifier is constructed by concatenating the Android ID, hardware metadata
	 * (Manufacturer, Model, Board), the application's package name, and a static salt.
	 * This ensures the resulting string is unique to the device-app combination.
	 *
	 * @return A hex-encoded SHA-256 string representing the device fingerprint.
	 */
	fun generate(): String {
		cachedId?.let { return it }

		synchronized(this) {
			cachedId?.let { return it }

			val fingerprint = listOf(
				androidId(),
				Build.MANUFACTURER,
				Build.MODEL,
				Build.BOARD,
				appContext.packageName,
				APP_SALT
			).joinToString("|")

			val hash = sha256(fingerprint)
			cachedId = hash
			return hash
		}
	}

	/**
	 * Retrieves the hardware-based Android ID from the system settings.
	 *
	 * This method includes logic to handle common edge cases, such as returning "unknown"
	 * for null/blank values and identifying the well-known "9774d56d682e549c"
	 * ID used by the Android emulator.
	 *
	 * @return A unique 64-bit hex string for the device, or a fallback string if the ID
	 *         is unavailable or identified as an emulator.
	 */
	@SuppressLint("HardwareIds")
	private fun androidId(): String {
		return Settings.Secure.getString(
			appContext.contentResolver,
			Settings.Secure.ANDROID_ID
		)?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
			?: "fallback"
	}

	/**
	 * Computes a SHA-256 hash of the provided input string and returns the result as a
	 * lowercase hexadecimal string.
	 *
	 * @param input The string to be hashed.
	 * @return A 64-character hexadecimal representation of the SHA-256 hash.
	 */
	private fun sha256(input: String): String {
		val digest = MessageDigest.getInstance("SHA-256")
		return digest.digest(input.toByteArray())
			.joinToString("") { "%02x".format(it) }
	}
}
