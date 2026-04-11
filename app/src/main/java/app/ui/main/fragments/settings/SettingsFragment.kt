package app.ui.main.fragments.settings

import android.os.*
import android.view.*
import android.widget.*
import androidx.lifecycle.*
import app.core.AIOApp.Companion.aioRawFiles
import app.core.bases.*
import app.ui.main.*
import com.airbnb.lottie.*
import kotlinx.coroutines.*
import lib.device.AppVersionUtility.versionCode
import lib.device.AppVersionUtility.versionName
import lib.process.*
import lib.texts.CommonTextUtils.fromHtmlStringToSpanned
import lib.ui.ViewUtility.hideView
import lib.ui.ViewUtility.showView
import java.lang.ref.*
import com.aio.R

class SettingsFragment : BaseFragment() {
	private val logger = LogHelperUtils.from(javaClass)
	private val weakRef = WeakReference(this)

	val safeSettingsFragRef get() = weakRef.get()
	val safeMotherActivityRef get() = safeBaseActivityRef as? MotherActivity

	var settingsOnClickLogic: SettingsOnClickLogic? = null

	override fun getLayoutResId(): Int = R.layout.frag_settings_1_main_1

	override fun onAfterLayoutLoad(layoutView: View, state: Bundle?) {
		try {
			safeSettingsFragRef?.let { fragRef ->
				safeFragmentLayoutRef?.let { layoutRef ->
					registerSelfReferenceInMotherActivity()
					settingUpLoadingStateAnimation(layoutRef)
					hideActualLayout()
					setupViewsOnClickEvents(fragRef, layoutRef)
				}
			}
		} catch (error: Exception) {
			logger.e("Exception during onAfterLayoutLoad()", error)
		}
	}

	override fun onResumeFragment() {
		logger.d("onResumeFragment() called")
		registerSelfReferenceInMotherActivity()
		try {
			safeSettingsFragRef?.let { fragmentRef ->
				safeFragmentLayoutRef?.let { layoutRef ->
					settingsOnClickLogic?.updateSettingStateUI()
					updateViewsWithCurrentData(layoutRef)
					fragmentRef.viewLifecycleOwner.lifecycleScope.launch {
						delay(700)
						if (fragmentRef.isResumed) {
							releaseActualLayout()
						}
					}
				}
			}
		} catch (error: Exception) {
			logger.e("Exception while updating settings state UI", error)
		}
	}

	override fun onPauseFragment() {
		logger.d("onPauseFragment() called")
	}

	override fun onDestroyView() {
		logger.d("onDestroyView() called")
		unregisterSelfReferenceInMotherActivity()
		settingsOnClickLogic = null
		super.onDestroyView()
	}

	private fun registerSelfReferenceInMotherActivity() {
		try {
			safeMotherActivityRef?.settingsFragment = safeSettingsFragRef
			safeMotherActivityRef?.sideNavigation?.closeDrawerNavigation()
		} catch (error: Exception) {
			logger.e("Error while registering fragment with MotherActivity", error)
		}
	}

	private fun unregisterSelfReferenceInMotherActivity() {
		try {
			safeMotherActivityRef?.settingsFragment = null
		} catch (error: Exception) {
			logger.e("Error during fragment unregistration", error)
		}
	}

	private fun setupViewsOnClickEvents(settingsFragmentRef: SettingsFragment, fragmentLayout: View) {
		try {
			settingsOnClickLogic = SettingsOnClickLogic(settingsFragmentRef)

			val clickActions = mapOf(
				R.id.btn_default_download_location to { settingsOnClickLogic?.showDownloadLocationPicker() },
				R.id.btn_dark_mode_ui to { settingsOnClickLogic?.togglesDarkModeUISettings() },
				R.id.btn_daily_suggestions to { settingsOnClickLogic?.toggleDailyContentSuggestions() },

				R.id.btn_default_download_folder to { settingsOnClickLogic?.changeDefaultDownloadFolder() },
				R.id.btn_hide_task_notifications to { settingsOnClickLogic?.toggleHideDownloadNotification() },
				R.id.btn_wifi_only_downloads to { settingsOnClickLogic?.toggleWifiOnlyDownload() },
				R.id.btn_single_click_open to { settingsOnClickLogic?.toggleSingleClickToOpenFile() },
				R.id.btn_play_notification_sound to { settingsOnClickLogic?.toggleDownloadNotificationSound() },
				R.id.btn_adv_downloads_settings to { settingsOnClickLogic?.openAdvanceDownloadsSettings() },

				R.id.btn_browser_homepage to { settingsOnClickLogic?.setBrowserDefaultHomepage() },
				R.id.btn_enable_adblock to { settingsOnClickLogic?.toggleBrowserBrowserAdBlocker() },
				R.id.btn_enable_popup_blocker to { settingsOnClickLogic?.toggleBrowserPopupAdBlocker() },
				R.id.btn_show_image_on_web to { settingsOnClickLogic?.toggleBrowserWebImages() },
				R.id.btn_enable_video_grabber to { settingsOnClickLogic?.toggleBrowserVideoGrabber() },
				R.id.btn_adv_browser_settings to { settingsOnClickLogic?.openAdvanceBrowserSettings() },

				R.id.btn_share_with_friends to { settingsOnClickLogic?.shareApplicationWithFriends() },
				R.id.btn_open_feedback to { settingsOnClickLogic?.openUserFeedbackActivity() },
				R.id.btn_open_about_info to { settingsOnClickLogic?.openApplicationInformation() },
				R.id.btn_open_privacy_policy to { settingsOnClickLogic?.showPrivacyPolicyActivity() },
				R.id.btn_open_terms_condition to { settingsOnClickLogic?.showTermsConditionActivity() },

				R.id.btn_check_new_update to { settingsOnClickLogic?.checkForNewApkVersion() },
				R.id.btn_restart_app to { settingsOnClickLogic?.restartApplication() },
			)

			clickActions.forEach { (id, action) ->
				fragmentLayout.setClickListener(id) {
					try {
						action()
					} catch (error: Exception) {
						logger.e("Error executing click action for viewId=$id", error)
					}
				}
			}
		} catch (error: Exception) {
			logger.e("Error during click listener setup", error)
		}
	}

	fun updateViewsWithCurrentData(fragmentLayout: View) {
		displayApplicationVersion(fragmentLayout)
	}

	private fun displayApplicationVersion(fragmentLayout: View) {
		try {
			with(fragmentLayout) {
				findViewById<TextView>(R.id.txt_version_info)?.apply {
					val versionNameText = "${getString(R.string.title_version_number)} $versionName"
					val versionCodeText = "${getString(R.string.title_build_version)} $versionCode"
					text = fromHtmlStringToSpanned("${versionNameText}<br/>${versionCodeText}")
				}
			}
		} catch (error: Exception) {
			logger.e("Error initializing version info view", error)
		}
	}

	private fun View.setClickListener(id: Int, action: () -> Unit) {
		try {
			findViewById<View>(id)?.setOnClickListener { action() }
		} catch (error: Exception) {
			logger.e("Error setting click listener for id=$id", error)
		}
	}

	private fun releaseActualLayout() {
		safeFragmentLayoutRef?.let { fragLayout ->
			hideView(fragLayout.findViewById(R.id.container_layout_loading), true)
			showView(fragLayout.findViewById(R.id.container_main_layout), true)
		}
	}

	private fun hideActualLayout() {
		safeFragmentLayoutRef?.let { fragLayout ->
			showView(fragLayout.findViewById(R.id.container_layout_loading), true)
			hideView(fragLayout.findViewById(R.id.container_main_layout), true)
		}
	}

	private fun settingUpLoadingStateAnimation(fragLayout: View) {
		fragLayout.findViewById<LottieAnimationView>(R.id.img_layout_loading_anim)?.apply {
			clipToCompositionBounds = false
			setScaleType(ImageView.ScaleType.FIT_XY)

			val composition = aioRawFiles.getLayoutLoadComposition()
			if (composition != null) {
				setComposition(composition)
				playAnimation()
				showView(this, true, 100)
			} else {
				setAnimation(R.raw.animation_layout_load)
				showView(this, true, 100)
			}
		}
	}
}