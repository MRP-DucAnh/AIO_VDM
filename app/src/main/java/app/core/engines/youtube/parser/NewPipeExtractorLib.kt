package app.core.engines.youtube.parser

import app.core.AIOApp.Companion.aioSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lib.process.LogHelperUtils
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization

/**
 * A singleton object that acts as a wrapper and initialization point for the NewPipe Extractor library.
 * It handles the setup of the library with the necessary configurations, such as the downloader
 * implementation and localization settings, making it ready for use throughout the application.
 */
object NewPipeExtractorLib {
	
	/**
	 * Dedicated logger instance used for internal diagnostics and monitoring the
	 * initialization lifecycle of the NewPipe Extractor library.
	 */
	private val logger = LogHelperUtils.from(javaClass)
	
	/**
	 * Initializes the NewPipe extractor library.
	 *
	 * This function sets up the core components required for the extractor to work,
	 * including the downloader implementation and localization settings. It retrieves the
	 * user's selected content region from the app's settings and uses it to configure
	 * the library's [Localization].
	 */
	suspend fun initSystem() {
		withContext(Dispatchers.IO){
			val contentRegion = aioSettings.selectedContentRegion
			NewPipe.init(DefaultYTDownloaderImpl(), Localization(contentRegion))
			logger.d("NewPipe initialized with region {$contentRegion}")
		}
	}
}