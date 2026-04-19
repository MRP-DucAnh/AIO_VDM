package app.core.bases.dialogs

import android.content.*
import android.view.*
import android.widget.*
import app.core.AIOApp.Companion.aioRawFiles
import app.core.bases.interfaces.*
import app.core.engines.downloader.DownloadNotification.Companion.FROM_DOWNLOAD_NOTIFICATION
import app.core.engines.downloader.DownloadNotification.Companion.INTENT_EXTRA_SOURCE_ORIGIN
import app.ui.main.*
import com.aio.R
import com.airbnb.lottie.*
import kotlinx.coroutines.*
import lib.process.*
import lib.process.ThreadsUtility.executeInBackground
import lib.texts.CommonTextUtils.getText
import lib.ui.ViewUtility.setBounceClick
import lib.ui.ViewUtility.setLeftSideDrawable
import lib.ui.ViewUtility.showView
import lib.ui.builders.*
import java.lang.ref.*

class PostDownloadDialog(
	private val activityInf: BaseActivityInf,
	private val onCloseDialog: () -> Unit = {}
) {

	private val logger = LogHelperUtils.from(javaClass)
	private val weakActivityInf = WeakReference(activityInf)
	private val activityRef get() = weakActivityInf.get()?.getActivity()

	private val dialogBuilder = DialogBuilder(activityRef)
	private val audioPlayerUtils = AudioPlayerUtils(activityRef)

	suspend fun initialize(): PostDownloadDialog {
		return withMainContext {
			audioPlayerUtils.prepare(R.raw.sound_download_added)
			dialogBuilder.setView(R.layout.dialog_waiting_progress_1)
			dialogBuilder.view.apply {
				setupLoadingAnimation()
				configureSuccessLayout()
			}
			return@withMainContext this@PostDownloadDialog
		}
	}

	suspend fun show() {
		withMainContext {
			dialogBuilder.show()
			configureDismissHandler(onCloseDialog)
			playDownloadAddedSFX()
		}
	}

	suspend fun close() {
		withMainContext {
			dialogBuilder.close()
		}
	}

	private suspend fun configureDismissHandler(onCloseDialog: () -> Unit) {
		dialogBuilder.dialog.setOnDismissListener {
			executeInBackground(200, { audioPlayerUtils.stop() })
			onCloseDialog.invoke()
		}
		dialogBuilder.dialog.setOnCancelListener {
			executeInBackground(200, { audioPlayerUtils.stop() })
			onCloseDialog.invoke()
		}
	}

	private suspend fun View.setupLoadingAnimation() {
		findViewById<LottieAnimationView>(R.id.img_progress_circle)?.apply {
			clipToCompositionBounds = false
			setScaleType(ImageView.ScaleType.FIT_XY)

			val loadedComposition = aioRawFiles.getSuccessfulDownloadComposition()
			if (loadedComposition != null) {
				setComposition(loadedComposition)
				playAnimation()
				showView(this, true, 100)
			} else {
				aioRawFiles.getSuccessfulDownloadComposition()
				setAnimation(R.raw.animation_successful)
				showView(this, true, 100)
			}
		}
	}

	private suspend fun View.configureSuccessLayout() {
		if (activityRef == null) return

		findViewById<TextView>(R.id.btn_dialog_positive).apply {
			text = getText(R.string.title_view_downloads)
			setLeftSideDrawable(R.drawable.ic_button_arrow_next)
		}

		findViewById<View>(R.id.btn_dialog_positive_container)
			?.setBounceClick { _, _ ->
				activityInf.getAttachedCoroutineScope().launch {
					close()
					if (activityRef is MotherActivity) {
						(activityRef as? MotherActivity)?.openDownloadsFragment()
					} else {
						val openingIntent = Intent(activityRef, MotherActivity::class.java)
						val sourceKey = INTENT_EXTRA_SOURCE_ORIGIN
						val sourceOrigin = FROM_DOWNLOAD_NOTIFICATION
						openingIntent.putExtra(sourceKey, sourceOrigin)
						activityRef?.startActivity(openingIntent)
					}
					onCloseDialog.invoke()
				}
			}

		findViewById<TextView>(R.id.txt_progress_info)
			?.apply { text = getText(R.string.title_download_added_successfully) }
	}

	private suspend fun playDownloadAddedSFX() {
		withIOContext {
			runCatching {
				val lottieFile = aioRawFiles.getSuccessfulDownloadComposition()
				val timeInMile = (if (lottieFile == null) 1500 else 100)
				delay(timeInMile.toLong())
				audioPlayerUtils.startPlaying()
			}.onFailure { error ->
				logger.e("Error in playing sfx audio", error)
			}
		}
	}
}