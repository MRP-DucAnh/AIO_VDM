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

public class FinishedTasksListAdapter extends BaseAdapter {

	private final LogHelperUtils logger = LogHelperUtils.from(getClass());
	private final WeakReference<FinishedTasksFragment> fragmentRef;
	private LayoutInflater inflater;
	private DownloadSystem downloadSystem;
	private int existingTaskCount;

	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private Future<?> backgroundJob;

	public FinishedTasksListAdapter(@NonNull FinishedTasksFragment fragment) {
		this.fragmentRef = new WeakReference<>(fragment);
		this.inflater = from(fragment.getSafeBaseActivityRef());
		this.downloadSystem = AIOApp.INSTANCE.getDownloadManager();
	}

	@Override
	public int getCount() {
		if (downloadSystem == null) return 0;
		return downloadSystem.getFinishedDownloadDataModels().size();
	}

	@Override
	@Nullable
	public DownloadDataModel getItem(int index) {
		if (downloadSystem == null) return null;
		return downloadSystem.getFinishedDownloadDataModels().get(index);
	}

	@Override
	public long getItemId(int index) {
		return index;
	}

	@SuppressLint("InflateParams")
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		if (convertView == null) {
			int layoutResId = layout.frag_down_4_finish_1_row_1;
			convertView = inflater.inflate(layoutResId, null);
		}
		updateViewHolder(convertView, position);
		return convertView;
	}

	@Override
	public void notifyDataSetChanged() {
		FinishedTasksFragment fragment = fragmentRef.get();
		if (fragment == null) return;

		int newCount = fragment.getFinishedDownloadModels().size();
		if (newCount != existingTaskCount) {
			super.notifyDataSetChanged();
			existingTaskCount = getCount();
			scheduleMediaStoreUpdate();
		}
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
			if (forceRefresh) super.notifyDataSetChanged();
			else notifyDataSetChanged();
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
			holder.clearResources();
		}

		FinishedTasksFragment fragment = fragmentRef.get();
		if (fragment != null) holder.updateView(getItem(position), fragment);
	}

	public void clearResources() {
		try {
			fragmentRef.clear();
			inflater = null;
			downloadSystem = null;
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
