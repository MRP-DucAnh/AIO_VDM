package lib.ui.builders

import android.graphics.drawable.*
import android.view.*
import android.view.Gravity.NO_GRAVITY
import android.view.MotionEvent.*
import android.view.View.*
import android.view.View.MeasureSpec.*
import android.widget.*
import androidx.core.content.res.*
import app.core.bases.interfaces.*
import com.aio.*
import lib.process.*
import lib.ui.builders.PopupBuilder.PopupPosition.LEFT
import lib.ui.builders.PopupBuilder.PopupPosition.RIGHT
import java.lang.ref.*

class PopupBuilder(
	private val activityInf: BaseActivityInf?,
	private val popupLayoutId: Int = -1,
	private val popupContentView: View? = null,
	private val popupAnchorView: View
) {

	private val logger = LogHelperUtils.from(javaClass)
	private val weakReferenceOfActivityInf = WeakReference(activityInf?.getActivity())
	private val popupWindow = PopupWindow(weakReferenceOfActivityInf.get())
	private lateinit var popupLayout: View
	private var popupPosition: PopupPosition = RIGHT

	init {
		try {
			initializePopupContent()
			validateContentView()
			setupPopupWindow()
		} catch (error: Exception) {
			logger.e("Error found while initializing the Popup Builder:", error)
			throw error
		}
	}

	suspend fun show(shouldHideStatusAndNavbar: Boolean = false) {
		withMainContext {
			try {
				if (popupWindow.isShowing) return@withMainContext
				if (shouldHideStatusAndNavbar) enableImmersiveMode()
				showPopupWindow()
			} catch (error: Exception) {
				logger.e("Error found while showing popup-view:", error)
			}
		}
	}

	suspend fun close() {
		withMainContext {
			try {
				val activity = weakReferenceOfActivityInf.get() ?: return@withMainContext
				if (activity.isValidForWindowManagement() && popupWindow.isShowing) {
					popupWindow.dismiss()
				}
			} catch (error: Exception) {
				logger.e("Error found while closing popup-view:", error)
			}
		}
	}

	suspend fun getPopupView(): View = withMainContext { popupWindow.contentView }

	suspend fun getPopupWindow(): PopupWindow = withMainContext { popupWindow }

	private fun initializePopupContent() {
		when {
			popupLayoutId != -1 -> {
				val inflater = LayoutInflater.from(weakReferenceOfActivityInf.get())
				popupLayout = inflater.inflate(popupLayoutId, null, false)
			}
			popupContentView != null -> popupLayout = popupContentView
		}
	}

	private fun validateContentView() {
		if (!::popupLayout.isInitialized) {
			throw IllegalArgumentException(
				"Must provide valid content via popupLayoutId or popupContentView"
			)
		}
	}

	fun setPosition(position: PopupPosition): PopupBuilder {
		this.popupPosition = position
		return this
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

	private fun createTransparentBackground(): Drawable? {
		return weakReferenceOfActivityInf.get()?.let { ctx ->
			ResourcesCompat.getDrawable(
				ctx.resources,
				R.drawable.bg_image_transparent,
				ctx.theme
			)
		}
	}

	private fun configureTouchHandling() {
		popupWindow.setTouchInterceptor { view, event ->
			when (event.action) {
				ACTION_UP -> view.performClick().let { false }
				ACTION_OUTSIDE -> popupWindow.dismiss().let { true }
				else -> false
			}
		}
	}

	@Suppress("DEPRECATION")
	private fun enableImmersiveMode() {
		val s1 = SYSTEM_UI_FLAG_FULLSCREEN
		val s2 = SYSTEM_UI_FLAG_HIDE_NAVIGATION
		val s3 = SYSTEM_UI_FLAG_IMMERSIVE_STICKY
		popupWindow.contentView.systemUiVisibility = (s1 or s2 or s3)
	}

	private fun showPopupWindow() {
		val anchorLocation = IntArray(2)
		popupAnchorView.getLocationOnScreen(anchorLocation)

		val anchorX = anchorLocation[0]
		val anchorY = anchorLocation[1]

		val margin = popupLayout.resources.getDimensionPixelSize(R.dimen._10)

		popupLayout.measure(UNSPECIFIED, UNSPECIFIED)
		val popupWidth = popupLayout.measuredWidth

		val xOffset = when (popupPosition) {
			RIGHT -> {
				val screenWidth = popupLayout.resources.displayMetrics.widthPixels
				screenWidth - popupWidth - margin
			}
			LEFT -> {
				(anchorX - popupWidth - margin).coerceAtLeast(margin)
			}
		}

		popupWindow.showAtLocation(popupAnchorView, NO_GRAVITY, xOffset, anchorY)
	}

	private fun BaseActivityInf?.isValidForWindowManagement(): Boolean {
		val valid = this?.getActivity()?.let { activity ->
			!activity.isFinishing && !activity.isDestroyed
		} ?: false
		logger.d("Activity valid for window management: $valid")
		return valid
	}

	enum class PopupPosition { LEFT, RIGHT }
}