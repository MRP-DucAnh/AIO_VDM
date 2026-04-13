package app.core.engines.downloader

import app.core.*
import com.aio.*
import lib.files.FileSizeFormatter.humanReadableSizeOf
import lib.networks.DownloaderUtils.getHumanReadableSpeed
import lib.process.*
import lib.texts.CommonTextUtils.getText

/**
 * Utility class for generating HTML-formatted download information strings.
 * * This object serves as a diagnostic tool for the AIO engine, converting internal
 * [AIODownload] state into a structured, user-readable HTML document.
 * It is primarily used to populate "Download Details" dialogs or debugging views
 * within a WebView.
 *
 * It handles two distinct presentation modes:
 * 1. **Video/Advanced Mode**: Tailored for yt-dlp based downloads, showing stream info and maxing status.
 * 2. **Standard Mode**: Optimized for generic file downloads, providing detailed multi-thread segment tracking.
 *
 * @author Shiba
 */
object DownloadInfoHTMLUtils {

	/**
	 * Constructs a comprehensive HTML diagnostic report for a specific download.
	 * * The generated string includes CSS-less HTML markers for:
	 * - Identity (ID, Filename)
	 * - Physical Storage (Directory, Category)
	 * - Network Metadata (Source URL, Referrer, User-Agent)
	 * - Real-time Metrics (Speed, ETA, Time Spent)
	 * - Protocol Capabilities (Resume, Multi-threading, Checksum)
	 * - Segment Debugging (Individual part progress for multi-threaded tasks)
	 *
	 * @param ddm The active [AIODownload] instance to inspect.
	 * @return A complete HTML document string (wrapped in `<html><body>` tags).
	 */
	@JvmStatic
	suspend fun buildDownloadInfoHtmlString(ddm: AIODownload): String {
		return withIOContext {

			val context = AIOApp.INSTANCE
			val stringBuilder = StringBuilder()

			// Branching logic: Special handling for Video Metadata (yt-dlp)
			if (ddm.videoInfo != null && ddm.videoFormat != null) {
				stringBuilder.append("<html><body>")
					.append(context.getString(R.string.title_b_download_id_b_br, "${ddm.id}"))
					.append(context.getString(R.string.title_b_file_name_b_br, ddm.fileName))
					.append(context.getString(R.string.title_b_progress_percentage_b_br, "${ddm.progressPercentage}%"))

				// Display yt-dlp specific processing status (e.g., "Merging fragments")
				if (ddm.downloadStatus != DownloadStatus.COMPLETE) {
					stringBuilder.append(context.getString(R.string.title_b_download_stream_info_b_br, ddm.ytdlpStatusInfo))
				} else {
					stringBuilder.append(context.getString(R.string.title_b_file_size_b_br, ddm.downloadedByteInFormat))
						.append(context.getString(R.string.title_b_downloaded_bytes_b_bytes_br, "${ddm.downloadedByte}"))
						.append(context.getString(R.string.title_b_downloaded_bytes_in_format_b_br, ddm.downloadedByteInFormat))
				}

				stringBuilder
					.append("---------------------------------<br>")
					.append(context.getString(R.string.title_b_file_category_b_br, ddm.fileCategoryName))
					.append(context.getString(R.string.title_b_file_directory_b_br, ddm.fileDirectory))
					.append(context.getString(R.string.title_b_file_url_b_br, buildUrlTag(ddm.fileURL)))
					.append(context.getString(R.string.title_b_download_webpage_b_br, buildUrlTag(ddm.siteReferrer)))
					.append("---------------------------------<br>")
					.append(context.getString(R.string.title_b_download_status_info_b_br, ddm.downloadStatusInfo))
					.append(context.getString(R.string.title_b_download_started_b_br, ddm.startTimeDateInFormat))
					.append(context.getString(R.string.title_b_download_last_modified_b_br, ddm.lastModifiedTimeDateInFormat))
					.append(context.getString(R.string.title_b_time_spent_b_br, ddm.timeSpentInFormat))
					.append("---------------------------------<br>")
					.append(context.getString(R.string.title_b_is_file_url_expired_b_br, "${ddm.isFileUrlExpired}"))
					.append(context.getString(R.string.title_b_is_failed_to_access_file_b_br, "${ddm.fileAccessFailed}"))
					.append(context.getString(R.string.title_b_is_waiting_for_network_b_br, "${ddm.isWaitingForNetwork}"))
					.append("---------------------------------<br>")
					.append(context.getString(R.string.title_b_checksum_validation_b_br, ifChecksumVerified(ddm)))
					.append(context.getString(R.string.title_b_multi_thread_support_b_br, isMultithreadingSupported(ddm)))
					.append(context.getString(R.string.title_b_resume_support_b_br, isResumeSupported(ddm)))
					.append(context.getString(R.string.title_b_unknown_file_size_b_br, isUnknownFile(ddm)))
					.append(context.getString(R.string.title_b_connection_retry_counts_b_times_br, "${ddm.totalTrackedConnectionRetries}"))
					.append("---------------------------------<br>")
					.append(context.getString(R.string.title_b_default_parallel_connections_b_br, "${defaultParallelConnection(ddm)}"))
					.append(context.getString(R.string.title_b_default_thread_connections_b_br, "${defaultNumOfThreadsAssigned(ddm)}"))
					.append(context.getString(R.string.title_b_buffer_size_b_br, getBufferSize(ddm)))
					.append(context.getString(R.string.title_b_http_proxy_b_br, getHttpProxy(ddm)))
					.append(context.getString(R.string.title_b_download_speed_limiter_b_br, formatNetworkSpeedLimit(ddm)))
					.append(context.getString(R.string.title_b_user_agent_b_br, ddm.config.downloadHttpUserAgent))
			} else {
				// Standard File Download logic
				stringBuilder.append("<html><body>")
					.append(context.getString(R.string.title_b_download_id_b_br, "${ddm.id}"))
					.append(context.getString(R.string.title_b_file_name_b_br, ddm.fileName))
					.append(context.getString(R.string.title_b_file_size_b_br, ddm.fileSizeInFormat))
					.append(context.getString(R.string.title_b_downloaded_bytes_b_bytes_br, "${ddm.downloadedByte}"))
					.append(context.getString(R.string.title_b_downloaded_bytes_in_format_b_br, ddm.downloadedByteInFormat))
					.append(context.getString(R.string.title_b_progress_percentage_b_br, "${ddm.progressPercentage}%"))
					.append("---------------------------------<br>")
					.append(context.getString(R.string.title_b_file_category_b_br, ddm.fileCategoryName))
					.append(context.getString(R.string.title_b_file_directory_b_br, ddm.fileDirectory))
					.append(context.getString(R.string.title_b_file_url_b_br, buildUrlTag(ddm.fileURL)))
					.append(context.getString(R.string.title_b_download_webpage_b_br, buildUrlTag(ddm.siteReferrer)))
					.append("---------------------------------<br>")
					.append(context.getString(R.string.title_b_download_status_info_b_br, ddm.downloadStatusInfo))
					.append(context.getString(R.string.title_b_download_started_b_br, ddm.startTimeDateInFormat))
					.append(context.getString(R.string.title_b_download_last_modified_b_br, ddm.lastModifiedTimeDateInFormat))
					.append(context.getString(R.string.title_b_time_spent_b_br, ddm.timeSpentInFormat))
					.append(context.getString(R.string.title_b_remaining_time_b_br, ddm.remainingTimeInFormat))
					.append("---------------------------------<br>")
					.append(context.getString(R.string.title_b_is_file_url_expired_b_br, "${ddm.isFileUrlExpired}"))
					.append(context.getString(R.string.title_b_is_failed_to_access_file_b_br, "${ddm.fileAccessFailed}"))
					.append(context.getString(R.string.title_b_is_waiting_for_network_b_br, "${ddm.isWaitingForNetwork}"))
					.append("---------------------------------<br>")
					.append(context.getString(R.string.title_b_realtime_network_speed_b_br, getRealtimeNetworkSpeed(ddm)))
					.append(context.getString(R.string.title_b_average_network_speed_b_br, ddm.averageSpeedInFormat))
					.append(context.getString(R.string.title_b_max_network_speed_b_br, ddm.maxSpeedInFormat))
					.append("---------------------------------<br>")
					.append(context.getString(R.string.title_b_checksum_validation_b_br, ifChecksumVerified(ddm)))
					.append(context.getString(R.string.title_b_multi_thread_support_b_br, isMultithreadingSupported(ddm)))
					.append(context.getString(R.string.title_b_resume_support_b_br, isResumeSupported(ddm)))
					.append(context.getString(R.string.title_b_unknown_file_size_b_br, isUnknownFile(ddm)))
					.append(context.getString(R.string.title_b_connection_retry_counts_b_times_br, "${ddm.totalTrackedConnectionRetries}"))
					.append("---------------------------------<br>")
					.append(context.getString(R.string.title_b_default_parallel_connections_b_br, "${defaultParallelConnection(ddm)}"))
					.append(context.getString(R.string.title_b_default_thread_connections_b_br, "${defaultNumOfThreadsAssigned(ddm)}"))
					.append(context.getString(R.string.title_b_buffer_size_b_br, getBufferSize(ddm)))
					.append(context.getString(R.string.title_b_http_proxy_b_br, getHttpProxy(ddm)))
					.append(context.getString(R.string.title_b_download_speed_limiter_b_br, formatNetworkSpeedLimit(ddm)))
					.append(context.getString(R.string.title_b_user_agent_b_br, ddm.config.downloadHttpUserAgent))
					.append(context.getString(R.string.title_b_est_part_chunk_size_b_br, estPartChunkSize(ddm)))
					.append("---------------------------------<br>")
					.append(context.getString(R.string.title_b_part_progress_percentages_b_br, getDownloadPartPercentage(ddm)))
					.append(context.getString(R.string.title_b_parts_downloaded_bytes_b_br, getPartDownloadedByte(ddm)))
					.append("</body></html>")
			}
			return@withIOContext stringBuilder.toString()
		}
	}

	/**
	 * Calculates the estimated byte size of each individual thread's workload.
	 * * @param downloadModel The model containing total [AIODownload.fileSize] and
	 * [app.core.engines.settings.AIOSettings.downloadDefaultThreadConnections].
	 * @return A human-readable size string (e.g., "5.2 MB").
	 */
	private fun estPartChunkSize(downloadModel: AIODownload): String {
		return humanReadableSizeOf(
			downloadModel.fileSize /
				downloadModel.config.downloadDefaultThreadConnections
		)
	}

	/**
	 * Converts the raw network speed limit setting into a displayable string.
	 * * @param downloadModel The model containing the max network speed setting.
	 * @return Formatted speed limit (e.g., "1.0 MB/s") or "Unlimited".
	 */
	private fun formatNetworkSpeedLimit(downloadModel: AIODownload): String {
		return getHumanReadableSpeed(
			downloadModel.config.downloadMaxNetworkSpeed.toDouble()
		)
	}

	/**
	 * Generates a detailed breakdown of bytes downloaded by each individual thread.
	 * * Output format: `[Part Index = Formatted Size]`
	 * * @param downloadModel The model containing the
	 *   [AIODownload.partsDownloadedByte] array.
	 * @return A multi-line HTML string representing segment bytes.
	 */
	private fun getPartDownloadedByte(downloadModel: AIODownload): String {
		val sb = StringBuilder()
		sb.append("<br>")
		downloadModel.partsDownloadedByte.forEachIndexed { index, downloadedByte ->
			sb.append("[${index} = ${humanReadableSizeOf(downloadedByte)}]<br>")
		}
		return sb.toString()
	}

	/**
	 * Generates a detailed breakdown of completion percentage for each thread.
	 * * Output format: `[Part Index = Percentage%]`
	 * * @param downloadModel The model containing the
	 *  [AIODownload.partProgressPercentage] array.
	 * @return A multi-line HTML string representing segment progress.
	 */
	private fun getDownloadPartPercentage(downloadModel: AIODownload): String {
		val sb = StringBuilder()
		sb.append("<br>")
		downloadModel.partProgressPercentage.forEachIndexed { index, percent ->
			sb.append("[${index} = ${percent}%]<br>")
		}
		return sb.toString()
	}

	/**
	 * Resolves the HTTP proxy configuration.
	 * * @param downloadModel The model containing proxy server address.
	 * @return The proxy address string or a localized "Not Configured" message.
	 */
	private fun getHttpProxy(downloadModel: AIODownload): String {
		return downloadModel.config.downloadHttpProxyServer.ifEmpty {
			getText(R.string.title_not_configured)
		}
	}

	/**
	 * Retrieves the buffer size used by the engine for I/O operations.
	 * * @param downloadModel The model containing the buffer size configuration.
	 * @return Formatted buffer size (e.g., "64 KB").
	 */
	private fun getBufferSize(downloadModel: AIODownload): String {
		return humanReadableSizeOf(
			downloadModel.config.downloadBufferSize.toDouble()
		)
	}

	/**
	 * Returns the maximum number of threads assigned to split the download.
	 */
	private fun defaultNumOfThreadsAssigned(downloadModel: AIODownload): Int {
		return downloadModel.config.downloadDefaultThreadConnections
	}

	/**
	 * Returns the global limit for concurrent parallel downloads.
	 */
	private fun defaultParallelConnection(downloadModel: AIODownload): Int {
		return downloadModel.config.downloadDefaultParallelConnections
	}

	/**
	 * Validates if the file size was provided by the server headers.
	 * * @return Localized "Yes" if size is unknown, "No" otherwise.
	 */
	private fun isUnknownFile(downloadModel: AIODownload): String {
		return if (downloadModel.isUnknownFileSize)
			getText(R.string.title_yes) else getText(R.string.title_no)
	}

	/**
	 * Validates if the download supports the HTTP Range header for resuming.
	 * * @return Localized "Yes" or "No".
	 */
	private fun isResumeSupported(downloadModel: AIODownload): String {
		return if (downloadModel.isResumeSupported)
			getText(R.string.title_yes) else getText(R.string.title_no)
	}

	/**
	 * Validates if the task is eligible for multi-threaded segmentation.
	 * * @return Localized "Yes" or "No".
	 */
	private fun isMultithreadingSupported(downloadModel: AIODownload): String {
		return if (downloadModel.isMultiThreadSupported)
			getText(R.string.title_yes) else getText(R.string.title_no)
	}

	/**
	 * Determines if post-download checksum validation is enabled in user settings.
	 * * @return Localized "Performed" or "Not Performed".
	 */
	private fun ifChecksumVerified(downloadModel: AIODownload): String {
		return if (downloadModel.config.downloadVerifyChecksum)
			getText(R.string.title_performed) else getText(R.string.title_not_performed)
	}

	/**
	 * Retrieves current network speed, returning a placeholder if the task is idle.
	 * * @return Formatted speed or "--" if not running.
	 */
	private fun getRealtimeNetworkSpeed(downloadModel: AIODownload): String {
		return if (!downloadModel.isRunning) "--" else downloadModel.realtimeSpeedInFormat
	}

	/**
	 * Generates a clickable HTML hyperlink tag.
	 * * @param url The target destination URL.
	 * @return An `<a>` tag string with localized "Click here" text.
	 */
	private fun buildUrlTag(url: String): String {
		return "<a href=\"$url\">${getText(R.string.title_click_here_to_open)}</a>"
	}
}