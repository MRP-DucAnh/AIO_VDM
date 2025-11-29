@file:Suppress("DEPRECATION")

package lib.process

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import java.util.Locale

object LocalizationHelper {

	private var currentLocale: Locale? = null

	@JvmStatic
	fun setAppLocale(context: Context, locale: Locale) {
		currentLocale = locale
		updateResources(context, locale)
	}

	@JvmStatic
	fun getLocalizedString(context: Context, @StringRes resId: Int): String {
		val resources = context.resources
		return if (currentLocale != null) {
			val config = Configuration(resources.configuration)
			config.setLocale(currentLocale)
			context.createConfigurationContext(config).resources.getString(resId)
		} else {
			resources.getString(resId)
		}
	}

	@JvmStatic
	private fun updateResources(context: Context, locale: Locale) {
		val resources = context.resources
		val config = Configuration(resources.configuration)
		config.setLocale(locale)
		resources.updateConfiguration(config, resources.displayMetrics)
	}
}
