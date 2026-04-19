package lib.device

import android.app.*
import android.content.*
import android.content.Intent.*
import androidx.core.net.*
import lib.networks.*
import lib.process.*
import java.lang.ref.*

object IntentUtility {

	@Throws(IllegalStateException::class)
	@JvmStatic
	suspend fun openUrlInBrowser(context: Context?, url: String) {
		withMainContext {
			if (url.isEmpty()) return@withMainContext
			val contextRef = WeakReference(context).get() ?: return@withMainContext
			val webpage = url.toUri()
			val intent = Intent(ACTION_VIEW, webpage)

			if (intent.resolveActivity(contextRef.packageManager) != null) {
				contextRef.startActivity(intent)
			} else {
				throw IllegalStateException("No activity found to handle intent: $intent")
			}
		}
	}

	@JvmStatic
	fun getIntentDataURI(activity: Activity?): String? {
		val safeContext = WeakReference(activity).get() ?: return null
		val intent = safeContext.intent
		val action = intent.action

		return when (action) {
			ACTION_SEND -> intent.getStringExtra(EXTRA_TEXT)
			ACTION_VIEW -> intent.dataString
			else -> null
		}
	}

	@JvmStatic
	fun openLinkInSystemBrowser(fileUrl: String,
	                            activity: Activity?, onFailed: () -> Unit) {
		WeakReference(activity).get()?.let { safeContextRef ->
			try {
				if (fileUrl.isNotEmpty() && URLUtility.isValidURL(fileUrl)) {
					val intent = Intent(ACTION_VIEW, fileUrl.toUri()).apply {
						addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK)
					}
					safeContextRef.startActivity(intent)
				} else onFailed()
			} catch (error: Exception) {
				error.printStackTrace()
				onFailed()
			}
		}
	}
}