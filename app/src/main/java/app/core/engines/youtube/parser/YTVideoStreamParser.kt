package app.core.engines.youtube.parser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import lib.process.LogHelperUtils
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * A utility object responsible for parsing and extracting metadata from YouTube video streams.
 * It leverages the NewPipe Extractor library to retrieve video details such as titles,
 * thumbnails, and stream configurations, featuring built-in retry logic to handle
 * network instabilities and anti-bot challenges.
 */
object YTVideoStreamParser {

	/**
	 * Internal logger instance used for tracking the metadata extraction lifecycle,
	 * debugging network attempts, and recording parsing errors or anti-bot triggers.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Fetches the URL of the first available thumbnail for a given YouTube video.
	 *
	 * @param youtubeVideoUrl The full URL of the YouTube video.
	 * @param onErrorFound A callback invoked if an exception occurs during the extraction process.
	 * @return The thumbnail URL as a [String], or `null` if no thumbnail is found or an error occurs.
	 */
	@JvmStatic
	suspend fun getThumbnail(
		youtubeVideoUrl: String,
		onErrorFound: (Exception) -> Unit = {}
	): String? = withContext(Dispatchers.IO) {
		fetchStreamInfoInternal(youtubeVideoUrl, onErrorFound)
			?.thumbnails
			?.firstOrNull()
			?.url
	}

	/**
	 * Fetches the title of a YouTube video using its URL.
	 *
	 * This method retrieves the video name from the [StreamInfo] metadata. It runs
	 * on the [Dispatchers.IO] context and utilizes internal retry logic to handle
	 * potential network or parsing issues.
	 *
	 * @param youtubeVideoUrl The full URL of the YouTube video.
	 * @param onErrorFound A callback invoked if an exception occurs during the extraction process.
	 * @return The title of the video as a [String], or `null` if the title could not be retrieved.
	 */
	@JvmStatic
	suspend fun getTitle(
		youtubeVideoUrl: String,
		onErrorFound: (Exception) -> Unit = {}
	): String? = withContext(Dispatchers.IO) {
		fetchStreamInfoInternal(youtubeVideoUrl, onErrorFound)
			?.name
	}

	/**
	 * Fetches the complete [StreamInfo] for a given YouTube video URL.
	 *
	 * This method retrieves detailed metadata including available video streams,
	 * audio streams, and captions. It uses internal retry logic to handle potential
	 * network issues or YouTube anti-bot measures.
	 *
	 * @param youtubeVideoUrl The full URL of the YouTube video to parse.
	 * @param onErrorFound An optional callback invoked if an exception occurs during extraction.
	 * @return The [StreamInfo] object containing all video metadata, or `null` if the extraction fails.
	 */
	@JvmStatic
	suspend fun getStreamInfo(
		youtubeVideoUrl: String,
		onErrorFound: (Exception) -> Unit = {}
	): StreamInfo? = withContext(Dispatchers.IO) {
		fetchStreamInfoInternal(youtubeVideoUrl, onErrorFound)
	}

	/**
	 * Internal centralized method to fetch [StreamInfo] with retry logic and specialized
	 * handling for YouTube's "page needs to be reloaded" (Anti-Bot) errors.
	 *
	 * This function performs up to three attempts to retrieve video metadata. If a
	 * reload-required error is detected, it implements a longer delay between retries.
	 *
	 * @param url The full URL of the YouTube video.
	 * @param onErrorFound A callback invoked if an exception persists after all retry attempts.
	 * @return The [StreamInfo] containing metadata and stream links, or `null` if
	 * extraction fails or no valid streams are found.
	 */
	private suspend fun fetchStreamInfoInternal(
		videoUrl: String,
		onErrorFound: (Exception) -> Unit
	): StreamInfo? = withContext(Dispatchers.IO) {
		logger.d("Fetching stream info: $videoUrl")
		repeat(3) { attempt ->
			try {
				val info = StreamInfo.getInfo(YouTube, videoUrl)
				if (info.videoOnlyStreams.isNotEmpty() || info.videoStreams.isNotEmpty()) {
					return@withContext info
				} else {
					logger.w("Empty streams found on attempt ${attempt + 1}")
				}
			} catch (error: Exception) {
				val errorMessage = error.message ?: ""
				val isReloadRequired = errorMessage.contains("page needs to be reloaded", ignoreCase = true)
				if (isReloadRequired) {
					val logMessage = "YouTube Anti-Bot triggered (Reload Required) on attempt"
					logger.e("$logMessage ${attempt + 1}", error)
				} else {
					logger.e("Error fetching stream info (attempt ${attempt + 1}/3): $errorMessage", error)
				}

				if (attempt == 2) {
					onErrorFound(error)
				} else {
					val waitTime = if (isReloadRequired) 1500L else 500L
					delay(waitTime)
				}
			}
		}
		null
	}

}