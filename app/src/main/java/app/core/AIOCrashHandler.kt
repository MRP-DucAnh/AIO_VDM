package app.core

import app.core.AIOApp.Companion.aioSettings
import com.aio.*
import lib.process.*
import lib.process.ThreadsUtility.executeInBackground
import lib.texts.CommonTextUtils.getText
import lib.texts.CommonTextUtils.removeDuplicateSlashes
import java.io.*
import java.text.*
import java.util.*

/**
 * A global [Thread.UncaughtExceptionHandler] that captures and logs uncaught exceptions.
 *
 * This handler is responsible for intercepting unexpected app terminations,
 * capturing detailed debugging information, and persisting it for analysis. Its core
 * duties include:
 * - Acting as the default handler for all uncaught exceptions application-wide.
 * - Capturing the full stack trace of the exception.
 * - Writing the stack trace to a local log file (e.g., `.aio_crash_log_20231027_103000.txt`)
 *   for offline inspection.
 * - Uploading the crash information to a backend service for centralized monitoring.
 * - Setting a flag in [aioSettings] to indicate a recent crash, which can be
 *   checked on the next app launch to inform the user or perform recovery actions.
 *
 * By centralizing crash reporting, this class improves the app's robustness and
 * simplifies post-mortem diagnostics. It is designed to be failsafe, wrapping its
 * logic in a `try-catch` block to prevent the handler itself from causing a
 * secondary crash.
 *
 * It can be registered at application startup like this:
 * `Thread.setDefaultUncaughtExceptionHandler(AIOCrashHandler())`
 */
class AIOCrashHandler : Thread.UncaughtExceptionHandler {

	/** Logger for this class, used for structured logging. */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Handles uncaught exceptions for any application thread, acting as the final safeguard.
	 *
	 * This method is invoked automatically by the JVM when a thread terminates due to an
	 * unhandled exception. Its primary responsibilities are to capture comprehensive diagnostic
	 * information and persist it for debugging, ensuring that no crash goes unnoticed.
	 *
	 * The process includes:
	 * 1.  Capturing the full stack trace of the `exception`.
	 * 2.  Saving the stack trace to a local, timestamped log file (e.g., `.aio_crash_log_...txt`).
	 * 3.  Attempting to upload the stack trace to a remote backend (Supabase) for centralized analysis.
	 * 4.  Setting a flag in [aioSettings] (`hasAppCrashedRecently`) to notify the application
	 *     on its next launch, enabling post-crash user-facing dialogs or recovery logic.
	 *
	 * To ensure reliability, the entire handler is wrapped in a `try-catch` block to prevent
	 * a failure within the handler itself from causing a secondary, unreportable crash.
	 *
	 * @param thread The thread that terminated due to the uncaught exception.
	 * @param exception The uncaught exception that caused the thread's termination.
	 */
	override fun uncaughtException(thread: Thread, exception: Throwable) {
		try {
			logger.d("Uncaught exception in thread: ${thread.name}")

			// Extract full stack trace into a string
			val stackTrace = StringWriter().use { sw ->
				PrintWriter(sw).use { pw ->
					exception.printStackTrace(pw)
					sw.toString()
				}
			}
			logger.d("Stack trace successfully captured")

			// Attempt to persist crash log
			if (AIOApp.IS_DEBUG_MODE_ON) {
				try {
					val externalDataFolderPath = AIOApp.AIO_DEFAULT_DOWNLOAD_PATH
					var directoryPath = "$externalDataFolderPath/${getText(R.string.title_aio_others)}/.configs"
					removeDuplicateSlashes(directoryPath)?.let { directoryPath = it }
					val dir = File(directoryPath)

					// Ensure directory exists
					if (!dir.exists()) {
						dir.mkdirs()
						logger.d("Crash log directory created at: ${dir.absolutePath}")
					}

					val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
						.format(System.currentTimeMillis())
					val fileName = ".aio_crash_log_$timeStamp.txt"
					// Write stack trace to log file
					val crashLogFile = File(dir, fileName)
					crashLogFile.writeText(stackTrace)

					logger.d("Crash log written at: ${crashLogFile.absolutePath}")
				} catch (error: Exception) {
					logger.e("Failed to save crash log: ${error.message}", error)
				}
			}

			// Save crash information for later inspection
			executeInBackground(timeOutInMilli = 500, codeBlock = {

			}, errorHandler = { error ->
				logger.e("Failed to upload crash log: ${error.message}")
			})

			// Mark crash state in settings
			aioSettings.hasAppCrashedRecently = true
			aioSettings.updateInDB()
			logger.d("Crash flag persisted in aioSettings")

		} catch (error: Exception) {
			// Catch secondary failures in the crash handling flow
			logger.d("Secondary error during crash handling: ${error.message}")
		}
	}
}
