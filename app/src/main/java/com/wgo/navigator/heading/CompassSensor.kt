package com.wgo.navigator.heading

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.wgo.navigator.metrics.WgoMetrics

/**
 * The watch compass (P11, docs/ARCHITECTURE.md "Heading fusion"): one listener on the fused rotation vector sensor
 * (`TYPE_ROTATION_VECTOR`, else `TYPE_GEOMAGNETIC_ROTATION_VECTOR`) at `SENSOR_DELAY_UI`, registered only between
 * [start] and [stop] (the navigation host decides: a trip with the screen interactive). Each sample goes to [onSample]
 * as the magnetic azimuth ([HeadingMath.azimuthFromRotationVector]) and the sensor accuracy, on the main thread.
 */
class CompassSensor(context: Context, private val onSample: (magneticDeg: Double?, accuracy: Int) -> Unit) {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: manager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

    /** The accuracy the system reported last (SENSOR_STATUS_*); the events' own field is used when it is set. */
    @Volatile
    private var accuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM

    /** True while the listener is registered (budget checks: never on Home idle). */
    @Volatile
    var running = false
        private set

    val available: Boolean get() = sensor != null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            onSample(HeadingMath.azimuthFromRotationVector(event.values), event.accuracy.takeIf { it >= 0 } ?: accuracy)
        }

        override fun onAccuracyChanged(s: Sensor?, value: Int) {
            accuracy = value
        }
    }

    fun start() {
        if (running || sensor == null || manager == null) return
        running = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        WgoMetrics.event("compass start ok=$running type=${sensor.type}")
    }

    fun stop() {
        if (!running) return
        manager?.unregisterListener(listener)
        running = false
        WgoMetrics.event("compass stop")
    }

    companion object {
        /** Magnetic declination (degrees, east positive) at a point now; 0 when it cannot be computed. */
        fun declination(lat: Double, lon: Double): Double = try {
            GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination.toDouble()
        } catch (e: RuntimeException) {
            0.0
        }
    }
}
