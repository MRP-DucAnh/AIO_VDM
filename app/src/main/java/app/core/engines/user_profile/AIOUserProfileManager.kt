@file:OptIn(ExperimentalTime::class)

package app.core.engines.user_profile

import app.core.AIOApp.Companion.aioUserProfile
import app.core.engines.supabase.SupabaseCloudServer.supabaseClient
import io.github.jan.supabase.auth.*
import lib.process.*
import kotlin.time.*

/**
 * Manages the local user profile, acting as a bridge between the application's
 * local user data (`AIOUserProfile`) and the remote user data stored in Supabase.
 *
 * This singleton object provides utilities to synchronize user profile information,
 * ensuring that the local state is up-to-date with the authenticated user's session
 * details from the Supabase backend. It handles fetching user data upon successful
 * authentication and populating the local profile model.
 *
 * @see AIOUserProfile
 * @see app.core.engines.supabase.SupabaseCloudServer
 */
object AIOUserProfileManager {
	
	/**
	 * Logger for the [AIOUserProfileManager] object.
	 */
	private val logger = LogHelperUtils.from(javaClass)
	
	/**
	 * Retrieves the singleton instance of the local user profile.
	 *
	 * This function provides a synchronized, static-accessible method to obtain the
	 * `AIOUserProfile` object, which holds the current user's profile data within the application.
	 *
	 * @return The singleton [AIOUserProfile] instance.
	 * @see AIOUserProfile
	 */
	@JvmStatic
	@Synchronized
	fun getAIOUserProfile(): AIOUserProfile = aioUserProfile
	
	/**
	 * Synchronizes the local user profile with data from the current Supabase session.
	 *
	 * This function operates asynchronously on a background thread. It first retrieves the
	 * current Supabase session and its associated user object. If a session and user exist,
	 * it checks if the user is verified (i.e., either their email or phone number has been confirmed).
	 *
	 * If the user is verified, the function proceeds to update the local `aioUserProfile`
	 * singleton with details from the Supabase user, including ID, contact information,
	 * verification status, metadata, and various timestamps. After updating the local
	 * profile, it sets the `isUserCurrentlyLoggedIn` and `isSupabaseLinked` flags to true
	 * and persists these changes to local storage.
	 *
	 * The synchronization will be skipped under the following conditions:
	 * - There is no active Supabase session.
	 * - The session exists, but its user object is null.
	 * - The Supabase user is not verified (neither email nor phone is confirmed).
	 *
	 * @see AIOUserProfile
	 * @see ThreadsUtility.executeInBackground
	 */
	@JvmStatic
	@Synchronized
	fun updateLocalUserWithSupabaseUser() {
		ThreadsUtility.executeInBackground(
			timeOutInMilli = 500,
			codeBlock = {
				logger.d("updateLocalUserWithSupabaseUser() called")
				
				val session = supabaseClient.auth.currentSessionOrNull()
				if (session == null) {
					logger.d("No active Supabase session, skipping sync")
					aioUserProfile.resetUserProfile()
					return@executeInBackground
				}
				
				val supabaseUser = session.user
				if (supabaseUser == null) {
					logger.d("Session exists but user is null, skipping sync")
					aioUserProfile.resetUserProfile()
					return@executeInBackground
				}
				
				val isVerified =
					supabaseUser.phoneConfirmedAt != null ||
						supabaseUser.emailConfirmedAt != null
				
				if (!isVerified) {
					logger.d("Supabase user not verified, local profile will NOT be synced")
					aioUserProfile.resetUserProfile()
					return@executeInBackground
				}
				
				logger.d("Verified Supabase user found, syncing local profile: ${supabaseUser.id}")
				aioUserProfile.apply {
					uniqueUserServerId = supabaseUser.id
					userEmailAddress = supabaseUser.email ?: ""
					userPhoneNumber = supabaseUser.phone ?: ""
					
					isEmailVerified = supabaseUser.emailConfirmedAt != null
					isPhoneVerified = supabaseUser.phoneConfirmedAt != null
					emailVerifiedAt = supabaseUser.emailConfirmedAt?.toString() ?: ""
					phoneVerifiedAt = supabaseUser.phoneConfirmedAt?.toString() ?: ""
					
					isUserAccountVerified = isEmailVerified || isPhoneVerified
					supabaseUserMetadata = supabaseUser.userMetadata?.toString() ?: "{}"
					supabaseAppMetadata = supabaseUser.appMetadata?.toString() ?: "{}"
					
					supabaseAccountCreatedAt = supabaseUser.createdAt.toString()
					lastSupabaseLoginAt = supabaseUser.lastSignInAt?.toString() ?: ""
					
					isUserCurrentlyLoggedIn = true
					isSupabaseLinked = true
					updateInStorage()
				}
				
				logger.d("Local user profile fully synced with Supabase")
			}
		)
	}
	
	@JvmStatic
	@Synchronized
	fun updateAppSettingsFromSupabaseSettings() {
		ThreadsUtility.executeInBackground(
			timeOutInMilli = 1000,
			codeBlock = {
				logger.d("updateAppSettingsFromSupabaseSettings() called")

			}
		)
	}
	
}