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
		try {
			this.weakRefFinishedFrag = new WeakReference<>(fragment);
			this.layoutInflater = from(fragment.getSafeBaseActivityRef());
			this.downloadSystem = AIOApp.INSTANCE.getDownloadManager();
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
		if (downloadSystem == null) return null;

		boolean con_1 = filteredList == null;
		boolean con_2 = index < 0 || index >= filteredList.size();
		if (con_1 || con_2) return null;
		return filteredList.get(index);
	}

	@Override
	public long getItemId(int index) {
		return index;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		logger.d("getView() called for position: " + position);
		try {
			if (convertView == null) {
				int layoutResId = layout.frag_down_4_finish_1_row_1;
				convertView = layoutInflater.inflate(layoutResId, null);
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
		try {
			FinishedTasksFragment frag = weakRefFinishedFrag.get();
			if (frag == null) return;

			rebuildCache();

			int newCount = getCount();
			if (newCount != existingTaskCount) {
				existingTaskCount = newCount;
				super.notifyDataSetChanged();
				scheduleMediaStoreUpdate();
			}
		} catch (Exception error) {
			logger.e("Error in notifyDataSetChanged()", error);
		}
	}

	public boolean isFilterActive() {
		boolean con_1 = customFilter != null;
		boolean con_2 = filteredList.size() != originalList.size();
		return con_1 && con_2;
	}

	public void setFilter(@Nullable TaskFilter filter) {
		try {
			this.customFilter = filter;
			applyFilter();
			rebuildCache();
			super.notifyDataSetChanged();
		} catch (Exception error) {
			logger.e("Error in setFilter()", error);
		}
	}

	private void applyFilter() {
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
			if (downloadSystem == null) return;
			originalList = downloadSystem.getFinishedDownloadDataModels();
			applyFilter();
		} catch (Exception error) {
			logger.e("Error in rebuildCache()", error);
		}
	}

	private void scheduleMediaStoreUpdate() {
		logger.d("scheduleMediaStoreUpdate() called");
		try {
			if (backgroundJob != null && !backgroundJob.isDone()) backgroundJob.cancel(true);

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
						}
					}
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

	private void updateViewHolder(View rowLayout, int position) {
		try {
			FinishedTasksViewHolder holder;

			if (rowLayout.getTag() == null) {
				holder = new FinishedTasksViewHolder(rowLayout);
				rowLayout.setTag(holder);
				updateView(position, holder);
			} else {
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
			holder.updateView(item, fragment);
		} else {
			logger.d("Fragment is null in updateViewHolder() for position: " + position);
		}
	}

	public void clearResources() {
		try {
			weakRefFinishedFrag.clear();
			layoutInflater = null;
			downloadSystem = null;

			int count = getCount();
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

			if (backgroundJob != null && !backgroundJob.isDone()) backgroundJob.cancel(true);
			executor.shutdownNow();

			logger.d("Resources cleared successfully, executor shut down. " +
					"Total views processed: " + clearedCount);
		} catch (Exception error) {
			logger.e("Error clearing resources", error);
		}
	}
}