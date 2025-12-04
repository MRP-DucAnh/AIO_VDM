package app.core

import android.os.*
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import lib.process.*
import java.lang.ref.*
import java.util.concurrent.*

open class AIOTimer(
	millisInFuture: Long,
	countDownInterval: Long,
	maxConcurrentListeners: Int = 10
) : CountDownTimer(millisInFuture, countDownInterval) {
	
	private val logger = LogHelperUtils.from(javaClass)
	private var loopCount = 0.0
	private var hasTimerActive = false
	private val timerListeners = CopyOnWriteArrayList<WeakReference<AIOTimerListener>>()
	private val limitedParallelism = Default.limitedParallelism(maxConcurrentListeners)
	private val listenerScope = CoroutineScope(SupervisorJob() + limitedParallelism)
	private val gcIntervalTicks: Int = (GC_INTERVAL_MILLIS / countDownInterval).toInt()
	
	companion object {
		
		private const val GC_INTERVAL_SECONDS = 30
		private const val GC_INTERVAL_MILLIS: Long = GC_INTERVAL_SECONDS * 1000L
	}
	
	override fun onTick(millisUntilFinished: Long) {
		loopCount++
		hasTimerActive = true
		logger.d("Tick $loopCount (GC check every $gcIntervalTicks ticks)")
		
		if (loopCount.toInt() % gcIntervalTicks == 0) {
			listenerScope.launch(IO) {
				logger.d("Forcing Garbage Collection via System.gc() at tick $loopCount")
				System.gc()
			}
		}
		
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
		hasTimerActive = false
		logger.d("Timer stopped. Resources cleared.")
	}
	
	fun clearResources() {
		if (hasTimerActive) {
			logger.d("Timer is active. Stopping and clearing resources.")
			return
		}
		timerListeners.clear()
		logger.d("Timer resources cleared.")
	}
	
	interface AIOTimerListener {
		
		fun onAIOTimerTick(loopCount: Double)
	}
}