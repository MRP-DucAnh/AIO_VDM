@file:Suppress("UNCHECKED_CAST")

package lib.process

import java.io.*

object CopyObjectUtils {

	private val logger = LogHelperUtils.from(javaClass)

	@JvmStatic
	suspend fun <T : Serializable?> deepCopy(`object`: T): T? {
		return withIOContext {
			try {
				val bos = ByteArrayOutputStream()
				ObjectOutputStream(bos).use { oos ->
					oos.writeObject(`object`)
					oos.flush()
				}

				ByteArrayInputStream(bos.toByteArray()).use { bis ->
					ObjectInputStream(bis).use { ois ->
						ois.readObject() as T
					}
				}
			} catch (error: Exception) {
				logger.e("Error while deep copying an object:", error)
				null
			}
		}
	}
}