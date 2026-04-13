@file:Suppress("UNCHECKED_CAST")

package lib.process

import java.io.*

/**
 * Utility for creating deep copies of [Serializable] objects.
 *
 * This class uses Java serialization to create complete, independent copies of object graphs.
 * The resulting instance shares no references with the original object.
 *
 * ### Requirements
 * - The source object and all nested members must implement [Serializable].
 * - Non-serializable dependencies will trigger a `NotSerializableException`.
 *
 * ### Performance Warning
 * Serialization-based copying is slower than manual cloning or copy constructors.
 * It is recommended for occasional state snapshots or defensive copying, but avoid
 * using it in high-frequency loops or performance-critical paths.
 *
 * ### Example
 * ```kotlin
 * val copy = CopyObjectUtils.deepCopy(originalUser)
 * ```
 *
 * @see Serializable
 * @see ObjectInputStream
 * @see ObjectOutputStream
 */
object CopyObjectUtils {

	private val logger = LogHelperUtils.from(javaClass)

	/**
	 * Creates a deep copy of the provided [Serializable] object.
	 *
	 * Serializes the input to a byte array and immediately deserializes it to a new instance.
	 * Transient and static fields are not preserved.
	 *
	 * @param T The type of the object, which must be [Serializable].
	 * @param object The object to clone. Can be null.
	 * @return A complete deep copy of the object, or `null` if the input is null or
	 * if an error occurs (e.g., [NotSerializableException]).
	 */
	@JvmStatic
	fun <T : Serializable?> deepCopy(`object`: T): T? {
		return try {
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