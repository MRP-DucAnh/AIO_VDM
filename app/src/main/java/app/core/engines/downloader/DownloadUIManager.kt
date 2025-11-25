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

	// Use two constant keys for clarity and type safety: one for the download ID, one for the UIManager instance
	private val constantRowTagId = 1219012121 // Use a resource ID for tag keys
	private val constantUIManagerTagId = 1212719910// Use a resource ID for tag keys

	@Synchronized
	fun redrawEverything() {
		logger.d("Redrawing all active download UI elements")
		ThreadsUtility.executeInBackground(codeBlock = {
			ThreadsUtility.executeOnMain(codeBlock = {
				logger.d("Clearing active tasks container and canceling resources")
				activeTasksFragment?.activeTasksListContainer?.let { container ->
					// 1. CLEAR: Iterate through existing views and call clearResources()
					container.children.forEach { view ->
						(view.getTag(constantUIManagerTagId) as? DownloaderRowUIManager)?.clearResources()
					}
					container.removeAllViews()
				}

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
				logger.d("No existing row found for: ${downloadModel.fileName}. Trying to add.")
				// OPTIMIZATION: If we are updating an item but the UI is missing (e.g., due to filtering/recycling issue)
				// we should add it back to ensure consistency.
				addNewActiveUI(downloadModel)
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
			// Set Download ID tag
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

				// 1. GC-PROOF STEP: Clear the resources (cancel coroutines, clear weak refs)
				(resultedRow.getTag(constantUIManagerTagId) as? DownloaderRowUIManager)?.clearResources()
				logger.d("Cleared resources for DownloaderRowUIManager: ${downloadModel.fileName}")

				// 2. REMOVAL: Remove the view from the container
				container.removeView(resultedRow)
				logger.d("Removed row from activeTasksListContainer: ${downloadModel.fileName}")

				// 3. OPTIMIZATION: Remove redundant background cleanup logic.
				// container.removeView(resultedRow) is usually sufficient if the view is a direct child.
				// The inner ThreadsUtility block for cleanup is confusing and likely unnecessary
				// since removeView(resultedRow) already handles removing it from its parent.
			} else {
				logger.d("No row found to remove for: ${downloadModel.fileName}")
			}
		}
	}

	private fun findViewByDownloadId(container: ViewGroup?, downloadId: Int): View? {
		// OPTIMIZATION: Use the resource ID tag constant
		return container?.children?.firstOrNull {
			it.getTag(constantRowTagId) == downloadId
		}
	}

	@Synchronized
	private fun configureActiveUI(rowUI: View, downloadModel: DownloadDataModel) {
		logger.d("Configuring UI for: ${downloadModel.fileName}")
		AsyncJobUtils.executeOnMainThread {
			// OPTIMIZATION: Use the dedicated UIManager Tag Key for storage
			var rowUIManager = rowUI.getTag(constantUIManagerTagId) as? DownloaderRowUIManager

			if (rowUIManager == null) {
				logger.d("Creating new DownloaderRowUI for: ${downloadModel.fileName}")
				rowUIManager = DownloaderRowUIManager(rowUI)
				// Store the UIManager instance using the dedicated tag key
				rowUI.setTag(constantUIManagerTagId, rowUIManager)
			}

			rowUIManager.apply {
				logger.d("Updating DownloaderRowUI for: ${downloadModel.fileName}")
				updateView(downloadModel)
			}
		}
	}
}