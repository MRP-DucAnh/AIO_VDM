package lib.process

import android.app.*
import android.app.PendingIntent.*
import android.content.*
import android.content.Intent.*
import app.core.*
import kotlinx.coroutines.*

object OSProcessUtils {

	@JvmStatic
	fun getCurrentMethodName(): String {
		return Thread.currentThread().stackTrace[2].methodName
	}

	@JvmStatic
	fun getCurrentClassName(): String {
		return Thread.currentThread().stackTrace[2].className
	}

	@JvmStatic
	fun getStackTraceAsString(): String {
		val stackTrace = Thread.currentThread().stackTrace
		return stackTrace.joinToString("\n") { it.toString() }
	}

	@JvmStatic
	fun isMainThread(): Boolean {
		return Thread.currentThread().name == "main"
	}

	@JvmStatic
	fun lambdaToString(lambda: () -> Unit): String {
		return lambda.toString()
	}

	@JvmStatic
	fun getCurrentFileName(): String {
		return Thread.currentThread().stackTrace[2].fileName
	}

	@JvmStatic
	fun getCurrentLineNumber(): Int {
		return Thread.currentThread().stackTrace[2].lineNumber
	}

	@JvmStatic
	fun generateUniqueId(): String {
		val timestamp = System.currentTimeMillis()
		val randomNum = (1000..9999).random()
		return "ID_${timestamp}_$randomNum"
	}

	@JvmStatic
	fun getCallerClassNameAndMethodName(): String {
		val stackTrace = Thread.currentThread().stackTrace
		val caller = stackTrace[3]
		return "${caller.className} -> ${caller.methodName}"
	}

	@JvmStatic
	fun isDebugMode(): Boolean {
		return AIOApp.IS_DEBUG_MODE_ON
	}

	@JvmStatic
	suspend fun restartApp(shouldKillProcess: Boolean = false) {
		withContext(Dispatchers.Main + NonCancellable) {
			val context = AIOApp.INSTANCE
			val intent = context.packageManager
				.getLaunchIntentForPackage(context.packageName)
			intent?.addFlags(FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_NEW_TASK)

			val pendingIntent = getActivity(
				context, 0, intent,
				FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE
			)

			val systemService = context.getSystemService(Context.ALARM_SERVICE)
			val alarmManager = systemService as AlarmManager
			val alarmType = AlarmManager.RTC
			val currentTimeMillis = System.currentTimeMillis()
			alarmManager.set(alarmType, currentTimeMillis + 100, pendingIntent)

			if (shouldKillProcess) {
				Runtime.getRuntime().exit(0)
			}
		}
	}
}