package lib.process

import android.app.*
import android.content.*
import android.content.pm.*
import androidx.core.net.*

/**
 * Utility object providing helper methods for working with Intents and launching
 * various applications.
 *
 * This helper class simplifies common Intent operations such as:
 * - Checking if an Intent can be handled by any installed activity
 * - Starting activities safely with fallback handling
 * - Launching popular social media and entertainment apps with proper error handling
 * - Extracting data from incoming Intents (e.g., shared text or view actions)
 *
 * All methods are annotated with @JvmStatic for seamless interoperability with Java code.*
 */
object IntentHelperUtils {

	/**
	 * Logger instance for this utility class, used to record errors and debugging information.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Retrieves a list of activities that can handle the specified Intent.
	 *
	 * This method queries the PackageManager to find all activities that match the given Intent
	 * criteria, which is useful for checking if an action can be performed before attempting it.
	 *
	 * @param activity The Activity context used to access the PackageManager. Can be null.
	 * @param intent The Intent to query for matching activities. Can be null.
	 * @return List of ResolveInfo objects representing activities that can handle the Intent.
	 *         Returns an empty list if either parameter is null or no matching activities are found.
	 */
	@JvmStatic
	fun getMatchingActivities(activity: Activity?, intent: Intent?): List<ResolveInfo> {
		if (intent == null || activity == null) return emptyList()
		return activity.packageManager.queryIntentActivities(intent, 0)
	}

	/**
	 * Extracts the primary data from an Activity's Intent.
	 *
	 * This method handles two common Intent actions:
	 * - ACTION_SEND: Returns the text extra from the Intent
	 * - ACTION_VIEW: Returns the data URI string
	 * - Other actions: Returns null
	 *
	 * @param activity The Activity whose Intent data should be extracted. Can be null.
	 * @return The extracted text or data string, or null if no data is available or
	 *         the action is not supported.
	 */
	@JvmStatic
	fun getIntentData(activity: Activity?): String? {
		val intent = activity?.intent
		val action = intent?.action

		return when (action) {
			Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
			Intent.ACTION_VIEW -> intent.dataString
			else -> null
		}
	}

	/**
	 * Checks whether there is any activity installed that can handle the given Intent.
	 *
	 * This is a safety check that should be performed before attempting to start an activity
	 * to avoid ActivityNotFoundException crashes.
	 *
	 * @param activity The Activity context used to access the PackageManager. Can be null.
	 * @param intent The Intent to check for availability. Can be null.
	 * @return true if at least one activity can handle the Intent, false otherwise.
	 */
	@JvmStatic
	fun canHandleIntent(activity: Activity?, intent: Intent?): Boolean {
		if (intent == null || activity == null) return false
		val activities = activity.packageManager.queryIntentActivities(intent, 0)
		return activities.isNotEmpty()
	}

	/**
	 * Safely starts an activity for the given Intent if possible.
	 *
	 * This method first checks if any activity can handle the Intent before
	 * attempting to start it. It returns a boolean indicating success or failure,
	 * allowing the caller to implement fallback logic.
	 *
	 * @param activity The Activity context from which to start the new activity. Can be null.
	 * @param intent The Intent that describes the activity to start. Can be null.
	 * @return true if the Intent was successfully started, false if the Intent was null,
	 *         the Activity was null, or no activity could handle the Intent.
	 */
	@JvmStatic
	fun startActivityIfPossible(activity: Activity?, intent: Intent?): Boolean {
		if (intent == null || activity == null) return false
		return if (canHandleIntent(activity, intent)) {
			activity.startActivity(intent)
			true
		} else {
			false
		}
	}

	/**
	 * Gets the package name of the first activity that can handle the given Intent.
	 *
	 * This is useful when you need to identify which application would handle a particular Intent
	 * without actually starting it.
	 *
	 * @param activity The Activity context used to access the PackageManager. Can be null.
	 * @param intent The Intent to query. Can be null.
	 * @return The package name of the first matching activity, or an empty string if no
	 *         matches are found or if parameters are invalid.
	 */
	@JvmStatic
	fun getPackageNameForIntent(activity: Activity?, intent: Intent?): String {
		if (intent == null || activity == null) return ""
		val packageManager = activity.packageManager
		val activities = packageManager.queryIntentActivities(intent, 0)
		return if (activities.isNotEmpty()) {
			activities[0].activityInfo.packageName
		} else {
			""
		}
	}

	/**
	 * Opens the Facebook app with a specified URL, or the main Facebook page if no URL is provided.
	 *
	 * This method attempts to launch the official Facebook app (com.facebook.katana).
	 * If the app is not installed, the onError callback is invoked (if provided).
	 * The operation is performed on the main thread using a coroutine context.
	 *
	 * @param context The Context used to start the activity. Must not be null.
	 * @param targetUrl The URL to open in the Facebook app.
	 * @param onError Optional callback invoked if the Facebook app is not installed.
	 * @return true if the Facebook app was successfully launched, false otherwise.
	 */
	@JvmStatic
	suspend fun openFacebookApp(
		context: Context,
		targetUrl: String? = "https://www.facebook.com",
		onError: (() -> Unit)? = null
	): Boolean {
		return withMainContext {
			try {
				val uri = (targetUrl ?: "https://www.facebook.com").toUri()
				val intent = Intent(Intent.ACTION_VIEW, uri).apply {
					setPackage("com.facebook.katana")
					addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				}

				if (intent.resolveActivity(context.packageManager) != null) {
					context.startActivity(intent)
					true
				} else {
					onError?.invoke()
					false
				}
			} catch (error: Exception) {
				onError?.invoke()
				false
			}
		}
	}

	/**
	 * Opens the YouTube app with a specified video or channel URL.
	 *
	 * Attempts to launch the official YouTube app (com.google.android.youtube).
	 * If the app is not installed, the onError callback is invoked (if provided).
	 * The operation is performed on the main thread using a coroutine context.
	 *
	 * @param context The Context used to start the activity. Must not be null.
	 * @param videoOrChannelUrl The YouTube video or channel URL to open.
	 * @param onError Optional callback invoked if the YouTube app is not installed.
	 */
	@JvmStatic
	suspend fun openYouTubeApp(
		context: Context,
		videoOrChannelUrl: String? = "https://www.youtube.com",
		onError: (() -> Unit)? = null
	) {
		withMainContext {
			try {
				val uri = (videoOrChannelUrl ?: "https://www.youtube.com").toUri()
				val intent = Intent(Intent.ACTION_VIEW, uri).apply {
					setPackage("com.google.android.youtube")
					addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				}

				if (intent.resolveActivity(context.packageManager) != null) {
					context.startActivity(intent)
				} else {
					onError?.invoke()
				}
			} catch (error: Exception) {
				onError?.invoke()
			}
		}
	}

	/**
	 * Opens the Instagram app with a specified profile URL.
	 *
	 * Attempts to launch the official Instagram app (com.instagram.android).
	 * If the app is not installed, the onError callback is invoked (if provided).
	 * The operation is performed on the main thread using a coroutine context.
	 *
	 * @param context The Context used to start the activity. Must not be null.
	 * @param profileUrl The Instagram profile URL to open. Defaults to "http://instagram.com".
	 * @param onError Optional callback invoked if the Instagram app is not installed.
	 */
	@JvmStatic
	suspend fun openInstagramApp(
		context: Context,
		profileUrl: String? = "http://instagram.com",
		onError: (() -> Unit)? = null
	) {
		withMainContext {
			try {
				val uri = (profileUrl ?: "http://instagram.com").toUri()
				val intent = Intent(Intent.ACTION_VIEW, uri).apply {
					setPackage("com.instagram.android")
					addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				}

				if (intent.resolveActivity(context.packageManager) != null) {
					context.startActivity(intent)
				} else {
					onError?.invoke()
				}
			} catch (error: Exception) {
				onError?.invoke()
			}
		}
	}

	/**
	 * Opens the WhatsApp application.
	 *
	 * Attempts to launch the official WhatsApp app (com.whatsapp) using its launch Intent.
	 * If the app is not installed, the onError callback is invoked (if provided).
	 * Any errors during the process are logged using the internal logger.
	 * The operation is performed on the main thread using a coroutine context.
	 *
	 * @param context The Context used to start the activity. Must not be null.
	 * @param onError Optional callback invoked if the WhatsApp app is not installed.
	 */
	@JvmStatic
	suspend fun openWhatsappApp(context: Context, onError: (() -> Unit)? = null) {
		withMainContext {
			try {
				val packageManager = context.packageManager
				val packageName = "com.whatsapp"
				val intent = packageManager.getLaunchIntentForPackage(packageName)
				if (intent != null) {
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
					context.startActivity(intent)
				} else onError?.invoke()
			} catch (error: Exception) {
				logger.e("Error in opening whatsapp:", error)
				onError?.invoke()
			}
		}
	}

	/**
	 * Opens the YouTube Music application.
	 *
	 * Attempts to launch the official YouTube Music app (com.google.android.apps.YouTube.music).
	 * If the app is not installed, the onError callback is invoked (if provided).
	 * Any errors during the process are logged using the internal logger.
	 * The operation is performed on the main thread using a coroutine context.
	 *
	 * @param context The Context used to start the activity. Must not be null.
	 * @param onError Optional callback invoked if the YouTube Music app is not.
	 */
	@JvmStatic
	suspend fun openYouTubeMusicApp(context: Context, onError: (() -> Unit)? = null) {
		withMainContext {
			try {
				val packageManager = context.packageManager
				val packageName = "com.google.android.apps.youtube.music"
				val intent = packageManager.getLaunchIntentForPackage(packageName)
				if (intent != null) {
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
					context.startActivity(intent)
				} else onError?.invoke()
			} catch (error: Exception) {
				logger.e("Error in opening youtube music:", error)
				onError?.invoke()
			}
		}
	}

	/**
	 * Opens the SoundCloud application.
	 *
	 * Attempts to launch the official SoundCloud app (com.soundcloud.android).
	 * If the app is not installed, the onError callback is invoked (if provided).
	 * Any errors during the process are logged using the internal logger.
	 * The operation is performed on the main thread using a coroutine context.
	 *
	 * @param context The Context used to start the activity. Must not be null.
	 * @param onError Optional callback invoked if the SoundCloud app is not installed.
	 */
	@JvmStatic
	suspend fun openSoundCloudApp(context: Context, onError: (() -> Unit)? = null) {
		withMainContext {
			try {
				val packageManager = context.packageManager
				val packageName = "com.soundcloud.android"
				val intent = packageManager.getLaunchIntentForPackage(packageName)
				if (intent != null) {
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
					context.startActivity(intent)
				} else onError?.invoke()
			} catch (error: Exception) {
				logger.e("Error in opening soundcloud:", error)
				onError?.invoke()
			}
		}
	}

	/**
	 * Opens the Pinterest application.
	 *
	 * Attempts to launch the official Pinterest app (com.pinterest).
	 * If the app is not installed, the onError callback is invoked (if provided).
	 * Any errors during the process are logged using the internal logger.
	 * The operation is performed on the main thread using a coroutine context.
	 *
	 * @param context The Context used to start the activity. Must not be null.
	 * @param onError Optional callback invoked if the Pinterest app is not installed.
	 */
	@JvmStatic
	suspend fun openPinterestApp(context: Context, onError: (() -> Unit)? = null) {
		withMainContext {
			try {
				val packageManager = context.packageManager
				val packageName = "com.pinterest"
				val intent = packageManager.getLaunchIntentForPackage(packageName)
				if (intent != null) {
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
					context.startActivity(intent)
				} else onError?.invoke()
			} catch (error: Exception) {
				logger.e("Error in opening pinterest:", error)
				onError?.invoke()
			}
		}
	}

	/**
	 * Opens the TikTok application.
	 *
	 * Attempts to launch the official TikTok app (com.zhiliaoapp.musically).
	 * If the app is not installed, the onError callback is invoked (if provided).
	 * Any errors during the process are logged using the internal logger.
	 * The operation is performed on the main thread using a coroutine context.
	 *
	 * @param context The Context used to start the activity. Must not be null.
	 * @param onError Optional callback invoked if the TikTok app is not installed.
	 */
	@JvmStatic
	suspend fun openTikTokApp(context: Context, onError: (() -> Unit)? = null) {
		withMainContext {
			try {
				val packageManager = context.packageManager
				val packageName = "com.zhiliaoapp.musically"
				val intent = packageManager.getLaunchIntentForPackage(packageName)
				if (intent != null) {
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
					context.startActivity(intent)
				} else onError?.invoke()
			} catch (error: Exception) {
				logger.e("Error in opening tiktok:", error)
				onError?.invoke()
			}
		}
	}

	/**
	 * Opens the Dailymotion application.
	 *
	 * Attempts to launch the official Dailymotion app (com.dailymotion.dailymotion).
	 * If the app is not installed, the onError callback is invoked (if provided).
	 * Any errors during the process are logged using the internal logger.
	 * The operation is performed on the main thread using a coroutine context.
	 *
	 * @param context The Context used to start the activity. Must not be null.
	 * @param onError Optional callback invoked if the Dailymotion app is not installed.
	 */
	@JvmStatic
	suspend fun openDailymotionApp(context: Context, onError: (() -> Unit)? = null) {
		withMainContext {
			try {
				val packageManager = context.packageManager
				val packageName = "com.dailymotion.dailymotion"
				val intent = packageManager.getLaunchIntentForPackage(packageName)
				if (intent != null) {
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
					context.startActivity(intent)
				} else onError?.invoke()
			} catch (error: Exception) {
				logger.e("Error in opening dailymotion:", error)
				onError?.invoke()
			}
		}
	}

	/**
	 * Opens the Reddit application.
	 *
	 * Attempts to launch the official Reddit app (com.reddit.frontpage).
	 * If the app is not installed, the onError callback is invoked (if provided).
	 * Any errors during the process are logged using the internal logger.
	 * The operation is performed on the main thread using a coroutine context.
	 *
	 * @param context The Context used to start the activity. Must not be null.
	 * @param onError Optional callback invoked if the Reddit app is not installed.
	 */
	@JvmStatic
	suspend fun openRedditApp(context: Context, onError: (() -> Unit)? = null) {
		withMainContext {
			try {
				val packageManager = context.packageManager
				val packageName = "com.reddit.frontpage"
				val intent = packageManager.getLaunchIntentForPackage(packageName)
				if (intent != null) {
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
					context.startActivity(intent)
				} else onError?.invoke()
			} catch (error: Exception) {
				logger.e("Error in opening reddit:", error)
				onError?.invoke()
			}
		}
	}

	/**
	 * Opens the X (formerly Twitter) application.
	 *
	 * Attempts to launch the official X/Twitter app (com.twitter.android).
	 * If the app is not installed, the onError callback is invoked (if provided).
	 * Any errors during the process are logged using the internal logger.
	 * The operation is performed on the main thread using a coroutine context.
	 *
	 * @param context The Context used to start the activity. Must not be null.
	 * @param onError Optional callback invoked if the X/Twitter app is not installed.
	 */
	@JvmStatic
	suspend fun openXApp(context: Context, onError: (() -> Unit)? = null) {
		withMainContext {
			try {
				val packageManager = context.packageManager
				val packageName = "com.twitter.android"
				val intent = packageManager.getLaunchIntentForPackage(packageName)
				if (intent != null) {
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
					context.startActivity(intent)
				} else onError?.invoke()
			} catch (error: Exception) {
				logger.e("Error in opening twitter:", error)
				onError?.invoke()
			}
		}
	}

	/**
	 * Opens the TED Talks application.
	 *
	 * Attempts to launch the official TED app (com.ted.android).
	 * If the app is not installed, the onError callback is invoked (if provided).
	 * Any errors during the process are logged using the internal logger.
	 * The operation is performed on the main thread using a coroutine context.
	 *
	 * @param context The Context used to start the activity. Must not be null.
	 * @param onError Optional callback invoked if the TED app is not installed.
	 */
	@JvmStatic
	suspend fun openTedTalksApp(context: Context, onError: (() -> Unit)? = null) {
		withMainContext {
			try {
				val packageManager = context.packageManager
				val packageName = "com.ted.android"
				val intent = packageManager.getLaunchIntentForPackage(packageName)
				if (intent != null) {
					intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
					context.startActivity(intent)
				} else onError?.invoke()
			} catch (error: Exception) {
				logger.e("Error in opening ted-talk:", error)
				onError?.invoke()
			}
		}
	}
}