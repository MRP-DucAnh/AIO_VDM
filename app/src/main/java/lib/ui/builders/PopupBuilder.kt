package lib.ui.builders

import android.graphics.drawable.*
import android.view.*
import android.view.Gravity.*
import android.view.MotionEvent.*
import android.view.View.*
import android.view.View.MeasureSpec.*
import android.widget.*
import androidx.core.content.res.*
import app.core.bases.*
import app.core.bases.interfaces.*
import com.aio.*
import lib.process.*
import java.lang.ref.*

class PopupBuilder(
	activityInf: BaseActivityInf?,
	private val popupLayoutId: Int = -1,
	private val popupContentView: View? = null,
	popupAnchorView: View
) {
	private val logger = LogHelperUtils.from(javaClass)
	private val weakActivityInf = WeakReference(activityInf)
	private val weakAnchorView = WeakReference(popupAnchorView)

	private val popupWindow = PopupWindow()
	private var popupLayout: View? = null
	private var popupPosition: PopupPosition = PopupPosition.RIGHT

	init {
		try {
			initializePopupContent()
			setupPopupWindow()
		} catch (error: Exception) {
			logger.e("Error initializing PopupBuilder:", error)
			throw error
		}
	}

	private fun getValidActivity(): BaseActivity? {
		return weakActivityInf.get()?.getActivity()
	}

	private fun initializePopupContent() {
		val activity = getValidActivity() ?: return
		when {
			popupLayoutId != -1 -> {
				val inflater = LayoutInflater.from(activity)
				popupLayout = inflater.inflate(popupLayoutId, null, false)
			}

			popupContentView != null -> {
				popupLayout = popupContentView
			}
		}

		if (popupLayout == null) {
			throw IllegalArgumentException("No valid content provided")
		}
	}

	private fun setupPopupWindow() {
		popupWindow.apply {
			isTouchable = true
			isFocusable = true
			isOutsideTouchable = true
			setBackgroundDrawable(createTransparentBackground())
			configureTouchHandling()
			width = WindowManager.LayoutParams.WRAP_CONTENT
			height = WindowManager.LayoutParams.WRAP_CONTENT
			contentView = popupLayout
		}
	}

	suspend fun show(shouldHideStatusAndNavbar: Boolean = false) {
		withMainContext {
			val activity = getValidActivity()
			val anchor = weakAnchorView.get()

			if (activity == null || activity.isFinishing ||
				activity.isDestroyed || anchor == null) return@withMainContext

			try {
				if (popupWindow.isShowing) return@withMainContext
				if (shouldHideStatusAndNavbar) enableImmersiveMode()

				showAtLocation(anchor)
			} catch (error: Exception) {
				logger.e("Error showing popup:", error)
			}
		}
	}

	private fun showAtLocation(anchor: View) {
		val layout = popupLayout ?: return
		val anchorLocation = IntArray(2)
		anchor.getLocationOnScreen(anchorLocation)

		val anchorX = anchorLocation[0]
		val anchorY = anchorLocation[1]
		val resources = layout.resources
		val margin = resources.getDimensionPixelSize(R.dimen._10)

		layout.measure(UNSPECIFIED, UNSPECIFIED)
		val popupWidth = layout.measuredWidth

		val xOffset = when (popupPosition) {
			PopupPosition.RIGHT -> {
				val displayMetrics = resources.displayMetrics
				val screenWidth = displayMetrics.widthPixels
				screenWidth - popupWidth - margin
			}

			PopupPosition.LEFT -> {
				val targetX = anchorX - popupWidth - margin
				targetX.coerceAtLeast(margin)
			}
		}

		popupWindow.showAtLocation(
			anchor, NO_GRAVITY, xOffset, anchorY
		)
	}

	suspend fun close() {
		withMainContext {
			if (popupWindow.isShowing) {
				popupWindow.dismiss()
			}
		}
	}

	private fun createTransparentBackground(): Drawable? {
		val activity = getValidActivity() ?: return null
		val res = activity.resources
		val drawId = R.drawable.bg_image_transparent
		return ResourcesCompat.getDrawable(res, drawId, activity.theme)
	}

	private fun configureTouchHandling() {
		popupWindow.setTouchInterceptor { view, event ->
			when (event.action) {
				ACTION_UP -> {
					view.performClick()
					false
				}

				ACTION_OUTSIDE -> {
					popupWindow.dismiss()
					true
				}

				else -> false
			}
		}
	}

	private fun enableImmersiveMode() {
		@Suppress("DEPRECATION")
		popupWindow.contentView?.systemUiVisibility = (
			SYSTEM_UI_FLAG_FULLSCREEN or
				SYSTEM_UI_FLAG_HIDE_NAVIGATION or
				SYSTEM_UI_FLAG_IMMERSIVE_STICKY
			)
	}

	enum class PopupPosition { LEFT, RIGHT }
}