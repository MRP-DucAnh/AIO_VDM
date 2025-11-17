package app.ui.main.fragments.downloads.fragments.finished

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.text.Spanned
import android.util.LruCache
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat.getDrawable
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import app.core.AIOApp.Companion.INSTANCE
import app.core.AIOApp.Companion.aioFavicons
import app.core.AIOApp.Companion.aioSettings
import app.core.bases.BaseActivity
import app.core.engines.downloader.DownloadDataModel
import app.core.engines.downloader.DownloadDataModel.Companion.THUMB_EXTENSION
import app.core.engines.settings.AIOSettings.Companion.PRIVATE_FOLDER
import com.aio.R
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy.ALL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lib.device.DateTimeUtils.formatLastModifiedDate
import lib.files.FileSizeFormatter.humanReadableSizeOf
import lib.files.FileSystemUtility.isArchiveByName
import lib.files.FileSystemUtility.isAudioByName
import lib.files.FileSystemUtility.isDocumentByName
import lib.files.FileSystemUtility.isImageByName
import lib.files.FileSystemUtility.isProgramByName
import lib.files.FileSystemUtility.isVideo
import lib.files.FileSystemUtility.isVideoByName
import lib.networks.DownloaderUtils.getAudioPlaybackTimeIfAvailable
import lib.process.LogHelperUtils
import lib.texts.CommonTextUtils.fromHtmlStringToSpanned
import lib.texts.CommonTextUtils.getText
import lib.ui.ViewUtility
import lib.ui.ViewUtility.getThumbnailFromFile
import lib.ui.ViewUtility.rotateBitmap
import lib.ui.ViewUtility.saveBitmapToFile
import lib.ui.ViewUtility.showView
import java.io.File
import java.lang.ref.WeakReference

/**
 * ViewHolder class for displaying finished download tasks in a RecyclerView/ListView.
 *
 * This class manages the presentation and interaction of individual download items
 * in the finished downloads list. It handles:
 *
 * - **Visual Presentation**: Thumbnails, file metadata, icons, and status indicators
 * - **User Interactions**: Click and long-click events for file operations
 * - **Resource Management**: Efficient image loading, caching, and memory cleanup
 * - **Async Operations**: Coroutine-based background processing for performance
 * - **Lifecycle Awareness**: Proper cancellation and cleanup during view recycling
 *
 * The ViewHolder implements a comprehensive UI update system that loads thumbnails
 * from multiple sources (APK icons, cached images, generated thumbnails), displays
 * detailed file information, and provides visual feedback through various indicators
 * for file type, storage location, and user interaction status.
 *
 * @param layout The root view layout for this ViewHolder containing all UI components
 */
class FinishedTasksViewHolder(layout: View) : RecyclerView.ViewHolder(layout) {

	/**
	 * Logger instance for tracking ViewHolder lifecycle events, UI updates, and error conditions.
	 * Provides detailed logging for debugging view recycling, coroutine operations, and data binding.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Weak reference to the layout view to prevent memory leaks during ViewHolder recycling.
	 * Allows the layout to be garbage collected when the ViewHolder is detached while maintaining
	 * safe access to view components during the ViewHolder's active lifecycle.
	 */
	private val weakReferenceOfLayout = WeakReference(layout)

	/**
	 * Safe reference to the layout view with null safety for accessing UI components.
	 * Returns the layout view if still available, or null if the layout has been garbage collected.
	 * Essential for preventing crashes when accessing views after ViewHolder detachment.
	 */
	private val safeLayoutRef: View? get() = weakReferenceOfLayout.get()

	/**
	 * LRU cache for storing formatted metadata strings to avoid expensive recomputation.
	 * Caches up to 100 formatted metadata entries keyed by download ID for efficient
	 * view recycling and smooth scrolling performance in large download lists.
	 */
	private val detailsCache = object : LruCache<String, Spanned>(100) {}

	/**
	 * Reference to the current coroutine job managing async UI updates and event configuration.
	 * Allows cancellation of ongoing operations when the ViewHolder is recycled or destroyed
	 * to prevent memory leaks and ensure proper cleanup.
	 */
	private var currentCoroutineJob: Job? = null

	/**
	 * Coroutine scope for managing all async operations within this ViewHolder instance.
	 * Uses SupervisorJob to allow independent failure of child coroutines and Main dispatcher
	 * for immediate UI updates. Automatically cancelled when ViewHolder is destroyed.
	 */
	private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

	/**
	 * Root container layout that encompasses the entire download item row.
	 * Serves as the clickable area for user interactions and provides the foundation
	 * for all visual elements within the download list item.
	 */
	private val rootConLayout: RelativeLayout? by lazy {
		safeLayoutRef?.findViewById(R.id.button_finish_download_row)
	}

	/**
	 * Primary thumbnail image view displaying the file preview or generated thumbnail.
	 * Shows visual representation of the downloaded content including images, video thumbnails,
	 * or application icons for APK files.
	 */
	private val thumbImgView: ImageView? by lazy {
		safeLayoutRef?.findViewById(R.id.img_file_thumbnail)
	}

	/**
	 * Website favicon indicator showing the source site's icon for the download.
	 * Provides visual context about the download origin and helps users identify
	 * familiar websites in their download history.
	 */
	private val faviconImgView: ImageView? by lazy {
		safeLayoutRef?.findViewById(R.id.img_site_favicon)
	}

	/**
	 * Primary text view displaying the downloaded file name.
	 * Shows the complete file name with proper truncation and formatting for
	 * clear identification of the downloaded content.
	 */
	private val titleTxtView: TextView? by lazy {
		safeLayoutRef?.findViewById(R.id.txt_file_name)
	}

	/**
	 * Secondary text view showing comprehensive file metadata information.
	 * Displays formatted details including file size, category, modification date,
	 * and media duration in a condensed, readable format.
	 */
	private val metadataTxtView: TextView? by lazy {
		safeLayoutRef?.findViewById(R.id.txt_file_info)
	}

	/**
	 * Text view displaying media playback duration for audio and video files.
	 * Shows formatted time duration (HH:MM:SS) when available for media content.
	 */
	private val durationTxtView: TextView? by lazy {
		safeLayoutRef?.findViewById(R.id.txt_media_duration)
	}

	/**
	 * Container layout for the media duration indicator and play icon.
	 * Provides visual grouping and background for media-specific information
	 * and controls visibility based on file type.
	 */
	private val durationConLayout: View? by lazy {
		safeLayoutRef?.findViewById(R.id.container_media_duration)
	}

	/**
	 * Play icon overlay indicating media file type and providing visual affordance.
	 * Appears over thumbnails for audio and video files to suggest playable content.
	 */
	private val playIndicatorView: View? by lazy {
		safeLayoutRef?.findViewById(R.id.img_media_play_indicator)
	}

	/**
	 * File type indicator icon showing the category of the downloaded file.
	 * Uses distinct icons for images, audio, video, documents, archives, programs,
	 * and generic files to provide quick visual categorization.
	 */
	private val fileTypeImgView: ImageView? by lazy {
		safeLayoutRef?.findViewById(R.id.img_file_type_indicator)
	}

	/**
	 * File opening action indicator showing the expected interaction behavior.
	 * Changes appearance based on user settings to indicate whether single-click
	 * opens files directly or shows options menu.
	 */
	private val openFileIndicatorImgView: ImageView? by lazy {
		safeLayoutRef?.findViewById(R.id.btn_open_download_file)
	}

	/**
	 * Storage location privacy indicator showing where the file is stored.
	 * Displays lock icon for private/secured storage or folder icon for
	 * standard accessible storage locations.
	 */
	private val privateFolderImgView: ImageView? by lazy {
		safeLayoutRef?.findViewById(R.id.img_private_folder_indicator)
	}

	/**
	 * New file indicator dot showing unopened download status.
	 * Appears as a small dot for files that haven't been opened by the user yet,
	 * providing visual feedback about exploration status in the download list.
	 */
	private val newIndicatorImgView: ImageView? by lazy {
		safeLayoutRef?.findViewById(R.id.img_new_indicator)
	}

	/**
	 * Current data model associated with this ViewHolder to prevent unnecessary reloads
	 */
	private var currentDataModel: DownloadDataModel? = null

	/**
	 * Updates the ViewHolder with new download data and sets up interactive event listeners.
	 *
	 * This function serves as the primary entry point for populating the ViewHolder with
	 * download information. It cancels any existing update operations, then launches a new
	 * coroutine to refresh all UI components and configure click interactions. Includes
	 * comprehensive null safety checks to prevent crashes with invalid parameters and
	 * ensures only one update operation runs concurrently per ViewHolder instance.
	 *
	 * @param dataModel The download data to display, null to skip update
	 * @param eventListener Click event handler for user interactions, null to skip listener setup
	 */
	fun updateView(
		dataModel: DownloadDataModel?,
		eventListener: FinishedTasksClickEvents?
	) {
		// Validate input parameters before proceeding with update
		if (dataModel == null) {
			clearViewImmediately()
			return
		}

		if (eventListener == null) return
		logger.d("updateView: Starting for download ${dataModel.downloadId}")

		// Check if this is the same data to prevent unnecessary reloads
		if (currentDataModel?.downloadId == dataModel.downloadId) {
			logger.d("updateView: Same data ${dataModel.downloadId}, skipping reload")
			return
		}

		logger.d("updateView: Starting for download ${dataModel.downloadId}")
		currentDataModel = dataModel

		// Set the data model immediately to prevent showing old data during loading
		setDataImmediately(dataModel)

		// Cancel any ongoing update job to prevent race conditions
		currentCoroutineJob?.cancel()

		// Launch new coroutine for async UI updates and event configuration
		currentCoroutineJob = coroutineScope.launch {
			logger.d("updateView: Coroutine started for ${dataModel.downloadId}")
			refreshDownloadProgress(dataModel)              // Update all visual components
			setupItemClickEventListeners(eventListener, dataModel)  // Configure user interactions
		}
	}

	/**
	 * Immediately sets basic data to prevent flickering during async loading.
	 * This provides instant visual feedback while detailed data loads in background.
	 */
	private fun setDataImmediately(dataModel: DownloadDataModel) {
		thumbImgView?.setImageResource(R.drawable.image_no_thumb_available)
	}

	/**
	 * Sets file type indicator immediately based on file name extension.
	 */
	private fun setFileTypeIndicatorImmediately(fileName: String?) {
		val icon = when {
			isImageByName(fileName) -> R.drawable.ic_button_images
			isAudioByName(fileName) -> R.drawable.ic_button_audio
			isVideoByName(fileName) -> R.drawable.ic_button_video
			isDocumentByName(fileName) -> R.drawable.ic_button_document
			isArchiveByName(fileName) -> R.drawable.ic_button_archives
			isProgramByName(fileName) -> R.drawable.ic_button_programs
			else -> R.drawable.ic_button_file
		}
		fileTypeImgView?.setImageResource(icon)
	}

	/**
	 * Clears the view immediately when data is null or during recycling.
	 */
	private fun clearViewImmediately() {
		titleTxtView?.text = ""
		metadataTxtView?.text = ""
		thumbImgView?.setImageDrawable(null)
		fileTypeImgView?.setImageDrawable(null)
		durationConLayout?.visibility = GONE
		playIndicatorView?.visibility = GONE
		newIndicatorImgView?.visibility = GONE
		faviconImgView?.setImageDrawable(null)
	}

	/**
	 * Clears all ViewHolder resources and cancels ongoing operations to prevent memory leaks.
	 *
	 * This function performs comprehensive cleanup of ViewHolder resources including
	 * coroutine jobs, cached data, image references, and event listeners. Essential for
	 * calling when the ViewHolder is recycled or destroyed to ensure proper garbage
	 * collection and prevent memory leaks from retained references. Includes safe context
	 * checking and graceful error handling to prevent crashes during cleanup.
	 */
	fun clearResources(clearWeakReference: Boolean = true) {
		logger.d("clearResources: Cleaning up ViewHolder resources")
		try {
			// Cancel current coroutine job and all child coroutines
			currentCoroutineJob?.cancel()
			coroutineScope.coroutineContext.cancelChildren()

			// Check if activity context is still valid before performing Glide operations
			if (rootConLayout?.context is BaseActivity) {
				val activity = rootConLayout?.context as BaseActivity
				if (activity.isDestroyed || !activity.isActivityRunning()) {
					logger.d("clearResources: Activity not running, skipping Glide cleanup")
					return  // Skip further cleanup if context is invalid
				}
			}

			// Clear Glide image loads and references to prevent memory leaks
			thumbImgView?.let { Glide.with(it).load(R.drawable.image_no_thumb_available).into(it) }
			faviconImgView?.let { Glide.with(it).clear(it) }

			// Reset image views to release bitmap memory
			thumbImgView?.setImageDrawable(null)
			faviconImgView?.setImageDrawable(null)
			newIndicatorImgView?.setImageDrawable(null)

			// Remove all event listeners to prevent callback leaks
			rootConLayout?.setOnClickListener(null)
			rootConLayout?.setOnLongClickListener(null)
			rootConLayout?.isClickable = false

			// Clear view tag to break reference cycles
			if (clearWeakReference) {
				// Clear cached metadata to free memory
				detailsCache.evictAll()

				safeLayoutRef?.tag = null
				weakReferenceOfLayout.clear()
			}

			logger.d("clearResources: Cleanup completed successfully")
		} catch (error: Exception) {
			// Log cleanup errors but don't crash - ensures cleanup continues
			logger.e("clearResources: Error during cleanup - ${error.message}", error)
		}
	}

	/**
	 * Cancels all ongoing coroutine operations and cleans up the coroutine scope.
	 *
	 * This function immediately terminates all active and pending coroutine operations
	 * within the ViewHolder's coroutine scope. Essential for preventing memory leaks
	 * and ensuring proper cleanup when the ViewHolder is recycled or destroyed. Should
	 * be called during ViewHolder recycling or when the parent fragment is destroyed.
	 */
	fun cancelAll() {
		logger.d("cancelAll: Cancelling entire coroutine scope")
		coroutineScope.cancel()
	}

	/**
	 * Sets up click and long-click event listeners for the download item with coroutine safety.
	 *
	 * This suspend function configures both standard click and long-click interactions
	 * for the download item container. Ensures listeners are only set when the coroutine
	 * is active and executes on the main thread for proper UI event handling. Logs user
	 * interactions for debugging and analytics purposes while maintaining clean separation
	 * of concerns between view presentation and event handling.
	 *
	 * @param eventListener The interface implementation handling download item interactions
	 * @param dataModel The download data model associated with this item for event callbacks
	 */
	private suspend fun setupItemClickEventListeners(
		eventListener: FinishedTasksClickEvents,
		dataModel: DownloadDataModel
	) {
		withContext(Dispatchers.Main) {
			// Verify coroutine is still active before setting up listeners
			if (!isActive) {
				logger.d("setupItemClickEventListeners: Coroutine not active")
				return@withContext
			}
			logger.d("setupItemClickEventListeners: Setting up listeners for ${dataModel.downloadId}")

			// Configure the root container layout with interactive properties
			rootConLayout?.apply {
				isClickable = true  // Enable click interactions for the entire item

				// Set standard click listener for primary interactions
				setOnClickListener {
					logger.d("ClickListener: Download ${dataModel.downloadId} clicked")
					eventListener.onFinishedDownloadClick(dataModel)
				}

				// Set long-click listener for secondary interactions and context menus
				setOnLongClickListener {
					logger.d("LongClickListener: Download ${dataModel.downloadId} long-clicked")
					eventListener.onFinishedDownloadLongClick(dataModel); true
				}
			}
		}
	}

	/**
	 * Refreshes all UI components for a download item with comprehensive data binding.
	 *
	 * This suspend function orchestrates the complete UI update process for a single
	 * download item, calling all individual update methods in sequence. Ensures consistent
	 * visual presentation by refreshing all display elements including title, metadata,
	 * thumbnails, icons, and indicators. Uses structured logging to track update progress
	 * and performance for debugging purposes.
	 *
	 * @param dataModel The download data model containing all information for UI presentation
	 */
	private suspend fun refreshDownloadProgress(dataModel: DownloadDataModel) {
		logger.d("refreshDownloadProgress: Starting UI refresh for ${dataModel.downloadId}")

		// Update all UI components in logical sequence
		updateFilesTitle(dataModel)               // Primary file name display
		updateFilesMetaInfo(dataModel)            // Secondary metadata information
		updateFaviconInfo(dataModel)              // Website source favicon
		updateFileTypeIndicator(dataModel)        // File category icon
		updatePrivateFolderIndicator(dataModel)   // Storage location indicator
		updateOpenFileIndicator(dataModel)        // File opening behavior icon
		updateNewFileIndicator(dataModel)         // Unread file indicator
		updateThumbnailInfo(dataModel)            // Main file thumbnail/image
		logger.d("refreshDownloadProgress: Completed UI refresh for ${dataModel.downloadId}")
	}

	/**
	 * Updates the file name title in the UI with safe main thread execution.
	 *
	 * This suspend function sets the downloaded file name as the primary title text
	 * in the list item. Ensures UI updates occur on the main thread while maintaining
	 * coroutine cancellation awareness to prevent updates on destroyed coroutines.
	 * Includes logging for tracking title assignment and debugging display issues.
	 *
	 * @param downloadDataModel The download data model containing the file name to display
	 */
	private suspend fun updateFilesTitle(downloadDataModel: DownloadDataModel) {
		withContext(Dispatchers.Main) {
			titleTxtView?.let {
				if (!isActive) return@withContext
				// Set the file name as the primary title text
				it.text = downloadDataModel.fileName
				logger.d("updateFilesTitle: Set title to ${downloadDataModel.fileName}")
			}
		}
	}

	/**
	 * Updates the file metadata information with intelligent caching and dynamic generation.
	 *
	 * This suspend function manages metadata display with a multi-layer caching strategy
	 * to avoid expensive recomputation. First checks memory cache for existing formatted
	 * metadata, and if missing, dynamically generates comprehensive file information including
	 * category, size, playback duration, and modification date. Automatically discovers and
	 * stores playback time for media files and updates persistent storage when new information
	 * is found. Uses HTML formatting for rich text display with proper spacing and styling.
	 *
	 * @param dataModel The download data model containing all metadata for display
	 */
	private suspend fun updateFilesMetaInfo(dataModel: DownloadDataModel) {
		val downloadId = dataModel.downloadId.toString()

		// Check memory cache first to avoid expensive metadata regeneration
		val cacheDetails = detailsCache.get(downloadId)
		if (cacheDetails != null) {
			logger.d("updateFilesMetaInfo: Cache hit for $downloadId")
			withContext(Dispatchers.Main) {
				if (!isActive) return@withContext
				// Use cached metadata and update playback time display
				metadataTxtView?.text = cacheDetails
				updatePlaybackTimeInfo(dataModel)
			}
			return
		}

		logger.d("updateFilesMetaInfo: Cache miss for $downloadId, generating metadata")

		// Extract and format all metadata components
		val category = dataModel.getUpdatedCategoryName(true)
		val fileSize = humanReadableSizeOf(dataModel.fileSize.toDouble())

		// Attempt to discover playback time if not already available
		val playbackTime = dataModel.mediaFilePlaybackDuration.ifEmpty {
			getAudioPlaybackTimeIfAvailable(dataModel)
		}

		// Store discovered playback time in persistent storage for future use
		if (dataModel.mediaFilePlaybackDuration.isEmpty() && playbackTime.isNotEmpty()) {
			logger.d("updateFilesMetaInfo: Discovered playback time $playbackTime for $downloadId")
			dataModel.mediaFilePlaybackDuration = playbackTime
			dataModel.updateInStorage()
		}

		// Format modification date for display
		val modifyDate = formatLastModifiedDate(dataModel.lastModifiedTimeDate)

		// Create formatted HTML string with all metadata components
		val metaInfoDetail = safeLayoutRef?.context?.getString(
			R.string.title_b_b_b_date_b,
			getText(R.string.title_info),
			category.removePrefix("AIO"),  // Clean category name
			fileSize, playbackTime, modifyDate
		)?.let { fromHtmlStringToSpanned(it) }

		if (!metaInfoDetail.isNullOrEmpty()) {
			// Cache the formatted metadata for future reuse
			detailsCache.put(dataModel.downloadId.toString(), metaInfoDetail)

			withContext(Dispatchers.Main) {
				if (!isActive) return@withContext
				// Display the generated metadata and update playback time
				metadataTxtView?.text = metaInfoDetail
				updatePlaybackTimeInfo(dataModel)
			}
		}
	}

	/**
	 * Updates the media playback time display for audio and video files.
	 *
	 * This suspend function controls the visibility and content of the media duration
	 * indicator. Shows the playback duration with a play icon for media files (audio/video)
	 * and hides the indicator for non-media files. Cleans up duration formatting by
	 * removing parentheses for cleaner display. Executes on main thread for safe UI updates.
	 *
	 * @param dataModel The download data model containing media duration information
	 */
	private suspend fun updatePlaybackTimeInfo(dataModel: DownloadDataModel) {
		val fileName = dataModel.fileName
		// Determine if file is media (audio or video) based on file name
		val isMedia = isVideoByName(fileName) || isAudioByName(fileName)
		// Clean duration format by removing parentheses
		val mediaDuration = dataModel.mediaFilePlaybackDuration.replace("(", "").replace(")", "")

		withContext(Dispatchers.Main) {
			if (!isActive) return@withContext
			if (isMedia && mediaDuration.isNotEmpty()) {
				// Show duration indicator for media files with valid duration
				logger.d("updatePlaybackTimeInfo: Showing duration $mediaDuration for $fileName")
				showView(durationConLayout, true)      // Show duration container
				showView(playIndicatorView, true)      // Show play icon
				durationTxtView?.text = mediaDuration   // Set duration text
			} else {
				// Hide duration indicator for non-media files or empty duration
				logger.d("updatePlaybackTimeInfo: Hiding duration for $fileName")
				playIndicatorView?.visibility = GONE      // Hide play icon
				durationConLayout?.visibility = GONE      // Hide duration container
			}
		}
	}

	/**
	 * Determines if video thumbnails should be hidden based on user privacy settings and file type.
	 *
	 * This function checks both the global application settings for video thumbnail visibility
	 * and verifies if the current file is actually a video. Returns true only when both conditions
	 * are met: the file is a video AND the user has enabled video thumbnail hiding in settings.
	 * Used to respect user privacy preferences for video content in the downloads list.
	 *
	 * @param dataModel The download data model containing file information and user settings
	 * @return True if video thumbnails should be hidden for privacy reasons, false otherwise
	 */
	private fun isVideoThumbnailNotAllowed(dataModel: DownloadDataModel): Boolean {
		// Check user preference for video thumbnail visibility from global settings
		val isVideoHidden = dataModel.globalSettings.downloadHideVideoThumbnail
		// Verify if the current file is actually a video file type
		val isVideoFile = isVideo(dataModel.getDestinationDocumentFile())
		// Thumbnail is not allowed only if both conditions are true
		val result = isVideoFile && isVideoHidden
		if (result) {
			logger.d("isVideoThumbnailNotAllowed: Video thumbnail hidden for privacy")
		}
		return result
	}

	/**
	 * Updates the website favicon display for a download item with privacy-aware loading.
	 *
	 * This suspend function manages favicon loading with multiple fallback strategies. First
	 * sets a default favicon, then checks privacy settings for video content. If allowed,
	 * attempts to load the cached favicon from the download source website. Handles missing
	 * favicon files, network errors, and privacy restrictions gracefully with appropriate
	 * fallback icons. Uses coroutine context switching for safe UI updates.
	 *
	 * @param dataModel The download data model containing website referrer information
	 */
	private suspend fun updateFaviconInfo(dataModel: DownloadDataModel) {
		val defaultFaviconResId = R.drawable.ic_image_default_favicon
		// Set default favicon immediately as placeholder
		withContext(Dispatchers.Main) {
			if (!isActive) return@withContext
			faviconImgView?.setImageResource(defaultFaviconResId)
		}

		// Check privacy settings - use default favicon if video thumbnails are restricted
		if (isVideoThumbnailNotAllowed(dataModel)) {
			logger.d("updateFaviconInfo: Using default favicon due to privacy settings")
			withContext(Dispatchers.Main) {
				if (!isActive) return@withContext
				faviconImgView?.let {
					Glide.with(it).load(defaultFaviconResId)
						.placeholder(defaultFaviconResId).into(it)
				}
			}
			return
		}

		// Attempt to load cached favicon from the download source website
		try {
			aioFavicons.getFavicon(dataModel.siteReferrer)?.let { faviconFilePath ->
				val faviconImgFile = File(faviconFilePath)
				// Verify favicon file exists and is accessible
				if (!faviconImgFile.exists() || !faviconImgFile.isFile) {
					logger.d("updateFaviconInfo: Favicon file not found at $faviconFilePath")
					return
				}

				logger.d("updateFaviconInfo: Loading favicon from $faviconFilePath")
				val faviconImgURI = faviconImgFile.toUri()
				withContext(Dispatchers.Main) {
					if (!isActive) return@withContext
					// Make favicon visible and load with Glide for smooth display
					showView(faviconImgView, true)
					faviconImgView?.let {
						Glide.with(it).load(faviconImgURI)
							.placeholder(defaultFaviconResId).into(it)
					}
				}
			} ?: logger.d("updateFaviconInfo: No favicon available for ${dataModel.siteReferrer}")
		} catch (error: Exception) {
			// Handle any errors during favicon loading with graceful fallback
			logger.e("updateFaviconInfo: Error loading favicon - ${error.message}", error)
			faviconImgView?.setImageResource(defaultFaviconResId)
		}
	}

	/**
	 * Updates the thumbnail image for a download item with intelligent caching and fallback strategies.
	 *
	 * This suspend function manages the complete thumbnail loading pipeline including privacy checks,
	 * APK icon extraction, cached thumbnail reuse, and dynamic thumbnail generation. Implements
	 * a multi-tier approach: first checks privacy settings, then attempts APK icon loading, falls
	 * back to cached thumbnails, and finally generates new thumbnails when needed. Handles image
	 * orientation correction and persistent caching for optimal performance across app sessions.
	 *
	 * @param dataModel The download data model containing file information and thumbnail metadata
	 */
	private suspend fun updateThumbnailInfo(dataModel: DownloadDataModel) {
		// Retrieve file information and default thumbnail resources
		val destinationFile = dataModel.getDestinationFile()
		val defaultThumb = dataModel.getThumbnailDrawableID()
		val defaultThumbDrawable = getDrawable(INSTANCE.resources, defaultThumb, null)
		withContext(Dispatchers.Main) { thumbImgView?.setImageDrawable(defaultThumbDrawable) }

		logger.d("updateThumbnailInfo: Starting for ${dataModel.fileName}")

		// Tier 1: Check privacy settings for video thumbnails
		if (isVideoThumbnailNotAllowed(dataModel)) {
			logger.d("updateThumbnailInfo: Using default thumbnail due to privacy settings")
			withContext(Dispatchers.Main) {
				if (!isActive) return@withContext
				// Display default thumbnail when privacy settings restrict video thumbnails
				thumbImgView?.let {
					Glide.with(it).load(defaultThumbDrawable)
						.placeholder(defaultThumbDrawable)
						.diskCacheStrategy(ALL)
						.into(it)
				}
			}
			return
		}

		// Tier 2: Attempt APK icon extraction for application files
		if (loadApkThumbnail(dataModel, thumbImgView, defaultThumb)) {
			logger.d("updateThumbnailInfo: APK thumbnail loaded successfully")
			return
		}

		// Tier 3: Check for existing cached thumbnail to avoid regeneration
		val cachedThumbPath = dataModel.thumbPath
		if (cachedThumbPath.isNotEmpty()) {
			logger.d("updateThumbnailInfo: Loading cached thumbnail from $cachedThumbPath")
			withContext(Dispatchers.Main) {
				if (!isActive) return@withContext
				// Load cached thumbnail using Glide for efficient display
				loadBitmapWithGlide(thumbImgView, dataModel.thumbPath, defaultThumb)
			}
			return
		}

		// Tier 4: Generate new thumbnail dynamically from file content
		logger.d("updateThumbnailInfo: Generating new thumbnail for ${dataModel.fileName}")
		val bitmapFromFile = getThumbnailFromFile(
			targetFile = destinationFile,
			thumbnailUrl = dataModel.videoInfo?.videoThumbnailUrl,
			requiredThumbWidth = 420
		)

		// Process and cache the generated thumbnail if successful
		if (bitmapFromFile != null) {
			logger.d("updateThumbnailInfo: Thumbnail generated, processing and caching")

			// Correct orientation for portrait images by rotating 270 degrees
			val isPortrait = bitmapFromFile.height > bitmapFromFile.width
			val rotatedBitmap = if (isPortrait) rotateBitmap(bitmapFromFile, 270f) else bitmapFromFile

			// Generate unique thumbnail filename and save to persistent storage
			val thumbnailName = "${dataModel.downloadId}$THUMB_EXTENSION"
			saveBitmapToFile(rotatedBitmap, thumbnailName)?.let { filePath ->
				// Update data model with new thumbnail path and persist changes
				dataModel.thumbPath = filePath
				dataModel.updateInStorage()
				logger.d("updateThumbnailInfo: Thumbnail saved to $filePath")

				// Display the newly cached thumbnail
				withContext(Dispatchers.Main) {
					if (!isActive) return@withContext
					loadBitmapWithGlide(thumbImgView, dataModel.thumbPath, defaultThumb)
				}
			}
		} else {
			// Fallback when thumbnail generation fails
			logger.d("updateThumbnailInfo: No thumbnail generated, using default")
		}
	}

	/**
	 * Loads a bitmap image from local file path into an ImageView using Glide with placeholder support.
	 *
	 * This utility function provides efficient image loading from local storage with proper
	 * URI conversion and placeholder management. Uses Glide's built-in caching and optimization
	 * for smooth image display while handling file system access and memory management automatically.
	 * Includes logging for debugging image loading operations and path verification.
	 *
	 * @param target The ImageView where the loaded bitmap will be displayed
	 * @param filePath The local filesystem path to the image file to load
	 * @param placeHolder The drawable resource ID to display during loading or if image fails to load
	 */
	private fun loadBitmapWithGlide(target: ImageView?, filePath: String, placeHolder: Int) {
		target?.let {
			logger.d("loadBitmapWithGlide: Loading bitmap from $filePath")
			// Convert file path to URI for Glide compatibility and efficient loading
			val imgURI = File(filePath).toUri()
			// Load image with Glide, using placeholder for loading states and fallback
			Glide.with(it).load(imgURI).placeholder(placeHolder).diskCacheStrategy(ALL).into(it)
		}
	}

	/**
	 * Updates the file type indicator icon based on the downloaded file's extension and format.
	 *
	 * This suspend function analyzes the file name to determine the appropriate category
	 * (image, audio, video, document, archive, program) and displays the corresponding
	 * icon. Uses file extension analysis functions to classify files and provides visual
	 * feedback about the download content type. Executes on main thread with coroutine
	 * cancellation awareness for proper lifecycle management.
	 *
	 * @param dataModel The download data model containing the file name for type analysis
	 */
	private suspend fun updateFileTypeIndicator(dataModel: DownloadDataModel) {
		withContext(Dispatchers.Main) {
			// Check coroutine activity to prevent UI updates on cancelled coroutines
			if (!isActive) return@withContext

			// Determine appropriate icon based on file type classification
			val icon = when {
				isImageByName(dataModel.fileName) -> {
					logger.d("updateFileTypeIndicator: Image file type")
					R.drawable.ic_button_images  // Image icon for photos, pictures, graphics
				}
				isAudioByName(dataModel.fileName) -> {
					logger.d("updateFileTypeIndicator: Audio file type")
					R.drawable.ic_button_audio   // Audio icon for music, sound files
				}
				isVideoByName(dataModel.fileName) -> {
					logger.d("updateFileTypeIndicator: Video file type")
					R.drawable.ic_button_video   // Video icon for movies, clips
				}
				isDocumentByName(dataModel.fileName) -> {
					logger.d("updateFileTypeIndicator: Document file type")
					R.drawable.ic_button_document // Document icon for PDF, text, office files
				}
				isArchiveByName(dataModel.fileName) -> {
					logger.d("updateFileTypeIndicator: Archive file type")
					R.drawable.ic_button_archives // Archive icon for ZIP, RAR, compressed files
				}
				isProgramByName(dataModel.fileName) -> {
					logger.d("updateFileTypeIndicator: Program file type")
					R.drawable.ic_button_programs // Program icon for APK, EXE, executable files
				}
				else -> {
					logger.d("updateFileTypeIndicator: Generic file type")
					R.drawable.ic_button_file    // Generic file icon for unrecognized types
				}
			}
			// Load and display the selected file type icon using Glide
			fileTypeImgView?.let { Glide.with(it).load(icon).diskCacheStrategy(ALL).into(it) }
		}
	}

	/**
	 * Updates the private folder indicator icon based on download storage location.
	 *
	 * This suspend function determines whether the downloaded file is stored in a private
	 * (secured) folder or standard storage location and updates the indicator icon accordingly.
	 * Uses coroutine context switching to ensure UI updates occur on the main thread while
	 * maintaining coroutine cancellation awareness for proper lifecycle management.
	 *
	 * @param dataModel The download data model containing storage location settings
	 */
	private suspend fun updatePrivateFolderIndicator(dataModel: DownloadDataModel) {
		withContext(Dispatchers.Main) {
			// Check coroutine activity to prevent UI updates on cancelled coroutines
			if (!isActive) return@withContext

			// Determine storage location type from global settings
			val globalSettings = dataModel.globalSettings
			val downloadLocation = globalSettings.defaultDownloadLocation
			val isPrivate = downloadLocation == PRIVATE_FOLDER

			// Select appropriate icon based on storage location privacy
			val icon = if (isPrivate) {
				logger.d("updatePrivateFolderIndicator: Private folder location")
				R.drawable.ic_button_lock  // Lock icon for private/secured storage
			} else {
				logger.d("updatePrivateFolderIndicator: Standard folder location")
				R.drawable.ic_button_folder  // Folder icon for standard storage
			}

			// Load and display the selected icon using Glide for smooth rendering
			privateFolderImgView?.let { Glide.with(it).load(icon).diskCacheStrategy(ALL).into(it) }
		}
	}

	/**
	 * Updates the open file indicator icon based on user preference settings.
	 *
	 * This suspend function adjusts the file opening indicator to reflect whether
	 * single-click behavior opens files directly or shows playback options. The icon
	 * provides visual feedback about the expected interaction behavior for downloaded files.
	 * Executes on main thread to ensure safe UI updates while maintaining suspend capability.
	 *
	 * @param dataModel The download data model (parameter maintained for consistency)
	 */
	private suspend fun updateOpenFileIndicator(dataModel: DownloadDataModel) {
		withContext(Dispatchers.Main) {
			// Determine icon based on single-click file opening preference
			val imgResId = if (!aioSettings.openDownloadedFileOnSingleClick)
				R.drawable.ic_button_open_v2  // Open icon when single-click shows options
			else
				R.drawable.ic_button_player   // Play icon when single-click opens directly

			// Apply the selected icon resource directly to the ImageView
			openFileIndicatorImgView?.setImageResource(imgResId)
		}
	}

	/**
	 * Updates the new file indicator visibility and appearance based on user interaction.
	 *
	 * This suspend function controls the display of the "new" indicator dot that shows
	 * whether a downloaded file has been opened by the user. The indicator is hidden
	 * after the user opens the file for the first time, providing visual feedback about
	 * file exploration status. Uses Glide for consistent icon loading with placeholder support.
	 *
	 * @param dataModel The download data model containing user interaction tracking
	 */
	private suspend fun updateNewFileIndicator(dataModel: DownloadDataModel) {
		withContext(Dispatchers.Main) {
			newIndicatorImgView?.let {
				// Load the new indicator dot icon with smooth Glide loading
				Glide.with(it)
					.load(R.drawable.ic_button_dot)           // Dot icon for new files
					.placeholder(R.drawable.rounded_transparent)  // Transparent placeholder
					.diskCacheStrategy(ALL)  // Cache strategy for fast load
					.into(it)

				// Show indicator only for unopened files, hide for files user has opened
				it.visibility = if (dataModel.hasUserOpenedTheFile) GONE else VISIBLE
			}
		}
	}

	/**
	 * Loads and displays APK application icons as thumbnails with caching and error handling.
	 *
	 * This suspend function extracts the application icon from APK files using Android's
	 * package manager, displays it in the target ImageView, and caches the extracted icon
	 * as a bitmap for future use. Handles various failure scenarios including missing APK
	 * files, corrupted packages, and extraction errors with appropriate fallback behavior.
	 * Uses coroutine context switching for optimal performance between UI and background operations.
	 *
	 * @param dataModel The download data model containing APK file information and cache path
	 * @param target The ImageView where the extracted APK icon will be displayed
	 * @param placeHolder The placeholder drawable resource ID to use during loading or on failure
	 * @return True if the APK icon was successfully loaded and displayed, false otherwise
	 */
	private suspend fun loadApkThumbnail(
		dataModel: DownloadDataModel,
		target: ImageView?,
		placeHolder: Int
	): Boolean = withContext(Dispatchers.Main) {
		// Check coroutine activity state to prevent operations on cancelled coroutines
		if (!isActive) {
			logger.d("loadApkThumbnail: Coroutine not active")
			return@withContext false
		}

		//Early return if the target image view is null
		if (target == null) {
			logger.d("Target image view is null: Returning status as false")
			return@withContext false
		}

		// Check for cached thumbnail first to avoid repeated APK parsing
		val cachedThumbPath = dataModel.thumbPath
		if (cachedThumbPath.isNotEmpty()) {
			logger.d("loadApkThumbnail: Using cached APK thumbnail")
			loadBitmapWithGlide(target, dataModel.thumbPath, placeHolder)
			return@withContext true
		}

		// Validate that the target file exists and is actually an APK file
		val apkFile = dataModel.getDestinationFile()
		if (!apkFile.exists() || !apkFile.name.endsWith(".apk", true)) {
			logger.d("loadApkThumbnail: Not an APK file - ${apkFile.name}")
			Glide.with(target).load(placeHolder)
				.diskCacheStrategy(ALL).into(target)
			return@withContext false
		}

		logger.d("loadApkThumbnail: Extracting APK icon from ${apkFile.name}")
		val packageManager: PackageManager = target.context.packageManager
		try {
			// Extract package information from APK file to access application metadata
			val packageInfo: PackageInfo? =
				packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_ACTIVITIES)

			// Process application info if package was successfully parsed
			packageInfo?.applicationInfo?.let { appInfo ->
				// Set source directories to enable icon loading from APK context
				appInfo.sourceDir = apkFile.absolutePath
				appInfo.publicSourceDir = apkFile.absolutePath

				// Load the application icon using package manager
				val appIconDrawable: Drawable = appInfo.loadIcon(packageManager)

				// Display the extracted icon using Glide with placeholder
				Glide.with(target).load(appIconDrawable)
					.placeholder(placeHolder)
					.diskCacheStrategy(ALL).into(target)

				logger.d("loadApkThumbnail: APK icon loaded, caching for future use")

				// Cache the extracted icon as bitmap file in background thread
				withContext(Dispatchers.IO) {
					ViewUtility.drawableToBitmap(appIconDrawable)?.let { bmp ->
						val thumbnailName = "${dataModel.downloadId}$THUMB_EXTENSION"
						saveBitmapToFile(bmp, thumbnailName)?.let { filePath ->
							// Update data model with cached thumbnail path and persist
							dataModel.thumbPath = filePath
							dataModel.updateInStorage()
							logger.d("loadApkThumbnail: APK thumbnail cached to $filePath")
						}
					}
				}
				true  // Successfully extracted and displayed APK icon
			} ?: run {
				// Handle case where no package info could be extracted from APK
				logger.d("loadApkThumbnail: No package info found for APK")
				false
			}
		} catch (error: Exception) {
			// Handle any exceptions during APK parsing or icon extraction
			logger.e("loadApkThumbnail: Error extracting APK icon - ${error.message}", error)

			// Apply fallback display settings and show placeholder
			target.apply {
				scaleType = ImageView.ScaleType.FIT_CENTER
				setPadding(0, 0, 0, 0)
				Glide.with(target).load(placeHolder)
					.placeholder(placeHolder)
					.diskCacheStrategy(ALL).into(target)
			}
			false  // Failed to extract APK icon
		}
	}
}