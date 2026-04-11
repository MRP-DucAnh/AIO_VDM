package app.ui.main.fragments.downloads.fragments.finished;

import static android.view.LayoutInflater.from;
import static com.aio.R.layout;
import static lib.files.FileSystemUtility.addToMediaStore;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import app.core.AIOApp;
import app.core.engines.downloader.AIODownload;
import app.core.engines.downloader.DownloadSystem;
import lib.process.LogHelperUtils;

/**
 * An adapter for a {@link android.widget.ListView} that manages and displays a list of completed download tasks.
 *
 * <p>This adapter provides functionality for:
 * <ul>
 *     <li>Displaying finished downloads using {@link AIODownload}.</li>
 *     <li>Filtering the task list via the {@link TaskFilter} interface.</li>
 *     <li>Asynchronously updating the Android MediaStore to ensure downloaded files appear in system galleries
 *     and media players.</li>
 *     <li>Efficient view recycling using the {@link FinishedTasksViewHolder} pattern.</li>
 *     <li>Memory management through a {@link WeakReference} to the parent fragment.</li>
 * </ul>
 *
 * <p>Data is synchronized with the {@link DownloadSystem} and cached locally
 * to improve UI performance during filtering and sorting operations.</p>
 */
public class FinishedTasksListAdapter extends BaseAdapter {

	/**
	 * Logger instance for this class, used to record diagnostic information,
	 * lifecycle events, and error details during the list adapter's operations.
	 */
	private final LogHelperUtils logger = LogHelperUtils.from(getClass());
	/**
	 * A weak reference to the {@link FinishedTasksFragment} that owns this adapter.
	 * Using a {@link WeakReference} helps prevent memory leaks by
	 * allowing the fragment to be garbage collected when it is no longer in use.
	 */
	private final WeakReference<FinishedTasksFragment> weakRefFinishedFrag;

	/**
	 * The {@link LayoutInflater} instance used to instantiate XML layout files
	 * into their corresponding View objects within the list.
	 */
	private LayoutInflater layoutInflater;
	/**
	 * A reference to the core {@link DownloadSystem} used to fetch, manage, and
	 * synchronize the list of completed download tasks from the application's
	 * background download service.
	 */
	private DownloadSystem downloadSystem;

	/**
	 * The number of tasks in the filtered dataset during the last update.
	 * Used to detect changes in the list size and determine if UI refreshes
	 * or MediaStore synchronizations are necessary.
	 */
	private int existingTaskCount;
	/**
	 * A single-thread executor service dedicated to executing background tasks,
	 * such as scanning downloaded files into the MediaStore, to ensure that
	 * file system I/O operations do not block the main UI thread.
	 */
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	/**
	 * Holds a reference to the currently active asynchronous task responsible for
	 * indexing files in the Android MediaStore. This allows for canceling
	 * stale update requests when new changes occur, preventing redundant
	 * or overlapping background execution.
	 */
	private Future<?> backgroundJob;

	/**
	 * The current filter logic applied to the list of finished tasks.
	 * When set, this interface determines which tasks from the master list are
	 * included in the {@link #filteredList}. If null, no filtering is performed
	 * and all finished tasks are displayed.
	 */
	private TaskFilter customFilter;
	/**
	 * The complete list of finished download tasks retrieved from the {@link DownloadSystem}.
	 * This acts as the unfiltered source of truth used to populate {@link #filteredList}
	 * whenever a {@link TaskFilter} is applied or the data is refreshed.
	 */
	private List<AIODownload> originalList = new ArrayList<>();
	/**
	 * The subset of completed download tasks that satisfy the current {@link TaskFilter}.
	 * This list serves as the primary data source for the adapter, determining which
	 * items are currently visible in the UI.
	 */
	private List<AIODownload> filteredList = new ArrayList<>();

	/**
	 * Interface definition for a callback used to filter the list of finished tasks.
	 *
	 * <p>Implementations of this interface determine whether a specific {@link AIODownload}
	 * should be included in the adapter's filtered dataset based on custom criteria.</p>
	 */
	public interface TaskFilter {
		boolean accept(AIODownload model);
	}

	/**
	 * Constructs a new FinishedTasksListAdapter.
	 * <p>
	 * This constructor initializes the adapter by capturing a weak reference to the parent fragment,
	 * setting up the layout inflater from the fragment's activity context, and retrieving the
	 * global download manager. It also triggers an initial cache build of finished tasks.
	 * </p>
	 *
	 * @param fragment The {@link FinishedTasksFragment} associated with this adapter.
	 * @throws RuntimeException if initialization or cache rebuilding fails.
	 */
	public FinishedTasksListAdapter(@NonNull FinishedTasksFragment fragment) {
		try {
			weakRefFinishedFrag = new WeakReference<>(fragment);
			layoutInflater = from(fragment.getSafeBaseActivityRef());
			downloadSystem = AIOApp.INSTANCE.getDownloadManager();
			rebuildCache();
		} catch (Exception error) {
			logger.e("Adapter init failed", error);
			throw new RuntimeException(error);
		}
	}

	/**
	 * How many items are in the data set represented by this adapter.
	 *
	 * @return The number of finished download tasks currently matching the active filter,
	 * or 0 if the filtered list is unavailable.
	 */
	@Override
	public int getCount() {
		return (filteredList != null) ? filteredList.size() : 0;
	}

	/**
	 * Retrieves the {@link AIODownload} at the specified position in the filtered list.
	 *
	 * @param index The position of the item within the adapter's data set.
	 * @return The data model at the specified index, or {@code null} if the download system
	 * is unavailable, the list is empty, or the index is out of bounds.
	 */
	@Override
	@Nullable
	public AIODownload getItem(int index) {
		if (downloadSystem == null) return null;
		if (filteredList == null ||
			index < 0 || index >= filteredList.size()) {
			return null;
		}
		return filteredList.get(index);
	}

	/**
	 * Gets the row id associated with the specified position in the list.
	 * In this implementation, the item's position is used as its unique identifier.
	 *
	 * @param index The position of the item within the adapter's data set.
	 * @return The identifier for the item at the specified position.
	 */
	@Override
	public long getItemId(int index) {
		return index;
	}

	/**
	 * Provides a {@link View} for displaying the download task at the specified position.
	 * <p>
	 * This implementation utilizes the View Holder pattern to optimize list performance:
	 * <ul>
	 *     <li>If {@code convertView} is null, it inflates the row layout and initializes a new
	 *     {@link FinishedTasksViewHolder}.</li>
	 *     <li>If {@code convertView} is non-null, it retrieves the existing holder and calls
	 *     {@code cancelAll()} to stop any pending asynchronous operations associated with the recycled view.</li>
	 * </ul>
	 * The view is then updated with data from the {@link AIODownload} corresponding to the position.
	 * </p>
	 *
	 * @param position    The position of the item within the adapter's data set.
	 * @param convertView The recycled view to populate, or null if a new view should be inflated.
	 * @param parent      The parent view group that this view will eventually be attached to.
	 * @return A View configured to display the data at the specified position.
	 * @throws RuntimeException if an error occurs during view inflation or binding.
	 */
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		try {
			FinishedTasksViewHolder holder;
			if (convertView == null) {
				int layoutResId = layout.frag_down_4_finish_1_row_1;
				convertView = layoutInflater.inflate(layoutResId, parent, false);
				holder = new FinishedTasksViewHolder(convertView);
				convertView.setTag(holder);
				logger.d("Created new row view and ViewHolder");
			} else {
				holder = (FinishedTasksViewHolder) convertView.getTag();
				holder.cancelAll();
			}

			updateView(position, holder);
			return convertView;
		} catch (Exception error) {
			logger.e("getView error", error);
			throw error;
		}
	}

	/**
	 * Refreshes the dataset and notifies the attached view of changes.
	 * <p>
	 * This method performs the following steps:
	 * <ol>
	 *     <li>Verifies that the parent fragment is still active to avoid unnecessary processing.</li>
	 *     <li>Synchronizes the local cache with the latest finished tasks from the {@link DownloadSystem}.</li>
	 *     <li>Checks if the total item count has changed compared to the last update.</li>
	 *     <li>If the count has changed, triggers {#notifyDataSetChanged()} to refresh the UI and
	 *         schedules a background update for the Android MediaStore.</li>
	 * </ol>
	 */
	@Override
	public void notifyDataSetChanged() {
		try {
			if (weakRefFinishedFrag.get() == null) return;

			rebuildCache();
			int newCount = getCount();
			if (newCount != existingTaskCount) {
				existingTaskCount = newCount;
				super.notifyDataSetChanged();
				scheduleMediaStoreUpdate();
			}
		} catch (Exception error) {
			logger.e("notifyDataSetChanged error", error);
		}
	}

	/**
	 * Checks whether a filter is currently being applied to the list of finished tasks.
	 *
	 * <p>A filter is considered active if a {@link TaskFilter} has been provided and
	 * the number of items in the {@code filteredList} differs from the {@code originalList},
	 * indicating that some tasks are currently hidden from view.</p>
	 *
	 * @return {@code true} if a filter is set and effectively limiting the results;
	 * {@code false} otherwise.
	 */
	public boolean isFilterActive() {
		return customFilter != null &&
			filteredList.size() != originalList.size();
	}

	/**
	 * Sets a new filter to be applied to the finished tasks list and refreshes the UI.
	 * <p>
	 * This method updates the current filtering logic, reapplies it to the underlying
	 * data set, synchronizes the cache, and notifies the adapter of the data change.
	 * If the provided filter is {@code null}, all filtering is removed and the full
	 * list of finished tasks will be displayed.
	 * </p>
	 *
	 * @param filter The {@link TaskFilter} implementation to use, or {@code null} to disable filtering.
	 */
	public void setFilter(@Nullable TaskFilter filter) {
		try {
			customFilter = filter;
			applyFilter();
			rebuildCache();
			super.notifyDataSetChanged();
			logger.d("Filter updated. Active: " + (filter != null));
		} catch (Exception error) {
			logger.e("setFilter error", error);
		}
	}

	/**
	 * Filters the master list of finished tasks based on the current {@link TaskFilter} criteria.
	 * <p>
	 * If no filter is set ({@code customFilter} is null), the {@code filteredList} is populated
	 * with all items from the {@code originalList}. If a filter is active, this method iterates
	 * through the original data and retains only the items that satisfy the filter's conditions.
	 * </p>
	 * <p>
	 * Any exceptions encountered during the filtering process are caught and logged to prevent
	 * UI crashes during data synchronization.
	 * </p>
	 */
	private void applyFilter() {
		try {
			if (customFilter == null) {
				filteredList = new ArrayList<>(originalList);
				return;
			}

			List<AIODownload> newList = new ArrayList<>();
			for (AIODownload model : originalList) {
				if (model != null && customFilter.accept(model)) newList.add(model);
			}
			filteredList = newList;
		} catch (Exception error) {
			logger.e("applyFilter error", error);
		}
	}

	/**
	 * Synchronizes the local data cache with the {@link DownloadSystem}.
	 * <p>
	 * This method retrieves the latest list of completed downloads from the global
	 * download manager and updates the {@code originalList}. After fetching the data,
	 * it invokes {@link #applyFilter()} to ensure the {@code filteredList} accurately
	 * reflects the current filtering criteria.
	 * </p>
	 */
	private void rebuildCache() {
		try {
			if (downloadSystem == null) return;
			originalList = downloadSystem.getFinishedDownloadDataModels();
			applyFilter();
		} catch (Exception error) {
			logger.e("rebuildCache error", error);
		}
	}

	/**
	 * Schedules a background task to synchronize downloaded files with the Android MediaStore.
	 * <p>
	 * This method ensures that newly completed downloads are indexed by the system, making them
	 * visible in gallery and media player applications. To maintain performance and avoid
	 * redundant work:
	 * <ul>
	 *     <li>Any currently running MediaStore update job is canceled before a new one starts.</li>
	 *     <li>The update runs asynchronously on a single-thread executor to prevent UI blocking.</li>
	 *     <li>It iterates through the current list of tasks, verifies the physical existence of
	 *         each file, and triggers a system scan for valid files.</li>
	 * </ul>
	 * </p>
	 */
	private void scheduleMediaStoreUpdate() {
		try {
			if (backgroundJob != null && !backgroundJob.isDone()) {
				backgroundJob.cancel(true);
			}

			backgroundJob = executor.submit(() -> {
				int count = getCount();
				for (int index = 0; index < count; index++) {
					if (Thread.currentThread().isInterrupted()) return;

					AIODownload model = getItem(index);
					if (model == null) continue;
					File file = model.getDestinationFile();
					if (file.exists()) {
						try {
							addToMediaStore(file);
						} catch (Exception fileError) {
							logger.e("MediaStore file error", fileError);
						}
					}
				}
			});
		} catch (Exception error) {
			logger.e("scheduleMediaStoreUpdate error", error);
		}
	}

	/**
	 * Notifies the adapter that the data set has been sorted, with an option to force a full cache refresh.
	 * <p>
	 * This method is specifically used when the ordering of the list changes. Depending on the
	 * {@code forceRefresh} flag, it either re-synchronizes the internal cache with the
	 * {@link DownloadSystem} before updating the UI, or delegates to the standard
	 * {@link #notifyDataSetChanged()} logic.
	 * </p>
	 *
	 * @param forceRefresh If {@code true}, the adapter will immediately rebuild its local
	 *                     data cache and notify the UI; if {@code false}, it follows the
	 *                     standard conditional update logic.
	 */
	public void notifyDataSetChangedOnSort(boolean forceRefresh) {
		try {
			if (forceRefresh) {
				rebuildCache();
				super.notifyDataSetChanged();
			} else {
				notifyDataSetChanged();
			}
		} catch (Exception error) {
			logger.e("notifyDataSetChangedOnSort error", error);
		}
	}

	/**
	 * Updates the data displayed in the view holder associated with a specific row.
	 * <p>
	 * This method retrieves the {@link FinishedTasksViewHolder} instance previously
	 * attached to the view's tag and delegates the UI binding to {@link #updateView(int, FinishedTasksViewHolder)}.
	 * It includes exception handling to ensure that failures in a single row's update
	 * do not crash the entire list adapter.
	 * </p>
	 *
	 * @param rowLayout The root view of the list item row containing the ViewHolder tag.
	 * @param position  The position of the item within the adapter's data set.
	 */
	private void updateViewHolder(View rowLayout, int position) {
		try {
			FinishedTasksViewHolder holder = (FinishedTasksViewHolder) rowLayout.getTag();
			updateView(position, holder);
		} catch (Exception error) {
			logger.e("updateViewHolder error", error);
		}
	}

	/**
	 * Binds the data from a {@link AIODownload} to a {@link FinishedTasksViewHolder} at the specified position.
	 * <p>
	 * This method retrieves the data item corresponding to the given position and, provided the
	 * parent fragment is still active and hasn't been garbage collected, delegates the UI
	 * population to the view holder's own update logic.
	 * </p>
	 *
	 * @param position The position of the item within the adapter's data set.
	 * @param holder   The view holder containing the UI components to be updated.
	 */
	private void updateView(int position, FinishedTasksViewHolder holder) {
		FinishedTasksFragment fragment = weakRefFinishedFrag.get();
		if (fragment != null) {
			AIODownload item = getItem(position);
			holder.updateView(item, fragment);
		}
	}

	/**
	 * Releases references and shuts down background services to prevent memory leaks.
	 * <p>
	 * This method performs a comprehensive cleanup by:
	 * <ul>
	 *     <li>Clearing the {@link WeakReference} to the parent fragment.</li>
	 *     <li>Nullifying references to the {@link LayoutInflater} and {@link DownloadSystem}.</li>
	 *     <li>Immediately shutting down the {@link ExecutorService} and canceling pending background tasks.</li>
	 * </ul>
	 * This should be called when the adapter is no longer needed, typically during
	 * the fragment's {@code onDestroyView} or {@code onDestroy} lifecycle events.
	 */
	public void clearResources() {
		try {
			weakRefFinishedFrag.clear();
			layoutInflater = null;
			downloadSystem = null;
			executor.shutdownNow();
			try {
				if (!executor.awaitTermination(60, TimeUnit.MILLISECONDS)) {
					logger.d("Executor service did not terminate in time.");
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			logger.d("Resources cleared, background job stopped.");
		} catch (Exception error) {
			logger.e("clearResources error", error);
		}
	}
}