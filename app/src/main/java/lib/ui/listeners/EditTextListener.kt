package lib.ui.listeners

import android.text.*

abstract class EditTextListener : TextWatcher {

	abstract override fun afterTextChanged(editable: Editable)
	override fun beforeTextChanged(charSequence: CharSequence, start: Int, count: Int, after: Int) = Unit
	override fun onTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int) = Unit
}