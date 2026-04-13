package lib.process

import android.app.*
import android.app.PendingIntent.*
import android.content.*
import android.content.Intent.*
import app.core.*
import kotlinx.coroutines.*

/**
 * Utility object providing various operating system and process-related operations.
 *
 * This helper class offers functionality for:
 * - Retrieving runtime execution information (stack traces, method names, line numbers)
 * - Thread detection and debugging utilities
 * - Application restart mechanisms with process termination options
 * - Unique identifier generation
 *
 * Many methods leverage the current thread's stack trace to provide introspection
 * capabilities, useful for logging, debugging, and runtime analysis.
 *
 * Note: Stack trace introspection methods may have performance implications and should
 * be used judiciously in production code, particularly in performance-critical paths.*
 */
object OSProcessUtils {

	/**
	 * Retrieves the name of the currently executing method.
	 *
	 * This method analyzes the current thread's stack trace to determine the name of
	 * the method that called the method invoking this utility function.
	 *
	 * Implementation note: The stack trace index 2 corresponds to the immediate caller
	 * of this method (since index 0 is getStackTrace(), index 1 is getCurrentMethodName(),
	 * and index 2 is the desired caller method).
	 *
	 * Usage example:
	 * ```kotlin
	 * fun myFunction() {
	 *     val methodName = OSProcessUtils.getCurrentMethodName()
	 *     // methodName will be "myFunction"
	 * }
	 * ```
	 *
	 * Performance note: Accessing the stack trace has moderate overhead. Consider caching
	 * the result if called frequently in loops or tight performance-sensitive code.
	 *
	 * @return The name of the method that called this function, as a String.
	 */
	@JvmStatic
	fun getCurrentMethodName(): String {
		return Thread.currentThread().stackTrace[2].methodName
	}

	/**
	 * Retrieves the fully qualified class name of the currently executing method's caller.
	 *
	 * This method analyzes the current thread's stack trace to determine the class name
	 * of the method that invoked this utility function.
	 *
	 * The returned class name includes the full package path (e.g., "com.example.MyClass").
	 *
	 * Implementation note: The stack trace index 2 corresponds to the immediate caller
	 * of this method (since index 0 is getStackTrace(), index 1 is getCurrentClassName(),
	 * and index 2 is the desired caller class).
	 *
	 * Usage example:
	 * ```kotlin
	 * package com.example
	 *
	 * class MyClass {
	 *     fun myFunction() {
	 *         val className = OSProcessUtils.getCurrentClassName()
	 *         // className will be "com.example.MyClass"
	 *     }
	 * }
	 * ```
	 *
	 * @return The fully qualified class name of the method that called this function.
	 */
	@JvmStatic
	fun getCurrentClassName(): String {
		return Thread.currentThread().stackTrace[2].className
	}

	/**
	 * Returns the current thread's stack trace as a formatted string.
	 *
	 * This method captures the complete stack trace of the current thread and converts
	 * it to a human-readable string format, with each stack frame element on a new line.
	 *
	 * This is particularly useful for:
	 * - Detailed debugging and error logging
	 * - Understanding execution flow in complex call chains
	 * - Capturing call context for asynchronous operations
	 * - Debugging race conditions or unexpected invocations
	 *
	 * The output format matches the standard Java stack trace format, showing each method
	 * call with class name, method name, file name, and line number where available.
	 *
	 * @return A formatted string containing the complete stack trace, with each element
	 *         separated by newline characters. Returns an empty string if the stack trace
	 *         is empty (theoretically impossible for a running thread).
	 */
	@JvmStatic
	fun getStackTraceAsString(): String {
		val stackTrace = Thread.currentThread().stackTrace
		return stackTrace.joinToString("\n") { it.toString() }
	}

	/**
	 * Checks whether the current thread is the main/UI thread of the application.
	 *
	 * The main thread (also known as the UI thread) is responsible for handling user
	 * interface updates, input events, and lifecycle callbacks. Long-running operations
	 * should not be performed on the main thread to avoid Application Not Responding (ANR)
	 * errors.
	 *
	 * This method is useful for:
	 * - Verifying that UI updates are performed on the main thread
	 * - Detecting potential threading violations
	 * - Conditionally dispatching work to background threads
	 * - Debugging threading-related bugs
	 *
	 * Note: In Android, the main thread is always named "main". This method relies on
	 * thread naming conventions which are consistent across Android applications.
	 *
	 * @return true if the current thread is the main/UI thread, false otherwise
	 *         (including background threads, AsyncTask threads, coroutine dispatchers, etc.).
	 */
	@JvmStatic
	fun isMainThread(): Boolean {
		return Thread.currentThread().name == "main"
	}

	/**
	 * Converts a lambda function to its string representation.
	 *
	 * This method returns the default toString() representation of the lambda, which
	 * typically includes the lambda's class name, identity hash code, and sometimes
	 * information about captured variables.
	 *
	 * This can be useful for:
	 * - Debugging and logging to identify which lambda is being executed
	 * - Comparing lambda instances (though identity comparison is more reliable)
	 * - Tracing callback invocations in complex event handling systems
	 *
	 * Note: The string representation is implementation-dependent and may vary between
	 * different Kotlin/JVM versions or compilers. Do not rely on the format for logic.
	 *
	 * Usage example:
	 * ```kotlin
	 * val myLambda = { println("Hello") }
	 * val lambdaString = OSProcessUtils.lambdaToString(myLambda)
	 * // Output might be: "Function0<java.lang.String>"
	 * ```
	 *
	 * @param lambda The lambda function to convert to a string representation.
	 * @return The string representation of the lambda, as returned by its toString() method.
	 */
	@JvmStatic
	fun lambdaToString(lambda: () -> Unit): String {
		return lambda.toString()
	}

	/**
	 * Retrieves the name of the source file containing the calling method.
	 *
	 * This method analyzes the current thread's stack trace to determine the file name
	 * of the source code file where the calling method is defined.
	 *
	 * The file name is extracted from the stack trace element and may be something like
	 * "MyClass.kt" or "MyClass.java". If debug information is not available, this may
	 * return null or an unknown value.
	 *
	 * Implementation note: The stack trace index 2 corresponds to the immediate caller
	 * of this method (since index 0 is getStackTrace(), index 1 is getCurrentFileName(),
	 * and index 2 is the desired caller).
	 *
	 * This method is primarily useful for:
	 * - Logging and debugging to identify source locations
	 * - Generating reports with file references
	 * - Automated testing and assertion frameworks
	 *
	 * @return The name of the source file containing the calling method, or potentially
	 *         null/unknown if debug information is missing (e.g., in obfuscated release builds).
	 */
	@JvmStatic
	fun getCurrentFileName(): String {
		return Thread.currentThread().stackTrace[2].fileName
	}

	/**
	 * Retrieves the line number in the source file where the calling method is located.
	 *
	 * This method analyzes the current thread's stack trace to determine the line number
	 * of the source code where the call to this utility function occurred.
	 *
	 * The line number is extracted from the stack trace element's lineNumber property.
	 * If debug information is not available (e.g., in release builds with ProGuard/R8
	 * obfuscation), this may return -2 (native method) or other negative values.
	 *
	 * Implementation note: The stack trace index 2 corresponds to the immediate caller
	 * of this method (since index 0 is getStackTrace(), index 1 is getCurrentLineNumber(),
	 * and index 2 is the desired caller).
	 *
	 * This method is useful for:
	 * - Precise logging of execution locations
	 * - Building debugging tools and profilers
	 * - Creating detailed error reports with exact source positions
	 *
	 * @return The line number in the source file where the call occurred, or a negative
	 *         value if the line number is unknown (e.g., -2 for native methods).
	 */
	@JvmStatic
	fun getCurrentLineNumber(): Int {
		return Thread.currentThread().stackTrace[2].lineNumber
	}

	/**
	 * Generates a unique identifier string based on timestamp and random number.
	 *
	 * This method creates a unique ID combining the current system time in milliseconds
	 * with a random 4-digit number, providing reasonable uniqueness for most application
	 * use cases without requiring heavy cryptographic operations.
	 *
	 * The format of the generated ID is: "ID_{timestamp}_{randomNumber}"
	 *
	 * Example: "ID_1704067200000_3847"
	 *
	 * Uniqueness characteristics:
	 * - Timestamp ensures chronological ordering and uniqueness across time
	 * - Random component reduces collision probability for IDs generated in the same
	 *   millisecond
	 * - Collision probability within the same millisecond is 1 in 9000 (1000-9999 range)
	 *
	 * This method is suitable for:
	 * - Temporary transaction IDs
	 * - Request tracing across asynchronous operations
	 * - Log correlation identifiers
	 * - Temporary cache keys
	 *
	 * Note: For cryptographically secure unique IDs or distributed systems requiring
	 * guaranteed global uniqueness, consider using UUID.randomUUID() instead.
	 *
	 * @return A unique identifier string in the format "ID_{timestamp}_{randomNumber}".
	 */
	@JvmStatic
	fun generateUniqueId(): String {
		val timestamp = System.currentTimeMillis()
		val randomNum = (1000..9999).random()
		return "ID_${timestamp}_$randomNum"
	}

	/**
	 * Returns the class name and method name of the method that called the caller.
	 *
	 * This method provides a deeper stack trace lookup, returning information about
	 * the method that invoked the method which called this utility (i.e., the grand-caller).
	 *
	 * Implementation details:
	 * - Stack trace index 0: getStackTrace()
	 * - Stack trace index 1: getCallerClassNameAndMethodName()
	 * - Stack trace index 2: The immediate caller of this method
	 * - Stack trace index 3: The caller of the immediate caller (target of this method)
	 *
	 * Output format: "{fully.qualified.ClassName} -> {methodName}"
	 *
	 * Usage example:
	 * ```kotlin
	 * class Level1 {
	 *     fun methodA() {
	 *         Level2().methodB()
	 *     }
	 * }
	 *
	 * class Level2 {
	 *     fun methodB() {
	 *         val info = OSProcessUtils.getCallerClassNameAndMethodName()
	 *         // info might be "com.example.Level1 -> methodA"
	 *     }
	 * }
	 * ```
	 *
	 * This is particularly useful for:
	 * - Creating advanced logging utilities that track call hierarchies
	 * - Building dependency injection or service locator debugging tools
	 * - Understanding complex call chains in framework code
	 * - Profiling and performance analysis
	 *
	 * @return A formatted string containing the class name and method name of the
	 *         grand-caller, or a fallback string if stack trace depth is insufficient.
	 */
	@JvmStatic
	fun getCallerClassNameAndMethodName(): String {
		val stackTrace = Thread.currentThread().stackTrace
		val caller = stackTrace[3]
		return "${caller.className} -> ${caller.methodName}"
	}

	/**
	 * Checks whether the application is running in debug mode.
	 *
	 * This method returns the debug mode status as configured in the AIOApp application
	 * class. This is useful for conditionally enabling debug-only features, verbose
	 * logging, or development tools that should not be present in production builds.
	 *
	 * Typical usage includes:
	 * - Enabling strict mode or additional runtime checks in debug builds
	 * - Showing developer menus or debug overlays only in development
	 * - Logging sensitive or verbose information only during development
	 * - Bypassing certain security checks or rate limits in debug environments
	 *
	 * Note: This method should not be relied upon for security decisions as the value
	 * could potentially be modified at runtime. Use BuildConfig.DEBUG for compile-time
	 * decisions when possible.
	 *
	 * @return true if the application is in debug mode, false for release/production builds.
	 * @see AIOApp.IS_DEBUG_MODE_ON
	 */
	@JvmStatic
	fun isDebugMode(): Boolean {
		return AIOApp.IS_DEBUG_MODE_ON
	}

	/**
	 * Restarts the entire application by recreating the main activity and optionally
	 * killing the current process.
	 *
	 * This method provides a clean application restart mechanism using Android's
	 * AlarmManager to schedule a delayed launch of the main activity, allowing the
	 * current process to properly shut down before restarting.
	 *
	 * How it works:
	 * 1. Retrieves the launch Intent for the application's main activity
	 * 2. Creates a PendingIntent with FLAG_CANCEL_CURRENT to ensure a fresh start
	 * 3. Schedules the PendingIntent with AlarmManager to fire after 100ms
	 * 4. Optionally terminates the current process with Runtime.getRuntime().exit(0)
	 *
	 * Key features:
	 * - Uses NonCancellable coroutine context to ensure restart completes even if
	 *   the coroutine is canceled
	 * - Runs on the main thread to ensure proper Android component lifecycle
	 * - Clears activity stack with FLAG_ACTIVITY_CLEAR_TOP and FLAG_ACTIVITY_NEW_TASK
	 * - Small delay (100ms) allows current activities to finish their lifecycle callbacks
	 *
	 * Usage considerations:
	 * - The alarm persists even if the app process dies, ensuring restart occurs
	 * - Should be used sparingly as it's disruptive to user experience
	 * - Useful for applying critical configuration changes or recovering from errors
	 * - The shouldKillProcess parameter provides control over process termination
	 *
	 * @param shouldKillProcess If true, terminates the current process immediately
	 *                          after scheduling the restart. If false, the process
	 *                          continues running but the restart will still occur.
	 *                          Default is false.
	 *
	 * Warning: Killing the process with shouldKillProcess = true prevents any pending
	 * operations (saved state, background tasks) from completing. Use with caution.
	 */
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

			val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
			alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 100, pendingIntent)

			if (shouldKillProcess) {
				Runtime.getRuntime().exit(0)
			}
		}
	}
}