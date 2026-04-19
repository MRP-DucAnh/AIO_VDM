package lib.device

import android.app.*
import android.content.*
import android.content.Intent.*
import androidx.core.net.*
import lib.networks.*
import lib.process.*

object IntentUtility {

	@JvmStatic
	suspend fun openUrlInBrowser(context: Context, url: String) {
		withMainContext {
			if (url.isBlank()) return@withMainContext
			val intent = Intent(ACTION_VIEW, url.toUri()).apply {
				if (context !is Activity) addFlags(FLAG_ACTIVITY_NEW_TASK)
			}

			try {
				context.startActivity(intent)
			} catch (error: ActivityNotFoundException) {
				error.printStackTrace()
			}
		}
	}

	@JvmStatic
	fun getIntentDataURI(intent: Intent?): String? {
		if (intent == null) return null
		return when (intent.action) {
			ACTION_SEND -> {
				intent.getStringExtra(EXTRA_TEXT)
					?: intent.clipData?.getItemAt(0)?.text?.toString()
			}
			ACTION_VIEW -> intent.dataString
			else -> null
		}
	}

	@JvmStatic
	suspend fun openLinkInSystemBrowser(
		context: Context, fileUrl: String, onFailed: () -> Unit = {}) {
		withMainContext {
			if (fileUrl.isBlank() || !URLUtility.isValidURL(fileUrl)) {
				onFailed()
				return@withMainContext
			}

			val intent = Intent(ACTION_VIEW, fileUrl.toUri()).apply {
				if (context !is Activity) {
					addFlags(FLAG_ACTIVITY_NEW_TASK)
				}
			}

			try {
				context.startActivity(intent)
			} catch (error: Exception) {
				error.printStackTrace()
				onFailed()
			}
		}
	}
}