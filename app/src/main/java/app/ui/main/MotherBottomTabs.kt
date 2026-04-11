package app.ui.main

import android.graphics.PorterDuff.Mode.SRC_IN
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat.getColor
import androidx.core.content.res.ResourcesCompat.getDrawable
import androidx.core.content.res.ResourcesCompat.getFont
import com.aio.R
import lib.process.LogHelperUtils
import lib.ui.ViewUtility.setBounceClick

class MotherBottomTabs(private val motherActivity: MotherActivity?) {

	private val logger = LogHelperUtils.from(javaClass)

	private val safeMotherActivityRef
		get() =
			motherActivity?.getActivity() as? MotherActivity

	private val buttons by lazy {
		safeMotherActivityRef?.let { activityRef ->
			mapOf(
				R.id.btn_home_tab to { activityRef.openHomeFragment() },
				R.id.btn_browser_tab to { activityRef.openBrowserFragment() },
				R.id.btn_tasks_tab to { activityRef.openDownloadsFragment() },
				R.id.btn_settings_tab to { activityRef.openSettingsFragment() }
			)
		}
	}

	fun initialize() {
		safeMotherActivityRef?.let { activityRef ->
			buttons?.let { buttons ->
				buttons.keys.forEach { id ->
					val view = activityRef.findViewById<View>(id)
					view?.setBounceClick { view, bool -> buttons[id]?.invoke() }
				}
			}
		}
	}

	fun updateTabSelectionUI(tab: Tab) {
		safeMotherActivityRef?.let { activityRef ->
			val buttonTabs = mapOf(
				Tab.HOME_TAB to listOf(R.id.btn_home_tab, R.id.img_home_tab, R.id.txt_home_tab),
				Tab.BROWSER_TAB to listOf(R.id.btn_browser_tab, R.id.img_browser_tab, R.id.txt_browser_tab),
				Tab.DOWNLOADS_TAB to listOf(R.id.btn_tasks_tab, R.id.img_tasks_tab, R.id.txt_task_tab),
				Tab.SETTINGS_TAB to listOf(R.id.btn_settings_tab, R.id.img_settings_tab, R.id.txt_settings_tab)
			)

			buttonTabs.values.forEach { ids ->
				activityRef.findViewById<View>(ids[0])?.let { container ->
					val bgNegativeSelector = R.drawable.ic_button_negative_selector
					val resources = activityRef.resources
					val activityTheme = activityRef.theme
					val inactiveButtonBg = getDrawable(resources, bgNegativeSelector, activityTheme)
					container.background = inactiveButtonBg
					container.elevation = resources.getDimension(R.dimen._0)
				}

				activityRef.findViewById<View>(ids[1])?.let { logoImage ->
					(logoImage as ImageView).apply {
						setColorFilter(getColor(context, R.color.color_secondary), SRC_IN)
					}
				}

				activityRef.findViewById<View>(ids[2])?.let { textTab ->
					(textTab as TextView).apply {
						setTextColor(getColor(context, R.color.color_text_primary))
						val boldTypeface = getFont(context, R.font.font_family_semibold)
						typeface = boldTypeface
					}
				}
			}

			buttonTabs[tab]?.let { ids ->
				activityRef.findViewById<View>(ids[0])?.let { container ->
					val bgDrawableResId = R.drawable.rounded_secondary_color
					val resources = activityRef.resources
					val activityTheme = activityRef.theme
					val buttonBg = getDrawable(resources, bgDrawableResId, activityTheme)
					container.background = buttonBg
					container.elevation = resources.getDimension(R.dimen._3)
				}

				activityRef.findViewById<View>(ids[1])?.let { logoImage ->
					(logoImage as ImageView).apply {
						setColorFilter(getColor(context, R.color.color_on_secondary), SRC_IN)
					}
				}

				activityRef.findViewById<View>(ids[2])?.let { textTab ->
					(textTab as TextView).apply {
						setTextColor(getColor(context, R.color.color_on_secondary))
						val boldTypeface = getFont(context, R.font.font_family_bold)
						typeface = boldTypeface
					}
				}
			}
		}
	}

	enum class Tab {
		HOME_TAB,
		BROWSER_TAB,
		DOWNLOADS_TAB,
		SETTINGS_TAB
	}
}