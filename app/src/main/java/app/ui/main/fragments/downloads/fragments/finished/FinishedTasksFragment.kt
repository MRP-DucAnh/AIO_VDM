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
	private val weakReferenceOfFragment = WeakReference(this)
	val safeMotherActivityRef by lazy { WeakReference(safeBaseActivityRef as MotherActivity).get() }
	val safeFinishTasksFragment by lazy { WeakReference(this).get() }

	private lateinit var emptyDownloadContainer: View
	private lateinit var buttonOpenActiveTasks: View
	private lateinit var openActiveTasksAnim: LottieAnimationView
	private lateinit var buttonHowToDownload: View
	private lateinit var taskListView: ListView

	lateinit var finishedTasksListAdapter: FinishedTasksListAdapter
	private var lastCheckedFinishedTasks = 0

	override fun getLayoutResId(): Int {
		return R.layout.frag_down_4_finish_1
	}

	override fun onAfterLayoutLoad(layoutView: View, state: Bundle?) {
		safeFinishTasksFragment?.let {
			selfReferenceRegisterIntoDownloadSystem()
			initializeViewsAndListAdapter(layoutView)
		}
	}

	override fun onResumeFragment() {
		selfReferenceRegisterToDownloadFragment()
		safeFinishTasksFragment?.let { AIOApp.aioTimer.register(it) }
	}

	override fun onPauseFragment() {
		selfReferenceRegisterToDownloadFragment()
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
		finishedTasksListAdapter.let { adapter ->
			adapter.clearResources()
			for (i in 0 until adapter.count) {
				(adapter.getView(i, null, null).tag as?
						FinishedTasksViewHolder)?.cancelAll()
			}
		}
		super.onDestroyView()
	}

	override fun onFinishedDownloadClick(downloadModel: DownloadDataModel) {
		safeMotherActivityRef?.let {
			val globalSettings = downloadModel.globalSettings
			val downloadLocation = globalSettings.defaultDownloadLocation
			val finishTaskOptions = FinishedDownloadOptions(weakReferenceOfFragment.get())

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
		safeMotherActivityRef?.let {
			fun openOptions() {
				val opt = FinishedDownloadOptions(weakReferenceOfFragment.get())
				safeMotherActivityRef?.doSomeVibration(50)
				opt.show(downloadModel)
			}

			val globalSettings = downloadModel.globalSettings
			val loc = globalSettings.defaultDownloadLocation
			if (loc == AIOSettings.PRIVATE_FOLDER) {
				authenticate(activity = it, onResult = { s -> if (s) openOptions() })
			} else openOptions()
		}
	}

	open fun getFinishedDownloadModels(): ArrayList<DownloadDataModel> {
		return downloadSystem.finishedDownloadDataModels
	}

	private fun initializeViewsAndListAdapter(layoutView: View) {
		safeFinishTasksFragment?.let {
			emptyDownloadContainer = layoutView.findViewById(R.id.container_empty_downloads)

			buttonHowToDownload = layoutView.findViewById(R.id.btn_how_to_download)
			buttonHowToDownload.setOnClickListener { GuidePlatformPicker(safeMotherActivityRef).show() }

			buttonOpenActiveTasks = layoutView.findViewById(R.id.btn_open_active_downloads)
			buttonOpenActiveTasks.setOnClickListener { openActiveTasksFragment() }

			openActiveTasksAnim = layoutView.findViewById(R.id.img_open_active_downloads)
			loadOpenActiveTasksAnimation()

			taskListView = layoutView.findViewById(R.id.container_download_tasks_finished)
			finishedTasksListAdapter = FinishedTasksListAdapter(it)
			taskListView.adapter = finishedTasksListAdapter

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

	private fun toggleEmptyDownloadListviewVisibility(emptyView: View, listView: ListView) {
		if (downloadSystem.isInitializing) return
		if (getFinishedDownloadModels().isEmpty()) {
			hideView(listView, true, 100).let { showView(emptyView, true, 300) }
		} else {
			hideView(emptyView, true, 100).let { showView(listView, true, 300) }
		}
		finishedTasksListAdapter.notifyDataSetChangedOnSort(false)
	}

	private fun selfReferenceRegisterToDownloadFragment() {
		safeFinishTasksFragment?.let {
			val f = parentFragment as? DownloadsFragment
			f?.finishedTasksFragment = it
			updateDownloadFragmentTitle(f)
		}
	}

	fun updateDownloadFragmentTitle(downloadsFragment: DownloadsFragment?) {
		downloadsFragment?.safeFragmentLayoutRef?.let {
			if (isFragmentRunning == false) return
			val total = downloadSystem.finishedDownloadDataModels.size
			if (total == lastCheckedFinishedTasks && total > 0) return
			val title = it.findViewById<TextView>(R.id.txt_current_frag_name)
			val fixedName = getText(R.string.title_downloaded_files)
			title?.text = "$fixedName ($total)"
		}
	}

	private fun selfReferenceRegisterIntoDownloadSystem() {
		safeFinishTasksFragment?.let {
			downloadSystem.downloadsUIManager.finishedTasksFragment = it
		}
	}

	private fun loadOpenActiveTasksAnimation() {
		openActiveTasksAnim.apply {
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
