package app.core.bases.interfaces

import app.core.bases.BaseActivity
import kotlinx.coroutines.CoroutineScope

interface BaseActivityInf {

	fun onRenderingLayout(): Int
	fun onAfterLayoutRender()

	fun onResumeActivity()
	fun onPauseActivity()

	fun launchPermissionRequest(permissions: ArrayList<String>)
	fun setSystemBarsColors(
		statusBarColorResId: Int,
		navigationBarColorResId: Int,
		isLightStatusBar: Boolean = false,
		isLightNavigationBar: Boolean = false
	)

	fun getAttachedCoroutineScope(): CoroutineScope
	fun runCodeOnAttachedThread(isUIThread: Boolean = false, codeBlock: () -> Unit)

	fun openActivity(targetActivity: Class<*>, shouldAnimate: Boolean = true)

	fun closeActivityWithSwipeAnimation(shouldAnimate: Boolean = false)
	fun closeActivityWithFadeAnimation(shouldAnimate: Boolean = false)

	fun onBackPressActivity()
	fun exitActivityOnDoubleBackPress()
	fun forceQuitApplication()

	fun openAppInfoSetting()
	fun openApplicationOfficialSite()

	fun getTimeZoneId(): String
	fun getActivity(): BaseActivity?

	fun doSomeVibration(timeInMillis: Int = 20)
	fun getSingleTopIntentFlags(): Int
}
