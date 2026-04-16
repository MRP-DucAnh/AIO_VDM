package lib.ui.builders

import android.annotation.*
import android.content.*
import android.util.*
import android.view.*
import androidx.viewpager.widget.*

class NonSwappableViewPager(context: Context, attrs: AttributeSet?) : ViewPager(context, attrs) {

	override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false

	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(ev: MotionEvent): Boolean = false
}