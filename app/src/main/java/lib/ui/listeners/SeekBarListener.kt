package lib.ui.listeners

import android.widget.*
import android.widget.SeekBar.*

abstract class SeekBarListener : OnSeekBarChangeListener {

	abstract fun onProgressChange(seekBar: SeekBar?, progress: Int, fromUser: Boolean)

	override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

	override fun onStopTrackingTouch(seekBar: SeekBar) = Unit

	override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
		onProgressChange(seekBar, progress, fromUser)
	}
}