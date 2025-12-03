package app.core.engines.youtube

import app.core.engines.objectbox.*
import io.objectbox.*
import lib.process.*
import java.util.concurrent.*

/**
 * YouTube Videos Models Database Manager
 *
 * This class provides Create, Read, Update, and Delete (CRUD) operations for managing
 * [YouTubeVideoDataModel] entities in the local **ObjectBox** database. It acts as the
 * single source of truth for persisting video metadata, including titles, channel information,
 * and view counts.
 *
 * The manager utilizes the **Singleton pattern** for consistent, centralized database access
 * and includes synchronized methods to ensure **thread safety** during concurrent operations.
 * Operations are executed within ObjectBox transactions for atomicity.
 *
 * @see YouTubeVideoDataModel The entity class representing YouTube video data stored locally.
 * @see ObjectBoxManager The base database manager responsible for initializing the BoxStore.
 */
object YTVideosModelsDBManager {
	
	/**
	 * Logger instance for this class, used for detailed operation logging.
	 */
	private val logger = LogHelperUtils.from(javaClass)
	
	/**
	 * ObjectBox BoxStore instance.
	 * This central access point to the database is initialized lazily upon first use.
	 */
	private val globalObjectBoxStore: BoxStore by lazy { getGlobalObjectBoxStore() }
	
	/**
	 * Lazy-initialized Box (Data Access Object) for [YouTubeVideoDataModel] entities.
	 * This Box provides the direct CRUD methods for the specific video entity type.
	 */
	private val ytVideoModelsBox: Box<YouTubeVideoDataModel> by lazy {
		globalObjectBoxStore.boxFor(YouTubeVideoDataModel::class.java).also {
			logger.d("YTVideos box initialized - Ready for YouTube video data operations.")
		}
	}
	
	/**
	 * Retrieves the singleton BoxStore instance.
	 * Ensures that the central database store is accessed correctly.
	 *
	 * @return [BoxStore] The initialized ObjectBox database store instance.
	 * @throws IllegalStateException if the BoxStore has not been initialized via [ObjectBoxManager].
	 */
	@JvmStatic
	fun getGlobalObjectBoxStore(): BoxStore {
		logger.d("Retrieving BoxStore instance for YouTube videos database.")
		return ObjectBoxManager.getBoxStore()
	}
	
	/**
	 * **Saves or updates** a YouTube video data model to the database.
	 *
	 * This method performs a synchronous database transaction to persist the data.
	 * If the model's ID is set (i.e., non-zero), the existing entity is updated;
	 * otherwise, a new entity is inserted with a newly generated ID.
	 *
	 * **Enhanced Logic:** This version includes a high-level function callback ([OnSaveResultListener])
	 * to communicate the operation result back to the caller without exposing low-level exceptions.
	 *
	 * @param youTubeVideoModel The [YouTubeVideoDataModel] object to save.
	 * The ID field will be updated upon success. If the ID is zero, a new entity will be created.
	 *
	 * @param listener The callback interface to handle the result (success or error).
	 *
	 * @threadsafe The method is synchronized to prevent concurrent modification issues.
	 *
	 * Example usage:
	 * ```
	 * YTVideosModelsDBManager.saveYouTubeVideoDataModelInDB(newVideo, object : OnSaveResultListener {
	 * override fun onSuccess(savedId: Long) {
	 * // UI update logic here: show success message
	 * }
	 * override fun onError(error: Exception) {
	 * // UI update logic here: show error message
	 * }
	 * })
	 * ```
	 */
	@JvmStatic
	@Synchronized
	fun saveYouTubeVideoModelsInDB(
		youTubeVideoModel: YouTubeVideoDataModel,
		listener: OnSaveResultListener
	) {
		try {
			// Execute within a database transaction for atomicity (all-or-nothing)
			globalObjectBoxStore.runInTx { ytVideoModelsBox.put(youTubeVideoModel) }
			
			logger.d(
				"YouTube video saved successfully - ID: ${youTubeVideoModel.id}, " +
					"Title: ${youTubeVideoModel.videoTitle}"
			)
			listener.onSuccess(youTubeVideoModel.id)
			
		} catch (error: Exception) {
			logger.e("Error saving youtube video data model: ${error.message}", error)
			listener.onError(error)
		}
	}
	
	/**
	 * Retrieves **all** YouTube video data models from the database.
	 *
	 * Note: This operation can be resource-intensive for very large datasets.
	 * For optimal performance, consider implementing a paged query approach if
	 * the dataset size is a concern.
	 *
	 * @return [List]<[YouTubeVideoDataModel]> A list of all persisted video models,
	 * or an empty list if no videos exist or an error occurs.
	 * @threadsafe Method is synchronized for safe reads.
	 */
	@JvmStatic
	@Synchronized
	fun getAllYouTubeVideoDataModels(): List<YouTubeVideoDataModel> {
		return try {
			val ytVidModels = ytVideoModelsBox.all
			
			if (ytVidModels.isEmpty()) {
				logger.d("No youtube videos found in database.")
			} else {
				logger.d("Retrieved ${ytVidModels.size} YouTube video(s) from database.")
			}
			ytVidModels
		} catch (error: Exception) {
			logger.e("Error retrieving YouTube video models: ${error.message}", error)
			emptyList()
		}
	}
	
	/**
	 * Retrieves a specific YouTube video from the database by its unique ObjectBox ID.
	 * If the ID does not exist, returns `null`.
	 *
	 * @param ytVideoModelID The unique identifier ([Long]) of the YouTube video to retrieve.
	 * @return [YouTubeVideoDataModel]? The video model if found, or `null` if the ID does
	 * not exist or an error occurred.
	 */
	@JvmStatic
	fun getYouTubeVideoById(ytVideoModelID: Long): YouTubeVideoDataModel? {
		return try {
			ytVideoModelsBox.get(ytVideoModelID)?.also {
				logger.d("Retrieved YouTube video by ID: $ytVideoModelID, Title: ${it.videoTitle}")
			}
		} catch (error: Exception) {
			logger.e("Error retrieving YouTube video by ID $ytVideoModelID: ${error.message}", error)
			null
		}
	}
	
	/**
	 * Deletes a specific YouTube video record from the database using its unique ID.
	 *
	 * The operation is wrapped in a transaction for reliability.
	 *
	 * @param ytVideoModelID The unique identifier ([Long]) of the YouTube video to delete.
	 * @return `true` if deletion succeeded or the entity was not found,
	 * `false` if a critical database error occurred.
	 *
	 * @threadsafe Method is synchronized to ensure thread-safe removal.
	 */
	@JvmStatic
	@Synchronized
	fun deleteYouTubeVideoById(ytVideoModelID: Long): Boolean {
		return try {
			globalObjectBoxStore.runInTx { ytVideoModelsBox.remove(ytVideoModelID) }
			logger.d("Deleted YouTube video with ID: $ytVideoModelID")
			true
		} catch (error: Exception) {
			logger.e("Error deleting YouTube video with ID $ytVideoModelID: ${error.message}", error)
			false
		}
	}
	
	/**
	 * **CAUTION: Destructive Operation**
	 *
	 * Removes **all** YouTube video records from the database.
	 *
	 * @return `true` if all records were successfully cleared, `false` if an error occurred.
	 * @threadsafe Method is synchronized to ensure a complete, atomic clear operation.
	 */
	@JvmStatic
	@Synchronized
	fun clearAllYouTubeVideos(): Boolean {
		return try {
			globalObjectBoxStore.runInTx { ytVideoModelsBox.removeAll() }
			logger.d("Cleared all YouTube videos from database.")
			true
		} catch (error: Exception) {
			logger.e("Error clearing YouTube videos: ${error.message}", error)
			false
		}
	}
	
	@JvmStatic
	@Synchronized
	fun clearOldYouTubeVideosModelsFromDB(daysToKeep: Int = 7) {
		try {
			val timeToKeepMillis = TimeUnit.DAYS.toMillis(daysToKeep.toLong())
			val cutoffTime = System.currentTimeMillis() - timeToKeepMillis
			
			val listOfModels = getAllYouTubeVideoDataModels()
			val modelsToDelete = listOfModels.filter { model ->
				model.lastModifiedTimeDate < cutoffTime
			}
			
			val idsToDelete = modelsToDelete.map { it.id }
			getGlobalObjectBoxStore().runInTx { ytVideoModelsBox.removeByIds(idsToDelete) }
			
			logger.i(
				"Successfully cleared ${idsToDelete.size} " +
					"old YouTube video models (older than $daysToKeep days)."
			)
		} catch (error: Exception) {
			logger.e("Error clearing old YouTube video models: ${error.message}", error)
		}
	}
	
	/**
	 * Interface definition for a callback to be invoked when a database save operation finishes.
	 * This decouples the database logic from the UI/Business logic, allowing for better error handling.
	 */
	interface OnSaveResultListener {
		
		/**
		 * Called when the database save operation is successful.
		 * @param savedId The unique ID of the entity that was saved (newly generated or existing).
		 */
		fun onSuccess(savedId: Long)
		
		/**
		 * Called when the database save operation fails due to an exception.
		 * @param error The Exception that occurred during the operation.
		 */
		fun onError(error: Exception)
	}
}