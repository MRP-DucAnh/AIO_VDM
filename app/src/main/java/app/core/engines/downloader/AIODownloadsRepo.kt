package app.core.engines.downloader

import app.core.engines.downloader.AIODownloadsRepo.storeDownloadRecord
import app.core.engines.downloader.DownloadStatus.COMPLETE
import app.core.engines.objectbox.*
import app.core.engines.settings.*
import app.core.engines.video_parser.parsers.*
import io.objectbox.*
import lib.process.*

/**
 * Repository for managing all database operations related to the All-In-One (AIO) downloader.
 *
 * This repository acts as the central Data Access Layer (DAL) for the application's
 * download-related entities. It utilizes ObjectBox for high-performance persistence and
 * provides a suspended API designed for use with Kotlin Coroutines to ensure all
 * database-heavy tasks occur on background threads.
 *
 * Key Responsibilities:
 * - **CRUD Operations:** Handling the lifecycle of [AIODownload] records and their children.
 * - **Relational Hydration:** Reconstructing complex object graphs from flat ID references.
 * - **Transactional Integrity:** Ensuring cascading saves and deletes are performed
 * atomically using [BoxStore.runInTx].
 * - **Optimized Querying:** Leveraging native database indexes for filtering active,
 * completed, or specific download models.
 *
 * All public methods in this object are thread-safe and utilize the IO dispatcher
 * context internally.
 */
object AIODownloadsRepo {

	/**
	 * Utility for logging events and errors specific to this database controller.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * The primary entry point for ObjectBox database operations.
	 * Initialized lazily to ensure the database is only opened when first accessed.
	 */
	private val globalObjectBox: BoxStore by lazy {
		logger.d("Initializing BoxStore via ObjectBoxManager...")
		ObjectBoxManager.getBoxStore().also {
			logger.i("ObjectBox BoxStore successfully initialized.")
		}
	}

	/**
	 * Data access for parent [AIODownload] records.
	 * This is the central entity that links all other metadata together.
	 */
	private val downloadsBox: Box<AIODownload> by lazy {
		logger.v("Creating Box instance for: AIODownload")
		globalObjectBox.boxFor(AIODownload::class.java)
	}

	/**
	 * Data access for [AIOVideoInfo] entities containing high-level video metadata
	 * (titles, durations, etc.).
	 */
	private val videoInfoBox: Box<AIOVideoInfo> by lazy {
		logger.v("Creating Box instance for: AIOVideoInfo")
		globalObjectBox.boxFor(AIOVideoInfo::class.java)
	}

	/**
	 * Data access for [AIOVideoFormat] entities.
	 * Stores specific format details like resolution, bitrate, and file extensions.
	 */
	private val videoFormatBox: Box<AIOVideoFormat> by lazy {
		logger.v("Creating Box instance for: AIOVideoFormat")
		globalObjectBox.boxFor(AIOVideoFormat::class.java)
	}

	/**
	 * Data access for [AIORemoteFileInfo] entities.
	 * Manages server-side file metadata and remote availability details.
	 */
	private val remoteFileInfoBox: Box<AIORemoteFileInfo> by lazy {
		logger.v("Creating Box instance for: AIORemoteFileInfo")
		globalObjectBox.boxFor(AIORemoteFileInfo::class.java)
	}

	/**
	 * Data access for [AIOSettings] entities.
	 * Stores user preferences or specific configurations associated with a download.
	 */
	private val settingsBox: Box<AIOSettings> by lazy {
		logger.v("Creating Box instance for: AIOSettings")
		globalObjectBox.boxFor(AIOSettings::class.java)
	}

	/**
	 * Persists a complete [AIODownload] record and all its associated metadata to the database.
	 *
	 * This function performs a cascading save operation within a single database transaction
	 * to ensure data integrity. It handles the following:
	 * 1. Assigns a unique ID to the [downloadRec] if it doesn't already have one.
	 * 2. Links and persists nested objects: [AIODownload.videoInfo],
	 * [AIOVideoInfo.videoFormats], [AIODownload.remoteFileInfo], and [AIODownload.config].
	 * 3. Updates foreign key references back to the parent download ID across all child entities.
	 *
	 * The operation is executed on the IO dispatcher to prevent UI blocking. Any errors
	 * encountered during the process are caught, logged, and will not propagate to the caller.
	 *
	 * @param downloadRec The download record containing the metadata and related objects to be stored.
	 */
	@JvmStatic
	suspend fun storeDownloadRecord(downloadRec: AIODownload) {
		withIOContext {
			runCatching {
				logger.d(
					"Transaction Started: Storing download record [ID: ${downloadRec.id}, " +
						"Title: ${downloadRec.fileName.ifEmpty { "Unknown" }}]"
				)

				val startTime = System.currentTimeMillis()

				globalObjectBox.runInTx {
					// 1. Initial put to ensure we have a valid parent ID
					val downloadId = if (downloadRec.id == 0L) {
						downloadsBox.put(downloadRec).also {
							logger.v("Assigned new Download ID: $it")
						}
					} else downloadRec.id

					// 2. Handle Video Info & nested Video Formats
					downloadRec.videoInfo?.let { videoInfo ->
						videoInfo.downloadId = downloadId
						videoInfo.numberOfVideoFormats = videoInfo.videoFormats.size
						val videoInfoId = videoInfoBox.put(videoInfo)
						downloadRec.videoInfoId = videoInfoId

						logger.v(
							"Linking ${videoInfo.videoFormats.size} " +
								"formats to VideoInfo ID: $videoInfoId"
						)

						// Performance Optimization: Use bulk put for formats
						videoInfo.videoFormats.forEach { it.parentVideoInfoId = videoInfoId }
						videoFormatBox.put(videoInfo.videoFormats)
					}

					// 3. Handle Standalone Video Format
					downloadRec.videoFormat?.let { videoFormat ->
						videoFormat.downloadId = downloadId
						downloadRec.videoFormatId = videoFormatBox.put(videoFormat)
						logger.v("Linked standalone VideoFormat ID: ${downloadRec.videoFormatId}")
					}

					// 4. Handle Remote File Info
					downloadRec.remoteFileInfo?.let { remoteFileInfo ->
						remoteFileInfo.downloadId = downloadId
						downloadRec.remoteFileInfoId = remoteFileInfoBox.put(remoteFileInfo)
						logger.v("Linked RemoteFileInfo ID: ${downloadRec.remoteFileInfoId}")
					}

					// 5. Handle Settings/Config
					downloadRec.config.let { settings ->
						settings.downloadId = downloadId
						downloadRec.configId = settingsBox.put(settings)
						logger.v("Linked Config ID: ${downloadRec.configId}")
					}

					// Final sync of the parent object with all updated internal IDs
					downloadsBox.put(downloadRec)
				}

				val duration = System.currentTimeMillis() - startTime
				logger.i(
					"Transaction Successful: Download ${downloadRec.id} " +
						"fully persisted in ${duration}ms"
				)

			}.onFailure { error ->
				logger.e(
					"CRITICAL FAILURE: Could not store download record " +
						"[ID: ${downloadRec.id}]. Database state might be inconsistent.", error
				)
			}
		}
	}

	/**
	 * Synchronizes and executes the deletion of an [AIODownload] and all its relational
	 * dependencies.
	 *
	 * This function performs a manual cascading delete within a transaction to maintain
	 * database cleanliness. It systematically removes:
	 * 1. Linked [AIOVideoInfo] records and their nested video formats.
	 * 2. Standalone [AIOVideoFormat] entries.
	 * 3. [AIORemoteFileInfo] metadata.
	 * 4. Associated [AIOSettings] configurations.
	 * 5. The primary [AIODownload] record itself.
	 *
	 * If the provided [download] object contains preloaded references, they are used for
	 * deletion; otherwise, the function queries the database for any orphaned records
	 * associated with the download ID.
	 *
	 * Any failures during the deletion process are caught and logged to prevent
	 * transactional crashes from propagating.
	 *
	 * @param download The [AIODownload] instance to be completely purged from the database.
	 */
	suspend fun deleteAssociatedRecords(download: AIODownload) {
		withIOContext {
			val dId = download.id
			logger.i("Starting cascading deletion for Download ID: $dId")

			val startTime = System.currentTimeMillis()

			// Use a transaction to ensure all-or-nothing deletion
			globalObjectBox.runCatching {
				globalObjectBox.runInTx {
					// 1. Handle Video Info & nested Video Formats
					val vInfo = download.videoInfo
					if (vInfo != null) {
						logger.v("Removing VideoInfo from memory reference: ${vInfo.id}")
						removeVideoInfoRecords(vInfo.id)
					} else {
						val vdInfoDownloadId = AIOVideoInfo_.downloadId
						videoInfoBox.query(vdInfoDownloadId.equal(dId))
							.build().use { query ->
								val ids = query.findIds()
								logger.v(
									"Found ${ids.size} orphaned VideoInfo records " +
										"to remove for Download: $dId"
								)
								ids.forEach { removeVideoInfoRecords(it) }
							}
					}

					// 2. Handle Standalone Video Format
					val vFormat = download.videoFormat
					if (vFormat != null) {
						logger.v("Removing standalone VideoFormat: ${vFormat.id}")
						videoFormatBox.remove(vFormat.id)
					} else {
						val vdFormatDownloadId = AIOVideoFormat_.downloadId
						videoFormatBox.query(vdFormatDownloadId.equal(dId))
							.build().use { query ->
								val count = query.remove()
								if (count > 0) {
									logger.v("Query-removed $count orphaned VideoFormat records")
								}
							}
					}

					// 3. Handle Remote File Info
					val rInfo = download.remoteFileInfo
					if (rInfo != null) {
						logger.v("Removing RemoteFileInfo: ${rInfo.id}")
						remoteFileInfoBox.remove(rInfo.id)
					} else {
						val remoteInfoDownloadId = AIORemoteFileInfo_.downloadId
						remoteFileInfoBox.query(remoteInfoDownloadId.equal(dId))
							.build().use { query ->
								val count = query.remove()
								if (count > 0) {
									logger.v("Query-removed $count orphaned RemoteFileInfo records")
								}
							}
					}

					// 4. Handle Settings/Config
					val configDownloadId = AIOSettings_.downloadId
					settingsBox.query(configDownloadId.equal(dId))
						.build().use { query ->
							val count = query.remove()
							logger.v("Removed $count associated Settings/Config records")
						}

					// 5. Finally, remove the parent download record
					val removed = downloadsBox.remove(dId)
					if (removed) {
						logger.d("Parent AIODownload record $dId removed successfully")
					} else {
						logger.w(
							"Parent AIODownload record $dId not found in box, " +
								"but children were processed"
						)
					}
				}

				val duration = System.currentTimeMillis() - startTime
				logger.i(
					"Successfully purged all records for Download" +
						"$dId in ${duration}ms"
				)

			}.onFailure { error ->
				logger.e(
					"CRITICAL: Cascading delete failed for Download ID: $dId." +
						" Database may contain orphaned records.", error
				)
			}
		}
	}

	/**
	 * Internal helper to remove a [AIOVideoInfo] record and its associated video formats.
	 * * This ensures that when a video info metadata object is deleted, all related
	 * format specifications (resolutions, bitrates, etc.) linked via
	 * [AIOVideoFormat.parentVideoInfoId] are also purged from the database to prevent
	 * orphaned data.
	 *
	 * @param videoInfoId The primary key of the video info record to remove.
	 */
	private fun removeVideoInfoRecords(videoInfoId: Long) {
		runCatching {
			videoInfoBox.remove(videoInfoId)
			val parentVideoInfoId = AIOVideoFormat_.parentVideoInfoId
			videoFormatBox.query(parentVideoInfoId.equal(videoInfoId))
				.build().use { query ->
					query.remove()
				}
		}.onFailure { error ->
			logger.e("Failed to remove videoInfo for ID: $videoInfoId", error)
		}
	}

	/**
	 * Retrieves an array of all download IDs present in the database.
	 * * This is a lightweight alternative to fetching full objects. It executes on the
	 * IO dispatcher. If the query fails, an empty [LongArray] is returned and the
	 * error is logged.
	 *
	 * @return A [LongArray] of all stored download IDs.
	 */
	@JvmStatic
	suspend fun getAllDownloadIds(): LongArray {
		return withIOContext {
			try {
				downloadsBox.query().build().use { query ->
					query.findIds()
				}
			} catch (error: Exception) {
				logger.e("Failed to retrieve download IDs", error)
				LongArray(0)
			}
		}
	}

	/**
	 * Retrieves IDs for downloads that are currently active (not finished or deleted).
	 * * This function uses a native database query to filter out records where:
	 * - The status is [COMPLETE]
	 * - The [AIODownload.isComplete] flag is true
	 * - The record is marked as deleted or removed
	 *
	 * Using native conditions ensures the filtering happens at the C-level, making
	 * this significantly faster than memory-side filtering.
	 *
	 * @return A [LongArray] of active download IDs.
	 */
	@JvmStatic
	suspend fun getActiveDownloadIds(): LongArray {
		return withIOContext {
			try {
				val downloadStatus = AIODownload_.downloadStatus
				downloadsBox.query(downloadStatus.notEqual(COMPLETE.toLong()))
					.equal(AIODownload_.isComplete, false)
					.equal(AIODownload_.isDeleted, false)
					.equal(AIODownload_.isRemoved, false)
					.build()
					.use { query ->
						query.findIds()
					}
			} catch (error: Exception) {
				logger.e("Failed to retrieve active download IDs", error)
				LongArray(0)
			}
		}
	}

	/**
	 * Retrieves an array of IDs for downloads that have successfully finished.
	 *
	 * This function identifies completed downloads based on two criteria:
	 * 1. The [AIODownload.downloadStatus] is explicitly set to [COMPLETE].
	 * 2. The [AIODownload.isComplete] boolean flag is true.
	 *
	 * Performance Note: For large databases, it is recommended to replace the
	 * memory-side [.filter] with a database-level [.equal] or [.or] query to
	 * leverage indexing.
	 *
	 * @return A [LongArray] of IDs for all finished downloads. Returns an
	 * empty array and logs an error if the database query fails.
	 */
	@JvmStatic
	suspend fun getFinishedDownloadIds(): LongArray {
		return withIOContext {
			try {
				val downloadStatus = AIODownload_.downloadStatus
				downloadsBox.query(downloadStatus.equal(COMPLETE.toLong()))
					.build().use { query ->
						query.findIds()
					}
			} catch (error: Exception) {
				logger.e("Failed to retrieve finished download IDs", error)
				LongArray(0)
			}
		}
	}

	/**
	 * Reconstructs the full object graph for an [AIODownload] by fetching its associated
	 * relational data from the database.
	 * * This function "hydrates" a shell [downloadRec] by:
	 * 1. Fetching the [AIOVideoInfo] and all its child [AIOVideoFormat] records in a single batch.
	 * 2. Attaching standalone [AIODownload.videoFormat], [AIODownload.remoteFileInfo], and
	 * [AIODownload.config] objects.
	 * 3. Finalizing the state by persisting the assembled record via [storeDownloadRecord].
	 *
	 * Performance Note: This version uses batch retrieval ([.find]) instead of individual
	 * ID lookups to minimize database overhead.
	 *
	 * @param downloadRec The [AIODownload] instance to be populated with its dependencies.
	 */
	@JvmStatic
	suspend fun assembleAssociatedRecords(downloadRec: AIODownload) {
		return withIOContext {
			val dId = downloadRec.id
			logger.d("Hydration started for Download ID: $dId")

			runCatching {
				// 1. Hydrate Video Info & its nested Formats
				videoInfoBox.get(downloadRec.videoInfoId)?.let { videoInfo ->
					logger.v("Found VideoInfo (${videoInfo.id}) for Download: $dId")
					val parentVideoInfoId = AIOVideoFormat_.parentVideoInfoId
					val formats = videoFormatBox.query(
						parentVideoInfoId.equal(videoInfo.id)
					).build().use { query ->
						query.find()
					}

					logger.v(
						"Batch-retrieved ${formats.size} formats for " +
							"VideoInfo: ${videoInfo.id}"
					)
					videoInfo.videoFormats = formats
					downloadRec.videoInfo = videoInfo
				} ?: logger.w(
					"No VideoInfo found for ID: ${downloadRec.videoInfoId} " +
						"(Download: $dId)"
				)

				// 2. Hydrate Standalone Video Format
				videoFormatBox.get(downloadRec.videoFormatId)
					?.let { videoFormat ->
						logger.v("Hydrated standalone VideoFormat: ${videoFormat.id}")
						downloadRec.videoFormat = videoFormat
					}

				// 3. Hydrate Remote File Info
				remoteFileInfoBox.get(downloadRec.remoteFileInfoId)
					?.let { remoteFileInfo ->
						logger.v("Hydrated RemoteFileInfo: ${remoteFileInfo.id}")
						downloadRec.remoteFileInfo = remoteFileInfo
					}

				// 4. Hydrate Settings/Config
				settingsBox.get(downloadRec.configId)?.let { config ->
					logger.v("Hydrated Config: ${config.id}")
					downloadRec.config = config
				}

				// Persist the fully assembled object graph
				logger.d(
					"Object graph assembled. Handing off to " +
						"storeDownloadRecord for ID: $dId"
				)
				storeDownloadRecord(downloadRec)

			}.onFailure { error ->
				logger.e(
					"Critical failure during record assembly for " +
						"ID: $dId", error
				)
			}
		}
	}
}