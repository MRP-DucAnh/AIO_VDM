package lib.process

import android.annotation.*
import android.content.*
import android.os.*
import android.provider.*
import lib.process.DeviceIdProvider.Companion.APP_SALT
import java.security.*

/**
 * Provides a unique, stable, and persistent identifier for the device.
 * * This class generates a device-specific fingerprint by hashing hardware metadata
 * with an application-specific salt. The resulting ID is deterministic,
 * meaning it will remain consistent across app restarts.
 *
 * ### Security & Privacy
 * - Uses **SHA-256** to ensure the original device hardware IDs are irreversible.
 * - Incorporates [APP_SALT] to prevent the ID from being cross-referenced by other apps.
 * - Filters out known "garbage" IDs (like the emulator-specific `9774d56d682e549c`).
 *
 * @property context The context used to access [Settings.Secure]. The class internally
 * extracts the application context to prevent memory leaks.
 */
class DeviceIdProvider(private val context: Context) {

	/**
	 * Application context extracted from the provided context.
	 * Using application context instead of Activity context prevents memory leaks
	 * when the DeviceIdProvider outlives the Activity lifecycle.
	 */
	private val appContext = context.applicationContext

	companion object {
		/**
		 * Application-specific salt value used to make the device ID unique to this app.
		 * Different apps using the same device will generate different IDs even if all
		 * other components are identical.
		 */
		private const val APP_SALT = "com.tubeaio.pro"

		/**
		 * Cached device ID to avoid regeneration on subsequent calls.
		 * Marked as volatile to ensure visibility across multiple threads.
		 * Null until the first successful generation.
		 */
		@Volatile private var cachedId: String? = null
	}

	/**
	 * Generates a unique device identifier or returns the cached version if already generated.
	 * This method is a suspend function that executes on an IO thread to avoid blocking
	 * the main thread during hash computation and system calls.
	 *
	 * @return AN SHA-256 hex string representing the unique device identifier.
	 *         The same device will always return the same ID (unless factory reset
	 *         or app reinstalled).
	 * @throws SecurityException if SHA-256 algorithm is not available
	 *         (extremely rare on Android devices)
	 * @see #androidId()
	 * @see #sha256(String)
	 */
	suspend fun generate(): String {
		return withIOContext {
			cachedId?.let { return@withIOContext it }

			synchronized(this) {
				cachedId?.let { return@withIOContext it }

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
				return@withIOContext hash
			}
		}
	}

	/**
	 * Retrieves the Android ID (Settings.Secure.ANDROID_ID) for the device.
	 * This method handles edge cases where the Android ID might be invalid or missing.
	 *
	 * @return The Android ID if valid and available, otherwise "fallback"
	 * @see Settings.Secure#ANDROID_ID
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
	 * Generates an SHA-256 hash of the input string.
	 * SHA-256 is a cryptographic hash function that produces a 256-bit (32-byte) hash value.
	 * The resulting hash is deterministic (same input always produces same output) and
	 * collision-resistant.
	 *
	 * @param input The input string to hash (typically the composite fingerprint)
	 * @return A 64-character hex string representing the SHA-256 hash of the input
	 * @throws NoSuchAlgorithmException if SHA-256 is not available
	 *         (should never happen on standard Android devices)
	 * @see MessageDigest
	 * @see <a href="https://en.wikipedia.org/wiki/SHA-2">SHA-256 on Wikipedia</a>
	 */
	private fun sha256(input: String): String {
		val digest = MessageDigest.getInstance("SHA-256")
		return digest.digest(input.toByteArray())
			.joinToString("") { "%02x".format(it) }
	}
}
