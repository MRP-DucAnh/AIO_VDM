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

	private final LogHelperUtils logger = LogHelperUtils.from(getClass());
	private final WeakReference<FinishedTasksFragment> fragmentRef;
	private final LayoutInflater layoutInflater;
	private final DownloadSystem downloadSystem;
	private int existingTaskCount;

	private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
	private Future<?> currentBackgroundTask = null;

	public FinishedTasksListAdapter(@NonNull FinishedTasksFragment fragment) {
		this.fragmentRef = new WeakReference<>(fragment);
		this.layoutInflater = from(fragment.getSafeBaseActivityRef());
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
			convertView = layoutInflater.inflate(layoutResId, null);
		}
		verifyAndUpdateViewHolder(convertView, position);
		return convertView;
	}

	@Override
	public void notifyDataSetChanged() {
		FinishedTasksFragment fragment = fragmentRef.get();
		if (fragment == null) return;

		int newTaskCount = fragment.getFinishedDownloadModels().size();
		if (newTaskCount != existingTaskCount) {
			super.notifyDataSetChanged();
			existingTaskCount = getCount();
			scheduleMediaStoreUpdate();
		}
	}

	private void scheduleMediaStoreUpdate() {
		try {
			if (currentBackgroundTask != null &&
					!currentBackgroundTask.isDone()) {
				currentBackgroundTask.cancel(true);
			}
		} catch (Exception error) {
			logger.e("Error found while scheduling media store update", error);
		}

		currentBackgroundTask = backgroundExecutor.submit(() -> {
			try {
				int count = getCount();
				for (int i = 0; i < count; i++) {
					if (Thread.currentThread().isInterrupted()) return;
					DownloadDataModel model = getItem(i);
					if (model == null) continue;
					File file = model.getDestinationFile();
					addToMediaStore(file);
				}
			} catch (Exception error) {
				logger.e("Error found in add to media file into gallery", error);
			}
		});
	}

	public void notifyDataSetChangedOnSort(Boolean isForceRefresh) {
		try {
			if (isForceRefresh) super.notifyDataSetChanged();
			else notifyDataSetChanged();
		} catch (Exception error) {
			logger.e("Error while notifying data set change", error);
		}
	}

	private void verifyAndUpdateViewHolder(View rowLayout, int position) {
		FinishedTasksViewHolder viewHolder;
		if (rowLayout.getTag() == null) {
			viewHolder = new FinishedTasksViewHolder(rowLayout);
			rowLayout.setTag(viewHolder);
		} else {
			viewHolder = (FinishedTasksViewHolder) rowLayout.getTag();
			viewHolder.clearResources();
		}
		FinishedTasksFragment fragment = fragmentRef.get();
		if (fragment != null) {
			viewHolder.updateView(getItem(position), fragment);
		}
	}

	public void clearResources() {
		try {
			if (currentBackgroundTask != null &&
					!currentBackgroundTask.isDone()) {
				currentBackgroundTask.cancel(true);
			}
			backgroundExecutor.shutdownNow();
		} catch (Exception error) {
			error.printStackTrace();
		}
	}
}
