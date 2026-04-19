@file:Suppress("DEPRECATION")

package app.core.bases.language

import android.content.*
import android.content.res.*
import app.core.AIOApp.Companion.INSTANCE
import app.core.AIOApp.Companion.aioSettings
import lib.process.*
import java.util.*

object LocalizeManager {

	private val logger = LogHelperUtils.from(javaClass)
	private val listeners = mutableSetOf<LanguageChangeListener>()

	@JvmStatic
	fun registerListener(listener: LanguageChangeListener) {
		listeners.add(listener)
	}

	@JvmStatic
	fun unregisterListener(listener: LanguageChangeListener) {
		listeners.remove(listener)
	}

	@JvmStatic
	fun onAttach(context: Context): Context {
		val storedLanguageCode = getStoredLanguageCode(context)
		return setApplicationLanguage(context, storedLanguageCode)
	}

	@JvmStatic
	fun getLanguageCode(context: Context): String {
		return getStoredLanguageCode(context)
	}

	@JvmStatic
	fun setApplicationLanguage(context: Context, languageCode: String): Context {
		storeLanguageCode(context, languageCode)
		return createLocalizedContext(context, languageCode)
	}

	@JvmStatic
	fun setApplicationLanguage(context: Context, locale: Locale) {
		storeLanguageCode(context, locale.language)
		createLocalizedContext(context, locale.language)
		listeners.forEach { listener ->
			listener.onLanguageChanged(locale)
		}
	}

	@JvmStatic
	private fun getStoredLanguageCode(context: Context): String {
		return LocalStoredLangPref.languageCode
	}

	@JvmStatic
	private fun storeLanguageCode(context: Context, languageCode: String) {
		ThreadsUtility.executeInBackground(timeOutInMilli = 100, codeBlock = {
			LocalStoredLangPref.languageCode = languageCode
			try {
				if (INSTANCE.isAIOSettingLoaded()) {
					aioSettings.selectedUiLanguage = languageCode
					aioSettings.updateInDB()
				}
			} catch (error: Exception) {
				logger.e("Error storing language code:", error)
			}
		})
	}

	@JvmStatic
	private fun createLocalizedContext(context: Context, language: String): Context {
		val locale = Locale(language)
		Locale.setDefault(locale)
		val configuration = context.resources.configuration
		configuration.setLocale(locale)
		configuration.setLayoutDirection(locale)
		return context.createConfigurationContext(configuration)
	}

	@JvmStatic
	fun updateApplicationLocale(context: Context, locale: Locale): Configuration? {
		setApplicationLanguage(context, locale)
		return setApplicationLanguage(context)
	}

	@JvmStatic
	fun getStoredLanguageLocale(context: Context): Locale {
		val lang = getStoredLanguageCode(context)
		return Locale(lang)
	}

	@JvmStatic
	fun setApplicationLanguage(context: Context): Configuration? {
		val resources: Resources = context.resources
		val configuration: Configuration = resources.configuration
		val locale: Locale = getStoredLanguageLocale(context)
		if (configuration.locale != locale) {
			configuration.setLocale(locale)
			setApplicationLanguage(context, locale)
			resources.updateConfiguration(configuration, resources.displayMetrics)
			return configuration
		}
		return null
	}

	@JvmStatic
	fun initializeLocale(context: Context) {
		LocalStoredLangPref.init(context)
		val locale = getStoredLanguageLocale(context)
		setApplicationLanguage(context, locale.language)
	}
}