package app.core.engines.settings

import androidx.documentfile.provider.*
import app.core.*
import app.core.AIOApp.Companion.AIO_DEFAULT_DOWNLOAD_PATH
import app.core.AIOApp.Companion.INSTANCE
import app.core.AIOApp.Companion.aioSettings
import app.core.AIOLanguage.Companion.ENGLISH
import app.core.engines.settings.AIOSettingsRepo.saveInDB
import com.aio.*
import com.anggrayudi.storage.file.*
import com.anggrayudi.storage.file.DocumentFileCompat.fromFullPath
import io.objectbox.annotation.*
import lib.files.FileSystemUtility.isWritableFile
import lib.process.*
import lib.texts.CommonTextUtils.getText
import java.io.*
import kotlin.jvm.Transient

/**
 * Global configuration entity representing the persistent state and user preferences
 * of the AIO application.
 *
 * This class serves as the central data hub for the application, managing:
 * 1. User Interface State: Theme preferences, language settings, and setup flags.
 * 2. Download Engine Parameters: Buffer sizes, thread counts, network restrictions,
 * and path management.
 * 3. Browser Configuration: Privacy settings, AdBlocker state, and User-Agent strings.
 * 4. Analytics & Usage: Interaction counters, usage duration, and advertisement metrics.
 *
 * All properties are persisted via ObjectBox using the [Entity] annotation. The class
 * includes self-healing logic for file system directories and safe asynchronous
 * database synchronization.
 */
@Entity
class AIOSettings : Serializable {

	/** * Internal logger instance, excluded from
	 * database persistence.
	 */
	@Transient
	private val logger = LogHelperUtils.from(javaClass)

	/** * Unique identifier for ObjectBox; [#assignable] allows
	 * manual ID management for global settings.
	 */
	@Id(assignable = true)
	var id: Long = 0L

	/** * Links these settings to a specific download entity,
	 * defaults to the global app settings ID.
	 */
	var downloadId: Long = AIOSettingsRepo.APP_SETTINGS_DB_ID

	/** * Indicates if the user has successfully finished
	 * the initial language selection flow.
	 */
	var isLanguageSetupCompleted: Boolean = false

	/** * Tracks if the user has interacted with or
	 * completed the in-app review prompt.
	 */
	var isAppReviewCompleted: Boolean = false

	/** * Records if the user opted to skip the system
	 * battery optimization whitelist prompt.
	 */
	var hasSkippedBatteryOptimization: Boolean = false

	/** * Diagnostic flag set when the app detects an improper
	 * shutdown or crash in the previous session.
	 */
	var hasAppCrashedRecently: Boolean = false

	/** * Cache of the last string pulled from the clipboard
	 * to prevent redundant "Link Detected" alerts.
	 */
	var lastClipboardTextProcessed: String = ""

	/** * Defines the storage strategy
	 * (e.g., Internal Private vs. Public Gallery).
	 */
	var defaultDownloadLocationType: Int = SYSTEM_GALLERY

	/** * The absolute file system path where downloaded
	 * files are currently being saved.
	 */
	var selectedDownloadDirectory: String = AIO_DEFAULT_DOWNLOAD_PATH

	/** * Path to the WhatsApp Status media folder,
	 * used for the status saver feature.
	 */
	var whatsAppStatusFullFolderPath: String =
		getText(R.string.text_whatsapp_status_folder_path)

	/** The current UI language code (e.g., "en", "hi"). */
	var selectedUiLanguage: String = ENGLISH

	/** The region code used to filter trending content. */
	var selectedContentRegion: String = "IN"

	/** * User preference for the app's visual theme
	 * (Light, Dark, or System Auto).
	 */
	var selectedThemeMode: Int = THEME_AUTO

	/** Default sorting criteria for the downloads list view. */
	var downloadsDefaultSortOrder: Int = SORT_DATE_NEWEST_FIRST

	/** * Global toggle for receiving daily content
	 * recommendations via notifications.
	 */
	var isDailyContentSuggestionEnabled: Boolean = true

	/** * Total number of successful downloads completed
	 * during the app's lifetime.
	 */
	var successfulDownloadCount: Int = 0

	/** * Total cumulative time the app process has existed,
	 * in milliseconds.
	 */
	var totalUsageDurationMs: Float = 0.0f

	/** Pre-formatted string representation of total usage. */
	var totalUsageDurationFormatted: String = ""

	/** * Cumulative time the app has been in the foreground,
	 * in milliseconds.
	 */
	var foregroundUsageDurationMs: Float = 0.0f

	/** Pre-formatted string representation of foreground time. */
	var foregroundUsageDurationFormatted: String = ""

	/* --- Analytics --- */

	/** Total number of times a push notification was tapped. */
	var pushNotificationClickCount: Int = 0

	/** Total push notifications received by the device. */
	var pushNotificationReceivedCount: Int = 0

	/** Count of manual language changes by the user. */
	var languageChangeClickCount: Int = 0

	/** Number of times the media player was launched. */
	var mediaPlaybackClickCount: Int = 0

	/** Count of visits to the tutorial section. */
	var howToGuideClickCount: Int = 0

	/** Interactions with the manual URL entry field. */
	var videoUrlEditorClickCount: Int = 0

	/** Visits to the browsing history screen. */
	var homeHistoryClickCount: Int = 0

	/** Number of times bookmarks were accessed. */
	var homeBookmarksClickCount: Int = 0

	/** Number of times recent downloads were viewed. */
	var recentDownloadsClickCount: Int = 0

	/** Clicks on website favicons on the home screen. */
	var homeFaviconClickCount: Int = 0

	/** Total manual checks for an app version update. */
	var versionCheckClickCount: Int = 0

	/** Total clicks on interstitial advertisements. */
	var interstitialAdClickCount: Int = 0

	/** Total impressions of interstitial advertisements. */
	var interstitialAdImpressionCount: Int = 0

	/** Total clicks on rewarded video ad buttons. */
	var rewardedAdClickCount: Int = 0

	/** Total successful completions of rewarded ads. */
	var rewardedAdImpressionCount: Int = 0

	/* --- Download Engine --- */

	/** Shows a unified progress bar for all active tasks. */
	var downloadSingleUIProgress: Boolean = true

	/** Toggle to show/hide preview images in the list. */
	var downloadHideVideoThumbnail: Boolean = false

	/** Plays a localized sound effect when a download finishes. */
	var downloadPlayNotificationSound: Boolean = true

	/** Prevents progress from appearing in the status bar. */
	var downloadHideNotification: Boolean = false

	/** Removes all progress indicators from the app UI. */
	var hideDownloadProgressFromUI: Boolean = false

	/** Auto-removes entries from the list once finished. */
	var downloadAutoRemoveTasks: Boolean = false

	/** Expiration time (days) for finished tasks. */
	var downloadAutoRemoveTaskAfterNDays: Int = 0

	/** Tapping a completed item in the list opens it. */
	var openDownloadedFileOnSingleClick: Boolean = true

	/** Auto-retry failed tasks on app restart. */
	var downloadAutoResume: Boolean = true

	/** Max retry attempts before a task is marked failed. */
	var downloadAutoResumeMaxErrors: Int = 35

	/** Enables the engine to follow complex redirections. */
	var downloadAutoLinkRedirection: Boolean = true

	/** Organizes files into subfolders (Video, Music, etc.). */
	var downloadAutoFolderCatalog: Boolean = true

	/** Optimizes thread count based on network conditions. */
	var downloadAutoThreadSelection: Boolean = true

	/** Moves completed files to encrypted/private storage. */
	var downloadAutoFileMoveToPrivate: Boolean = false

	/** Attempts to extract audio streams automatically. */
	var downloadAutoConvertVideosToMp3: Boolean = false

	/** Size of the read/write buffer (default 8KB). */
	var downloadBufferSize: Int = 1024 * 8

	/** Max time (ms) to wait for data from the server. */
	var downloadMaxHttpReadingTimeout: Int = 1000 * 30

	/** Parallel segments used for a single file download. */
	var downloadDefaultThreadConnections: Int = 1

	/** Number of files that can download simultaneously. */
	var downloadDefaultParallelConnections: Int = 1

	/** Validates file integrity using hashes after completion. */
	var downloadVerifyChecksum: Boolean = false

	/** Bandwidth limit (Bps). 0 indicates unlimited. */
	var downloadMaxNetworkSpeed: Long = 0

	/** Restricts downloads to Wi-Fi connections only. */
	var downloadWifiOnly: Boolean = false

	/** The User-Agent string for download HTTP requests. */
	var downloadHttpUserAgent: String =
		getText(R.string.text_downloads_default_http_user_agent)

	/** Proxy configuration (Host:Port) for traffic routing. */
	var downloadHttpProxyServer: String = ""

	/** System-wide limit for concurrent active tasks. */
	var numberOfMaxDownloadThreshold: Int = 1

	/** Total count of download actions initiated. */
	var numberOfDownloadsUserDid: Int = 0

	/* --- Browser --- */

	/** The URL loaded when a new tab is opened. */
	var browserDefaultHomepage: String =
		getText(R.string.text_https_youtube_com)

	/** Browser requests desktop versions of websites. */
	var browserDesktopBrowsing: Boolean = false

	/** Prevents saving history, cookies, or cache. */
	var browserPrivateBrowsing: Boolean = false

	/** Toggles the internal ad-blocking engine. */
	var browserEnableAdblocker: Boolean = true

	/** Toggles JavaScript execution in the web view. */
	var browserEnableJavascript: Boolean = true

	/** Toggles image loading to save data. */
	var browserEnableImages: Boolean = true

	/** Prevents unsolicited popup windows. */
	var browserEnablePopupBlocker: Boolean = true

	/** Floating button that identifies downloadable videos. */
	var browserEnableVideoGrabber: Boolean = true

	/** The User-Agent string used by the browser web view. */
	var browserHttpUserAgent: String =
		getText(R.string.text_browser_default_mobile_http_user_agent)

	/**
	 * Persists the current settings state to the database asynchronously.
	 *
	 * This function is thread-safe and utilizes [ThreadsUtility] to offload the
	 * database write to a background thread with a short delay (500ms) to debounce
	 * rapid consecutive updates. It ensures the download directory path is
	 * sanitized via [ensureDownloadDirExists] before saving.
	 */
	suspend fun updateInDB() {
		withIOContext {
			runCatching {
				ensureDownloadDirExists()
				saveInDB(this@AIOSettings)
			}.onFailure { error ->
				logger.e("Settings save error: ${error.message}", error)
			}
		}
	}

	/**
	 * Verifies that the user-selected download directory is currently accessible and writable.
	 *
	 * If the validation fails (e.g., due to unmounted storage or revoked permissions),
	 * it triggers [setupDefaultDownloadDir] to restore a valid path and synchronizes
	 * the change back to the database.
	 */
	suspend fun validateUserSelectedFolder() {
		withIOContext {
			runCatching {
				if (!isWritableFile(getUserSelectedDir())) {
					val created = setupDefaultDownloadDir()
					if (created) aioSettings.updateInDB()
				}
			}.onFailure { error ->
				logger.e("Folder validation error: ${error.message}", error)
			}
		}
	}

	/**
	 * Resolves the [DocumentFile] handle for the download directory based on the user's
	 * preferred storage strategy.
	 *
	 * @return A [DocumentFile] pointing to either the app-private data directory or
	 * the system public gallery, depending on [defaultDownloadLocationType]. Returns
	 * null if the type is unrecognized.
	 */
	private suspend fun getUserSelectedDir(): DocumentFile? {
		return withIOContext {
			when (aioSettings.defaultDownloadLocationType) {
				PRIVATE_FOLDER -> getDirectory(INSTANCE.dataDir.absolutePath)
				SYSTEM_GALLERY -> getDefaultDirectory()
				else -> null
			}
		}
	}

	/**
	 * Resolves the [DocumentFile] representation of the default download directory.
	 *
	 * This function ensures that the [selectedDownloadDirectory] path is not empty
	 * (falling back to the system default if necessary) before attempting to retrieve
	 * the directory handle.
	 * * @return A [DocumentFile] pointing to the default directory, or `null` if the
	 * directory cannot be resolved or accessed.
	 */
	private suspend fun getDefaultDirectory(): DocumentFile? {
		return withIOContext {
			ensureDownloadDirExists()
			return@withIOContext getDirectory(selectedDownloadDirectory)
		}
	}

	/**
	 * Retrieves a [DocumentFile] handle for a specific local file system path.
	 *
	 * This utility wraps the storage abstraction layer to provide a SAF-compatible
	 * (Storage Access Framework) reference to a directory, specifically requesting
	 * write access to ensure the engine can save files there.
	 *
	 * @param internalDir The absolute file system path to resolve.
	 * @return A [DocumentFile] if the path is valid and accessible, `null` otherwise.
	 */
	private suspend fun getDirectory(internalDir: String): DocumentFile? {
		return withIOContext {
			fromFullPath(INSTANCE, internalDir, requiresWriteAccess = true)
		}
	}

	/**
	 * Validates the existence of a download path string within the current settings.
	 *
	 * If [selectedDownloadDirectory] is found to be empty or uninitialized, this
	 * function populates it with the hardcoded [AIO_DEFAULT_DOWNLOAD_PATH]. This
	 * acts as a safety "self-healing" mechanism before IO operations.
	 */
	private suspend fun ensureDownloadDirExists() {
		withIOContext {
			if (selectedDownloadDirectory.isEmpty()) {
				selectedDownloadDirectory = AIO_DEFAULT_DOWNLOAD_PATH
			}
		}
	}

	/**
	 * Initializes and creates the default download directory within the public storage space.
	 *
	 * This function performs the following steps:
	 * 1. Switches to the IO dispatcher to perform disk operations.
	 * 2. Retrieves the localized folder name from string resources.
	 * 3. Accesses the system's public download directory and creates the application-specific
	 * subfolder if it does not already exist.
	 * 4. Invokes the provided callback to report the success or failure of the operation.
	 *
	 * @return `true` if the directory exists and is ready for use,
	 * `false` otherwise. Defaults to an empty implementation.
	 */
	private suspend fun setupDefaultDownloadDir(): Boolean {
		return withIOContext {
			runCatching {
				val dirName = AIO_DOWNLOADS
				val dir = INSTANCE.getPublicDownloadDir()?.makeFolder(INSTANCE, dirName)
				dir?.exists() == true
			}.getOrDefault(false)
		}
	}

	/**
	 * Static constants and utility members for the [AIOSettings] class.
	 * Defines default values, theme modes, storage types, and sorting order identifiers.
	 */
	companion object {
		/** Logger instance for the companion scope. */
		private val logger = LogHelperUtils.from(AIOSettings::class.java)

		/** File name or preference key used to track if dark mode was manually toggled. */
		const val DARK_MODE_INDICATOR_FIE: String = "darkmode.on"

		/** Theme mode that follows the Android system configuration. */
		const val THEME_AUTO = -1

		/** Forced dark theme mode. */
		const val THEME_DARK = 1

		/** Forced light theme mode. */
		const val THEME_LIGHT = 2

		/** Storage type for the app's internal, private data directory. */
		const val PRIVATE_FOLDER = 1

		/** Storage type for the public, system-accessible media gallery. */
		const val SYSTEM_GALLERY = 2

		/** Sort downloads by date, with the most recent items appearing at the top. */
		const val SORT_DATE_NEWEST_FIRST = 3

		/** Sort downloads by date, with the oldest items appearing at the top. */
		const val SORT_DATE_OLDEST_FIRST = 4

		/** Sort downloads alphabetically by name (A to Z). */
		const val SORT_NAME_A_TO_Z = 5

		/** Sort downloads alphabetically in reverse order (Z to A). */
		const val SORT_NAME_Z_TO_A = 6

		/** Sort downloads by file size, from smallest to largest. */
		const val SORT_SIZE_SMALLEST_FIRST = 7

		/** Sort downloads by file size, from largest to smallest. */
		const val SORT_SIZE_LARGEST_FIRST = 8

		/** Prioritize video files at the top of the download list. */
		const val SORT_TYPE_VIDEOS_FIRST = 9

		/** Prioritize music/audio files at the top of the download list. */
		const val SORT_TYPE_MUSIC_FIRST = 10
	}
}