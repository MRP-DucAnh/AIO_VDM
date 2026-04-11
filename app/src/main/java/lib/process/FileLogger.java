package lib.process;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

import static lib.texts.CommonTextUtils.removeDuplicateSlashes;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

import app.core.AIOApp;
import lib.texts.CommonTextUtils;

/**
 * A thread-safe file logging utility for persistent log storage.
 * <p>
 * This class provides a centralized mechanism for writing application logs to a
 * dedicated file on device storage. It uses a single-threaded executor to ensure
 * log writes are sequential and thread-safe, preventing race conditions when
 * multiple threads attempt to log simultaneously. Logs are stored in a timestamped
 * file within the application's configuration directory for later analysis.
 */
public final class FileLogger {

	/**
	 * Single-threaded executor for sequential log write operations.
	 * <p>
	 * This executor ensures that all log writes are processed in the order they
	 * are submitted, preventing concurrent file access issues. Using a single
	 * thread eliminates the need for complex synchronization around file I/O
	 * operations while maintaining reasonable performance for logging purposes.
	 */
	private static final ExecutorService EXECUTOR = newSingleThreadExecutor();

	/**
	 * Shared PrintWriter instance for log file output.
	 * <p>
	 * This static writer is lazily initialized and shared across all logging
	 * operations. The synchronized getter method ensures thread-safe initialization.
	 * The writer is configured with auto-flush enabled, ensuring log messages are
	 * immediately written to disk rather than buffered in memory.
	 */
	private static PrintWriter writer;

	/**
	 * Thread-safe accessor for the log file PrintWriter.
	 * <p>
	 * This method implements lazy initialization of the log file writer. It creates
	 * a timestamped log file in the application's configuration directory the first
	 * time it's called. If the directory doesn't exist, it attempts to create it.
	 * Returns null if file creation fails, causing log messages to be silently
	 * discarded.
	 *
	 * @return PrintWriter instance for log file output, or null if initialization failed
	 */
	private static synchronized PrintWriter getWriter() {
		// Initialize writer on first access
		if (writer == null) {
			try {
				// Define configuration directory path for log storage
				String aioConfigDirPath = AIOApp.Companion.getAIO_DEFAULT_DOWNLOAD_PATH() + "/.configs";
				aioConfigDirPath = removeDuplicateSlashes(aioConfigDirPath);

				// Create directory structure if it doesn't exist
				File aioConfigDir = new File(aioConfigDirPath);
				if (!aioConfigDir.exists() && !aioConfigDir.mkdirs()) {
					Log.e("FileLogger", "Failed to create log directory");
					return null;
				}

				// Generate timestamp for unique filename
				String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
					.format(System.currentTimeMillis());
				String fileName = ".aio_crash_log_stream" + timeStamp + ".txt";
				File crashLogFile = new File(aioConfigDir, fileName);

				// Initialize PrintWriter with auto-flush enabled
				writer = new PrintWriter(new FileWriter(crashLogFile, true), true);

			} catch (IOException e) {
				Log.e("FileLogger", "Failed to open log file", e);
			}
		}
		return writer;
	}

	/**
	 * Asynchronously writes a log entry to the log file.
	 * <p>
	 * This method queues log messages for background file writing using the
	 * single-threaded executor. Each log entry includes a timestamp, tag, and
	 * message for comprehensive debugging. The method returns immediately without
	 * waiting for the write operation to complete, ensuring minimal impact on
	 * application performance.
	 *
	 * @param tag     Identifies the source of the log message (typically class name)
	 * @param message The actual log content to record
	 */
	public static void log(String tag, String message) {
		// Submit log write task to executor for asynchronous processing
		EXECUTOR.execute(() -> {
			PrintWriter printWriter = getWriter();
			if (printWriter != null) {
				// Format: timestamp [tag] message
				printWriter.println(System.currentTimeMillis()
					+ " [" + tag + "] "
					+ message);
			}
		});
	}

	/**
	 * Gracefully shuts down the logging system.
	 * <p>
	 * This method should be called during application termination to ensure all
	 * pending log writes are completed and resources are properly released. It
	 * shuts down the executor service (allowing queued tasks to complete) and
	 * closes the log file writer. Once called, subsequent log() calls will have
	 * no effect.
	 */
	public static void shutdown() {
		// Shutdown executor after processing pending tasks
		EXECUTOR.shutdown();

		// Close file writer to release system resources
		if (writer != null) writer.close();
	}
}