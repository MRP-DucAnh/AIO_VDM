package app.ui.main.fragments.downloads.fragments.finished

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.isVisible
import app.core.AIOApp
import app.core.AIOApp.Companion.aioRawFiles
import app.core.AIOApp.Companion.aioSettings
import app.core.AIOApp.Companion.downloadSystem
import app.core.AIOTimer.AIOTimerListener
import app.core.bases.BaseFragment
import app.core.engines.downloader.DownloadDataModel
import app.core.engines.settings.AIOSettings
import app.ui.main.MotherActivity
import app.ui.main.fragments.downloads.DownloadsFragment
import app.ui.main.guides.GuidePlatformPicker
import com.aio.R
import com.airbnb.lottie.LottieAnimationView
import lib.device.SecureFileUtil.authenticate
import lib.process.LogHelperUtils
import lib.ui.ViewUtility.hideView
import lib.ui.ViewUtility.showView
import java.lang.ref.WeakReference

class FinishedTasksFragment : BaseFragment(),
	FinishedTasksClickEvents, AIOTimerListener {

	private val logger = LogHelperUtils.from(javaClass)
	private val weakSelfReference = WeakReference(this)
	private var emptyDownloadContainer: View? = null
	private var buttonOpenActiveTasks: View? = null
	private var openActiveTasksAnim: LottieAnimationView? = null
	private var buttonHowToDownload: View? = null
	private var taskListView: ListView? = null
	private var lastCheckedFinishedTasks = 0

	var finishedTasksListAdapter: FinishedTasksListAdapter? = null
	val safeMotherActivityRef: MotherActivity? get() = safeBaseActivityRef as MotherActivity
	val safeFinishTasksFragment: FinishedTasksFragment? get() = weakSelfReference.get()

	override fun getLayoutResId(): Int {
		return R.layout.frag_down_4_finish_1
	}

	override fun onAfterLayoutLoad(layoutView: View, state: Bundle?) {
		initializeViewsAndListAdapter(layoutView)
	}

	override fun onResumeFragment() {
		registerIntoDownloadSystem()
		registerToDownloadFragment()
		safeFinishTasksFragment?.let { AIOApp.aioTimer.register(it) }
	}

	override fun onPauseFragment() {
		unregisterIntoDownloadSystem()
		unregisterToDownloadFragment()
		safeFinishTasksFragment?.let { AIOApp.aioTimer.unregister(it) }
	}

	override fun onAIOTimerTick(loopCount: Double) {
		safeFinishTasksFragment?.let {
			updateDownloadFragmentTitle(parentFragment as? DownloadsFragment)
			toggleEmptyDownloadListviewVisibility(emptyDownloadContainer, taskListView)
			toggleOpenActiveTasksButtonVisibility(buttonOpenActiveTasks)
		}
	}

	override fun onDestroyView() {
		safeFinishTasksFragment?.let { AIOApp.aioTimer.unregister(it) }
		downloadSystem.downloadsUIManager.finishedTasksFragment = null
		finishedTasksListAdapter?.let { adapter ->
			adapter.clearResources()
			for (index in 0 until adapter.count) {
				(adapter.getView(index, null, null).tag as?
						FinishedTasksViewHolder)?.cancelAll()
			}
		}
		weakSelfReference.clear()
		finishedTasksListAdapter = null
		taskListView?.adapter = null

		taskListView = null
		emptyDownloadContainer = null
		buttonOpenActiveTasks = null
		buttonHowToDownload = null
		openActiveTasksAnim = null

		super.onDestroyView()
	}

	override fun onFinishedDownloadClick(downloadModel: DownloadDataModel) {
		safeMotherActivityRef?.let {
			val globalSettings = downloadModel.globalSettings
			val downloadLocation = globalSettings.defaultDownloadLocation
			val finishTaskOptions = FinishedDownloadOptions(safeFinishTasksFragment)

			fun openOptions() {
				if (aioSettings.openDownloadedFileOnSingleClick) {
					finishTaskOptions.setDownloadModel(downloadModel)
					finishTaskOptions.playTheMedia()
				} else {
					finishTaskOptions.show(downloadModel)
				}
			}

			if (downloadLocation == AIOSettings.PRIVATE_FOLDER) {
				authenticate(activity = it, onResult = { s -> if (s) openOptions() })
			} else openOptions()
		}
	}

	override fun onFinishedDownloadLongClick(downloadModel: DownloadDataModel) {
		safeMotherActivityRef?.let { activity ->
			fun openOptions() {
				val opt = FinishedDownloadOptions(safeFinishTasksFragment)
				activity.doSomeVibration()
				opt.show(downloadModel)
			}

			val globalSettings = downloadModel.globalSettings
			val loc = globalSettings.defaultDownloadLocation
			if (loc == AIOSettings.PRIVATE_FOLDER) {
				authenticate(activity = activity, onResult = { s -> if (s) openOptions() })
			} else openOptions()
		}
	}

	fun getFinishedDownloadModels(): ArrayList<DownloadDataModel> {
		return downloadSystem.finishedDownloadDataModels
	}

	private fun initializeViewsAndListAdapter(layout: View) {
		safeFinishTasksFragment?.let { fragment ->
			val activityRef = fragment.safeMotherActivityRef
			emptyDownloadContainer = layout.findViewById(R.id.container_empty_downloads)

			buttonHowToDownload = layout.findViewById(R.id.btn_how_to_download)
			buttonHowToDownload?.setOnClickListener { GuidePlatformPicker(activityRef).show() }

			buttonOpenActiveTasks = layout.findViewById(R.id.btn_open_active_downloads)
			buttonOpenActiveTasks?.setOnClickListener { openActiveTasksFragment() }

			openActiveTasksAnim = layout.findViewById(R.id.img_open_active_downloads)
			loadOpenActiveTasksAnimation()

			taskListView = layout.findViewById(R.id.container_download_tasks_finished)

			finishedTasksListAdapter = FinishedTasksListAdapter(fragment)
			taskListView?.adapter = finishedTasksListAdapter

			toggleEmptyDownloadListviewVisibility(emptyDownloadContainer, taskListView)
			toggleOpenActiveTasksButtonVisibility(buttonOpenActiveTasks)
		}
	}

	private fun openActiveTasksFragment() {
		(parentFragment as? DownloadsFragment)?.openActiveTab()
	}

	private fun toggleOpenActiveTasksButtonVisibility(button: View?) {
		button?.let {
			val active = downloadSystem.activeDownloadDataModels
			if (active.isNotEmpty()) {
				if (!it.isVisible) showView(it, true, 300)
			} else {
				if (it.isVisible) hideView(it, true, 300)
			}
		}
	}

	private fun toggleEmptyDownloadListviewVisibility(emptyView: View?, listView: ListView?) {
		if (downloadSystem.isInitializing) return
		if (emptyView == null || listView == null) return
		if (getFinishedDownloadModels().isEmpty()) {
			hideView(listView, true, 100).let { showView(emptyView, true, 300) }
		} else {
			hideView(emptyView, true, 100).let { showView(listView, true, 300) }
		}
		finishedTasksListAdapter?.notifyDataSetChangedOnSort(false)
	}

	private fun registerToDownloadFragment() {
		safeFinishTasksFragment?.let {
			val downloadsFragment = parentFragment as? DownloadsFragment
			downloadsFragment?.finishedTasksFragment = it
			updateDownloadFragmentTitle(downloadsFragment)
		}
	}

	private fun unregisterToDownloadFragment() {
		safeFinishTasksFragment?.let {
			val downloadsFragment = parentFragment as? DownloadsFragment
			downloadsFragment?.finishedTasksFragment = null
		}
	}

	fun updateDownloadFragmentTitle(downloadsFragment: DownloadsFragment?) {
		downloadsFragment?.safeFragmentLayoutRef?.let {
			if (isFragmentRunning == false) return
			val total = downloadSystem.finishedDownloadDataModels.size
			if (total == lastCheckedFinishedTasks && total > 0) return
			val title = it.findViewById<TextView>(R.id.txt_current_frag_name)
			val fixedName = getText(R.string.title_downloaded_files)
			val titleText = "$fixedName ($total)"
			title?.text = titleText
		}
	}

	private fun registerIntoDownloadSystem() {
		downloadSystem.downloadsUIManager.finishedTasksFragment = safeFinishTasksFragment
	}

	private fun unregisterIntoDownloadSystem() {
		downloadSystem.downloadsUIManager.finishedTasksFragment = null
	}

	private fun loadOpenActiveTasksAnimation() {
		openActiveTasksAnim?.apply {
			clipToCompositionBounds = false
			setScaleType(ImageView.ScaleType.FIT_XY)
			aioRawFiles.getDownloadFoundAnimationComposition()?.let {
				setComposition(it)
				playAnimation()
			} ?: setAnimation(R.raw.animation_videos_found)
			showView(this, true, 100)
		}
	}
}
