package app.core

import android.os.CountDownTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lib.process.LogHelperUtils
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

open class AIOTimer(
	millisInFuture: Long,
	countDownInterval: Long,
	maxConcurrentListeners: Int = 10
) : CountDownTimer(millisInFuture, countDownInterval) {

	private val logger = LogHelperUtils.from(javaClass)
	private var loopCount = 0.0
	private val timerListeners = CopyOnWriteArrayList<WeakReference<AIOTimerListener>>()
	private val limitedParallelism = Default.limitedParallelism(maxConcurrentListeners)
	private val listenerScope = CoroutineScope(SupervisorJob() + limitedParallelism)

	override fun onTick(millisUntilFinished: Long) {
		loopCount++
		logger.d("Tick $loopCount")

		val iterator = timerListeners.iterator()
		while (iterator.hasNext()) {
			val listenerRef = iterator.next()
			if (listenerRef.get() == null) {
				timerListeners.remove(listenerRef)
			}
		}

		timerListeners.forEach { listenerRef ->
			listenerRef.get()?.let { listener ->
				val classSimpleName = listener.javaClass.simpleName

				listenerScope.launch {
					try {
						withContext(Dispatchers.Main) {
							logger.d("Notifying $classSimpleName @ $loopCount")
							listener.onAIOTimerTick(loopCount)
						}
					} catch (error: Exception) {
						logger.e("Error in listener callback: $classSimpleName", error)
					}
				}
			}
		}
	}

	override fun onFinish() {
		logger.d("Timer finished. Restarting.")
		this.start()
	}

	fun register(listener: AIOTimerListener) {
		val classSimpleName = listener.javaClass.simpleName
		if (timerListeners.none { it.get() == listener }) {
			timerListeners.add(WeakReference(listener))
			logger.d("Listener registered: $classSimpleName")
		} else if (timerListeners.any { it.get() == listener }) {
			logger.d("Listener already registered: $classSimpleName")
		}
	}

	fun unregister(listener: AIOTimerListener) {
		val classSimpleName = listener.javaClass.simpleName
		timerListeners.removeIf { it.get() == listener }
		logger.d("Listener unregistered: $classSimpleName")
	}

	fun stop() {
		this.cancel()
		listenerScope.cancel(message = "AIOTimer stopped")
		timerListeners.clear()
		logger.d("Timer stopped. Resources cleared.")
	}

	interface AIOTimerListener {
		fun onAIOTimerTick(loopCount: Double)
	}
}