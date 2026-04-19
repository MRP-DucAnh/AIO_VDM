package app.core.bases.language

import android.app.*
import android.content.*
import java.util.*

open class LocaleApplicationImpl :
	Application(), LocaleManagerInf, LanguageChangeListener {

	override fun onCreate() {
		super.onCreate()
		LocalizeManager.initializeLocale(this)
	}

	override fun attachBaseContext(base: Context) {
		super.attachBaseContext(LocalizeManager.onAttach(base))
	}

	override fun setLanguageLocale(locale: Locale) {
		LocalizeManager.updateApplicationLocale(this, locale)
	}

	override fun onLanguageChanged(newLocale: Locale) {
	}
}