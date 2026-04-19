@file:Suppress("DEPRECATION")

package app.core.bases.language

import android.content.*
import android.content.res.*
import android.os.*
import androidx.appcompat.app.*
import java.util.*

open class LocaleActivityImpl :
	AppCompatActivity(), LocaleManagerInf, LanguageChangeListener {

	open var skipConfigurationUpdates = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		LocalizeManager.registerListener(this)
	}

	override fun onLanguageChanged(newLocale: Locale) {
		updateActivityConfiguration(newLocale)
		Handler(Looper.getMainLooper())
			.postDelayed({ recreate() }, LocalizeConstant.PREPARE_DELAY)
	}

	private fun updateActivityConfiguration(locale: Locale) {
		val configuration = resources.configuration
		configuration.setLocale(locale)
		configuration.setLocales(LocaleList(locale))
		resources.updateConfiguration(configuration, resources.displayMetrics)
	}

	override fun attachBaseContext(baseContext: Context) {
		super.attachBaseContext(LocalizeManager.onAttach(baseContext))
	}

	public override fun onDestroy() {
		LocalizeManager.unregisterListener(this)
		super.onDestroy()
	}

	override fun setLanguageLocale(locale: Locale) {
		LocalizeManager.setApplicationLanguage(this, locale)
	}

	override fun onConfigurationChanged(newConfiguration: Configuration) {
		super.onConfigurationChanged(newConfiguration)
		if (skipConfigurationUpdates) return
		Handler(Looper.getMainLooper())
			.postDelayed({ recreate() }, LocalizeConstant.PREPARE_DELAY)
	}

	open fun openPrepareLocalize() {
		LanguagePrepareActivity.navigate(this)
	}
}