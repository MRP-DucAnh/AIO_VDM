package app.core.engines.fst_sldb

import app.core.engines.downloader.*
import app.core.engines.fst_sldb.FSTSerializerBuilder.fstSerializationConfig
import app.core.engines.settings.*
import app.core.engines.video_parser.parsers.*
import org.nustaq.serialization.*
import org.nustaq.serialization.FSTConfiguration.*

/**
 * Provides a globally accessible, pre-configured [FSTConfiguration] instance for serialization.
 *
 * This object holds a singleton `FSTConfiguration` optimized for performance within the application.
 * It eliminates the need to create new configurations repeatedly, ensuring consistent and efficient
 * serialization and deserialization across different parts of the app.
 *
 * The configuration is based on [FSTConfiguration.createAndroidDefaultConfiguration] and is
 * set to prefer speed (`isPreferSpeed = true`). It also pre-registers common data models
 * to reduce serialization overhead and output size.
 *
 * ### Usage
 *
 * Access the shared configuration directly via the `fstSerializationConfig` property:
 *
 * ```kotlin
 * // Get the pre-configured instance
 * val fstConfig = FSTSerializerBuilder.fstSerializationConfig
 *
 * // Use the configuration to serialize/deserialize
 * val bytes = fstConfig.asByteArray(myObject)
 * val deserializedObject = fstConfig.asObject(bytes)
 * ```
 *
 * @see FSTConfiguration
 * @see fstSerializationConfig
 */
object FSTSerializerBuilder {
	
	/**
	 * Global [FSTConfiguration] instance for high-performance serialization.
	 *
	 * This singleton instance is shared across the application to ensure consistent
	 * and efficient serialization/deserialization of objects. It is initialized
	 * with [FSTConfiguration.createAndroidDefaultConfiguration] and optimized for speed
	 * by setting `isPreferSpeed = true`.
	 *
	 * To further improve performance and reduce the size of the serialized output,
	 * the following frequently used classes are pre-registered:
	 * - [AIOSettings]
	 * - [AIODownload]
	 * - [AIOVideoFormat]
	 * - [AIOVideoInfo]
	 *
	 * ### Example Usage:
	 * ```kotlin
	 * // Serialize an object to a byte array
	 * val bytes = fstConfig.asByteArray(someObject)
	 *
	 * // Deserialize a byte array back into an object
	 * val restoredObject = fstConfig.asObject(bytes) as SomeObject
	 * ```
	 */
	@JvmStatic
	val fstSerializationConfig: FSTConfiguration =
		createAndroidDefaultConfiguration().apply {
			registerClass(
				AIOSettings::class.java, AIODownload::class.java,
				AIOVideoFormat::class.java, AIOVideoInfo::class.java, AIORemoteFileInfo::class.java
			)
			isPreferSpeed = true
		}
	
}