package app.core.engines.video_parser.parsers

import io.objectbox.annotation.*
import java.io.*

/**
 * Represents a specific media format or stream available for a video.
 * * This entity stores technical metadata about individual streams (e.g., different
 * resolutions or codecs) associated with a parent video. It is persisted using
 * ObjectBox and supports serialization for cross-component data passing.
 *
 * @property id Unique identifier for the ObjectBox entity. Defaults to 0 for new objects.
 * @property parentVideoInfoId Foreign key reference to the [AIOVideoInfo] this format belongs to.
 * @property downloadId Reference ID used to track this specific format during the download process.
 * @property isFromSocialMedia Flag indicating if the video source is a social media platform.
 * @property formatId The raw identifier provided by the parser (e.g., '137' for 1080p on YouTube).
 * @property formatExtension The file extension for this format (e.g., "mp4", "webm").
 * @property formatResolution The display resolution (e.g., "1920x1080").
 * @property formatFileSize Human-readable string of the file size (e.g., "25.4 MB").
 * @property formatVcodec The video codec used (e.g., "avc1", "vp9").
 * @property formatAcodec The audio codec used (e.g., "mp4a", "opus").
 * @property formatTBR The Total Bit Rate (TBR) value for the stream quality.
 * @property formatProtocol The delivery protocol (e.g., "https", "m3u8_native").
 * @property formatStreamingUrl The direct source URL used to stream or download the media.
 */
@Entity
data class AIOVideoFormat(
	@Id
	var id: Long = 0L,
	var parentVideoInfoId: Long = -1L,
	var downloadId: Long = -1L,
	var isFromSocialMedia: Boolean = false,
	var formatId: String = "",
	var formatExtension: String = "",
	var formatResolution: String = "",
	var formatFileSize: String = "",
	var formatVcodec: String = "",
	var formatAcodec: String = "",
	var formatTBR: String = "",
	var formatProtocol: String = "",
	var formatStreamingUrl: String = ""
) : Serializable

/**
 * Represents the comprehensive metadata for a video extracted by the parser.
 * * This is the primary data structure returned after parsing a URL. It contains
 * general video info (title, thumbnail, duration) and a list of available
 * [AIOVideoFormat] options.
 *
 * @property id Unique identifier for the ObjectBox entity.
 * @property downloadId The unique ID assigned to the download task associated with this video.
 * @property videoTitle The title of the video retrieved from the source page.
 * @property videoThumbnailUrl The URL pointing to the video's preview image.
 * @property videoThumbnailByReferer Whether the thumbnail requires the referer header to be loaded.
 * @property videoDescription A brief summary or description text of the video content.
 * @property videoUrlReferer The original URL or referer used to bypass hotlinking protections.
 * @property videoUrl The primary source URL of the video page.
 * @property videoFormats A list of all available [AIOVideoFormat] objects parsed for this video.
 * @property numberOfVideoFormats A helper count of the entries in [videoFormats].
 * @property videoCookie Session cookies required to authenticate or access the video stream.
 * @property videoDuration The length of the video in milliseconds.
 * @property videoCookieTempPath File path to a temporary file storing session cookies for external tools (e.g., yt-dlp).
 */
@Entity
data class AIOVideoInfo(
	@Id
	var id: Long = 0L,
	var downloadId: Long = -1L,
	var videoTitle: String? = null,
	var videoThumbnailUrl: String? = null,
	var videoThumbnailByReferer: Boolean = false,
	var videoDescription: String? = null,
	var videoUrlReferer: String? = null,
	var videoUrl: String = "",
	var videoFormats: List<AIOVideoFormat> = emptyList(),
	var numberOfVideoFormats: Int = 0,
	var videoCookie: String? = "",
	var videoDuration: Long = 0L,
	var videoCookieTempPath: String = ""
) : Serializable