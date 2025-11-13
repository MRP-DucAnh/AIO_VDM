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

	private final LogHelperUtils log = LogHelperUtils.from(getClass());
	private final WeakReference<FinishedTasksFragment> fragmentRef;
	private final LayoutInflater inflater;
	private final DownloadSystem downloadSystem;
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
		return downloadSystem.getFinishedDownloadDataModels().size();
	}

	@Override
	public DownloadDataModel getItem(int index) {
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
					for (int i = 0; i < count; i++) {
						if (Thread.currentThread().isInterrupted()) return;
						DownloadDataModel model = getItem(i);
						if (model == null) continue;

						File file = model.getDestinationFile();
						addToMediaStore(file);
					}
					log.d("Media store updated for " + count + " files.");
				} catch (Exception e) {
					log.e("Error updating media store", e);
				}
			});
		} catch (Exception e) {
			log.e("Failed to schedule media store update", e);
		}
	}

	public void notifyDataSetChangedOnSort(boolean forceRefresh) {
		try {
			if (forceRefresh) super.notifyDataSetChanged();
			else notifyDataSetChanged();
		} catch (Exception e) {
			log.e("notifyDataSetChangedOnSort error", e);
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
			if (backgroundJob != null && !backgroundJob.isDone()) {
				backgroundJob.cancel(true);
			}
			executor.shutdownNow();
			log.d("Resources cleared, executor shut down.");
		} catch (Exception e) {
			log.e("Error clearing resources", e);
		}
	}
}
