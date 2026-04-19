package app.core.bases.dialogs

import android.text.Html.*
import android.text.method.*
import android.view.*
import android.widget.*
import app.core.AIOApp.Companion.aioRawFiles
import app.core.bases.*
import app.core.bases.interfaces.*
import app.core.engines.updater.AIOUpdater.*
import com.aio.R
import com.airbnb.lottie.*
import kotlinx.coroutines.*
import lib.device.ShareUtility.openApkFile
import lib.process.*
import lib.ui.ViewUtility.setViewOnClickListener
import lib.ui.ViewUtility.showView
import lib.ui.builders.*
import lib.ui.builders.ToastView.Companion.showToast
import java.io.*
import java.lang.ref.*

class UpdaterDialog(
	private val activityInf: BaseActivityInf,
	private val latestVersionApkFile: File,
	private val versionInfo: UpdateInfo
) {
	private val logger = LogHelperUtils.from(javaClass)
	private val weakActivityInf = WeakReference(activityInf)
	private val activityRef get() = weakActivityInf.get()?.getActivity()
	private val dialogBuilder = DialogBuilder(activityRef)

	suspend fun initialize(): UpdaterDialog {
		return withMainContext {
			activityRef?.let { activityRef ->
				dialogBuilder.setView(R.layout.dialog_new_version_updater_1)
				dialogBuilder.setCancelable(false)
				setupNewVersionUpdateAnimation()
				setupUpdateChangeLogMessage(activityRef)
				setupClickListeners(activityRef)
			}
			return@withMainContext this@UpdaterDialog
		}
	}

	suspend fun show() {
		withMainContext {
			if (!dialogBuilder.isShowing) {
				dialogBuilder.show()
			}
		}
	}

	suspend fun close() {
		withMainContext {
			if (dialogBuilder.isShowing) {
				dialogBuilder.close()
			}
		}
	}

	private suspend fun setupClickListeners(activityRef: BaseActivity) {
		setViewOnClickListener(
			{ button: View ->
				activityRef.activityCoroutineScope.launch {
					setupClickEvents(button)
				}
			},
			dialogBuilder.view,
			R.id.btn_dialog_positive_container
		)
	}

	private suspend fun setupNewVersionUpdateAnimation() {
		activityRef?.let {
			with(dialogBuilder.view) {
				findViewById<LottieAnimationView>(R.id.img_version_animation).apply {
					clipToCompositionBounds = false
					setScaleType(ImageView.ScaleType.FIT_XY)

					aioRawFiles.getNewVersionUpdateComposition()?.let {
						setComposition(it)
						playAnimation()
					} ?: setAnimation(R.raw.animation_new_app_version)

					showView(this, true, 100)
				}
			}
		}
	}

	private suspend fun setupUpdateChangeLogMessage(activityRef: BaseActivity) {
		dialogBuilder.view.findViewById<TextView>(R.id.txt_dialog_message)?.let { textView ->
			val htmlMsg = activityRef.getString(
				R.string.title_b_latest_version_b,
				versionInfo.latestVersion
			).trimIndent()

			textView.text = fromHtml(htmlMsg, FROM_HTML_MODE_COMPACT)
			textView.movementMethod = LinkMovementMethod.getInstance()
		}
	}

	private suspend fun setupClickEvents(button: View) {
		when (button.id) {
			R.id.btn_dialog_positive_container -> {
				close()

				activityRef?.let { activityRef ->
					openApkFile(activityRef, latestVersionApkFile)
				} ?: run {
					showToast(activityRef, R.string.title_something_went_wrong)
				}
			}
		}
	}
}