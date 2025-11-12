@file:Suppress("DEPRECATION")

package app.core.bases.language

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.preference.PreferenceManager.getDefaultSharedPreferences
import androidx.core.content.edit
import java.util.Locale

class LocaleAwareManager(context: Context) {

	private val appContext = context.applicationContext
	private val preferences: SharedPreferences =
		getDefaultSharedPreferences(appContext)

	fun setLocale(): Context {
		return updateResources(language)
	}

	fun setNewLocale(language: String): Context {
		persistLanguage(language)
		return updateResources(language)
	}

	val language: String
		get() = preferences.getString(LANGUAGE_KEY, LANGUAGE_ENGLISH) ?: LANGUAGE_ENGLISH

	@SuppressLint("ApplySharedPref")
	private fun persistLanguage(language: String) {
		preferences.edit(commit = true) { putString(LANGUAGE_KEY, language) }
	}

	private fun updateResources(language: String): Context {
		val locale = Locale(language)
		Locale.setDefault(locale)

		val config = Configuration(appContext.resources.configuration)
		config.setLocale(locale)
		config.setLayoutDirection(locale)

		return appContext.createConfigurationContext(config)
	}

	companion object {
		const val LANGUAGE_ENGLISH = "en"
		private const val LANGUAGE_KEY = "language_key"

		fun getLocale(res: Resources): Locale = res.configuration.locales[0]
	}
}
