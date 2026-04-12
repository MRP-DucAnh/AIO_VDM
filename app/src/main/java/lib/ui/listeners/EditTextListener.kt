package lib.ui.listeners

import android.text.*

/**
 * A skeletal implementation of the [TextWatcher] interface to reduce boilerplate in
 * [android.widget.EditText] listeners. By extending this class, you only need to override
 * the specific text change events that are relevant to your logic. This is ideal for scenarios
 * where you only care about the final state of the text (via [afterTextChanged]) and wish to ignore
 * the intermediate [beforeTextChanged] and [onTextChanged] callbacks.
 */
abstract class EditTextListener : TextWatcher {

	/**
	 * Invoked after the text has been modified.
	 * * This is the primary method to override when you need to react to the final state
	 * of the text in an [android.widget.EditText]. It is called after the underlying buffer has
	 * been updated, making it suitable for validation or UI updates based on the
	 * resulting string.
	 *
	 * @param editable The editable text that has just been changed.
	 */
	abstract override fun afterTextChanged(editable: Editable)

	/**
	 * Invoked to notify you that text is about to be replaced.
	 * * Default implementation is empty. Use this if you need to inspect the
	 * [charSequence] before the change is committed.
	 *
	 * @param charSequence The current text before the change.
	 * @param start The position of the beginning of the cursor.
	 * @param count The number of characters to be replaced.
	 * @param after The length of the new text being added.
	 */
	override fun beforeTextChanged(
		charSequence: CharSequence,
		start: Int, count: Int, after: Int
	) = Unit

	/**
	 * Invoked to notify you that text has been replaced.
	 * * Default implementation is empty. Use this if you need to know which specific
	 * characters within [charSequence] were modified during the transition.
	 *
	 * @param charSequence The current text after the change.
	 * @param start The position where the change began.
	 * @param before The length of the text that was replaced.
	 * @param count The number of new characters added.
	 */
	override fun onTextChanged(
		charSequence: CharSequence,
		start: Int, before: Int, count: Int
	) = Unit
}