package com.example

import android.view.Choreographer

/**
 * Accurately measures genuine Android device FPS using Choreographer VSync frame callbacks.
 * Reports actual hardware rendering frequency (e.g. 60, 90, 120 FPS or frame drops).
 */
object RealFpsTracker {
    private var frameCount = 0
    private var lastTimeNanos = 0L
    private var isRunning = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return

            if (lastTimeNanos == 0L) {
                lastTimeNanos = frameTimeNanos
            } else {
                frameCount++
                val deltaNanos = frameTimeNanos - lastTimeNanos
                if (deltaNanos >= 1_000_000_000L) { // 1 second interval
                    val fps = ((frameCount * 1_000_000_000L) / deltaNanos).toInt()
                    FakeLagSettings.realHardwareFps.value = fps.coerceIn(1, 240)
                    frameCount = 0
                    lastTimeNanos = frameTimeNanos
                }
            }

            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        frameCount = 0
        lastTimeNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stop() {
        isRunning = false
        try {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        } catch (e: Exception) {
            // Safe catch
        }
    }
}
