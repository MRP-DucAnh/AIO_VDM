package app.core.engines.user_profile

import app.core.AIOApp.Companion.aioUserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import lib.process.LogHelperUtils

object AIOUserProfileManager {

	private val logger = LogHelperUtils.from(javaClass)

	@JvmStatic
	@Synchronized
	fun getAIOUserProfile(): AIOUserProfile = aioUserProfile

	@JvmStatic
	@Synchronized
	fun updateLocalUserWithSupabaseUser(updateMinInternal: Long = 2_000L) {
		logger.d(
			"updateLocalUserWithSupabaseUser() skipped because Supabase auth is disabled. " +
				"minInterval=$updateMinInternal"
		)
	}

	@JvmStatic
	@Synchronized
	fun logOutFromSupabaseAndLocalUser(scope: CoroutineScope, onLogout: (() -> Unit)? = null) {
		scope.launch {
			aioUserProfile.resetUserProfile()
			onLogout?.invoke()
		}
	}

	@JvmStatic
	@Synchronized
	fun updateAppSettingsFromSupabaseSettings() {
		logger.d("updateAppSettingsFromSupabaseSettings() skipped because Supabase is disabled")
	}
}
