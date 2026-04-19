package app.core.bases.language

import java.util.*

interface LanguageChangeListener {
	fun onLanguageChanged(newLocale: Locale)
}