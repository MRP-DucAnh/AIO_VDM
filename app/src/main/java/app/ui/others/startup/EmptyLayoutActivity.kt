package app.ui.others.startup

import app.core.bases.BaseActivity
import lib.process.LogHelperUtils
import java.lang.ref.WeakReference

class EmptyLayoutActivity : BaseActivity() {

	private val logger = LogHelperUtils.from(javaClass)
	private val safeSelfReference = WeakReference(this)
	private val safeEmptyLayoutActivityRef get() = safeSelfReference.get()

	override fun onRenderingLayout(): Int {
		logger.d("onRenderingLayout called")
		return -1
	}

	override fun onAfterLayoutRender() {
		logger.d("onAfterLayoutRender called")
	}

	override fun onBackPressActivity() {
		logger.d("onBackPressActivity called")
		exitActivityOnDoubleBackPress()
	}

	override fun onDestroy() {
		logger.d("onDestroy called, clearing references")
		safeSelfReference.clear()
		clearWeakActivityReference()
		super.onDestroy()
	}
}