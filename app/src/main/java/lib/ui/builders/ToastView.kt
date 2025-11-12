@file:Suppress("DEPRECATION")

package lib.ui.builders

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import app.core.bases.BaseActivity
import app.core.bases.interfaces.BaseActivityInf
import com.aio.R
import lib.networks.URLUtility.isValidURL
import lib.texts.CommonTextUtils.getText

/**
 * A custom Toast class that allows setting a custom layout and icon.
 *
 * This class provides a reusable and centralized way to show toasts with
 * enhanced customization in your application. It prevents showing toasts for URLs
 * and uses a consistent layout across the app.
 *
 * @constructor Creates a ToastView instance with the given context.
 * @param context The context to use. Usually your Application or Activity context.
 */
class ToastView(context: Context) : Toast(context) {

	/**
	 * Sets the icon for the toast if a valid view is available.
	 *
	 * @param iconResId The resource ID of the drawable to use as the icon.
	 */
	fun setIcon(iconResId: Int) {
		view?.findViewById<ImageView>(R.id.img_toast_app_icon)
			?.apply { setImageResource(iconResId) }
	}

	companion object {

		/**
		 * Shows a toast message. This method accepts either a string message or a resource ID.
		 * It automatically avoids displaying if the message is a valid URL.
		 *
		 * @param activityInf The activity interface reference needed.
		 * @param msg The string message to display. Optional.
		 * @param msgId The resource ID for the message string. Optional.
		 */
		@JvmStatic
		fun showToast(activityInf: BaseActivityInf?, msg: String? = null, msgId: Int = -1) {
			if (activityInf == null) return
			when {
				msgId != -1 -> showResourceToast(activityInf, msgId)
				msg != null -> showTextToast(activityInf, msg)
			}
		}

		/**
		 * Displays a toast using a string resource ID.
		 * Skips displaying if the resolved message is a URL.
		 *
		 * @param activityInf The activity reference needed.
		 * @param msgId The resource ID of the message string.
		 */
		private fun showResourceToast(activityInf: BaseActivityInf, msgId: Int) {
			val message = getText(msgId)
			if (isValidURL(message)) return
			makeText(activityInf, message)?.show()
		}

		/**
		 * Displays a toast using a string message.
		 * Skips displaying if the message is a URL.
		 *
		 * @param activityInf The activity reference needed.
		 * @param msg The message string to show.
		 */
		private fun showTextToast(activityInf: BaseActivityInf, msg: String) {
			if (isValidURL(msg)) return
			makeText(activityInf, msg)?.show()
		}

		/**
		 * Creates and configures a [ToastView] instance using a custom layout and duration.
		 *
		 * @param activityInf The context used to create the toast.
		 * @param toastMessage The message to display in the toast.
		 * @param duration The duration of the toast. Defaults to [Toast.LENGTH_LONG].
		 * @return A configured [ToastView] instance.
		 */
		private fun makeText(activityInf: BaseActivityInf,
			toastMessage: CharSequence?, duration: Int = LENGTH_LONG): ToastView? {
			return activityInf.getActivity()?.let { safeActivityContext ->
				configureToastView(safeActivityContext, toastMessage, duration)
			}
		}

		/**
		 * Inflates the custom toast layout and sets its properties.
		 *
		 * @param activity The context used to inflate the view.
		 * @param toastMessage The text to display.
		 * @param duration How long to display the toast.
		 * @return A [ToastView] instance with the custom layout and message.
		 */
		@SuppressLint("InflateParams")
		private fun configureToastView(activity: BaseActivity,
			toastMessage: CharSequence?, duration: Int): ToastView {
			// Wrap the activity with its current theme
			val themedCtx = ContextThemeWrapper(activity, R.style.style_application)
			val inflater = LayoutInflater.from(themedCtx)

			// Create toast with activity (not application) context
			return ToastView(activity.applicationContext).apply {
				val toastView = inflater.inflate(R.layout.lay_custom_toast_view_1, null)
				toastView.findViewById<TextView>(R.id.txt_toast_message).text = toastMessage
				view = toastView
				setDuration(duration)
			}
		}
	}
}