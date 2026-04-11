package lib.process;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;

import app.core.AIOApp;

/**
 * A comprehensive logging utility class for Android applications.
 * <p>
 * This class provides a unified interface for logging messages at various severity
 * levels (error, debug, info, verbose, warning) while automatically tagging logs
 * with the originating class name. It integrates with both Android's Log system
 * and a custom file logger for persistent storage. The class respects the global
 * debug mode setting, preventing log output in production builds unless explicitly
 * enabled.
 */
public final class LogHelperUtils implements Serializable {

	/**
	 * The class associated with this logger instance.
	 * <p>
	 * Used to generate log tags with the simple class name, making it easy to
	 * identify the source of log messages in debugging tools and log outputs.
	 */
	private final Class<?> class_;

	/**
	 * Flag indicating whether debug logging is enabled.
	 * <p>
	 * This is determined by the global application debug setting. When false,
	 * all logging methods return early, preventing unnecessary log generation
	 * and improving performance in production builds.
	 */
	private final boolean isDebuggingMode;

	/**
	 * Private constructor for logger instance creation.
	 * <p>
	 * Initializes the logger with a specific class and determines the current
	 * debugging mode from the application configuration. The constructor is
	 * private to enforce use of the factory method pattern.
	 *
	 * @param class_ The class to associate with this logger instance
	 */
	private LogHelperUtils(Class<?> class_) {
		this.class_ = class_;
		this.isDebuggingMode = AIOApp.Companion.getIS_DEBUG_MODE_ON();
	}

	/**
	 * Factory method to create a logger instance for a specific class.
	 * <p>
	 * This is the primary entry point for obtaining a logger instance. It ensures
	 * proper initialization and provides a clean API for logger creation.
	 *
	 * @param class_ The class requesting the logger
	 * @return A new LogHelperUtils instance configured for the specified class
	 */
	@NonNull
	public static LogHelperUtils from(@NonNull Class<?> class_) {
		return new LogHelperUtils(class_);
	}

	/**
	 * Converts a Throwable to its string stack trace representation.
	 * <p>
	 * This utility method extracts the complete stack trace from an exception
	 * or error for logging purposes. It captures both the exception message
	 * and the full call stack in a formatted string.
	 *
	 * @param throwable The exception or error to convert
	 * @return String representation of the throwable including stack trace
	 */
	@NonNull
	public static String toString(@NonNull Throwable throwable) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw, true);
		throwable.printStackTrace(pw);
		return sw.toString();
	}

	/**
	 * Logs an error message.
	 * <p>
	 * Outputs the message to Android Logcat at ERROR level and writes it to
	 * the file logger for persistent storage. Only executes when debug mode is enabled.
	 *
	 * @param message The error message to log
	 */
	public void e(@NonNull String message) {
		if (!isDebuggingMode) return;
		String msg = toMessage(message);
		Log.e(class_.getSimpleName(), msg);
		FileLogger.log(class_.getSimpleName(), msg);
	}

	/**
	 * Logs an error from a Throwable.
	 * <p>
	 * Extracts the stack trace from the provided Throwable and logs it at
	 * ERROR level to both Logcat and file storage.
	 *
	 * @param error The exception or error to log
	 */
	public void e(@NonNull Throwable error) {
		if (!isDebuggingMode) return;
		String msg = toString(error);
		Log.e(class_.getSimpleName(), msg);
		FileLogger.log(class_.getSimpleName(), msg);
	}

	/**
	 * Logs an error message with associated Throwable.
	 * <p>
	 * Combines a custom error message with a Throwable's stack trace for
	 * comprehensive error logging. Both components are written to Logcat
	 * and the file logger.
	 *
	 * @param message   Custom error description
	 * @param throwable The associated exception or error
	 */
	public void e(@NonNull String message, @NonNull Throwable throwable) {
		if (!isDebuggingMode) return;
		String msg = toMessage(message);
		Log.e(class_.getSimpleName(), msg, throwable);
		FileLogger.log(class_.getSimpleName(), msg + "\n" + toString(throwable));
	}

	/**
	 * Logs a debug message.
	 * <p>
	 * Outputs debugging information at DEBUG level. Useful for tracking
	 * application flow and variable states during development.
	 *
	 * @param message The debug message to log
	 */
	public void d(@NonNull String message) {
		if (!isDebuggingMode) return;
		String msg = toMessage(message);
		Log.d(class_.getSimpleName(), msg);
		FileLogger.log(class_.getSimpleName(), msg);
	}

	/**
	 * Logs a debug message from a Throwable.
	 * <p>
	 * Logs exception information at DEBUG level, typically used for
	 * non-critical errors or expected exceptions during debugging.
	 *
	 * @param err The Throwable to log at debug level
	 */
	public void d(@NonNull Throwable err) {
		if (!isDebuggingMode) return;
		String msg = toString(err);
		Log.d(class_.getSimpleName(), msg);
		FileLogger.log(class_.getSimpleName(), msg);
	}

	/**
	 * Logs a debug message with method context.
	 * <p>
	 * Convenience method that prepends the method name to the debug message
	 * for better context in logs.
	 *
	 * @param methodName The name of the method where logging occurs
	 * @param message    The debug message content
	 */
	public void d(@NonNull String methodName, @NonNull String message) {
		d(methodName + message);
	}

	/**
	 * Logs an informational message.
	 * <p>
	 * Outputs general application information at INFO level, useful for
	 * tracking normal application operation and significant events.
	 *
	 * @param message The informational message to log
	 */
	public void i(@NonNull String message) {
		if (!isDebuggingMode) return;
		String msg = toMessage(message);
		Log.i(class_.getSimpleName(), msg);
		FileLogger.log(class_.getSimpleName(), msg);
	}

	/**
	 * Logs an informational message from a Throwable.
	 * <p>
	 * Logs exception information at INFO level for non-error conditions
	 * that are worth noting during normal operation.
	 *
	 * @param err The Throwable to log at info level
	 */
	public void i(@NonNull Throwable err) {
		if (!isDebuggingMode) return;
		String msg = toString(err);
		Log.i(class_.getSimpleName(), msg);
		FileLogger.log(class_.getSimpleName(), msg);
	}

	/**
	 * Logs an informational message with method context.
	 *
	 * @param methodName The name of the method where logging occurs
	 * @param message    The informational message content
	 */
	public void i(@NonNull String methodName, @NonNull String message) {
		i(methodName + message);
	}

	/**
	 * Logs a verbose message.
	 * <p>
	 * Outputs detailed debugging information at VERBOSE level, typically
	 * used for high-frequency logs or very detailed tracing.
	 *
	 * @param message The verbose message to log
	 */
	public void v(@NonNull String message) {
		if (!isDebuggingMode) return;
		String msg = toMessage(message);
		Log.v(class_.getSimpleName(), msg);
		FileLogger.log(class_.getSimpleName(), msg);
	}

	/**
	 * Logs a verbose message from a Throwable.
	 *
	 * @param err The Throwable to log at verbose level
	 */
	public void v(@NonNull Throwable err) {
		if (!isDebuggingMode) return;
		String msg = toString(err);
		Log.v(class_.getSimpleName(), msg);
		FileLogger.log(class_.getSimpleName(), msg);
	}

	/**
	 * Logs a verbose message with method context.
	 *
	 * @param methodName The name of the method where logging occurs
	 * @param message    The verbose message content
	 */
	public void v(@NonNull String methodName, @NonNull String message) {
		v(methodName + message);
	}

	/**
	 * Logs a warning message.
	 * <p>
	 * Outputs warning information at WARN level for conditions that are
	 * not errors but may indicate potential problems or unexpected states.
	 *
	 * @param message The warning message to log
	 */
	public void w(@NonNull String message) {
		if (!isDebuggingMode) return;
		String msg = toMessage(message);
		Log.w(class_.getSimpleName(), msg);
		FileLogger.log(class_.getSimpleName(), msg);
	}

	/**
	 * Logs a warning message with associated Throwable.
	 * <p>
	 * Combines a warning message with exception information for conditions
	 * that are recoverable but should be noted.
	 *
	 * @param message   The warning message
	 * @param throwable The associated Throwable
	 */
	public void w(@NonNull String message, @NonNull Throwable throwable) {
		if (!isDebuggingMode) return;
		String msg = toMessage(message);
		Log.w(class_.getSimpleName(), msg, throwable);
		FileLogger.log(class_.getSimpleName(), msg + "\n" + toString(throwable));
	}

	/**
	 * Safely converts a message string, handling null values.
	 * <p>
	 * Internal utility method that ensures message strings are never null
	 * when passed to logging methods. If null is provided, returns a
	 * placeholder string indicating the error.
	 *
	 * @param message The message to convert
	 * @return The original message or a null placeholder string
	 */
	private String toMessage(String message) {
		return message == null ? "Error Message = NULL!!" : message;
	}
}