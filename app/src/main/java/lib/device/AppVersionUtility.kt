package lib.device

import android.content.*
import android.content.pm.*
import android.os.Build.VERSION.*
import androidx.core.content.pm.*
import kotlinx.coroutines.sync.*
import lib.process.*
import kotlin.coroutines.cancellation.*

object AppVersionUtility {

	private var cachedVersionName: String? = null
	private var cachedVersionCode: Long? = null
	private val mutex = Mutex()

	private suspend fun ensureCacheLoaded(context: Context) {
		if (cachedVersionName != null &&
			cachedVersionCode != null) return

		mutex.withLock {
			if (cachedVersionName != null &&
				cachedVersionCode != null) return@withLock

			withIOContext {
				try {
					val packageName = context.packageName
					val packageManager = context.packageManager

					val info = if (SDK_INT >= 33) {
						val packageInfoFlags = PackageManager.PackageInfoFlags.of(0)
						packageManager.getPackageInfo(packageName, packageInfoFlags)
					} else {
						@Suppress("DEPRECATION")
						packageManager.getPackageInfo(packageName, 0)
					}

					cachedVersionName = info.versionName
					cachedVersionCode = PackageInfoCompat.getLongVersionCode(info)
				} catch (error: Exception) {
					if (error is CancellationException) throw error
					error.printStackTrace()
				}
			}
		}
	}

	suspend fun getVersionName(context: Context): String? {
		ensureCacheLoaded(context)
		return cachedVersionName
	}

	suspend fun getVersionCode(context: Context): Long {
		ensureCacheLoaded(context)
		return cachedVersionCode ?: 0L
	}

	val deviceSDKVersion: Int
		get() = SDK_INT
}