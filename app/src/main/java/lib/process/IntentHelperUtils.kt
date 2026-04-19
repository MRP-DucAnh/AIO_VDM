package lib.process

import android.app.*
import android.content.*
import android.content.pm.*
import androidx.core.net.*

object IntentHelperUtils {

	private val logger = LogHelperUtils.from(javaClass)

	@JvmStatic
	fun getMatchingActivities(activity: Activity?,
	                          intent: Intent?): List<ResolveInfo> {
		if (intent == null || activity == null) return emptyList()
		return activity.packageManager.queryIntentActivities(intent, 0)
	}

	@JvmStatic
	suspend fun getIntentData(activity: Activity?): String? {
		val intent = activity?.intent
		val action = intent?.action

		return when (action) {
			Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
			Intent.ACTION_VIEW -> intent.dataString
			else -> null
		}
	}

	@JvmStatic
	suspend fun canHandleIntent(activity: Activity?,
	                            intent: Intent?): Boolean {
		if (intent == null || activity == null) return false
		val packageManager = activity.packageManager
		val activities = packageManager.queryIntentActivities(intent, 0)
		return activities.isNotEmpty()
	}

	@JvmStatic
	suspend fun startActivityIfPossible(activity: Activity?,
	                                    intent: Intent?): Boolean {
		if (intent == null || activity == null) return false
		return if (canHandleIntent(activity, intent)) {
			activity.startActivity(intent)
			true
		} else {
			false
		}
	}

	@JvmStatic
	suspend fun getPackageNameForIntent(activity: Activity?,
	                                    intent: Intent?): String {
		if (intent == null || activity == null) return ""
		val packageManager = activity.packageManager
		val activities = packageManager.queryIntentActivities(intent, 0)
		return if (activities.isNotEmpty()) {
			activities[0].activityInfo.packageName
		} else {
			""
		}
	}

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
			} catch (_: Exception) {
				onError?.invoke()
				false
			}
		}
	}

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
			} catch (_: Exception) {
				onError?.invoke()
			}
		}
	}

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
			} catch (_: Exception) {
				onError?.invoke()
			}
		}
	}

	@JvmStatic
	suspend fun openWhatsappApp(context: Context,
	                            onError: (() -> Unit)? = null) {
		withMainContext {
			try {
				val pm = context.packageManager
				val packageName = "com.whatsapp"
				val intent = pm.getLaunchIntentForPackage(packageName)
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

	@JvmStatic
	suspend fun openYouTubeMusicApp(context: Context,
	                                onError: (() -> Unit)? = null) {
		withMainContext {
			try {
				val pm = context.packageManager
				val packageName = "com.google.android.apps.youtube.music"
				val intent = pm.getLaunchIntentForPackage(packageName)
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

	@JvmStatic
	suspend fun openSoundCloudApp(context: Context,
	                              onError: (() -> Unit)? = null) {
		withMainContext {
			try {
				val pm = context.packageManager
				val packageName = "com.soundcloud.android"
				val intent = pm.getLaunchIntentForPackage(packageName)
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

	@JvmStatic
	suspend fun openPinterestApp(context: Context,
	                             onError: (() -> Unit)? = null) {
		withMainContext {
			try {
				val pm = context.packageManager
				val packageName = "com.pinterest"
				val intent = pm.getLaunchIntentForPackage(packageName)
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

	@JvmStatic
	suspend fun openTikTokApp(context: Context,
	                          onError: (() -> Unit)? = null) {
		withMainContext {
			try {
				val pm = context.packageManager
				val packageName = "com.zhiliaoapp.musically"
				val intent = pm.getLaunchIntentForPackage(packageName)
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

	@JvmStatic
	suspend fun openDailymotionApp(context: Context,
	                               onError: (() -> Unit)? = null) {
		withMainContext {
			try {
				val pm = context.packageManager
				val packageName = "com.dailymotion.dailymotion"
				val intent = pm.getLaunchIntentForPackage(packageName)
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

	@JvmStatic
	suspend fun openRedditApp(context: Context,
	                          onError: (() -> Unit)? = null) {
		withMainContext {
			try {
				val pm = context.packageManager
				val packageName = "com.reddit.frontpage"
				val intent = pm.getLaunchIntentForPackage(packageName)
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

	@JvmStatic
	suspend fun openXApp(context: Context,
	                     onError: (() -> Unit)? = null) {
		withMainContext {
			try {
				val pm = context.packageManager
				val intent = pm.getLaunchIntentForPackage("com.twitter.android")
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

	@JvmStatic
	suspend fun openTedTalksApp(context: Context,
	                            onError: (() -> Unit)? = null) {
		withMainContext {
			try {
				val pm = context.packageManager
				val intent = pm.getLaunchIntentForPackage("com.ted.android")
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