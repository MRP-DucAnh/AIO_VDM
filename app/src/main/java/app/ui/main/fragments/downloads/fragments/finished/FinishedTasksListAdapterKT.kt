package app.ui.main.fragments.downloads.fragments.finished

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.core.AIOApp.Companion.INSTANCE
import app.core.engines.downloader.DownloadDataModel
import app.core.engines.downloader.DownloadSystem
import com.aio.R
import lib.files.FileSystemUtility.addToMediaStore
import lib.process.LogHelperUtils
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Adapter class for displaying finished download tasks in a ListView with efficient view recycling.
 *
 * This adapter manages the presentation of completed downloads in a scrollable list interface,
 * providing thumbnail images, file metadata, and interactive elements. Implements proper
 * resource management with weak references to prevent memory leaks, background processing
 * for system media updates, and optimized view recycling for smooth scrolling performance.
 * Handles the complete lifecycle of finished download display including data binding,
 * view holder management, and system integration for media file recognition.
 */
class FinishedTasksListAdapterKT(fragment: FinishedTasksFragment) :
	RecyclerView.Adapter<FinishedTasksViewHolder>() {

	/**
	 * Logger instance for tracking adapter lifecycle events, data changes, and error conditions.
	 * Used for debugging list operations, view recycling issues, and background task monitoring.
	 */
	private val logger = LogHelperUtils.from(this::class.java)

	/**
	 * Weak reference to the parent FinishedTasksFragment to prevent memory leaks.
	 * Allows the fragment to be garbage collected when destroyed while maintaining
	 * access to fragment context and callbacks during the adapter's lifetime.
	 */
	private val weakReferenceOfFinishedFrag = WeakReference(fragment)

	/**
	 * Layout inflater for creating list item views from XML layout resources.
	 * Cached for performance to avoid repeated system service lookups during
	 * list scrolling and view recycling operations.
	 */
	private var inflater: LayoutInflater = LayoutInflater.from(fragment.safeBaseActivityRef)

	/**
	 * Reference to the application's download management system for accessing
	 * finished download data models. Provides the underlying data source for
	 * the list adapter and handles download state synchronization.
	 */
	private var downloadSystem: DownloadSystem? = INSTANCE.getDownloadManager()

	/**
	 * Tracks the previous count of download items to detect changes and optimize
	 * UI updates. Prevents unnecessary list refreshes and MediaStore updates
	 * when the dataset remains unchanged.
	 */
	private var existingTaskCount = 0

	/**
	 * Single-threaded executor service for background operations such as MediaStore
	 * database updates. Ensures sequential processing of background tasks to
	 * prevent race conditions and resource contention during media file registration.
	 */
	private val executor: ExecutorService = Executors.newSingleThreadExecutor()

	/**
	 * Future representing the current background job for MediaStore updates.
	 * Allows cancellation and monitoring of background operations to prevent
	 * redundant updates and ensure proper cleanup during adapter destruction.
	 */
	private var backgroundJob: Future<*>? = null

	/**
	 * Returns the total number of finished download items available for display.
	 *
	 * Provides the count of completed downloads to the list view for proper rendering
	 * and scrollbar calculation. Returns 0 if the download system is unavailable,
	 * ensuring safe list operations during system initialization, shutdown, or
	 * when download services are temporarily inaccessible.
	 *
	 * @return The number of finished download items in the system, or 0 if the
	 * download system is not available
	 */
	override fun getItemCount(): Int {
		return downloadSystem?.finishedDownloadDataModels?.size ?: 0
	}

	/**
	 * Retrieves the download data model at the specified position in the list.
	 *
	 * Provides access to individual download items for view binding and data display.
	 * Returns null for invalid positions or if the download system is unavailable,
	 * ensuring safe data access during list scrolling, view updates, and edge cases
	 * where data might be temporarily inaccessible.
	 *
	 * @param index The position of the item to retrieve (0-based index within the list)
	 * @return The DownloadDataModel at the specified position, or null if the position
	 * is invalid, download system is unavailable, or data cannot be accessed
	 */
	fun getItem(index: Int): DownloadDataModel? {
		return downloadSystem?.finishedDownloadDataModels?.get(index)
	}

	/**
	 * Returns a stable ID for the item at the specified position in the list.
	 *
	 * Provides unique identifiers for list items to support efficient view recycling,
	 * animation, and item tracking. Uses the position index as the ID since finished
	 * download items maintain consistent ordering and don't require persistent unique
	 * identifiers across dataset changes.
	 *
	 * @param index The position of the item within the adapter's dataset
	 * @return The stable ID for the item (same as the position index in this implementation)
	 */
	override fun getItemId(index: Int): Long = index.toLong()

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FinishedTasksViewHolder {
		val rowLayout = inflater.inflate(R.layout.frag_down_4_finish_1_row_1, parent, false)
		return FinishedTasksViewHolder(rowLayout)
	}

	override fun onBindViewHolder(holder: FinishedTasksViewHolder, position: Int) {
		val fragment = weakReferenceOfFinishedFrag.get()
		if (fragment == null) return

		val model = getItem(position)
		holder.updateView(model, fragment)
	}

	override fun onViewRecycled(holder: FinishedTasksViewHolder) {
		holder.clearResources(true)
		super.onViewRecycled(holder)
	}

	/**
	 * Notifies the adapter that the underlying data has changed with change detection optimization.
	 *
	 * Only triggers UI refresh and background operations when the item count actually changes,
	 * preventing unnecessary list redraws and redundant MediaStore updates. Automatically
	 * schedules MediaStore database updates when new downloads are detected to ensure system
	 * gallery visibility for newly downloaded media files. Maintains count tracking to
	 * distinguish between actual content changes and identical dataset refreshes.
	 */
	@SuppressLint("NotifyDataSetChanged")
	fun refreshDataSetChange() {
		// Check if parent fragment is still available using weak reference
		val fragment = weakReferenceOfFinishedFrag.get()
		if (fragment == null) return  // Skip if fragment is destroyed

		// Get current download count from fragment for change detection
		val newCount = itemCount

		// Only proceed if item count has actually changed
		if (newCount != existingTaskCount) {
			super.notifyDataSetChanged()        // Trigger base class UI refresh
			existingTaskCount = itemCount       // Update internal count tracking
			scheduleMediaStoreUpdate()          // Update system media database for new files
		}
	}

	/**
	 * Schedules a background task to update the Android MediaStore database for all downloaded media files.
	 *
	 * This method cancels any existing media store update job and submits a new background task
	 * that iterates through all download items, adding each file to the MediaStore for proper
	 * gallery visibility and system-wide media access. Includes thread interruption checks
	 * for responsive cancellation and comprehensive error handling for individual file operations.
	 */
	private fun scheduleMediaStoreUpdate() {
		try {
			// Cancel any existing background job to prevent duplicate operations
			backgroundJob?.let { job ->
				if (!job.isDone) {
					job.cancel(true)  // Force cancel with thread interruption
				}
			}

			// Submit new background task to executor for media store updates
			backgroundJob = executor.submit {
				try {
					val count = itemCount
					// Iterate through all items in the adapter
					for (index in 0 until count) {
						// Check for thread interruption to allow graceful cancellation
						if (Thread.currentThread().isInterrupted) return@submit

						// Retrieve download model for current position
						val model = getItem(index) ?: continue  // Skip null models

						// Get destination file and add to media store
						val file = model.getDestinationFile()
						addToMediaStore(file)  // Update system media database
					}
					logger.d("Media store updated for $count files.")
				} catch (error: Exception) {
					// Log errors for individual file processing but continue with other files
					logger.e("Error updating media store", error)
				}
			}
		} catch (error: Exception) {
			// Log errors related to task scheduling or executor submission
			logger.e("Failed to schedule media store update", error)
		}
	}

	/**
	 * Notifies the adapter that the underlying data has changed and refreshes the UI display.
	 *
	 * This method provides controlled dataset change notification with optional forced refresh.
	 * When forceRefresh is true, it uses the base class notification which may trigger more
	 * comprehensive UI updates. Includes exception handling to prevent crashes during UI refresh.
	 *
	 * @param forceRefresh If true, forces a complete dataset refresh; if false, uses standard
	 *                     change notification for optimal performance.
	 */
	@SuppressLint("NotifyDataSetChanged")
	fun notifyDataSetChangedOnSort(forceRefresh: Boolean) {
		try {
			if (forceRefresh) super.notifyDataSetChanged()  // Force complete UI refresh
			else refreshDataSetChange()                     // Standard dataset change notification
		} catch (error: Exception) {
			// Log UI update errors but don't crash - ensures adapter remains functional
			logger.e("notifyDataSetChangedOnSort error", error)
		}
	}

	/**
	 * Clears all resources, cancels background operations, and shuts down executors to prevent memory leaks.
	 *
	 * This method performs comprehensive cleanup of all adapter resources including fragment references,
	 * view holders, background jobs, and executor services. Essential for calling during fragment
	 * destruction or when the adapter is no longer needed to ensure proper garbage collection and
	 * prevent memory leaks from retained references and running tasks.
	 */
	fun clearResources(recyclerView: RecyclerView?) {
		try {
			// Clear weak reference to fragment to allow garbage collection
			weakReferenceOfFinishedFrag.clear()
			// Clear download system reference to prevent memory retention
			downloadSystem = null

			// Safely clear resources for all view holders in the adapter
			recyclerView?.let { rv ->
				for (index in 0 until rv.childCount) {
					val vh = rv.getChildViewHolder(rv.getChildAt(index))

					// Clear resources for individual view holder to release bitmaps and references
					if (vh is FinishedTasksViewHolder) {
						vh.clearResources(true)
					}
				}
			}

			// Cancel background processing job if still running
			backgroundJob?.let { job ->
				if (!job.isDone) {
					job.cancel(true)  // Force cancel with interrupt
				}
			}

			// Shutdown executor service and cancel pending tasks
			executor.shutdownNow()
			logger.d("Resources cleared, executor shut down.")

		} catch (error: Exception) {
			// Log any errors during cleanup but don't crash - ensure cleanup continues
			logger.e("Error clearing resources", error)
		}
	}
}