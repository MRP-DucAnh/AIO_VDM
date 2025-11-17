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
	 * Current filter predicate applied to the dataset. When null, all items are shown.
	 * This allows for dynamic filtering based on various criteria like file type, name, date, etc.
	 * The function takes a DownloadDataModel and returns true to include the item, false to exclude it.
	 */
	private var currentFilter: ((DownloadDataModel) -> Boolean)? = null

	/**
	 * Cached list of all download data models from the download system.
	 * Used as the source dataset before applying any filters.
	 */
	private val allDownloadDataModels: List<DownloadDataModel>
		get() = downloadSystem?.finishedDownloadDataModels ?: emptyList()

	/**
	 * Filtered list of download data models after applying the current filter predicate.
	 * This is the actual dataset displayed in the RecyclerView.
	 */
	private val filteredDownloadDataModels: List<DownloadDataModel>
		get() = currentFilter?.let { filter ->
			allDownloadDataModels.filter(filter)
		} ?: allDownloadDataModels

	/**
	 * Returns the total number of filtered download items available for display.
	 *
	 * Provides the count of filtered completed downloads to the list view for proper rendering
	 * and scrollbar calculation. Returns 0 if the download system is unavailable,
	 * ensuring safe list operations during system initialization, shutdown, or
	 * when download services are temporarily inaccessible.
	 *
	 * @return The number of filtered finished download items in the system, or 0 if the
	 * download system is not available
	 */
	override fun getItemCount(): Int = filteredDownloadDataModels.size

	/**
	 * Retrieves the download data model at the specified position in the filtered list.
	 *
	 * Provides access to individual filtered download items for view binding and data display.
	 * Returns null for invalid positions or if the download system is unavailable,
	 * ensuring safe data access during list scrolling, view updates, and edge cases
	 * where data might be temporarily inaccessible.
	 *
	 * @param index The position of the item to retrieve (0-based index within the filtered list)
	 * @return The DownloadDataModel at the specified position, or null if the position
	 * is invalid, download system is unavailable, or data cannot be accessed
	 */
	fun getItem(index: Int): DownloadDataModel? {
		return filteredDownloadDataModels.getOrNull(index)
	}

	/**
	 * Returns a stable ID for the item at the specified position in the list.
	 *
	 * Provides unique identifiers for list items to support efficient view recycling,
	 * animation, and item tracking. Uses a combination of filter state and position
	 * to ensure stable IDs across filter changes.
	 *
	 * @param index The position of the item within the adapter's dataset
	 * @return The stable ID for the item
	 */
	override fun getItemId(index: Int): Long {
		val item = getItem(index)
		// Use filename hash combined with position for a reasonably stable ID
		return (item?.fileName?.hashCode()?.toLong() ?: 0L) + index
	}

	/**
	 * Creates a new ViewHolder for displaying finished download items in the list.
	 *
	 * Inflates the list item layout and initializes a ViewHolder to manage the item view.
	 * This enables efficient view recycling during scrolling operations.
	 *
	 * @param parent The parent ViewGroup that will contain the new view
	 * @param viewType The type of view to create (unused in this homogeneous list)
	 * @return A new FinishedTasksViewHolder instance for the list item
	 */
	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FinishedTasksViewHolder {
		val rowLayout = inflater.inflate(R.layout.frag_down_4_finish_1_row_1, parent, false)
		return FinishedTasksViewHolder(rowLayout)
	}

	/**
	 * Binds download data to the ViewHolder at the specified position.
	 *
	 * Retrieves the filtered download item and delegates view updating to the ViewHolder.
	 * Includes safety checks for fragment availability and data existence before binding.
	 *
	 * @param holder The ViewHolder to update with download data
	 * @param position The position in the filtered dataset to display
	 */
	override fun onBindViewHolder(holder: FinishedTasksViewHolder, position: Int) {
		val fragment = weakReferenceOfFinishedFrag.get()
		if (fragment == null) return

		val model = getItem(position)
		holder.updateView(model, fragment)
	}

	/**
	 * Called when a ViewHolder is recycled to free up resources.
	 *
	 * Ensures proper cleanup of view resources like bitmaps and image loaders
	 * before the ViewHolder is reused for a different list item.
	 *
	 * @param holder The ViewHolder being recycled for reuse
	 */
	override fun onViewRecycled(holder: FinishedTasksViewHolder) {
		holder.clearResources(clearWeakReference = false)
		super.onViewRecycled(holder)
	}

	/**
	 * Applies a filter to the dataset and updates the UI accordingly.
	 *
	 * This high-level function allows dynamic filtering of the download list based on
	 * custom criteria. The filter predicate is applied to each item, and only items
	 * for which the predicate returns true are displayed. Set to null to clear all filters.
	 *
	 * @param filterPredicate A lambda function that takes a DownloadDataModel and returns
	 *                       true to include the item, false to exclude it. Pass null to
	 *                       clear the current filter and show all items.
	 */
	@SuppressLint("NotifyDataSetChanged")
	fun setFilter(filterPredicate: ((DownloadDataModel) -> Boolean)?) {
		currentFilter = filterPredicate
		notifyDataSetChangedOnSort(forceRefresh = true)
		logger.d("Filter applied, showing $itemCount of ${allDownloadDataModels.size} items")
	}

	/**
	 * Clears any active filter and shows all download items.
	 * Resets the adapter to display complete unfiltered dataset.
	 */
	fun clearFilter() {
		setFilter(null)
	}

	/**
	 * Checks if a filter is currently active on the dataset.
	 * Returns true when filtering is applied, false for all items.
	 */
	fun isFilterActive(): Boolean = currentFilter != null

	/**
	 * Gets total count of all download items before filtering.
	 * Returns complete item count without any filter restrictions.
	 */
	fun getUnfilteredItemCount(): Int = allDownloadDataModels.size

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
		val newCount = getUnfilteredItemCount()

		// Only proceed if item count has actually changed
		if (newCount != existingTaskCount) {
			super.notifyDataSetChanged()        // Trigger base class UI refresh
			existingTaskCount = newCount        // Update internal count tracking
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
					val models = allDownloadDataModels
					// Iterate through all items in the source dataset (not filtered)
					models.forEachIndexed { index, model ->
						// Check for thread interruption to allow graceful cancellation
						if (Thread.currentThread().isInterrupted) return@submit

						// Get destination file and add to media store
						val file = model.getDestinationFile()
						addToMediaStore(file)  // Update system media database
					}
					logger.d("Media store updated for ${models.size} files.")
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
			// Clear current filter
			currentFilter = null

			// Safely clear resources for all view holders in the adapter
			recyclerView?.let { rv ->
				for (index in 0 until rv.childCount) {
					val viewHolder = rv.getChildViewHolder(rv.getChildAt(index))

					// Clear resources for individual view holder to release bitmaps and references
					if (viewHolder is FinishedTasksViewHolder) {
						viewHolder.clearResources(true)
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