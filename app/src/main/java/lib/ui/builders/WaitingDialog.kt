package lib.ui.builders

import android.view.*
import android.view.View.*
import android.widget.*
import androidx.lifecycle.*
import app.core.*
import app.core.bases.interfaces.*
import com.aio.R
import com.airbnb.lottie.*
import kotlinx.coroutines.*
import lib.process.*
import lib.ui.*
import lib.ui.ViewUtility.showView
import lib.ui.builders.DialogBuilder.*

class WaitingDialog(
	private val baseActivityInf: BaseActivityInf?,
	private val loadingMessage: String,
	private val shouldHideOkayButton: Boolean = false,
	private val isCancelable: Boolean = true,
	private val dialogCancelListener: OnCancelListener? = null
) {

	private val logger = LogHelperUtils.from(javaClass)

	val dialogBuilder: DialogBuilder? by lazy {
		DialogBuilder(baseActivityInf)
	}

	init {
		dialogBuilder?.apply {
			baseActivityInf?.getActivity()?.lifecycleScope?.launch {
				initializeDialogComponents()
			}
		}
	}

	private suspend fun DialogBuilder.initializeDialogComponents() {
		setView(R.layout.dialog_waiting_progress_1)
		setCancelable(isCancelable)
		configureCancelListener()
		configureDialogContent()
	}

	private fun DialogBuilder.configureCancelListener() {
		dialog.setOnDismissListener { dialog ->
			dialogCancelListener?.onCancel(dialog) ?: dialog?.cancel()
		}

		dialog.setOnCancelListener { dialog ->
			dialogCancelListener?.onCancel(dialog) ?: dialog?.cancel()
		}
	}

	private suspend fun DialogBuilder.configureDialogContent() {
		view.apply {
			findViewById<TextView>(R.id.txt_progress_info).let {
				it.text = loadingMessage
				ViewUtility.animateFadInOutAnim(it)
			}

			findViewById<View>(R.id.btn_dialog_positive_container).apply {
				setOnClickListener { close() }
				visibility = if (shouldHideOkayButton) GONE else VISIBLE
			}

			findViewById<LottieAnimationView>(R.id.img_progress_circle).apply {
				AIOApp.aioRawFiles.getWaitingLoadingComposition()?.let {
					setComposition(it)
					playAnimation()
				} ?: run { setAnimation(R.raw.animation_waiting_loading) }

				showView(targetView = this, shouldAnimate = true, animTimeout = 400)
			}
		}
	}

	suspend fun show() {
		withMainContext {
			dialogBuilder?.let { dialogBuilder ->
				if (!dialogBuilder.isShowing) {
					dialogBuilder.show()
				}
			} ?: run {
				logger.e("Cannot show dialog - invalid context")
				throw IllegalStateException("Dialog context unavailable")
			}
		}
	}

	suspend fun close(): Boolean {
		return withMainContext {
			dialogBuilder?.let {
				if (it.isShowing) {
					it.close()
					true
				} else false
			} ?: false
		}
	}
}