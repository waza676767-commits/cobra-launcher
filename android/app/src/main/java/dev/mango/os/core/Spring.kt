package dev.mango.os.core

import android.view.Choreographer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A damped spring solved in closed form every vsync (so it stays exact at 60, 90 or 120 Hz).
 * Velocity carries over when the target changes mid-flight, which is what makes
 * interruptions feel natural (like grabbing a page while it's still settling).
 * Nothing runs once the spring is at rest: zero cost while idle.
 */
class Spring(
    var stiffness: Float = 380f,
    var dampingRatio: Float = 0.86f,
    /** Distance to target below which the spring may stop. */
    var threshold: Float = 0.001f
) {
    var value = 0f
        private set
    var velocity = 0f
        private set
    var target = 0f
        private set
    var running = false
        private set

    var onUpdate: ((Float) -> Unit)? = null
    var onEnd: (() -> Unit)? = null

    private var lastFrame = 0L
    private val callback = Choreographer.FrameCallback { t -> frame(t) }

    fun animateTo(to: Float, startVelocity: Float = velocity) {
        target = to
        velocity = startVelocity
        if (!running) {
            running = true
            lastFrame = 0L
            Choreographer.getInstance().postFrameCallback(callback)
        }
    }

    fun snapTo(v: Float) {
        cancel()
        value = v
        target = v
        velocity = 0f
        onUpdate?.invoke(v)
    }

    /** Move the resting point without animating (e.g. during a finger drag). */
    fun setValue(v: Float) {
        value = v
    }

    fun cancel() {
        if (running) {
            running = false
            Choreographer.getInstance().removeFrameCallback(callback)
        }
    }

    private fun frame(time: Long) {
        if (!running) return
        val dt = if (lastFrame == 0L) 1f / 120f else ((time - lastFrame) / 1_000_000_000f).coerceIn(0.0005f, 1f / 24f)
        lastFrame = time
        FrameBoost.ping()
        step(dt)
        if (abs(value - target) < threshold && abs(velocity) < threshold * 12f) {
            value = target
            velocity = 0f
            running = false
            onUpdate?.invoke(value)
            onEnd?.invoke()
            return
        }
        onUpdate?.invoke(value)
        if (running) Choreographer.getInstance().postFrameCallback(callback)
    }

    private fun step(dt: Float) {
        val k = stiffness * Theme.animSpeed * Theme.animSpeed
        val zeta = if (Theme.reduceMotion) maxOf(dampingRatio, 1f) else dampingRatio
        val w0 = sqrt(k)
        val x0 = value - target
        val v0 = velocity
        val x: Float
        val v: Float
        when {
            zeta < 1f -> {
                val wd = w0 * sqrt(1f - zeta * zeta)
                val e = exp(-zeta * w0 * dt)
                val a = x0
                val b = (v0 + zeta * w0 * x0) / wd
                val c = cos(wd * dt)
                val s = sin(wd * dt)
                x = e * (a * c + b * s)
                v = e * ((b * wd - zeta * w0 * a) * c + (-a * wd - zeta * w0 * b) * s)
            }
            zeta == 1f -> {
                val e = exp(-w0 * dt)
                val b = v0 + w0 * x0
                x = e * (x0 + b * dt)
                v = e * (b - w0 * (x0 + b * dt))
            }
            else -> {
                val r = sqrt(zeta * zeta - 1f)
                val r1 = -w0 * (zeta - r)
                val r2 = -w0 * (zeta + r)
                val c2 = (v0 - r1 * x0) / (r2 - r1)
                val c1 = x0 - c2
                val e1 = exp(r1 * dt)
                val e2 = exp(r2 * dt)
                x = c1 * e1 + c2 * e2
                v = c1 * r1 * e1 + c2 * r2 * e2
            }
        }
        value = target + x
        velocity = v
    }
}
