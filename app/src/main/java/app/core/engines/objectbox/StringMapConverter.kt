package app.core.engines.objectbox

import io.objectbox.converter.PropertyConverter

class StringMapConverter : PropertyConverter<Map<String, String>, String> {
	override fun convertToEntityProperty(databaseValue: String?): Map<String, String> {
		return if (databaseValue.isNullOrEmpty()) {
			emptyMap()
		} else {
			try {
				databaseValue.split(";").associate { header ->
					val parts = header.split(":", limit = 2)
					if (parts.size == 2) {
						parts[0].trim() to parts[1].trim()
					} else {
						parts[0].trim() to ""
					}
				}
			} catch (e: Exception) {
				emptyMap()
			}
		}
	}

	override fun convertToDatabaseValue(entityProperty: Map<String, String>?): String {
		return entityProperty?.entries?.joinToString(";") { (key, value) ->
			"$key:$value"
		} ?: ""
	}
}