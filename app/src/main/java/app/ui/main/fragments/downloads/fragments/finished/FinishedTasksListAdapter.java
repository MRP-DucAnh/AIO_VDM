package app.ui.main.fragments.downloads.fragments.finished;

import static android.view.LayoutInflater.from;
import static com.aio.R.layout;
import static lib.files.FileSystemUtility.addToMediaStore;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import app.core.AIOApp;
import app.core.engines.downloader.DownloadDataModel;
import app.core.engines.downloader.DownloadSystem;
import lib.process.LogHelperUtils;

/**
 * Adapter class for displaying finished download tasks in a ListView with efficient view recycling.
 * <p>
 * This adapter manages the presentation of completed downloads in a scrollable list interface,
 * providing thumbnail images, file metadata, and interactive elements. Implements proper
 * resource management with weak references to prevent memory leaks, background processing
 * for system media updates, and optimized view recycling for smooth scrolling performance.
 * Handles the complete lifecycle of finished download display including data binding,
 * view holder management, and system integration for media file recognition.
 */
public class FinishedTasksListAdapter extends BaseAdapter {

	/**
	 * Logger instance for tracking adapter lifecycle events, data changes, and error conditions.
	 * Used for debugging list operations, view recycling issues, and background task monitoring.
	 */
	private final LogHelperUtils logger = LogHelperUtils.from(getClass());

	/**
	 * Weak reference to the parent FinishedTasksFragment to prevent memory leaks.
	 * Allows the fragment to be garbage collected when destroyed while maintaining
	 * access to fragment context and callbacks during the adapter's lifetime.
	 */
	private final WeakReference<FinishedTasksFragment> weakReferenceOfFinishedFrag;

	/**
	 * Layout inflater for creating list item views from XML layout resources.
	 * Cached for performance to avoid repeated system service lookups during
	 * list scrolling and view recycling operations.
	 */
	private LayoutInflater inflater;

	/**
	 * Reference to the application's download management system for accessing
	 * finished download data models. Provides the underlying data source for
	 * the list adapter and handles download state synchronization.
	 */
	private DownloadSystem downloadSystem;

	/**
	 * Tracks the previous count of download items to detect changes and optimize
	 * UI updates. Prevents unnecessary list refreshes and MediaStore updates
	 * when the dataset remains unchanged.
	 */
	private int existingTaskCount;

	/**
	 * Single-threaded executor service for background operations such as MediaStore
	 * database updates. Ensures sequential processing of background tasks to
	 * prevent race conditions and resource contention during media file registration.
	 */
	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	/**
	 * Future representing the current background job for MediaStore updates.
	 * Allows cancellation and monitoring of background operations to prevent
	 * redundant updates and ensure proper cleanup during adapter destruction.
	 */
	private Future<?> backgroundJob;

	/**
	 * Constructs a new FinishedTasksListAdapter with fragment context and system dependencies.
	 * <p>
	 * Initializes the adapter with weak fragment reference to prevent memory leaks while allowing
	 * proper fragment lifecycle management. Sets up layout inflater for efficient view creation
	 * and obtains download system reference for accessing finished download data. Ensures safe
	 * context handling and proper integration with the application's download management system.
	 *
	 * @param fragment The parent FinishedTasksFragment that owns this adapter, provides context,
	 *                 and manages the download display lifecycle
	 */
	public FinishedTasksListAdapter(@NonNull FinishedTasksFragment fragment) {
		// Store weak reference to fragment to prevent memory leaks during configuration changes
		this.weakReferenceOfFinishedFrag = new WeakReference<>(fragment);
		// Initialize layout inflater from fragment's activity context for view creation
		this.inflater = from(fragment.getSafeBaseActivityRef());
		// Obtain download system instance for accessing finished download data models
		this.downloadSystem = AIOApp.INSTANCE.getDownloadManager();
	}

	/**
	 * Returns the total number of finished download items available for display.
	 * <p>
	 * Provides the count of completed downloads to the list view for proper rendering
	 * and scrollbar calculation. Returns 0 if the download system is unavailable,
	 * ensuring safe list operations during system initialization, shutdown, or
	 * when download services are temporarily inaccessible.
	 *
	 * @return The number of finished download items in the system, or 0 if the
	 * download system is not available
	 */
	@Override
	public int getCount() {
		if (downloadSystem == null) return 0;
		return downloadSystem.getFinishedDownloadDataModels().size();
	}

	/**
	 * Retrieves the download data model at the specified position in the list.
	 * <p>
	 * Provides access to individual download items for view binding and data display.
	 * Returns null for invalid positions or if the download system is unavailable,
	 * ensuring safe data access during list scrolling, view updates, and edge cases
	 * where data might be temporarily inaccessible.
	 *
	 * @param index The position of the item to retrieve (0-based index within the list)
	 * @return The DownloadDataModel at the specified position, or null if the position
	 * is invalid, download system is unavailable, or data cannot be accessed
	 */
	@Override
	@Nullable
	public DownloadDataModel getItem(int index) {
		if (downloadSystem == null) return null;
		return downloadSystem.getFinishedDownloadDataModels().get(index);
	}

	/**
	 * Returns a stable ID for the item at the specified position in the list.
	 * <p>
	 * Provides unique identifiers for list items to support efficient view recycling,
	 * animation, and item tracking. Uses the position index as the ID since finished
	 * download items maintain consistent ordering and don't require persistent unique
	 * identifiers across dataset changes.
	 *
	 * @param index The position of the item within the adapter's dataset
	 * @return The stable ID for the item (same as the position index in this implementation)
	 */
	@Override
	public long getItemId(int index) {
		return index;
	}

	/**
	 * Creates or recycles a view for the specified list position and binds data to it.
	 * <p>
	 * Implements efficient view recycling by reusing convertView when available to minimize
	 * layout inflation overhead. Inflates new list item layouts only when necessary using
	 * the cached layout inflater. Updates the view holder with current download data for
	 * proper display. Uses null parent in inflation for performance optimization in list views.
	 *
	 * @param position    The position of the item within the adapter's data set (0-based index)
	 * @param convertView The recycled view to populate, or null if a new view needs to be created
	 * @param parent      The parent ViewGroup that this view will eventually be attached to
	 * @return A view displaying the download data at the specified position
	 */
	@SuppressLint("InflateParams")
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		// Reuse existing view if available to avoid expensive layout inflation
		if (convertView == null) {
			// Inflate new list item layout using cached inflater
			int layoutResId = layout.frag_down_4_finish_1_row_1;
			convertView = inflater.inflate(layoutResId, null);
		}
		// Update view holder with download data for current list position
		updateViewHolder(convertView, position);
		return convertView;
	}

	/**
	 * Notifies the adapter that the underlying data has changed with change detection optimization.
	 * <p>
	 * Only triggers UI refresh and background operations when the item count actually changes,
	 * preventing unnecessary list redraws and redundant MediaStore updates. Automatically
	 * schedules MediaStore database updates when new downloads are detected to ensure system
	 * gallery visibility for newly downloaded media files. Maintains count tracking to
	 * distinguish between actual content changes and identical dataset refreshes.
	 */
	@Override
	public void notifyDataSetChanged() {
		// Check if parent fragment is still available using weak reference
		FinishedTasksFragment fragment = weakReferenceOfFinishedFrag.get();
		if (fragment == null) return;  // Skip if fragment is destroyed

		// Get current download count from fragment for change detection
		int newCount = fragment.getFinishedDownloadModels().size();

		// Only proceed if item count has actually changed
		if (newCount != existingTaskCount) {
			super.notifyDataSetChanged();  // Trigger base class UI refresh
			existingTaskCount = getCount(); // Update internal count tracking
			scheduleMediaStoreUpdate();    // Update system media database for new files
		}
	}

	/**
	 * Schedules a background task to update the Android MediaStore database for all downloaded media files.
	 * <p>
	 * This method cancels any existing media store update job and submits a new background task
	 * that iterates through all download items, adding each file to the MediaStore for proper
	 * gallery visibility and system-wide media access. Includes thread interruption checks
	 * for responsive cancellation and comprehensive error handling for individual file operations.
	 */
	private void scheduleMediaStoreUpdate() {
		try {
			// Cancel any existing background job to prevent duplicate operations
			if (backgroundJob != null && !backgroundJob.isDone()) {
				backgroundJob.cancel(true);  // Force cancel with thread interruption
			}

			// Submit new background task to executor for media store updates
			backgroundJob = executor.submit(() -> {
				try {
					int count = getCount();
					// Iterate through all items in the adapter
					for (int index = 0; index < count; index++) {
						// Check for thread interruption to allow graceful cancellation
						if (Thread.currentThread().isInterrupted()) return;

						// Retrieve download model for current position
						DownloadDataModel model = getItem(index);
						if (model == null) continue;  // Skip null models

						// Get destination file and add to media store
						File file = model.getDestinationFile();
						addToMediaStore(file);  // Update system media database
					}
					logger.d("Media store updated for " + count + " files.");
				} catch (Exception error) {
					// Log errors for individual file processing but continue with other files
					logger.e("Error updating media store", error);
				}
			});
		} catch (Exception error) {
			// Log errors related to task scheduling or executor submission
			logger.e("Failed to schedule media store update", error);
		}
	}

	/**
	 * Notifies the adapter that the underlying data has changed and refreshes the UI display.
	 * <p>
	 * This method provides controlled dataset change notification with optional forced refresh.
	 * When forceRefresh is true, it uses the base class notification which may trigger more
	 * comprehensive UI updates. Includes exception handling to prevent crashes during UI refresh.
	 *
	 * @param forceRefresh If true, forces a complete dataset refresh; if false, uses standard
	 *                     change notification for optimal performance.
	 */
	public void notifyDataSetChangedOnSort(boolean forceRefresh) {
		try {
			if (forceRefresh) super.notifyDataSetChanged();  // Force complete UI refresh
			else notifyDataSetChanged();        // Standard dataset change notification
		} catch (Exception error) {
			// Log UI update errors but don't crash - ensures adapter remains functional
			logger.e("notifyDataSetChangedOnSort error", error);
		}
	}

	/**
	 * Updates or creates a ViewHolder for the specified list position and binds data to it.
	 * <p>
	 * This method implements efficient view holder pattern by either reusing existing holders
	 * or creating new ones, then updating them with the appropriate download data. Includes
	 * proper resource cleanup before reuse and safe fragment reference handling to prevent
	 * memory leaks when fragments are destroyed.
	 *
	 * @param rowLayout The layout view representing the list item to be updated
	 * @param position  The adapter position of the item to display in the list
	 */
	private void updateViewHolder(View rowLayout, int position) {
		FinishedTasksViewHolder holder;

		// Check if view holder already exists for this view
		if (rowLayout.getTag() == null) {
			// Create new view holder and attach to view tag for reuse
			holder = new FinishedTasksViewHolder(rowLayout);
			rowLayout.setTag(holder);
		} else {
			// Reuse existing view holder after clearing previous resources
			holder = (FinishedTasksViewHolder) rowLayout.getTag();
		}

		// Safely get fragment reference and update view holder with data
		FinishedTasksFragment fragment = weakReferenceOfFinishedFrag.get();
		if (fragment != null) {
			holder.updateView(getItem(position), fragment);  // Bind data to UI
		}
	}

	/**
	 * Clears all resources, cancels background operations, and shuts down executors to prevent memory leaks.
	 * <p>
	 * This method performs comprehensive cleanup of all adapter resources including fragment references,
	 * view holders, background jobs, and executor services. Essential for calling during fragment
	 * destruction or when the adapter is no longer needed to ensure proper garbage collection and
	 * prevent memory leaks from retained references and running tasks.
	 */
	public void clearResources() {
		try {
			// Clear weak reference to fragment to allow garbage collection
			weakReferenceOfFinishedFrag.clear();
			// Nullify layout inflater to release context references
			inflater = null;
			// Clear download system reference to prevent memory retention
			downloadSystem = null;

			// Safely clear resources for all view holders in the adapter
			int count = getCount();
			for (int index = 0; index < count; index++) {
				// Retrieve view for each position in the adapter
				View view = getView(index, null, null);
				if (view != null) {
					// Get view holder tag attached to the view
					Object tag = view.getTag();
					if (tag instanceof FinishedTasksViewHolder) {
						// Clear resources for individual view holder to release bitmaps and references
						((FinishedTasksViewHolder) tag).clearResources(true);
					}
				}
			}

			// Cancel background processing job if still running
			if (backgroundJob != null && !backgroundJob.isDone()) {
				backgroundJob.cancel(true);  // Force cancel with interrupt
			}

			// Shutdown executor service and cancel pending tasks
			executor.shutdownNow();
			logger.d("Resources cleared, executor shut down.");

		} catch (Exception error) {
			// Log any errors during cleanup but don't crash - ensure cleanup continues
			logger.e("Error clearing resources", error);
		}
	}
}
