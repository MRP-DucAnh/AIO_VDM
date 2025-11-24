package lib.files

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import lib.process.LogHelperUtils
import java.io.File
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
// Removed unused CoroutineScope parameter for better GC isolation
class VideoToAudioConverter {

	private val logger = LogHelperUtils.from(javaClass)
	private var isProcessCancelledByUser = AtomicBoolean(false)

	// Tracks if muxer.start() was called, necessary for safe muxer.stop() logic
	private var isMuxerStarted = false

	fun extractAudio(inputFile: String, outputFile: String, listener: ConversionListener) {
		val weakListener = WeakReference(listener)
		weakListener.get()?.let { safeListener ->

			// Initialize as nullable for safe access and assignment in try block
			var extractor: MediaExtractor? = null
			var muxer: MediaMuxer? = null

			try {
				logger.d("Starting audio extraction from video: $inputFile")

				// --- Initialization inside try block ---
				extractor = MediaExtractor()
				extractor.setDataSource(inputFile)

				var audioTrackIndex = -1
				var format: MediaFormat? = null

				logger.d("Scanning tracks in video file...")
				for (index in 0 until extractor.trackCount) {
					format = extractor.getTrackFormat(index)
					val mime = format.getString(MediaFormat.KEY_MIME)
					logger.d("Checking track $index: MIME = $mime")

					if (mime?.startsWith("audio/") == true) {
						audioTrackIndex = index
						extractor.selectTrack(index)
						logger.d("Selected audio track index: $audioTrackIndex")
						break
					}
				}

				if (audioTrackIndex == -1 || format == null) {
					val errorMsg = "No audio track found in video file: $inputFile"
					logger.d(errorMsg)
					safeListener.onFailure(errorMsg)
					// extractor and muxer cleanup handled by finally block (extractor is not null)
					return
				}

				logger.d("Initializing MediaMuxer with output file: $outputFile")
				muxer = MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
				val newTrackIndex = muxer.addTrack(format)
				muxer.start()
				isMuxerStarted = true // Set flag after successful start

				// --- Extraction Loop ---
				val buffer = ByteBuffer.allocate(4096)
				val bufferInfo = MediaCodec.BufferInfo()
				val fileSize = File(inputFile).length().toFloat()
				var extractedSize = 0L

				logger.d("Starting audio sample extraction loop...")

				while (!isProcessCancelledByUser.load()) {
					buffer.clear()

					val sampleSize = extractor.readSampleData(buffer, 0)
					if (sampleSize < 0) {
						logger.d("End of stream reached, sampleSize: $sampleSize")
						break
					}

					bufferInfo.offset = 0
					bufferInfo.size = sampleSize
					bufferInfo.presentationTimeUs = extractor.sampleTime
					bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME

					muxer.writeSampleData(newTrackIndex, buffer, bufferInfo)
					extractor.advance()

					extractedSize += sampleSize
					val progress = ((extractedSize / fileSize) * 100).toInt()
					safeListener.onProgress(progress)
				}

				// --- Handle Cancellation ---
				if (isProcessCancelledByUser.load()) {
					logger.d("Audio extraction cancelled by user")
					safeListener.onFailure("Audio extraction cancelled")
					// Cleanup handled by finally block
					return
				}

				// --- Success Cleanup (Optional but good practice) ---
				muxer.stop()
				isMuxerStarted = false // Update flag
				// Remaining cleanup handled by finally block

				logger.d("Audio extraction completed successfully. Output: $outputFile")
				safeListener.onSuccess(outputFile)

			} catch (error: Exception) {
				// --- Handle Failure ---
				val errorMsg = "Audio extraction failed: ${error.message}"
				logger.e("$errorMsg. Error: $error")
				safeListener.onFailure(errorMsg)

			} finally {
				// --- GC-PROOF Resource Cleanup ---
				// This block ensures resources are released regardless of how 'try' exits.

				// 1. Attempt to stop the muxer only if it was started successfully.
				if (isMuxerStarted) {
					try {
						muxer?.stop()
					} catch (error: Exception) {
						logger.e("Error stopping muxer " +
							"(ignored to proceed with release): ${error.message}")
					}
				}

				// 2. Release muxer resource (must run even if stop failed)
				muxer?.release()

				// 3. Release extractor resource (must run regardless of muxer status)
				extractor?.release()

				logger.d("MediaMuxer and MediaExtractor resources released in finally block.")
				isMuxerStarted = false // Reset state for next use
			}
		}
	}

	fun cancel() {
		logger.d("Cancellation requested for audio extraction")
		isProcessCancelledByUser.store(true)
	}

	interface ConversionListener {
		fun onProgress(progress: Int)
		fun onSuccess(outputFile: String)
		fun onFailure(errorMessage: String)
	}
}