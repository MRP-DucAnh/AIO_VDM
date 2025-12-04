package app.core

import app.core.FSTBuilder.fstConfig
import app.core.engines.downloader.*
import app.core.engines.settings.*
import app.core.engines.video_parser.parsers.*
import org.nustaq.serialization.*

/**
 * Singleton object responsible for managing the global [FSTConfiguration] instance.
 *
 * This ensures a single, shared configuration of the **FST (Fast-Serialization)**
 * library is used across the entire application. Using a single instance avoids
 * unnecessary memory overhead, keeps serialization consistent, and improves performance
 * by pre-registering frequently serialized classes.
 *
 * The configuration is optimized for speed (`isPreferSpeed = true`) and is initialized
 * using `FSTConfiguration.createAndroidDefaultConfiguration()`.
 *
 * ### Registered Classes:
 * - [AIOSettings]
 * - [DownloadDataModel]
 * - [VideoFormat]
 * - [VideoInfo]
 *
 * ### Usage
 * Access the configuration directly to serialize or deserialize objects:
 * ```kotlin
 * // Serialize an object
 * val bytes = FSTBuilder.fstConfig.asByteArray(myObject)
 *
 * // Deserialize an object
 * val myObject = FSTBuilder.fstConfig.asObject(bytes)
 * ```
 *
 * @see fstConfig for the global instance.
 */
object FSTBuilder {

	/**
	 * Global FST configuration instance shared across the application.
	 *
	 * This instance provides high-performance serialization and deserialization of
	 * objects to and from a binary format. It is initialized using
	 * [FSTConfiguration.createAndroidDefaultConfiguration] and is optimized for speed
	 * (`isPreferSpeed = true`).
	 *
	 * Key classes like [AIOSettings], [DownloadDataModel], [VideoFormat], and [VideoInfo]
	 * are pre-registered to improve serialization performance and reduce overhead.
	 *
	 * ### Usage
	 * ```kotlin
	 * // Serialize an object
	 * val bytes = FSTBuilder.fstConfig.asByteArray(myObject)
	 *
	 * // Deserialize an object
	 * val myObject = FSTBuilder.fstConfig.asObject(bytes) as MyObjectType
	 * ```
	 */
	@JvmStatic
	val fstConfig: FSTConfiguration = FSTConfiguration.createAndroidDefaultConfiguration().apply {
		registerClass(AIOSettings::class.java, DownloadDataModel::class.java,
			VideoFormat::class.java, VideoInfo::class.java)
		isPreferSpeed = true
	}

}
