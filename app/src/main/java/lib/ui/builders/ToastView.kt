@file:Suppress("DEPRECATION")

package lib.ui.builders

import android.annotation.*
import android.content.*
import android.view.*
import android.widget.*
import androidx.appcompat.view.ContextThemeWrapper
import app.core.bases.*
import app.core.bases.interfaces.*
import com.aio.*
import kotlinx.coroutines.*
import lib.networks.URLUtility.*
import lib.process.*
import lib.texts.CommonTextUtils.getText
import lib.ui.builders.ToastView.Companion.showResourceToast
import lib.ui.builders.ToastView.Companion.showTextToast

/**
 * A custom [Toast] implementation that provides access to specialized view components.
 * * This class extends the standard Android [Toast] to allow for custom layouts—specifically
 * those containing an [ImageView] for icons. It provides lifecycle-aware and thread-safe
 * methods to modify the toast's UI elements before [show] is called.
 *
 * @param context The application or activity context used to initialize the base Toast.
 */
class ToastView(context: Context) : Toast(context) {

	/**
	 * Updates the icon displayed within the toast's custom view.
	 *
	 * This method uses [withMainContext] to ensure that the [ImageView] update
	 * occurs on the Main thread, preventing potential CalledFromWrongThreadException
	 * errors when called from background coroutines.
	 * * ### Requirements:
	 * The current [view] of the toast must contain an [ImageView] with the
	 * ID `R.id.img_toast_app_icon`. If the view or the specific ImageView
	 * is null, this method fails silently.
	 *
	 * @param iconResId The drawable resource ID to be set as the toast's icon.
	 * @throws Exception potentially forwarded from [withMainContext] or resource resolution.
	 */
	suspend fun setIcon(iconResId: Int) {
		withMainContext {
			// Access the inflated view hierarchy of the toast
			view?.findViewById<ImageView>(R.id.img_toast_app_icon)
				?.apply {
					// Apply the image resource directly to the view
					setImageResource(iconResId)
				}
		}
	}

	companion object {

		/**
		 * Orchestrates the display of a toast message on the UI thread.
		 *
		 * This method acts as a central dispatcher that handles both string resources and
		 * literal strings. It ensures thread safety by switching to the Main context and
		 * uses the activity's lifecycle-aware coroutine scope to prevent displaying toasts
		 * after an activity has been destroyed.
		 *
		 * ### Logic Priority:
		 * 1. If [msgId] is provided (not -1), it calls [showResourceToast].
		 * 2. If [msgId] is -1 but [msg] is not null, it calls [showTextToast].
		 * 3. If neither are provided, no action is taken.
		 *
		 * @param activityInf The abstraction layer for the Activity; if null, the request is ignored.
		 * @param msg An optional raw string to display.
		 * @param msgId An optional Android string resource ID (e.g., R.string.error_message).
		 */
		@JvmStatic
		suspend fun showToast(activityInf: BaseActivityInf?, msg: String? = null, msgId: Int = -1) {
			withMainContext {
				// Safety check: Exit if the activity reference is missing
				if (activityInf == null) return@withMainContext

				// Launch in the activity's scope to ensure the coroutine is canceled
				// if the activity finishes, preventing "context leaked" window errors.
				activityInf.getAttachedCoroutineScope().launch {
					when {
						msgId != -1 -> showResourceToast(activityInf, msgId)
						msg != null -> showTextToast(activityInf, msg)
					}
				}
			}
		}

		/**
		 * Triggers the display of a toast message using a resource ID.
		 * * This method ensures the operation runs on the Main Thread and utilizes the activity's
		 * specific coroutine scope to manage the lifecycle of the toast display.
		 *
		 * @param activityInf An interface providing access to the Activity context and its lifecycle scope.
		 * @param msgId The string resource ID to be displayed. Defaults to -1 (invalid).
		 */
		@JvmStatic
		suspend fun showToast(activityInf: BaseActivityInf?, msgId: Int = -1) {
			withMainContext {
				if (activityInf == null) return@withMainContext
				// Launches the toast display within the activity's scope to prevent leaks
				activityInf.getAttachedCoroutineScope().launch {
					showResourceToast(activityInf, msgId)
				}
			}
		}

		/**
		 * Internal helper to resolve the resource string and validate content before display.
		 * * @param activityInf The activity interface.
		 * @param msgId The string resource ID.
		 */
		private fun showResourceToast(activityInf: BaseActivityInf, msgId: Int) {
			val message = getText(msgId)
			// Security/UX Check: Do not display the toast if the content is a raw URL
			if (isValidURL(message)) return
			makeText(activityInf, message)?.show()
		}

		/**
		 * Directly displays a string-based toast after validating the content.
		 *
		 * @param activityInf The activity interface.
		 * @param msg The raw string message to display.
		 */
		private fun showTextToast(activityInf: BaseActivityInf, msg: String) {
			if (isValidURL(msg)) return
			makeText(activityInf, msg)?.show()
		}

		/**
		 * Factory method that builds the [ToastView].
		 * * It safely retrieves the [BaseActivity] from the [BaseActivityInf] wrapper to
		 * ensure the [ToastView] is created with a valid UI context.
		 *
		 * @param activityInf The activity interface.
		 * @param toastMessage The text content to display.
		 * @param duration How long to display the toast. Defaults to [Toast.LENGTH_LONG].
		 * @return A configured [ToastView] instance, or null if the Activity is no longer available.
		 */
		private fun makeText(
			activityInf: BaseActivityInf,
			toastMessage: CharSequence?,
			duration: Int = LENGTH_LONG
		): ToastView? {
			return activityInf.getActivity()?.let { safeActivityContext ->
				configureToastView(safeActivityContext, toastMessage, duration)
			}
		}

		/**
		 * Configures and initializes a custom [ToastView] with a specific theme and layout.
		 *
		 * This method creates a [ContextThemeWrapper] to ensure the toast layout adheres to the
		 * application's branding style (`R.style.style_application`), regardless of the activity's
		 * individual theme.
		 *
		 * ### Performance & Warnings:
		 * * **@SuppressLint("InflateParams")**: Passing `null` as the root to [LayoutInflater.inflate]
		 * is necessary here because Toasts use a system-controlled window where the parent layout
		 * is not available at inflation time. Note that XML LayoutParams on the root element of
		 * `R.layout.lay_custom_toast_view_1` will be ignored.
		 * * **Context Handling**: Uses `activity.applicationContext` for the [ToastView] instance
		 * to prevent potential memory leaks if the Toast outlives the Activity's lifecycle.
		 *
		 * @param activity The [BaseActivity] used to derive the theme and layout inflater.
		 * @param toastMessage The text or styled sequence to be displayed in the toast.
		 * @param duration The display length (e.g., [android.widget.Toast.LENGTH_SHORT]).
		 * @return A fully configured [ToastView] ready to be displayed via `.show()`.
		 */
		@SuppressLint("InflateParams")
		private fun configureToastView(
			activity: BaseActivity,
			toastMessage: CharSequence?,
			duration: Int
		): ToastView {
			// Apply the application-wide theme to the layout inflater
			val themedCtx = ContextThemeWrapper(activity, R.style.style_application)
			val inflater = LayoutInflater.from(themedCtx)

			// Use applicationContext for the ToastView itself to avoid Activity-leaks
			return ToastView(activity.applicationContext).apply {
				// Inflate the custom XML layout
				// We pass null because the Toast manages its own view hierarchy root
				val toastView = inflater.inflate(R.layout.lay_custom_toast_view_1, null)

				// Bind the message to the TextView
				toastView.findViewById<TextView>(R.id.txt_toast_message).text = toastMessage

				// Assign the inflated view to the Toast container
				view = toastView
				this.duration = duration
			}
		}
	}
}