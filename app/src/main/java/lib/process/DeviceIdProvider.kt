package lib.process

import android.annotation.*
import android.content.*
import android.os.*
import android.provider.*
import app.core.*
import java.security.*

class DeviceIdProvider(private val context: Context) {

	private val appContext = context.applicationContext

	companion object {
		private const val APP_SALT = "com.$APP_DEFAULT_FULL_NAME.pro"
		@Volatile private var cachedId: String? = null
	}

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

	@SuppressLint("HardwareIds")
	private fun androidId(): String {
		return Settings.Secure.getString(
			appContext.contentResolver,
			Settings.Secure.ANDROID_ID
		)?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
			?: "fallback"
	}

	private fun sha256(input: String): String {
		val digest = MessageDigest.getInstance("SHA-256")
		return digest.digest(input.toByteArray())
			.joinToString("") { "%02x".format(it) }
	}
}