package app.core.engines.downloader

import android.net.*
import androidx.core.net.*
import androidx.documentfile.provider.*
import app.core.*
import app.core.AIOApp.Companion.INSTANCE
import app.core.AIOApp.Companion.aioSettings
import app.core.AIOApp.Companion.internalDataFolder
import app.core.engines.downloader.AIODownload.Companion.TEMP_EXTENSION
import app.core.engines.downloader.AIODownloadsRepo.assembleAssociatedRecords
import app.core.engines.downloader.AIODownloadsRepo.deleteAssociatedRecords
import app.core.engines.downloader.AIODownloadsRepo.storeDownloadRecord
import app.core.engines.downloader.DownloadStatus.CLOSE
import app.core.engines.downloader.DownloadStatus.COMPLETE
import app.core.engines.downloader.DownloadStatus.DOWNLOADING
import app.core.engines.settings.*
import app.core.engines.settings.AIOSettings.Companion.PRIVATE_FOLDER
import app.core.engines.settings.AIOSettings.Companion.SYSTEM_GALLERY
import app.core.engines.video_parser.parsers.*
import com.aio.R.*
import com.anggrayudi.storage.file.*
import io.objectbox.annotation.*
import lib.files.FileExtensions.ARCHIVE_EXTENSIONS
import lib.files.FileExtensions.DOCUMENT_EXTENSIONS
import lib.files.FileExtensions.IMAGE_EXTENSIONS
import lib.files.FileExtensions.MUSIC_EXTENSIONS
import lib.files.FileExtensions.PROGRAM_EXTENSIONS
import lib.files.FileExtensions.VIDEO_EXTENSIONS
import lib.files.FileSizeFormatter.humanReadableSizeOf
import lib.files.FileSystemUtility.endsWithExtension
import lib.files.FileSystemUtility.isWritableFile
import lib.files.FileSystemUtility.saveStringToInternalStorage
import lib.networks.DownloaderUtils.getHumanReadableFormat
import lib.networks.DownloaderUtils.renameIfDownloadFileExistsWithSameName
import lib.networks.DownloaderUtils.updateSmartCatalogDownloadDir
import lib.process.*
import lib.process.CopyObjectUtils.deepCopy
import lib.texts.CommonTextUtils.getText
import lib.texts.CommonTextUtils.removeDuplicateSlashes
import java.io.*

/**
 * Data model representing a single download task within the application.
 *
 * This class serves as the primary entity for ObjectBox database persistence and contains
 * the comprehensive state of a download, including network metadata, file system paths,
 * multi-threading progress, and engine-specific configurations (e.g., yt-dlp).
 *
 * It provides lifecycle methods for:
 * 1. Persistence ([updateInDB])
 * 2. File management and migration ([migrateToPrivateStorage], [moveToSysGalleryFolder])
 * 3. Cleanup and deletion ([deleteInDB])
 * 4. UI-ready formatting of speeds, sizes, and statuses.
 *
 * @property id Unique identifier and primary key for database storage.
 * @property downloadStatus The current state of the task (e.g., [DownloadStatus.DOWNLOADING]).
 * @property fileName The name of the resulting file on disk.
 * @property fileURL The remote source URL of the file.
 * @property config A local, thread-safe snapshot of the user settings applied to this task.
 */
@Entity
class AIODownload : Serializable {

	/** Local logger instance; not persisted in the database. */
	@Transient var logger = LogHelperUtils.from(AIODownload::class.java)

	/** Unique ObjectBox primary key for the download record. */
	@Id var id: Long = 0L

	/** Current operational status (e.g., DOWNLOADING, COMPLETE, CLOSE). */
	@Index var downloadStatus: Int = CLOSE

	/** True if the download engine is currently processing this task. */
	@Index var isRunning: Boolean = false

	/** True if the file has been fully downloaded and verified. */
	@Index var isComplete: Boolean = false

	/** Internal flag marking the record for soft deletion. */
	@Index var isDeleted: Boolean = false

	/** Flag indicating the record has been removed from the user's view. */
	@Index var isRemoved: Boolean = false

	/** True if the download failed because the source URL is no longer valid. */
	@Index var isFileUrlExpired: Boolean = false

	/** True if the download is paused specifically due to loss of internet connection. */
	@Index var isWaitingForNetwork: Boolean = false

	/** True if an error occurred specifically within the yt-dlp processing core. */
	@Index var isYtdlpErrorFound: Boolean = false

	/** The raw error string returned by the yt-dlp process. */
	@Index var ytdlpErrorMessage: String = ""

	/** Flag to check if the yt-dlp environment has been successfully prepared. */
	@Index var isYtdlpInitialized: Boolean = false

	/** Temporary filesystem path where yt-dlp stores intermediate mixing files. */
	@Index var ytdlpTempOutputPath: String = ""

	/** Real-time console output or status updates from the yt-dlp process. */
	@Index var ytdlpStatusInfo: String = ""

	/** True if the file was deleted or moved outside the app. */
	@Index var isDestinationFileMissing: Boolean = false

	/** True if the downloaded file hash does not match the source checksum. */
	@Index var isChecksumInvalid: Boolean = false

	/** Flag for generic File I/O permission or access issues. */
	@Index var fileAccessFailed: Boolean = false

	/** Tracks if the user has already seen the "URL Expired" notification. */
	@Index var expiredUrlDialogShown: Boolean = false

	/** True if the Smart Catalog (Auto-sorting) has been configured for this task. */
	@Index var isSmartDirInitialized: Boolean = false

	/** A localized message to be displayed to the user regarding task errors. */
	@Index var userAlertMessage: String = ""

	/** Indicates if the download was initiated via the internal WebView browser. */
	@Index var isDownloadFromBrowser: Boolean = false

	/** Custom HTTP headers (User-Agent, etc.) required to access the file. */
	@Index var extraWebHeaders: Map<String, String>? = null

	/** The final name of the file including extension. */
	@Index var fileName: String = ""

	/** The direct source link for the file. */
	@Index var fileURL: String = ""

	/** The webpage URL where the download was discovered. */
	@Index var siteReferrer: String = ""

	/** The physical directory path where the file is stored. */
	@Index var fileDirectory: String = ""

	/** Standard internet media type (e.g., video/mp4). */
	@Index var fileMimeType: String = ""

	/** Logical grouping for the file (e.g., Videos, Music). */
	@Index var fileCategoryName: String = ""

	/** A short, human-readable string summarizing the current status. */
	@Index var downloadStatusInfo: String = "--"

	/** HTTP header info describing file size and name from the server. */
	var fileContentDisposition: String = ""

	/** The session cookie string required for authenticated downloads. */
	var siteCookie: String = ""

	/** Local filesystem path to the cached thumbnail image. */
	@Index var thumbPath: String = ""

	/** Remote URL for the file's preview image. */
	@Index var thumbnailUrl: String = ""

	/** Android URI representation of the file directory for SAF operations. */
	@Index var fileDirectoryURI: String = ""

	/** Formatted string of when the download started (e.g., "Oct 12, 10:30 AM"). */
	@Index var startTimeDateInFormat: String = ""

	/** Unix timestamp of the download start time. */
	@Index var startTimeDate: Long = 0L

	/** Formatted string of the last time the file was touched or updated. */
	@Index var lastModifiedTimeDateInFormat: String = ""

	/** Unix timestamp of the last modification. */
	@Index var lastModifiedTimeDate: Long = 0L

	/** Total size of the file in bytes. */
	@Index var fileSize: Long = 0L

	/** Flag for streams where the total length is not provided by the server. */
	@Index var isUnknownFileSize: Boolean = false

	/** SHA/MD5 string used to verify file integrity. */
	@Index var fileChecksum: String = "--"

	/** Human-readable size (e.g., "1.2 GB"). */
	@Index var fileSizeInFormat: String = ""

	/** The calculated average speed of the entire download session. */
	@Index var averageSpeed: Long = 0L

	/** The highest recorded transfer speed for this task. */
	@Index var maxSpeed: Long = 0L

	/** The current instantaneous transfer speed. */
	@Index var realtimeSpeed: Long = 0L

	/** Formatted average speed (e.g., "5 MB/s"). */
	@Index var averageSpeedInFormat: String = "--"

	/** Formatted maximum speed. */
	@Index var maxSpeedInFormat: String = "--"

	/** Formatted current speed. */
	@Index var realtimeSpeedInFormat: String = "--"

	/** Indicates if the server supports the HTTP Range header for pausing. */
	@Index var isResumeSupported: Boolean = false

	/** True if the file can be split into multiple parallel segments. */
	@Index var isMultiThreadSupported: Boolean = false

	/** Number of times the engine has tried to resume a broken session. */
	@Index var resumeSessionRetryCount: Int = 0

	/** Total count of network connection failures during this task. */
	@Index var totalTrackedConnectionRetries: Int = 0

	/** Numeric progress from 0 to 100. */
	@Index var progressPercentage: Long = 0L

	/** Formatted progress string (e.g., "85%"). */
	@Index var progressPercentageInFormat: String = ""

	/** Total bytes successfully written to disk. */
	@Index var downloadedByte: Long = 0L

	/** Formatted written bytes (e.g., "500 MB"). */
	@Index var downloadedByteInFormat: String = "--"

	/* --- Multi-threading Segment Arrays (Support up to 18 threads) --- */
	var partStartingPoint: LongArray = LongArray(18)
	var partEndingPoint: LongArray = LongArray(18)
	var partChunkSizes: LongArray = LongArray(18)
	var partsDownloadedByte: LongArray = LongArray(18)
	var partProgressPercentage: IntArray = IntArray(18)

	/** Cumulative time spent actively downloading in milliseconds. */
	@Index var timeSpentInMilliSec: Long = 0L

	/** Estimated seconds remaining until completion. */
	@Index var remainingTimeInSec: Long = 0L

	/** Formatted elapsed time (e.g., "02:15"). */
	@Index var timeSpentInFormat: String = "--"

	/** Formatted ETA (e.g., "00:45"). */
	@Index var remainingTimeInFormat: String = "--"

	/** Complex metadata object from the video parser; not stored in DB. */
	@Transient var videoInfo: AIOVideoInfo? = null

	/** The unique database ID of the associated [videoInfo] metadata record. */
	@Index var videoInfoId: Long = -1L

	/** Specific stream format data; not stored in DB. */
	@Transient var videoFormat: AIOVideoFormat? = null

	/** The unique database ID of the associated [videoFormat] record. */
	@Index var videoFormatId: Long = -1L

	/** Basic remote file metadata; not stored in DB. */
	@Transient var remoteFileInfo: AIORemoteFileInfo? = null

	/** The unique database ID of the associated [remoteFileInfo] record. */
	@Index var remoteFileInfoId: Long = -1L

	/** The actual shell/CLI command used to invoke yt-dlp. */
	@Index var executionCommand: String = ""

	/** Total runtime of the media file (e.g., "10:02"). */
	@Index var mediaFilePlaybackDuration: String = ""

	/** Tracks if the user has clicked to play/view the file after completion. */
	@Index var hasUserOpenedTheFile: Boolean = false

	/** Local copy of the app settings for thread-safe access during download. */
	@Transient var config: AIOSettings = (deepCopy(aioSettings)
		?: aioSettings).apply { id = 0L }

	/** The unique database ID of the associated [config] record. */
	@Index var configId: Long = -1

	companion object {
		/** Intent extra key for passing ID between components. */
		const val ID_DOWNLOAD_KEY = "DOWNLOAD_ID_KEY"

		/** Filename suffix for the stored Netscape cookie file. */
		const val COOKIES_EXTENSION = "_cookies.txt"

		/** Filename suffix for the stored thumbnail image. */
		const val THUMB_EXTENSION = "_thumb.jpg"

		/** Extension appended to files that are still in progress. */
		const val TEMP_EXTENSION = ".tmp_download"
	}

	/**
	 * Initializer block ensures that every instance resolves its
	 * physical storage path immediately upon creation or DB restoration.
	 */
	init {
		// Automatically resolve the file directory upon object creation
		initStorageLocation()
	}

	/**
	 * Synchronizes the current object state with the local database.
	 * * This function handles pre-save cleanup, cookie persistence, and
	 * the actual database write operation. It is safe to call from any
	 * thread as it handles its own background context switching.
	 */
	suspend fun updateInDB() {
		withIOContext {
			runCatching {
				// Guard: Prevent saving incomplete or empty models to the DB
				if (fileName.isEmpty() && fileURL.isEmpty()) {
					return@withIOContext
				}

				// Backup session cookies to internal storage
				saveCookiesIfAvailable()

				// Normalize progress and speed values for a consistent UI state
				sanitizeDownloadState()

				// Commit the current model instance to the ObjectBox store
				storeDownloadRecord(this@AIODownload)

			}.onFailure { error ->
				// Log persistence failures with the specific ID for debugging
				logger.e("Storage update failed for download ID: $id", error)
			}
		}
	}

	/**
	 * Orchestrates the full deletion of a download record and its associated files.
	 * * This function performs a deep cleanup, including:
	 * 1. Internal metadata (Thumbnails and Cookies).
	 * 2. Temporary download artifacts via [clearTempLeftOvers].
	 * 3. Database records via [deleteAssociatedRecords].
	 * 4. The physical media file, depending on storage type and user preference.
	 *
	 * @param shouldDeleteFile Only applicable for files in the System Gallery.
	 * If true, the actual media file will be deleted from public storage.
	 */
	suspend fun deleteInDB(shouldDeleteFile: Boolean = false) {
		withIOContext {
			runCatching {
				val internalDir = internalDataFolder

				// Resolve handles for internal metadata files
				val cookieFile = internalDir.findFile("$id$COOKIES_EXTENSION")
				val thumbFile = internalDir.findFile("$id$THUMB_EXTENSION")

				// Safely delete the thumbnail if write permissions exist
				isWritableFile(thumbFile).let { isWritable ->
					if (isWritable) thumbFile?.delete()
				}

				// Safely delete the cookie file if write permissions exist
				isWritableFile(cookieFile).let { isWritable ->
					if (isWritable) cookieFile?.delete()
				}

				// Purge any engine-specific leftovers (yt-dlp partials, etc.)
				clearTempLeftOvers(internalDir)

				// Remove the record from the ObjectBox database
				deleteAssociatedRecords(this@AIODownload)

				// Determine if the physical media file should be erased
				val storageType = config.defaultDownloadLocationType
				val shouldDeleteOriginal = (storageType == SYSTEM_GALLERY && shouldDeleteFile)

				// Logic: Files in private storage are always deleted with the record.
				// Files in public storage are deleted only if explicitly requested.
				if (storageType == PRIVATE_FOLDER || shouldDeleteOriginal) {
					deleteOriginalDownloadedFile()
				}

			}.onFailure { error ->
				// Log the failure against the specific Download ID for troubleshooting
				logger.e("Deletion error for download ID: $id", error)
			}
		}
	}

	/**
	 * Synchronizes the [videoInfo], [videoFormat], and [remoteFileInfo] metadata objects
	 * from the database based on their respective stored IDs.
	 *
	 * This method should be called after loading an [AIODownload] instance from ObjectBox
	 * to restore the full object tree, as [@Transient] fields are not automatically
	 * populated by the database engine.
	 */
	suspend fun loadAssociatedRecords() {
		withIOContext {
			assembleAssociatedRecords(this@AIODownload)
		}
	}

	/**
	 * Moves the downloaded file from a public directory to the app's private storage.
	 * * This process updates the [fileDirectory], handles file renaming in case of
	 * conflicts, and persists the new location to the database.
	 * @param onError Callback triggered for incomplete downloads or migration errors.
	 * @param onSuccess Callback triggered after successful migration to private storage.
	 */
	suspend fun migrateToPrivateStorage(
		onError: (String) -> Unit, onSuccess: () -> Unit
	) {
		withIOContext {
			// Prevent migration of partial or failed downloads
			if (downloadStatus != COMPLETE) {
				withMainContext {
					onError.invoke("Download is not completed yet.")
				}
			} else {
				// Update configuration state
				config.defaultDownloadLocationType = PRIVATE_FOLDER
				val originalFile = getDestinationFile()

				// Resolve private storage path
				val externalDataFolderPath = INSTANCE.getExternalDataFolder()
					?.getAbsolutePath(INSTANCE)

				fileDirectory = (if (!externalDataFolderPath.isNullOrEmpty())
					externalDataFolderPath else INSTANCE.dataDir.absolutePath)

				// Update folder-specific metadata and check for naming collisions
				updateSmartCatalogDownloadDir(this@AIODownload)
				renameIfDownloadFileExistsWithSameName(this@AIODownload)

				// Execute the physical file move
				val migratedFile = getDestinationFile()
				originalFile.copyTo(migratedFile, overwrite = true)
				originalFile.delete()

				// Persist changes and notify UI
				updateInDB()
				withMainContext { onSuccess.invoke() }
			}
		}
	}

	/**
	 * Moves the downloaded file from private storage to the public system gallery.
	 * * This makes the file accessible to the Android MediaStore and other media
	 * applications. It ensures the file is placed in the user's preferred
	 * public download directory.
	 * @param onError Callback for incomplete downloads or permission issues.
	 * @param onSuccess Callback triggered after successful migration to public storage.
	 */
	suspend fun moveToSysGalleryFolder(
		onError: (String) -> Unit, onSuccess: () -> Unit
	) {
		withIOContext {
			// Only completed files can be exported to the gallery
			if (downloadStatus != COMPLETE) {
				withMainContext {
					onError.invoke("Download is not completed yet.")
				}
			} else {
				// Update configuration to public gallery mode
				config.defaultDownloadLocationType = SYSTEM_GALLERY
				val originalFile = getDestinationFile()

				// Resolve public destination path
				val externalDir = AIOApp.AIO_DEFAULT_DOWNLOAD_PATH
				fileDirectory = config.selectedDownloadDirectory.ifEmpty { externalDir }

				// Sync catalog metadata and handle potential filename duplicates in the gallery
				updateSmartCatalogDownloadDir(this@AIODownload)
				renameIfDownloadFileExistsWithSameName(this@AIODownload)

				// Physically transfer the file
				val migratedFile = getDestinationFile()
				originalFile.copyTo(migratedFile, overwrite = true)
				originalFile.delete()

				// Finalize database record and notify UI
				updateInDB()
				withMainContext { onSuccess.invoke() }
			}
		}
	}

	/**
	 * Resolves the absolute path of the Netscape cookie file if it exists on disk.
	 * * @return The full string path to the cookie file, or null if cookies
	 * are not required or the file has not been created yet.
	 */
	suspend fun getCookieFilePathIfAvailable(): String? {
		return withIOContext {
			// Guard: No cookies associated with this download session
			if (siteCookie.isEmpty()) return@withIOContext null

			val cookieFileName = "$id$COOKIES_EXTENSION"
			val internalDir = internalDataFolder
			val cookieFile = internalDir.findFile(cookieFileName)

			// Verify physical existence before returning the absolute path
			return@withIOContext if (cookieFile != null && cookieFile.exists())
				cookieFile.getAbsolutePath(INSTANCE) else null
		}
	}

	/**
	 * Formats and saves the session cookies to internal storage.
	 * * This ensures that external download engines can utilize the user's
	 * session/authentication context.
	 *
	 * @param shouldOverride If true, forces a re-write of the cookie file
	 * even if one already exists for this download ID.
	 */
	suspend fun saveCookiesIfAvailable(shouldOverride: Boolean = false) {
		withIOContext {
			// Exit if there is no cookie data to save
			if (siteCookie.isEmpty()) return@withIOContext

			val cookieFileName = "$id$COOKIES_EXTENSION"
			val internalDir = internalDataFolder
			val cookieFile = internalDir.findFile(cookieFileName)

			// Prevent redundant writes unless an override is explicitly requested
			if (!shouldOverride && cookieFile != null && cookieFile.exists()) {
				return@withIOContext
			}

			// Convert raw string to Netscape format and commit to internal storage
			val formattedCookies = formatNetscapeCookieFile(siteCookie)
			saveStringToInternalStorage(cookieFileName, formattedCookies)
		}
	}

	/**
	 * Resolves the directory for temporary download artifacts.
	 * @return A [File] object pointing to a ".temp" folder inside the current [fileDirectory].
	 */
	suspend fun getTempDestinationDir(): File {
		return withIOContext {
			// Appends a .temp suffix to the main directory path
			return@withIOContext File("${fileDirectory}.temp/")
		}
	}

	/**
	 * Resolves the final download path as a [DocumentFile].
	 * Use this for operations requiring Android Storage Access Framework compatibility.
	 */
	suspend fun getDestinationDocumentFile(): DocumentFile {
		return withIOContext {
			val destinationPath = removeDuplicateSlashes("$fileDirectory/$fileName")
			return@withIOContext DocumentFile.fromFile(File(destinationPath!!))
		}
	}

	/**
	 * Resolves the final download path as a standard [File].
	 * Use this for high-performance I/O operations on internal or accessible external storage.
	 */
	suspend fun getDestinationFile(): File {
		return withIOContext {
			val destinationPath = removeDuplicateSlashes("$fileDirectory/$fileName")
			return@withIOContext File(destinationPath!!)
		}
	}

	/**
	 * Resolves the path for the active partial download file.
	 * @return A [File] pointing to the final path plus the [TEMP_EXTENSION].
	 */
	suspend fun getTempDestinationFile(): File {
		return withIOContext {
			val tempFilePath = "${getDestinationFile().absolutePath}${TEMP_EXTENSION}"
			return@withIOContext File(tempFilePath)
		}
	}

	/**
	 * Retrieves the [Uri] for the cached thumbnail image if it exists.
	 */
	suspend fun getThumbnailURI(): Uri? {
		return withIOContext {
			val thumbFilePath = "$id$THUMB_EXTENSION"
			// Look for the thumbnail in the app's internal protected data folder
			return@withIOContext internalDataFolder.findFile(thumbFilePath)?.uri
		}
	}

	/**
	 * Permanently deletes the cached thumbnail file and updates the database record.
	 */
	suspend fun clearCachedThumbnailFile() {
		withIOContext {
			runCatching {
				val thumbnailUri = getThumbnailURI()
				// Convert URI back to a File object for physical deletion
				thumbnailUri?.toFile()?.delete()
				thumbPath = ""
				updateInDB() // Persist the change so the UI reflects "no thumbnail"
			}.onFailure { error ->
				logger.e("Error clearing cached thumbnail file", error)
			}
		}
	}

	/**
	 * @return The resource ID for the "no thumbnail available" placeholder image.
	 */
	fun getThumbnailDrawableID(): Int {
		return drawable.image_no_thumb_available
	}

	/**
	 * Resolves the final localized status string to be displayed in the UI.
	 * * This function serves as a logic gate between standard downloader metrics
	 * and specialized engine feedback (e.g., yt-dlp merging status). It ensures
	 * that transitional states like "Preparing" or "Waiting to join" are
	 * prioritized over generic speed/percentage strings when appropriate.
	 *
	 * @return A fully formatted string representing the current state of the task.
	 */
	suspend fun getFormattedDownloadStatusInfo(): String {
		return withIOContext {
			// Check if this is a specialized video download with metadata
			if (videoFormat != null && videoInfo != null) {

				return@withIOContext if (downloadStatus == CLOSE) {
					// Pre-fetch localized strings for state comparison
					val waitingToJoin = getText(string.title_waiting_to_join).lowercase()
					val preparingToDownload = getText(string.title_preparing_download).lowercase()
					val downloadFailed = getText(string.title_download_io_failed).lowercase()

					// If the status contains specific lifecycle keywords, preserve that info
					if (downloadStatusInfo.lowercase().startsWith(waitingToJoin) ||
						downloadStatusInfo.lowercase().startsWith(preparingToDownload) ||
						downloadStatusInfo.lowercase().startsWith(downloadFailed)
					) {
						downloadStatusInfo
					} else {
						// Fallback to standard speed/percentage formatting
						normalDownloadStatusInfo()
					}
				} else {
					// If the download is ACTIVE
					val currentStatus = getText(string.title_started_downloading).lowercase()

					if (!downloadStatusInfo.lowercase().startsWith(currentStatus)) {
						// Refresh status if we haven't reached the 'Started' phase yet
						normalDownloadStatusInfo()
					} else {
						// Prioritize detailed engine feedback (e.g., yt-dlp console output)
						ytdlpStatusInfo
					}
				}
			} else {
				// Standard non-video/generic download routing
				return@withIOContext normalDownloadStatusInfo()
			}
		}
	}

	/**
	 * Categorizes the download based on its file extension and returns a localized name.
	 * * This is used to sort downloads into tabs (Images, Videos, etc.) and to
	 * display the appropriate category label in the UI.
	 *
	 * @param shouldRemoveAIOPrefix If true, returns the generic category name (e.g., "Sounds").
	 * If false, returns the branded version (e.g., "AIO Sounds").
	 * @return A localized string representing the file's category.
	 */
	suspend fun getUpdatedCategoryName(shouldRemoveAIOPrefix: Boolean = false): String {
		return withIOContext {
			if (shouldRemoveAIOPrefix) {
				// Logic for generic category names
				val categoryName = when {
					endsWithExtension(fileName, IMAGE_EXTENSIONS) -> getText(string.title_images)
					endsWithExtension(fileName, VIDEO_EXTENSIONS) -> getText(string.title_videos)
					endsWithExtension(fileName, MUSIC_EXTENSIONS) -> getText(string.title_sounds)
					endsWithExtension(fileName, DOCUMENT_EXTENSIONS) -> getText(string.title_documents)
					endsWithExtension(fileName, PROGRAM_EXTENSIONS) -> getText(string.title_programs)
					endsWithExtension(fileName, ARCHIVE_EXTENSIONS) -> getText(string.title_archives)
					else -> getText(string.title_aio_others)
				}
				return@withIOContext categoryName
			} else {
				// Logic for AIO-branded category names
				val categoryName = when {
					endsWithExtension(fileName, IMAGE_EXTENSIONS) -> getText(string.title_aio_images)
					endsWithExtension(fileName, VIDEO_EXTENSIONS) -> getText(string.title_aio_videos)
					endsWithExtension(fileName, MUSIC_EXTENSIONS) -> getText(string.title_aio_sounds)
					endsWithExtension(fileName, DOCUMENT_EXTENSIONS) -> getText(string.title_aio_documents)
					endsWithExtension(fileName, PROGRAM_EXTENSIONS) -> getText(string.title_aio_programs)
					endsWithExtension(fileName, ARCHIVE_EXTENSIONS) -> getText(string.title_aio_archives)
					else -> getText(string.title_aio_others)
				}
				return@withIOContext categoryName
			}
		}
	}

	/**
	 * Converts the raw byte count of the file into a localized, human-readable string.
	 * * @return A formatted string (e.g., "2.5 GB", "450 KB").
	 * Returns "Unknown size" if the size information is unavailable or uninitialized.
	 */
	suspend fun getFormattedFileSize(): String {
		return withIOContext {
			// Handle cases where the server didn't provide a Content-Length header
			return@withIOContext if (fileSize <= 1 || isUnknownFileSize) {
				getText(string.title_unknown_size)
			} else {
				// Perform math to convert raw Long bytes into a readable Double format
				humanReadableSizeOf(fileSize.toDouble())
			}
		}
	}

	/**
	 * Extracts the file extension from the [fileName].
	 * * @return The extension string (e.g., "mp4", "zip") without the leading dot.
	 * Returns an empty string if no extension is found.
	 */
	suspend fun getFileExtension(): String {
		return withIOContext {
			// Locate the final dot in the filename and return everything after it
			val extension = fileName.substringAfterLast('.', "")
			return@withIOContext extension
		}
	}

	/**
	 * Updates the [fileDirectory] property to reflect current global settings.
	 * * This should be called before starting or resuming a download to ensure
	 * the engine is writing to the correct physical location. It handles
	 * environment-specific path resolution for both private and public storage.
	 */
	suspend fun refreshUpdatedDownloadFolder() {
		withIOContext {
			when (config.defaultDownloadLocationType) {
				PRIVATE_FOLDER -> {
					// Determine the best available private path (External Folder vs Internal Data)
					val externalDir = INSTANCE.getExternalDataFolder()
						?.getAbsolutePath(INSTANCE)

					if (!externalDir.isNullOrEmpty()) {
						fileDirectory = externalDir
					} else {
						// Fallback to absolute internal path if external folder isn't accessible
						val internalDir = INSTANCE.dataDir.absolutePath
						fileDirectory = internalDir
					}
				}

				SYSTEM_GALLERY -> {
					// Resolve the public gallery path based on user preference or default constant
					val galleryPath = AIOApp.AIO_DEFAULT_DOWNLOAD_PATH
					fileDirectory = config.selectedDownloadDirectory
						.ifEmpty { galleryPath }
				}
			}
		}
	}

	/**
	 * Cleans up residual temporary files created during the download or post-processing stages.
	 * * This function targets two specific types of "leftovers":
	 * 1. **yt-dlp partials**: Deletes all files in the internal directory that match the prefix
	 * of the current yt-dlp temporary output path.
	 * 2. **Session Cookies**: Deletes the temporary Netscape-formatted cookie file used
	 * for authenticated parsing.
	 *
	 * Performance Note: Uses [FilenameFilter] on a raw [File] object to avoid the high overhead
	 * of DocumentFile/SAF when scanning the internal directory.
	 *
	 * @param internalDir The [DocumentFile] representing the app's internal data storage.
	 */
	private suspend fun clearTempLeftOvers(internalDir: DocumentFile) {
		withIOContext {
			runCatching {
				// Only proceed if media metadata is present to prevent accidental broad deletion
				if (videoFormat != null && videoInfo != null) {

					// --- Part 1: Clear yt-dlp artifacts ---
					if (ytdlpTempOutputPath.isNotEmpty()) {
						val tempYtdlpFileName = File(ytdlpTempOutputPath).name

						// Convert to raw File for high-performance iteration and filtering
						internalDir.toRawFile(INSTANCE)?.listFiles { _, name ->
							name.startsWith(tempYtdlpFileName)
						}?.forEach { file ->
							try {
								file?.let {
									if (!file.isFile) return@let
									// Final safety check before deletion
									if (file.name.startsWith(tempYtdlpFileName)) {
										file.delete()
									}
								}
							} catch (error: Exception) {
								logger.e("Error deleting temp file: ${file?.name}", error)
							}
						}
					}

					// --- Part 2: Clear temporary cookie files ---
					val cookiePath = videoInfo?.videoCookieTempPath
					if (!cookiePath.isNullOrEmpty()) {
						val tempCookieFile = File(cookiePath)
						if (tempCookieFile.isFile && tempCookieFile.exists()) {
							tempCookieFile.delete()
						}
					}
				}
			}.onFailure { error ->
				logger.e("General cleanup failure for ID: $id", error)
			}
		}
	}

	/**
	 * Deletes the downloaded file from the filesystem.
	 *
	 * This function handles the physical removal of the media file based on the
	 * current [fileDirectory] and [fileName] properties. It performs safety checks
	 * to ensure the file is writable before deletion.
	 *
	 * @param onSuccess A callback triggered immediately after the file is successfully deleted.
	 * Note: This is invoked on the IO thread context.
	 */
	private suspend fun deleteOriginalDownloadedFile(onSuccess: () -> Unit = {}) {
		withIOContext {
			runCatching {
				// Resolve the DocumentFile handle for the current download destination
				val originalFile = getDestinationDocumentFile()

				// Validate write permissions/file existence
				isWritableFile(originalFile).let { isWritable ->
					if (isWritable) {
						// Attempt physical deletion
						val isDeleted = originalFile.delete()

						if (isDeleted) {
							onSuccess.invoke()
						}
					}
				}
			}.onFailure { error ->
				// Log the failure with the specific download ID for easier debugging
				logger.e("Deletion error for download ID: $id", error)
			}
		}
	}

	/**
	 * Compiles a formatted status string for the UI display.
	 * * This method aggregates various real-time metrics (speed, progress, ETA)
	 * into a single localized string. It handles logic for both active
	 * downloading and post-processing states.
	 *
	 * @return A pipe-separated string (e.g., "50% Of 10 MB | 1.2 MB/s | 00:05").
	 */
	private suspend fun normalDownloadStatusInfo(): String {
		return withIOContext {
			val textDownload = getText(string.title_downloaded)

			// Handle Video-specific metadata display (usually for yt-dlp tasks)
			if (videoFormat != null && videoInfo != null) {
				return@withIOContext "$downloadStatusInfo  |  $textDownload ($progressPercentage%)" +
					"  |  --/s  |  --:-- "
			} else {
				val totalFileSize = fileSizeInFormat

				// Determine if we should show real-time speed or a placeholder
				val downloadSpeedInfo = if (downloadStatus == CLOSE) "--/s"
				else realtimeSpeedInFormat

				// Determine if we should show ETA or a placeholder (e.g., when offline)
				val remainingTimeInfo = if (downloadStatus == CLOSE || isWaitingForNetwork)
					"--:--" else remainingTimeInFormat

				val downloadingStatus = getText(string.title_started_downloading).lowercase()

				// Branch logic: Show "Progress Of Total" if currently active,
				// otherwise show the status info prefix.
				return@withIOContext if (downloadStatusInfo.lowercase().startsWith(downloadingStatus)) {
					"$progressPercentageInFormat% Of $totalFileSize  |  " +
						"$downloadSpeedInfo  |  $remainingTimeInfo"
				} else {
					"$downloadStatusInfo  |  $textDownload ($progressPercentage%)  |  " +
						"$downloadSpeedInfo |  $remainingTimeInfo"
				}
			}
		}
	}

	/**
	 * Initializes the [fileDirectory] based on the user's current settings.
	 * * This is a critical initialization step that determines the root path
	 * for all subsequent file I/O operations. It handles logic for:
	 * 1. App-specific private storage (Internal/External).
	 * 2. Public System Gallery storage with fallback to default paths.
	 *
	 * Note: This updates the [fileDirectory] property directly.
	 */
	private fun initStorageLocation() {
		if (aioSettings.defaultDownloadLocationType == PRIVATE_FOLDER) {
			// Attempt to get the Android/data/com.aio... path
			val externalDir = INSTANCE.getExternalDataFolder()?.getAbsolutePath(INSTANCE)
			val internalDir = INSTANCE.dataDir.absolutePath

			// Favor external app-data folder over internal root for space management
			fileDirectory = if (!externalDir.isNullOrEmpty()) externalDir else internalDir

		} else if (aioSettings.defaultDownloadLocationType == SYSTEM_GALLERY) {
			// Use the default public 'Downloads/AIO' path as the primary fallback
			val externalDir = AIOApp.AIO_DEFAULT_DOWNLOAD_PATH

			// Use the user-defined custom directory, or fallback to default
			fileDirectory = config.selectedDownloadDirectory.ifEmpty { externalDir }
		}
	}

	/**
	 * Normalizes the download state variables to prevent UI inconsistencies.
	 * * This function performs two main roles:
	 * 1. Resets real-time metrics (speed) when the download is idle/paused.
	 * 2. Hard-sets all progress trackers (including multi-threaded part arrays)
	 * to 100% when the status is marked as COMPLETE.
	 */
	private suspend fun sanitizeDownloadState() {
		withIOContext {
			// Exit if the download is currently active to preserve real-time speed/progress data
			val isDownloading = (downloadStatus == DOWNLOADING)
			if (isRunning && isDownloading) return@withIOContext

			// Reset speed indicators for non-active downloads
			realtimeSpeed = 0L
			realtimeSpeedInFormat = "--"

			val isCompleted = (downloadStatus == COMPLETE)
			if (isComplete && isCompleted) {
				// Force reset of temporal metrics
				remainingTimeInSec = 0
				remainingTimeInFormat = "--:--"

				// Sync main progress indicators
				progressPercentage = 100L
				progressPercentageInFormat = getText(string.title_100_percentage)

				// Ensure byte counts reflect full completion
				downloadedByte = fileSize
				downloadedByteInFormat = getHumanReadableFormat(downloadedByte)

				// Normalize every individual segment in a multi-threaded download
				partProgressPercentage.forEachIndexed { index, _ ->
					partProgressPercentage[index] = 100
					partsDownloadedByte[index] = partChunkSizes[index]
				}
			}
		}
	}

	/**
	 * Transforms a raw cookie string into the legacy Netscape HTTP Cookie format.
	 * * This is necessary for providing authentication contexts to command-line
	 * tools (like yt-dlp) that do not support raw header strings.
	 *
	 * @param cookieString The raw semicolon-separated cookie string (e.g., "ID=123; sess=abc").
	 * @return A multi-line string formatted with tab-separated Netscape fields.
	 */
	private suspend fun formatNetscapeCookieFile(cookieString: String): String {
		return withIOContext {
			// Split and trim the raw cookies into individual key-value pairs
			val cookies = cookieString.split(";").map { it.trim() }

			// Define standard Netscape field defaults
			val domain = ""           // Domain (empty defaults to the current request domain)
			val path = "/"            // Accessible to all paths
			val secure = "FALSE"      // HTTPS only flag
			val expiry = "2147483647" // Epoch for the year 2038 (maximum 32-bit int)

			val stringBuilder = StringBuilder()
			stringBuilder.append("# Netscape HTTP Cookie File\n")
			stringBuilder.append("# This file was generated by the app.\n\n")

			for (cookie in cookies) {
				val parts = cookie.split("=", limit = 2)
				if (parts.size == 2) {
					val name = parts[0].trim()
					val value = parts[1].trim()

					// Construct the tab-separated line required by Netscape standards
					stringBuilder.append("$domain\tFALSE\t$path\t$secure\t$expiry\t$name\t$value\n")
				}
			}

			return@withIOContext stringBuilder.toString()
		}
	}
}