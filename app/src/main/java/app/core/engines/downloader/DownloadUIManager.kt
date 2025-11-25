package app.core.engines.downloader

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout.LayoutParams
import android.widget.LinearLayout.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.children
import app.core.AIOApp
import app.ui.main.MotherActivity
import app.ui.main.fragments.downloads.fragments.active.ActiveTasksFragment
import app.ui.main.fragments.downloads.fragments.finished.FinishedTasksFragment
import com.aio.R
import lib.process.AsyncJobUtils
import lib.process.LogHelperUtils
import lib.process.ThreadsUtility

class DownloadUIManager(private val downloadSystem: DownloadSystem) {

	private val logger = LogHelperUtils.from(javaClass)

	var safeMotherActivity: MotherActivity? = null
	var activeTasksFragment: ActiveTasksFragment? = null
	var finishedTasksFragment: FinishedTasksFragment? = null
	var loadingDownloadModelTextview: TextView? = null

	private val constantRowTagId = 1290111211

	@Synchronized
	fun redrawEverything() {
		logger.d("Redrawing all active download UI elements")
		ThreadsUtility.executeInBackground(codeBlock = {
			ThreadsUtility.executeOnMain(codeBlock = {
				logger.d("Clearing active tasks container")
				activeTasksFragment?.activeTasksListContainer?.removeAllViews()
				downloadSystem.activeDownloadDataModels.forEach { downloadDataModel ->
					logger.d("Adding UI for active download: ${downloadDataModel.fileName}")
					addNewActiveUI(downloadDataModel)
				}
			})
		})
	}

	@Synchronized
	fun addNewActiveUI(downloadModel: DownloadDataModel, position: Int = -1) {
		logger.d("Adding new active UI for: ${downloadModel.fileName}, position=$position")
		AsyncJobUtils.executeOnMainThread {
			val rowUI = generateActiveUI(downloadModel)
			logger.d("Generated new row UI for: ${downloadModel.fileName}")
			configureActiveUI(rowUI, downloadModel)
			val activeDownloadsListContainer = activeTasksFragment?.activeTasksListContainer
			if (position != -1) {
				logger.d("Inserting row at position: $position")
				activeDownloadsListContainer?.addView(rowUI, position)
			} else {
				logger.d("Appending row at end")
				activeDownloadsListContainer?.addView(rowUI)
			}
		}
	}

	@Synchronized
	fun updateActiveUI(downloadModel: DownloadDataModel) {
		logger.d("Updating active UI for: ${downloadModel.fileName}")
		AsyncJobUtils.executeOnMainThread {
			val activeDownloadsListContainer = activeTasksFragment?.activeTasksListContainer
			val resultedRow = findViewByDownloadId(activeDownloadsListContainer, downloadModel.downloadId)
			if (resultedRow != null) {
				logger.d("Found existing row, configuring UI for: ${downloadModel.fileName}")
				configureActiveUI(resultedRow, downloadModel)
			} else {
				logger.d("No existing row found for: ${downloadModel.fileName}")
			}
		}
	}

	@SuppressLint("InflateParams")
	private fun generateActiveUI(downloadModel: DownloadDataModel): View {
		logger.d("Generating UI for download model: ${downloadModel.fileName}")
		val rowUI = if (safeMotherActivity == null) {
			val inflater = LayoutInflater.from(AIOApp.INSTANCE)
			inflater.inflate(R.layout.frag_down_3_active_1_row_1, null)
		} else {
			val themedCtx = ContextThemeWrapper(safeMotherActivity, R.style.style_application)
			val inflater = LayoutInflater.from(themedCtx)
			inflater.inflate(R.layout.frag_down_3_active_1_row_1, null)
		}

		rowUI.apply {
			setTag(constantRowTagId, downloadModel.downloadId)
			isClickable = true
			setOnClickListener {
				logger.d("Row clicked for: ${downloadModel.fileName}")
				activeTasksFragment?.onDownloadUIItemClick(downloadModel)
			}
			setOnLongClickListener {
				logger.d("Row long-clicked for: ${downloadModel.fileName}")
				activeTasksFragment?.safeMotherActivityRef?.doSomeVibration(50)
				activeTasksFragment?.onDownloadUIItemClick(downloadModel); true
			}
			val dpValue = 0f
			val pixels = dpValue * context.resources.displayMetrics.density
			val layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
			layoutParams.apply { setMargins(0, 0, 0, pixels.toInt()) }
			this.layoutParams = layoutParams
		}

		return rowUI
	}

	@Synchronized
	fun removeActiveUI(downloadModel: DownloadDataModel) {
		logger.d("Removing active UI for: ${downloadModel.fileName}")
		val activeDownloadListContainer = activeTasksFragment?.activeTasksListContainer
		activeDownloadListContainer?.let { container ->
			val resultedRow = findViewByDownloadId(container, downloadModel.downloadId)
			if (resultedRow != null) {
				logger.d("Found row to remove for: ${downloadModel.fileName}")
				if (resultedRow.parent != null) {
					val parent = resultedRow.parent as ViewGroup
					parent.removeView(resultedRow)
					logger.d("Removed row from parent view group")
				}
				container.removeView(resultedRow)
				logger.d("Removed row from activeTasksListContainer")

				ThreadsUtility.executeInBackground(codeBlock = {
					ThreadsUtility.executeOnMain {
						val leftOverView = findViewByDownloadId(container, downloadModel.downloadId)
						if (leftOverView != null) {
							logger.d("Cleaning up remaining view for: ${downloadModel.fileName}")
							if (leftOverView.parent != null) {
								val parent = leftOverView.parent as ViewGroup
								parent.removeView(leftOverView)
							}
							activeTasksFragment?.activeTasksListContainer?.removeView(leftOverView)
						}
					}
				})
			} else {
				logger.d("No row found to remove for: ${downloadModel.fileName}")
			}
		}
	}

	private fun findViewByDownloadId(container: ViewGroup?, downloadId: Int): View? {
		return container?.children?.firstOrNull {
			it.getTag(constantRowTagId) == downloadId
		}
	}

	@Synchronized
	private fun configureActiveUI(rowUI: View, downloadModel: DownloadDataModel) {
		logger.d("Configuring UI for: ${downloadModel.fileName}")
		AsyncJobUtils.executeOnMainThread {
			if (rowUI.tag == null) {
				logger.d("Creating new DownloaderRowUI for: ${downloadModel.fileName}")
				rowUI.tag = DownloaderRowUIManager(rowUI)
			}
			(rowUI.tag as DownloaderRowUIManager).apply {
				logger.d("Updating DownloaderRowUI for: ${downloadModel.fileName}")
				updateView(downloadModel)
			}
		}
	}
}
