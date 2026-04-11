package app.core.engines.downloader

import io.objectbox.annotation.*
import java.io.*

/**
 * Represents metadata about a remote file used in the download system.
 *
 * This class holds all essential information about a file hosted on a remote server
 * without downloading the actual file contents. It is serializable with DSL-JSON
 * for easy storage, transfer, and caching operations within the download engine.
 *
 * ## Key Features
 *
 * ### 1. Access Control & Error Handling
 * - [isFileForbidden]: Indicates whether access to the file was denied by the server
 * - [errorMessage]: Provides details about any failure encountered while fetching file metadata
 *
 * ### 2. File Identification & Integrity
 * - [fileName]: The name of the file extracted from Content-Disposition headers or URL path
 * - [fileSize]: Size of the file in bytes; -1 indicates unknown size
 * - [fileChecksum]: Optional cryptographic checksum (e.g., SHA-256, MD5) for file integrity verification
 *
 * ### 3. Download Capabilities
 * - [isSupportsMultipart]: True if server supports partial content downloads (HTTP Range requests)
 * - [isSupportsResume]: True if download can be resumed using range requests, ETag, or Last-Modified headers
 *
 * ## Usage
 *
 * This class is designed to be lightweight and easily transportable between components.
 * It captures only the metadata required for managing downloads, tracking progress,
 * and performing integrity checks.
 *
 * @property id Unique identifier for ObjectBox database (fixed to 1 for singleton pattern)
 * @property downloadId Unique identifier linking to the associated download model
 * @property isFileForbidden True if server returned 403 Forbidden or similar access denial
 * @property errorMessage Descriptive error message when file info retrieval fails
 * @property fileName Name of the remote file with extension
 * @property fileSize File size in bytes, or -1 if unknown/unavailable
 * @property fileChecksum Cryptographic hash for integrity verification (optional)
 * @property isSupportsMultipart Indicates support for parallel/multipart downloads
 * @property isSupportsResume Indicates support for resuming interrupted downloads
 *
 * @see io.objectbox.annotation.Id for primary key configuration in ObjectBox
 */
@Entity
class AIORemoteFileInfo : Serializable {

	@Id var id: Long = 0L
	@Index var downloadId: Long = -1L
	@Index var isFileForbidden: Boolean = false
	@Index var errorMessage: String = ""
	@Index var fileName: String = ""
	@Index var fileSize: Long = 0L
	@Index var fileChecksum: String = ""
	@Index var isSupportsMultipart: Boolean = false
	@Index var isSupportsResume: Boolean = false
}