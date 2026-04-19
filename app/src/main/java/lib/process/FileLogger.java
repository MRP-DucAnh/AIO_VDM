package lib.process;

import static java.util.Locale.getDefault;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static lib.texts.CommonTextUtils.removeDuplicateSlashes;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService;

import app.core.AIOApp;

public final class FileLogger {

	private static final ExecutorService EXECUTOR = newSingleThreadExecutor();
	private static PrintWriter writer;

	private static synchronized PrintWriter getWriter() {
		if (writer == null) {
			try {
				String aioConfigDirPath = AIOApp.Companion
					.getAIO_DEFAULT_DOWNLOAD_PATH() + "/.configs";
				aioConfigDirPath = removeDuplicateSlashes(aioConfigDirPath);

				File aioConfigDir = new File(aioConfigDirPath);
				if (!aioConfigDir.exists() && !aioConfigDir.mkdirs()) {
					Log.e("FileLogger", "Failed to create log directory");
					return null;
				}

				String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", getDefault())
					.format(System.currentTimeMillis());
				String fileName = ".aio_crash_log_stream" + timeStamp + ".txt";
				File crashLogFile = new File(aioConfigDir, fileName);

				writer = new PrintWriter(new FileWriter(crashLogFile, true), true);

			} catch (IOException error) {
				Log.e("FileLogger", "Failed to open log file", error);
			}
		}
		return writer;
	}

	public static void log(String tag, String message) {
		EXECUTOR.execute(() -> {
			PrintWriter printWriter = getWriter();
			if (printWriter != null) {
				printWriter.println(System.currentTimeMillis()
					+ " [" + tag + "] "
					+ message);
			}
		});
	}

	public static void shutdown() {
		EXECUTOR.shutdown();
		if (writer != null) writer.close();
	}
}