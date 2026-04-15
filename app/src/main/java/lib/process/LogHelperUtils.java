package lib.process;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;

import app.core.AIOApp;

public final class LogHelperUtils implements Serializable {

	private final Class<?> class_;
	private final boolean isDebuggingMode;

	private LogHelperUtils(Class<?> class_) {
		this.class_ = class_;
		this.isDebuggingMode = AIOApp.Companion.getIS_DEBUG_MODE_ON();
	}

	@NonNull
	public static LogHelperUtils from(@NonNull Class<?> class_) {
		return new LogHelperUtils(class_);
	}

	@NonNull
	public static String toString(@NonNull Throwable throwable) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw, true);
		throwable.printStackTrace(pw);
		return sw.toString();
	}

	public void e(@NonNull String message) {
		if (!isDebuggingMode) return;
		String msg = toMessage(message);
		Log.e(class_.getSimpleName(), msg);
		FileLogger.log(class_.getSimpleName(), msg);
	}

	public void e(@NonNull Throwable error) {
		if (!isDebuggingMode) return;
		String msg = toString(error);
		Log.e(class_.getSimpleName(), msg);
		FileLogger.log(class_.getSimpleName(), msg);
	}

	public void e(@NonNull String message, @NonNull Throwable throwable) {
		if (!isDebuggingMode) return;
		String msg = toMessage(message);
		Log.e(class_.getSimpleName(), msg, throwable);
		FileLogger.log(class_.getSimpleName(), msg + "\n" + toString(throwable));
	}

	public void d(@NonNull String message) {
		if (!isDebuggingMode) return;
		String msg = toMessage(message);
		Log.d(class_.getSimpleName(), msg);
		FileLogger.log(class_.getSimpleName(), msg);
	}

	public void d(@NonNull Throwable err) {
		if (!isDebuggingMode) return;
		String msg = toString(err);
		Log.d(class_.getSimpleName(), msg);
		FileLogger.log(class_.getSimpleName(), msg);
	}

	public void d(@NonNull String methodName, @NonNull String message) {
		d(methodName + message);
	}

	public void i(@NonNull String message) {
		if (!isDebuggingMode) return;
		String msg = toMessage(message);
		Log.i(class_.getSimpleName(), msg);
		FileLogger.log(class_.getSimpleName(), msg);
	}

	public void i(@NonNull Throwable err) {
		if (!isDebuggingMode) return;
		String msg = toString(err);
		Log.i(class_.getSimpleName(), msg);
		FileLogger.log(class_.getSimpleName(), msg);
	}

	public void i(@NonNull String methodName, @NonNull String message) {
		i(methodName + message);
	}

	public void v(@NonNull String message) {
		if (!isDebuggingMode) return;
		String msg = toMessage(message);
		Log.v(class_.getSimpleName(), msg);
		FileLogger.log(class_.getSimpleName(), msg);
	}

	public void v(@NonNull Throwable err) {
		if (!isDebuggingMode) return;
		String msg = toString(err);
		Log.v(class_.getSimpleName(), msg);
		FileLogger.log(class_.getSimpleName(), msg);
	}

	public void v(@NonNull String methodName, @NonNull String message) {
		v(methodName + message);
	}

	public void w(@NonNull String message) {
		if (!isDebuggingMode) return;
		String msg = toMessage(message);
		Log.w(class_.getSimpleName(), msg);
		FileLogger.log(class_.getSimpleName(), msg);
	}

	public void w(@NonNull String message, @NonNull Throwable throwable) {
		if (!isDebuggingMode) return;
		String msg = toMessage(message);
		Log.w(class_.getSimpleName(), msg, throwable);
		FileLogger.log(class_.getSimpleName(), msg + "\n" + toString(throwable));
	}

	private String toMessage(String message) {
		return message == null ? "Error Message = NULL!!" : message;
	}
}