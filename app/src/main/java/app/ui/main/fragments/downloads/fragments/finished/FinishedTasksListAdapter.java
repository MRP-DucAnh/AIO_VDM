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
		this.weakRefFinishedFrag = new WeakReference<>(fragment);
		this.layoutInflater = from(fragment.getSafeBaseActivityRef());
		this.downloadSystem = AIOApp.INSTANCE.getDownloadManager();
		this.rebuildCache();
	}

	@Override
	public int getCount() {
		if (downloadSystem == null) return 0;
		return filteredList == null ? 0 : filteredList.size();
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
		if (convertView == null) {
			int layoutResId = layout.frag_down_4_finish_1_row_1;
			convertView = layoutInflater.inflate(layoutResId, null);
		}
		updateViewHolder(convertView, position);
		return convertView;
	}

	@Override
	public void notifyDataSetChanged() {
		FinishedTasksFragment frag = weakRefFinishedFrag.get();
		if (frag == null) return;

		rebuildCache();

		int newCount = frag.getFinishedDownloadModels().size();

		if (newCount != existingTaskCount) {
			super.notifyDataSetChanged();
			existingTaskCount = getCount();
			scheduleMediaStoreUpdate();
		}
	}

	public boolean isFilterActive() {
		boolean con_1 = customFilter != null;
		boolean con_2 = filteredList.size() != originalList.size();
		return con_1 && con_2;
	}

	public void setFilter(@Nullable TaskFilter filter) {
		this.customFilter = filter;
		applyFilter();
		rebuildCache();
		super.notifyDataSetChanged();
	}

	private void applyFilter() {
		if (customFilter == null) {
			filteredList = new ArrayList<>(originalList);
			return;
		}
		List<DownloadDataModel> newList = new ArrayList<>();
		for (DownloadDataModel model : originalList) {
			if (model != null && customFilter.accept(model)) {
				newList.add(model);
			}
		}
		filteredList = newList;
	}

	private void rebuildCache() {
		if (downloadSystem == null) return;
		originalList = downloadSystem.getFinishedDownloadDataModels();
		applyFilter();
	}

	private void scheduleMediaStoreUpdate() {
		try {
			if (backgroundJob != null && !backgroundJob.isDone()) {
				backgroundJob.cancel(true);
			}

			backgroundJob = executor.submit(() -> {
				try {
					int count = getCount();
					for (int index = 0; index < count; index++) {
						if (Thread.currentThread().isInterrupted()) return;

						DownloadDataModel model = getItem(index);
						if (model == null) continue;

						File file = model.getDestinationFile();
						addToMediaStore(file);
					}
					logger.d("Media store updated for " + count + " files.");
				} catch (Exception error) {
					logger.e("Error updating media store", error);
				}
			});
		} catch (Exception error) {
			logger.e("Failed to schedule media store update", error);
		}
	}

	public void notifyDataSetChangedOnSort(boolean forceRefresh) {
		try {
			if (forceRefresh) {
				rebuildCache();
				super.notifyDataSetChanged();
			} else notifyDataSetChanged();
		} catch (Exception error) {
			logger.e("notifyDataSetChangedOnSort error", error);
		}
	}

	private void updateViewHolder(View rowLayout, int position) {
		FinishedTasksViewHolder holder;

		if (rowLayout.getTag() == null) {
			holder = new FinishedTasksViewHolder(rowLayout);
			rowLayout.setTag(holder);
		} else {
			holder = (FinishedTasksViewHolder) rowLayout.getTag();
		}

		FinishedTasksFragment fragment = weakRefFinishedFrag.get();
		if (fragment != null) {
			holder.updateView(getItem(position), fragment);
		}
	}

	public void clearResources() {
		try {
			weakRefFinishedFrag.clear();
			layoutInflater = null;
			downloadSystem = null;

			int count = getCount();
			for (int index = 0; index < count; index++) {
				View view = getView(index, null, null);
				if (view != null) {
					Object tag = view.getTag();
					if (tag instanceof FinishedTasksViewHolder) {
						((FinishedTasksViewHolder) tag).clearResources(true);
					}
				}
			}

			if (backgroundJob != null && !backgroundJob.isDone()) {
				backgroundJob.cancel(true);
			}

			executor.shutdownNow();
			logger.d("Resources cleared, executor shut down.");

		} catch (Exception error) {
			logger.e("Error clearing resources", error);
		}
	}
}
