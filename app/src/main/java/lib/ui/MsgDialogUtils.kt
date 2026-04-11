package lib.ui

import android.content.*
import android.view.View.*
import android.widget.*
import app.core.AIOApp.Companion.INSTANCE
import app.core.bases.interfaces.*
import com.aio.*
import lib.process.*
import lib.texts.CommonTextUtils.getText
import lib.ui.MsgDialogUtils.getMessageDialog
import lib.ui.builders.*
import java.lang.ref.*

/**
 * A utility object providing high-level helper methods for constructing and displaying
 * custom application dialogs.
 *
 * This singleton provides a simplified API for interacting with [DialogBuilder],
 * abstracting away the boilerplate code required for view inflation and component
 * lookup. It facilitates the creation of standardized message alerts while
 * maintaining the flexibility to inject custom styling and behavior via
 * functional callbacks.
 */
object MsgDialogUtils {

	/**
	 * Provides access to the global application [Context].
	 * * This property acts as a bridge to the singleton application instance,
	 * ensuring that resource strings and system services are accessible
	 * even when a specific Activity context is not immediately available.
	 */
	private val applicationContext: Context
		get() = INSTANCE

	/**
	 * Orchestrates the creation and immediate display of a customizable message dialog
	 * on the UI thread.
	 *
	 * This function serves as the high-level entry point for showing alerts or information
	 * pop-ups. It wraps the [getMessageDialog] factory logic within a [withMainContext] block
	 * to guarantee that view inflation, property assignment, and the final [DialogBuilder.show]
	 * call occur on the Main thread, preventing potential threading exceptions.
	 *
	 * It inherits all the deep customization hooks from the underlying builder logic,
	 * allowing for fine-grained control over typography, container layouts, and
	 * visibility rules while maintaining a concise call site for standard use cases.
	 *
	 * @param baseActivityInf The activity interface used to derive the hosting [app.core.bases.BaseActivity].
	 * @param isCancelable Whether the dialog can be dismissed by back-press or outside touch.
	 * @param isTitleVisible Toggle to force-display the title section.
	 * @param titleText The text sequence for the dialog header.
	 * @param messageTxt The primary descriptive text content.
	 * @param positiveButtonText The label for the primary confirmation action.
	 * @param negativeButtonText The label for the secondary dismissal action.
	 * @param isNegativeButtonVisible Toggle to hide the secondary action button entirely.
	 * @param onPositiveButtonClickListener Lambda for the primary button; defaults to [DialogBuilder.close].
	 * @param onNegativeButtonClickListener Lambda for the secondary button; defaults to [DialogBuilder.close].
	 * @param messageTextViewCustomize Styling hook for the message body [TextView].
	 * @param titleTextViewCustomize Styling hook for the title [TextView].
	 * @param dialogBuilderCustomize Direct access to the [DialogBuilder] instance before [DialogBuilder.show] is called.
	 * @param positiveButtonTextCustomize Styling hook for the positive button text.
	 * @param negativeButtonTextCustomize Styling hook for the negative button text.
	 * @param positiveButtonContainerCustomize Styling hook for the positive button's [RelativeLayout] wrapper.
	 * @param negativeButtonContainerCustomize Styling hook for the negative button's [RelativeLayout] wrapper.
	 * @return The active [DialogBuilder] instance being displayed, or null if context was lost.
	 */
	@JvmStatic
	suspend fun showMessageDialog(
		baseActivityInf: BaseActivityInf?,
		isCancelable: Boolean = true,
		isTitleVisible: Boolean = false,
		titleText: CharSequence = getText(R.string.title_title_goes_here),
		messageTxt: CharSequence = applicationContext.getString(R.string.title_message_goes_here),
		positiveButtonText: CharSequence = applicationContext.getString(R.string.title_okay),
		negativeButtonText: CharSequence = applicationContext.getString(R.string.title_cancel),
		isNegativeButtonVisible: Boolean = true,
		onPositiveButtonClickListener: OnClickListener? = null,
		onNegativeButtonClickListener: OnClickListener? = null,
		messageTextViewCustomize: ((TextView) -> Unit)? = {},
		titleTextViewCustomize: ((TextView) -> Unit)? = {},
		dialogBuilderCustomize: ((DialogBuilder) -> Unit)? = {},
		positiveButtonTextCustomize: ((TextView) -> Unit)? = {},
		negativeButtonTextCustomize: ((TextView) -> Unit)? = {},
		positiveButtonContainerCustomize: ((RelativeLayout) -> Unit)? = {},
		negativeButtonContainerCustomize: ((RelativeLayout) -> Unit)? = {}
	): DialogBuilder? {
		return withMainContext {
			val dialogBuilder = getMessageDialog(
				baseActivityInf = baseActivityInf,
				isCancelable = isCancelable,
				isTitleVisible = isTitleVisible,
				titleText = titleText,
				messageTxt = messageTxt,
				positiveButtonText = positiveButtonText,
				negativeButtonText = negativeButtonText,
				isNegativeButtonVisible = isNegativeButtonVisible,
				onPositiveButtonClickListener = onPositiveButtonClickListener,
				onNegativeButtonClickListener = onNegativeButtonClickListener,
				messageTextViewCustomize = messageTextViewCustomize,
				titleTextViewCustomize = titleTextViewCustomize,
				dialogBuilderCustomize = dialogBuilderCustomize,
				positiveButtonTextCustomize = positiveButtonTextCustomize,
				positiveButtonContainerCustomize = positiveButtonContainerCustomize,
				negativeButtonTextCustomize = negativeButtonTextCustomize,
				negativeButtonContainerCustomize = negativeButtonContainerCustomize
			)
			dialogBuilder?.show()
			dialogBuilder
		}
	}

	/**
	 * Constructs and configures a highly customizable message dialog based on a specific layout template.
	 *
	 * This utility function acts as a factory for [DialogBuilder], providing a comprehensive set of
	 * parameters to control visibility, text content, and click behavior for a standard message dialog.
	 * It uses a [WeakReference] to the provided [BaseActivityInf] to prevent memory leaks during
	 * the dialog construction process.
	 *
	 * The function allows for "deep customization" through lambda hooks, enabling the caller to
	 * modify specific UI components (like [TextView]s or [RelativeLayout] containers) without
	 * needing to subclass the dialog logic.
	 *
	 * @param baseActivityInf The activity interface used to retrieve the underlying [app.core.bases.BaseActivity] context.
	 * @param isCancelable Defines whether the dialog can be dismissed by tapping outside or pressing back.
	 * @param isTitleVisible Explicit toggle for the title visibility.
	 * @param titleText The text displayed in the title section.
	 * @param messageTxt The primary content text for the dialog.
	 * @param positiveButtonText Label for the primary action button.
	 * @param negativeButtonText Label for the secondary action button.
	 * @param isNegativeButtonVisible Toggle to show or hide the secondary action button.
	 * @param onPositiveButtonClickListener Lambda to execute on positive action; defaults to closing the dialog.
	 * @param onNegativeButtonClickListener Lambda to execute on negative action; defaults to closing the dialog.
	 * @param messageTextViewCustomize Hook to apply custom styles/properties to the message [TextView].
	 * @param titleTextViewCustomize Hook to apply custom styles/properties to the title [TextView].
	 * @param dialogBuilderCustomize Hook to modify the [DialogBuilder] instance itself.
	 * @param positiveButtonTextCustomize Hook for styling the positive button's text.
	 * @param negativeButtonTextCustomize Hook for styling the negative button's text.
	 * @param positiveButtonContainerCustomize Hook for styling the positive button's clickable container.
	 * @param negativeButtonContainerCustomize Hook for styling the negative button's clickable container.
	 * @return A fully configured [DialogBuilder] instance, or null if the activity context is no longer available.
	 */
	@JvmStatic
	suspend fun getMessageDialog(
		baseActivityInf: BaseActivityInf?,
		isCancelable: Boolean = true,
		isTitleVisible: Boolean = false,
		titleText: CharSequence = getText(R.string.title_title_goes_here),
		messageTxt: CharSequence = getText(R.string.title_message_goes_here),
		positiveButtonText: CharSequence = INSTANCE.getString(R.string.title_okay),
		negativeButtonText: CharSequence = INSTANCE.getString(R.string.title_cancel),
		isNegativeButtonVisible: Boolean = true,
		onPositiveButtonClickListener: OnClickListener? = null,
		onNegativeButtonClickListener: OnClickListener? = null,
		messageTextViewCustomize: ((TextView) -> Unit)? = {},
		titleTextViewCustomize: ((TextView) -> Unit)? = {},
		dialogBuilderCustomize: ((DialogBuilder) -> Unit)? = {},
		positiveButtonTextCustomize: ((TextView) -> Unit)? = {},
		negativeButtonTextCustomize: ((TextView) -> Unit)? = {},
		positiveButtonContainerCustomize: ((RelativeLayout) -> Unit)? = {},
		negativeButtonContainerCustomize: ((RelativeLayout) -> Unit)? = {},
	): DialogBuilder? {
		return withMainContext {
			baseActivityInf?.getActivity()?.let { activityRef ->
				DialogBuilder(activityRef).apply {
					setView(R.layout.dialog_basic_message_1)
					setCancelable(isCancelable)

					val titleTextView = view.findViewById<TextView>(R.id.txt_dialog_title)
					val messageTextView = view.findViewById<TextView>(R.id.txt_dialog_message)
					val btnNegativeTextView = view.findViewById<TextView>(R.id.button_dialog_negative)
					val btnNegativeContainer = view.findViewById<RelativeLayout>(R.id.button_dialog_negative_container)
					val btnPositiveTextView = view.findViewById<TextView>(R.id.btn_dialog_positive)
					val btnPositiveContainer = view.findViewById<RelativeLayout>(R.id.btn_dialog_positive_container)

					titleTextView.text = titleText
					messageTextView.text = messageTxt
					btnPositiveTextView.text = positiveButtonText
					btnNegativeTextView.text = negativeButtonText

					messageTextViewCustomize?.invoke(messageTextView)
					titleTextViewCustomize?.invoke(titleTextView)
					dialogBuilderCustomize?.invoke(this)
					positiveButtonTextCustomize?.invoke(btnPositiveTextView)
					positiveButtonContainerCustomize?.invoke(btnPositiveContainer)
					negativeButtonTextCustomize?.invoke(btnNegativeTextView)
					negativeButtonContainerCustomize?.invoke(btnNegativeContainer)

					btnNegativeTextView.visibility = if (isNegativeButtonVisible) VISIBLE else GONE
					btnNegativeContainer.visibility = if (isNegativeButtonVisible) VISIBLE else GONE

					titleTextView.visibility = when {
						!isTitleVisible -> GONE
						titleTextView.text.toString() == getText(R.string.title_title_goes_here) -> GONE
						else -> VISIBLE
					}

					btnNegativeContainer.setOnClickListener(
						onNegativeButtonClickListener ?: OnClickListener { close() }
					)

					btnPositiveContainer.setOnClickListener(
						onPositiveButtonClickListener ?: OnClickListener { close() }
					)
				}
			}
		}
	}
}