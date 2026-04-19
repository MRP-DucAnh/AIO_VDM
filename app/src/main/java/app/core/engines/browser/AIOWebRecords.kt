package app.core.engines.browser

import io.objectbox.annotation.*
import java.io.*
import java.util.*

@Entity
data class AIOWebRecords(
	@Id var id: Long = 0,
	@Index(type = IndexType.HASH) var url: String = "",
	@Index var name: String = "",
	@Index var creationDate: Date = Date(),
	var modifiedDate: Date = Date(),
	var thumbFilePath: String = "",
	var description: String = "",
	var tags: String = "",
	@Index var favorite: Boolean = false,
	@Index var folder: String = "",
	@Index var isBookmark: Boolean = false,
	@Index var accessCount: Int = 0,
	@Index var lastAccessed: Date = Date()
) : Serializable