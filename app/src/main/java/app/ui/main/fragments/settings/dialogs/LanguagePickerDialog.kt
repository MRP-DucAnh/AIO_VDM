package app.ui.main.fragments.settings.dialogs

import android.view.*
import android.view.ViewGroup.LayoutParams.*
import android.widget.*
import app.core.AIOApp.Companion.aioLanguage
import app.core.AIOApp.Companion.aioSettings
import app.core.bases.*
import com.aio.*
import lib.ui.builders.*

class LanguagePickerDialog(val baseActivity: BaseActivity) {
	
	var onApplyListener: () -> Unit? = {}
	
	private val languageSelectionDialog by lazy {
		DialogBuilder(getActivity()).apply {
			setView(R.layout.dialog_language_pick_1)
		}
	}
	
	init {
		languageSelectionDialog.setCancelable(false)
		languageSelectionDialog.view.apply {
			setAvailableLanguages(this)
			setButtonOnClickListeners(this)
		}
	}
	
	private fun getActivity(): BaseActivity? {
		return baseActivity.getActivity()
	}
	
	fun getDialogBuilder(): DialogBuilder {
		return languageSelectionDialog
	}
	
	fun close() {
		if (languageSelectionDialog.isShowing) {
			languageSelectionDialog.close()
		}
	}
	
	fun show() {
		if (!languageSelectionDialog.isShowing) {
			languageSelectionDialog.show()
		}
	}
	
	fun isShowing(): Boolean {
		return languageSelectionDialog.isShowing
	}
	
	private fun setAvailableLanguages(dialogView: View) {
		getActivity()?.let { activityRef ->
			removeAllRadioSelectionViews(dialogView)
			
			val groupView = getLanguageRadioGroupView(dialogView)
			val languagesList = aioLanguage.languagesList
			languagesList.forEachIndexed { index, (_, name) ->
				View.inflate(activityRef, R.layout.dialog_language_pick_1_item_1, null).apply {
					(this as RadioButton).apply {
						id = index
						text = name
						
						val itemHeight = resources.getDimensionPixelSize(R.dimen._40)
						layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, itemHeight)
						val hp = resources.getDimensionPixelSize(R.dimen._5)
						val vp = resources.getDimensionPixelSize(R.dimen._5)
						setPadding(hp, vp, hp, vp)
					}
					
					groupView.addView(this)
				}
			}
			
			val langCode = aioSettings.userSelectedUILanguage
			val languageIndex = languagesList.indexOfFirst { it.first == langCode }
			if (languageIndex < 0) return
			
			groupView.findViewById<RadioButton>(languageIndex)?.isChecked = true
		}
	}
	
	private fun removeAllRadioSelectionViews(dialogLayoutView: View) {
		getLanguageRadioGroupView(dialogLayoutView).removeAllViews()
	}
	
	private fun getLanguageRadioGroupView(view: View): RadioGroup {
		return view.findViewById(R.id.language_options_container)
	}
	
	private fun setButtonOnClickListeners(dialogLayoutView: View) {
		dialogLayoutView.findViewById<View>(R.id.btn_dialog_positive_container).apply {
			setOnClickListener {
				applySelectedApplicationLanguage(dialogLayoutView)
			}
		}
	}
	
	private fun applySelectedApplicationLanguage(dialogLayoutView: View) {
		val languageRadioGroup = getLanguageRadioGroupView(dialogLayoutView)
		val selectedLanguageId = languageRadioGroup.checkedRadioButtonId
		
		if (selectedLanguageId == -1) return
		val (selectedLanguageCode, _) = aioLanguage.languagesList[selectedLanguageId]
		
		aioSettings.userSelectedUILanguage = selectedLanguageCode
		aioSettings.updateInStorage()
		
		close()
		onApplyListener()
	}
}