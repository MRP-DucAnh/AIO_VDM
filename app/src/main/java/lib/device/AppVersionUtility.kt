package lib.device

import android.content.pm.PackageManager.*
import android.content.pm.PackageManager.PackageInfoFlags.*
import android.os.Build.VERSION.*
import android.os.Build.VERSION_CODES.*
import app.core.*

object AppVersionUtility {

	@JvmStatic
	val versionName: String?
		get() {
			val context = AIOApp.INSTANCE
			val packageName = context.packageName
			return try {
				val packageManager = context.packageManager

				val packageInfo = if (SDK_INT >= TIRAMISU) {
					val flags = of(GET_SIGNING_CERTIFICATES.toLong())
					packageManager.getPackageInfo(packageName, flags)
				} else {
					@Suppress("DEPRECATION")
					packageManager.getPackageInfo(packageName, GET_SIGNATURES)
				}

				packageInfo.versionName
			} catch (error: NameNotFoundException) {
				error.printStackTrace()
				null
			}
		}

	@JvmStatic
	val versionCode: Long
		get() {
			val context = AIOApp.INSTANCE
			val packageName = context.packageName
			return try {
				val packageManager = context.packageManager
				val packageInfo = if (SDK_INT >= TIRAMISU) {
					val flags = of(GET_SIGNING_CERTIFICATES.toLong())
					packageManager.getPackageInfo(packageName, flags)
				} else {
					@Suppress("DEPRECATION")
					packageManager.getPackageInfo(packageName, GET_SIGNATURES)
				}

				packageInfo.longVersionCode
			} catch (error: NameNotFoundException) {
				error.printStackTrace()
				0
			}
		}

	@JvmStatic
	val deviceSDKVersion: Int
		get() = SDK_INT
}