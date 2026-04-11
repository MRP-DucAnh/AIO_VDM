package app.core.engines.objectbox

import android.content.Context
import app.core.engines.browser.MyObjectBox
import app.core.engines.downloader.AIODownloadsRepo.initializeAllDownloadSystemObjectBoxes
import app.core.engines.objectbox.ObjectBoxManager.initializeObjectBoxDB
import io.objectbox.BoxStore
import lib.process.LogHelperUtils

/**
 * **ObjectBoxManager**
 *
 * Centralized controller for managing the shared [BoxStore] instance used by all ObjectBox entities
 * within the application.
 *
 * ### Purpose
 * Ensures a **single, thread-safe, lazily-initialized** ObjectBox database connection (BoxStore)
 * across the entire app. ObjectBox recommends using only one [BoxStore] instance per process
 * to avoid data corruption, inconsistent state, or excess resource consumption.
 *
 * ### Why a Singleton?
 * - **Performance:** Reuses a single connection pool and cache, improving data operations.
 * - **Consistency:** Guarantees atomic access to shared entities and prevents race conditions.
 * - **Memory Efficiency:** Avoids redundant database connections across modules.
 * - **Lifecycle Control:** Central point for initializing and closing the database.
 *
 * Typical usage example:
 * ```kotlin
 * // Initialize once in Application.onCreate()
 * ObjectBoxManager.init(applicationContext)
 *
 * // Access globally when needed
 * val boxStore = ObjectBoxManager.getBoxStore()
 * val userBox = boxStore.boxFor(User::class.java)
 * ```
 */
object ObjectBoxManager {

	/**
	 * Logger instance used for tracking internal state transitions, errors,
	 * and database lifecycle events within the ObjectBox manager.
	 */
	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * The backing field for the shared [BoxStore] instance.
	 *
	 * Marked as [Volatile] to ensure that changes to this variable are immediately visible
	 * to all threads, supporting the thread-safe double-checked locking pattern used
	 * during initialization.
	 */
	@Volatile
	private var boxStore: BoxStore? = null

	/**
	 * Initializes the shared [BoxStore] instance.
	 *
	 * This method **must be called exactly once**, ideally from your [android.app.Application] class.
	 * It is safe to call multiple times — only the first invocation will create the BoxStore.
	 *
	 * @param context Application-level [Context], used by ObjectBox to locate the database directory.
	 *
	 * @throws IllegalStateException if initialization fails due to an internal ObjectBox error.
	 */
	@JvmStatic
	fun initializeObjectBoxDB(context: Context) {
		if (boxStore != null) return

		synchronized(this) {
			if (boxStore == null) {
				try {
					boxStore = MyObjectBox.builder()
						.androidContext(context.applicationContext)
						.build()
					initializeAllDownloadSystemObjectBoxes()
					logger.d("Shared ObjectBox BoxStores initialized successfully")
				} catch (error: Exception) {
					logger.e("Failed to initialize shared ObjectBox: ${error.message}", error)

					val isSchemaIssue = error.message?.contains("is not compatible") == true ||
						error.message?.contains("Schema") == true ||
						error.javaClass.name.contains("DbSchemaException")

					if (isSchemaIssue) {
						logger.w("ObjectBox schema mismatch detected. Clearing database and retrying...")
						try {
							// For safety, close any existing store if by any chance it's partially open
							boxStore?.close()
							boxStore = null

							// Delete all ObjectBox database files
							BoxStore.deleteAllFiles(context.applicationContext, null)

							// Attempt to initialize again
							boxStore = MyObjectBox.builder()
								.androidContext(context.applicationContext)
								.build()
							initializeAllDownloadSystemObjectBoxes()
							logger.d("Shared ObjectBox BoxStore re-initialized successfully after data cleanup")
						} catch (retryError: Exception) {
							logger.e("Failed to re-initialize ObjectBox even after cleanup: ${retryError.message}", retryError)
							throw IllegalStateException("ObjectBox initialization failed persistently", retryError)
						}
					} else {
						throw IllegalStateException("ObjectBox initialization failed", error)
					}
				}
			}
		}
	}

	/**
	 * Returns the shared [BoxStore] instance.
	 *
	 * @return The globally shared [BoxStore] for all ObjectBox database access.
	 * @throws IllegalStateException if [initializeObjectBoxDB] has not been called before.
	 */
	@JvmStatic
	fun getBoxStore(): BoxStore {
		return boxStore ?: throw IllegalStateException(
			"ObjectBox not initialized. Call ObjectBoxManager.init() first."
		)
	}

	/**
	 * Closes the shared ObjectBox [BoxStore] instance and releases its resources.
	 *
	 * This should be invoked when the application process is about to terminate.
	 * It safely nullifies the internal reference and logs the shutdown event.
	 *
	 * **Note:** Do not call this during normal runtime unless you explicitly intend
	 * to stop all ObjectBox operations.
	 */
	@JvmStatic
	fun closeObjectBoxDB() {
		synchronized(this) {
			try {
				boxStore?.close()
				boxStore = null
				logger.d("Shared ObjectBox BoxStore closed successfully")
			} catch (error: Exception) {
				logger.e("Error closing shared ObjectBox: ${error.message}", error)
			}
		}
	}
}