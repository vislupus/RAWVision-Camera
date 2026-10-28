package com.rawvision.camera.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.view.OrientationEventListener
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.sqrt

/** Last known position for geotagging, using the framework LocationManager (no Play Services needed). */
class LocationTracker(private val context: Context) {

    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()
    private var listening = false

    // Explicit object (not a lambda): on API 28 the extra interface methods are still abstract at runtime.
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) { _location.value = pickBetter(_location.value, location) }
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (listening || !hasPermission) return
        listening = true
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        providers.forEach { provider ->
            runCatching {
                if (manager.isProviderEnabled(provider)) {
                    manager.getLastKnownLocation(provider)?.let { _location.value = pickBetter(_location.value, it) }
                    manager.requestLocationUpdates(provider, 4000L, 3f, listener)
                }
            }
        }
    }

    fun stop() {
        if (!listening) return
        listening = false
        runCatching { manager.removeUpdates(listener) }
    }

    private fun pickBetter(old: Location?, new: Location): Location {
        if (old == null) return new
        val newer = new.time - old.time > 30_000
        val moreAccurate = new.accuracy <= old.accuracy
        return if (newer || moreAccurate) new else old
    }
}

/** Device roll angle (degrees) from the accelerometer for the spirit level overlay. */
class LevelSensor(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val _roll = MutableStateFlow(0f)
    val roll: StateFlow<Float> = _roll.asStateFlow()
    private var smoothed = 0f

    fun start() { sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) } }
    fun stop() = manager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
        val g = sqrt(x * x + y * y + z * z)
        if (g < 1f) return
        // Roll relative to the portrait orientation; 0 = perfectly level.
        val angle = Math.toDegrees(atan2(-x, y).toDouble()).toFloat()
        smoothed += (angle - smoothed) * 0.15f
        _roll.value = smoothed
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/** Physical device rotation in degrees (0 / 90 / 180 / 270) used for the JPEG/DNG orientation tag. */
class RotationTracker(context: Context) {
    private val _rotation = MutableStateFlow(0)
    val rotation: StateFlow<Int> = _rotation.asStateFlow()

    private val listener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return
            val snapped = ((orientation + 45) / 90 * 90) % 360
            if (snapped != _rotation.value) _rotation.value = snapped
        }
    }

    fun start() { if (listener.canDetectOrientation()) listener.enable() }
    fun stop() = listener.disable()
}
