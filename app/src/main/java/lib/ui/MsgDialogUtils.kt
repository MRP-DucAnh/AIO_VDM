package lib.ui

import android.content.*
import android.view.View.*
import android.widget.*
import app.core.AIOApp.Companion.INSTANCE
import app.core.bases.interfaces.*
import com.aio.*
import lib.process.*
import lib.texts.CommonTextUtils.getText
import lib.ui.builders.*

object MsgDialogUtils {

	private val applicationContext: Context
		get() = INSTANCE

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