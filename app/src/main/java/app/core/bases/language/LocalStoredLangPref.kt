package app.core.bases.language

import android.content.*
import java.io.*

object LocalStoredLangPref {

	private lateinit var languageConfigDir: File

	fun init(context: Context) {
		languageConfigDir = File(context.filesDir, "language_config")
		languageConfigDir.mkdirs()
	}

	var languageCode: String
		get() = runCatching {
			if (::languageConfigDir.isInitialized && languageConfigDir.exists()) {
				val langFile = languageConfigDir.listFiles { file ->
					file.isFile && file.extension == "langcode"
				}?.firstOrNull()

				langFile?.nameWithoutExtension ?: "en"
			} else {
				"en"
			}
		}.getOrDefault("en")
		set(value) {
			runCatching {
				clear()
				File(languageConfigDir, "$value.langcode").createNewFile()
			}
		}

	fun clear() {
		runCatching {
			if (::languageConfigDir.isInitialized && languageConfigDir.exists()) {
				languageConfigDir.listFiles { file ->
					file.isFile && file.extension == "langcode"
				}?.forEach { it.delete() }
			}
		}
	}

	fun contains(): Boolean {
		return runCatching {
			::languageConfigDir.isInitialized &&
				languageConfigDir.exists() &&
				languageConfigDir.listFiles { file ->
					file.isFile && file.extension == "langcode"
				}?.isNotEmpty() == true
		}.getOrDefault(false)
	}

	fun getLanguageFile(): File? {
		return runCatching {
			if (::languageConfigDir.isInitialized && languageConfigDir.exists()) {
				languageConfigDir.listFiles { file ->
					file.isFile && file.extension == "langcode"
				}?.firstOrNull()
			} else null
		}.getOrNull()
	}
}