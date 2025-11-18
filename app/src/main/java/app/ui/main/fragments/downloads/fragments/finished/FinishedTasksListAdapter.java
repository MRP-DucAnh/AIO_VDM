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

import app.core.AIOApp;
import app.core.engines.downloader.DownloadDataModel;
import app.core.engines.downloader.DownloadSystem;
import lib.process.LogHelperUtils;

public class FinishedTasksListAdapter extends BaseAdapter {

	private final LogHelperUtils logger = LogHelperUtils.from(getClass());
	private final WeakReference<FinishedTasksFragment> weakRefFinishedFrag;
	private LayoutInflater layoutInflater;
	private DownloadSystem downloadSystem;

	private int existingTaskCount;
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private Future<?> backgroundJob;

	private TaskFilter customFilter;
	private List<DownloadDataModel> originalList = new ArrayList<>();
	private List<DownloadDataModel> filteredList = new ArrayList<>();

	public interface TaskFilter {
		boolean accept(DownloadDataModel model);
	}

	public FinishedTasksListAdapter(@NonNull FinishedTasksFragment fragment) {
		logger.d("FinishedTasksListAdapter constructor called");
		try {
			this.weakRefFinishedFrag = new WeakReference<>(fragment);
			this.layoutInflater = from(fragment.getSafeBaseActivityRef());
			this.downloadSystem = AIOApp.INSTANCE.getDownloadManager();
			logger.d("Adapter initialized with fragment reference and download system");
			this.rebuildCache();
		} catch (Exception error) {
			logger.e("Error in FinishedTasksListAdapter constructor", error);
			throw error;
		}
	}

	@Override
	public int getCount() {
		int count = (downloadSystem == null) ? 0 :
				(filteredList == null ? 0 : filteredList.size());
		logger.d("getCount() returning: " + count);
		return count;
	}

	@Override
	@Nullable
	public DownloadDataModel getItem(int index) {
		logger.d("getItem() called for index: " + index);
		if (downloadSystem == null) {
			logger.d("downloadSystem is null in getItem()");
			return null;
		}

		boolean con_1 = filteredList == null;
		boolean con_2 = index < 0 || index >= filteredList.size();
		if (con_1 || con_2) {
			logger.d("Invalid index or filteredList. index: " + index +
					", filteredList null: " + con_1 + ", index out of bounds: " + con_2);
			return null;
		}

		DownloadDataModel item = filteredList.get(index);
		logger.d("getItem() returning item at index " + index + ": " +
				(item != null ? item.getDestinationFile().getName() : "null"));

		return item;
	}

	@Override
	public long getItemId(int index) {
		logger.d("getItemId() called for index: " + index);
		return index;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		logger.d("getView() called for position: " + position);
		try {
			if (convertView == null) {
				int layoutResId = layout.frag_down_4_finish_1_row_1;
				convertView = layoutInflater.inflate(layoutResId, null);
				logger.d("Created new view for position: " + position);
			} else {
				logger.d("Reusing existing view for position: " + position);
			}

			updateViewHolder(convertView, position);
			return convertView;
		} catch (Exception error) {
			logger.e("Error in getView() for position: " + position, error);
			throw error;
		}
	}

	@Override
	public void notifyDataSetChanged() {
		logger.d("notifyDataSetChanged() called");
		try {
			FinishedTasksFragment frag = weakRefFinishedFrag.get();
			if (frag == null) {
				logger.d("Fragment reference is null in notifyDataSetChanged()");
				return;
			}

			rebuildCache();

			int newCount = getCount();
			logger.d("New task count: " + newCount + ", existing task count: " + existingTaskCount);

			if (newCount != existingTaskCount) {
				existingTaskCount = newCount;
				logger.d("Task count changed, calling super.notifyDataSetChanged()");
				super.notifyDataSetChanged();
				scheduleMediaStoreUpdate();
			} else {
				logger.d("Task count unchanged, skipping super.notifyDataSetChanged()");
			}
		} catch (Exception error) {
			logger.e("Error in notifyDataSetChanged()", error);
		}
	}

	public boolean isFilterActive() {
		boolean con_1 = customFilter != null;
		boolean con_2 = filteredList.size() != originalList.size();

		boolean isActive = con_1 && con_2;
		logger.d("isFilterActive() returning: " + isActive +
				" (filter: " + con_1 + ", size diff: " + con_2 + ")");

		return isActive;
	}

	public void setFilter(@Nullable TaskFilter filter) {
		logger.d("setFilter() called, filter: " + (filter != null ? "provided" : "null"));
		try {
			this.customFilter = filter;
			applyFilter();
			rebuildCache();
			logger.d("Filter applied, calling super.notifyDataSetChanged()");
			super.notifyDataSetChanged();
		} catch (Exception error) {
			logger.e("Error in setFilter()", error);
		}
	}

	private void applyFilter() {
		logger.d("applyFilter() called, customFilter: " +
				(customFilter != null ? "present" : "null"));
		try {
			if (customFilter == null) {
				filteredList = new ArrayList<>(originalList);
				logger.d("No filter applied, filteredList size: " + filteredList.size());
				return;
			}

			List<DownloadDataModel> newList = new ArrayList<>();
			int acceptedCount = 0;
			for (DownloadDataModel model : originalList) {
				if (model != null && customFilter.accept(model)) {
					newList.add(model);
					acceptedCount++;
				}
			}
			filteredList = newList;
			logger.d("Filter applied. Original: " + originalList.size() +
					", Filtered: " + filteredList.size() + ", Accepted: " + acceptedCount);

		} catch (Exception error) {
			logger.e("Error in applyFilter()", error);
		}
	}

	private void rebuildCache() {
		logger.d("rebuildCache() called");
		try {
			if (downloadSystem == null) {
				logger.d("downloadSystem is null in rebuildCache()");
				return;
			}

			List<DownloadDataModel> newOriginalList = downloadSystem.getFinishedDownloadDataModels();
			logger.d("Retrieved " + newOriginalList.size() + " finished download models");

			originalList = newOriginalList;
			applyFilter();
			logger.d("Cache rebuilt. Original list: " +
					originalList.size() + ", Filtered list: " + filteredList.size());
		} catch (Exception error) {
			logger.e("Error in rebuildCache()", error);
		}
	}

	private void scheduleMediaStoreUpdate() {
		logger.d("scheduleMediaStoreUpdate() called");
		try {
			if (backgroundJob != null && !backgroundJob.isDone()) {
				logger.d("Cancelling previous background job");
				backgroundJob.cancel(true);
			}

			backgroundJob = executor.submit(() -> {
				logger.d("Background media store update started");
				try {
					int count = getCount();
					logger.d("Processing " + count + " files for media store update");

					int processedCount = 0;
					int errorCount = 0;

					for (int index = 0; index < count; index++) {
						if (Thread.currentThread().isInterrupted()) {
							logger.d("Media store update interrupted");
							return;
						}

						DownloadDataModel model = getItem(index);
						if (model == null) {
							logger.d("Model is null at index: " + index);
							continue;
						}

						File file = model.getDestinationFile();
						if (file.exists()) {
							try {
								addToMediaStore(file);
								processedCount++;
								logger.d("Added to media store: " + file.getName());
							} catch (Exception fileError) {
								errorCount++;
								logger.e("Error adding file to media store: " + file.getName(), fileError);
							}
						} else {
							logger.d("File does not exist or is null: " + file.getAbsolutePath());
						}
					}
					logger.d("Media store update completed. Total: " + count +
							", Processed: " + processedCount + ", Errors: " + errorCount);
				} catch (Exception error) {
					logger.e("Error in background media store update", error);
				}
			});
			logger.d("Media store update scheduled successfully");
		} catch (Exception error) {
			logger.e("Failed to schedule media store update", error);
		}
	}

	public void notifyDataSetChangedOnSort(boolean forceRefresh) {
		logger.d("notifyDataSetChangedOnSort() called, forceRefresh: " + forceRefresh);
		try {
			if (forceRefresh) {
				logger.d("Force refresh requested, rebuilding cache");
				rebuildCache();
				super.notifyDataSetChanged();
			} else {
				logger.d("No force refresh, calling regular notifyDataSetChanged");
				notifyDataSetChanged();
			}
		} catch (Exception error) {
			logger.e("notifyDataSetChangedOnSort error", error);
		}
	}

	private void updateViewHolder(View rowLayout, int position) {
		logger.d("updateViewHolder() called for position: " + position);
		try {
			FinishedTasksViewHolder holder;

			if (rowLayout.getTag() == null) {
				logger.d("Creating new ViewHolder for position: " + position);
				holder = new FinishedTasksViewHolder(rowLayout);
				rowLayout.setTag(holder);
				updateView(position, holder);
			} else {
				logger.d("Reusing existing ViewHolder for position: " + position);
				holder = (FinishedTasksViewHolder) rowLayout.getTag();
				updateView(position, holder);
			}

		} catch (Exception error) {
			logger.e("Error in updateViewHolder() for position: " + position, error);
		}
	}

	private void updateView(int position, FinishedTasksViewHolder holder) {
		FinishedTasksFragment fragment = weakRefFinishedFrag.get();
		if (fragment != null) {
			DownloadDataModel item = getItem(position);
			logger.d("Updating ViewHolder with item: " +
					(item != null ? item.getDestinationFile().getName() : "null"));
			holder.updateView(item, fragment);
		} else {
			logger.d("Fragment is null in updateViewHolder() for position: " + position);
		}
	}

	public void clearResources() {
		logger.d("clearResources() called");
		try {
			weakRefFinishedFrag.clear();
			layoutInflater = null;
			downloadSystem = null;
			logger.d("Cleared weak reference and nullified dependencies");

			int count = getCount();
			logger.d("Clearing resources for " + count + " views");

			int clearedCount = 0;
			for (int index = 0; index < count; index++) {
				try {
					View view = getView(index, null, null);
					if (view != null) {
						Object tag = view.getTag();
						if (tag instanceof FinishedTasksViewHolder) {
							((FinishedTasksViewHolder) tag).clearResources(true);
							clearedCount++;
						}
					}
				} catch (Exception viewError) {
					logger.e("Error clearing resources for view at index: " + index, viewError);
				}
			}
			logger.d("Cleared resources for " + clearedCount + " view holders");

			if (backgroundJob != null && !backgroundJob.isDone()) {
				logger.d("Cancelling background job");
				backgroundJob.cancel(true);
			}

			executor.shutdownNow();
			logger.d("Resources cleared successfully, executor shut down. " +
					"Total views processed: " + clearedCount);

		} catch (Exception error) {
			logger.e("Error clearing resources", error);
		}
	}
}