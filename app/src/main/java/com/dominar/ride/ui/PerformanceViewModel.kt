package com.dominar.ride.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominar.ride.data.db.PerformanceRunDao
import com.dominar.ride.data.db.PerformanceRunEntity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.sqrt

/** State machine for the 0-100 km/h timer. */
sealed interface TimerState {
    data object Idle : TimerState

    /** Location updates running; waiting for the bike to come to a stop. */
    data object WaitingForStop : TimerState

    /** Standing still — launch whenever you're ready. */
    data object Ready : TimerState

    /** Launch detected, clock running. */
    data object Running : TimerState

    data class Finished(val timeTo60Ms: Long?, val timeTo100Ms: Long?) : TimerState
}

@HiltViewModel
class PerformanceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val performanceRunDao: PerformanceRunDao
) : ViewModel(), SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gravitySensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val linearAccelSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    val leanAvailable: Boolean = rotationSensor != null || gravitySensor != null

    // ---------- Lean angle ----------

    private val _leanDeg = MutableStateFlow(0f)
    /** Smoothed lean angle in degrees. Negative = left, positive = right. */
    val leanDeg: StateFlow<Float> = _leanDeg.asStateFlow()

    private val _maxLeanLeft = MutableStateFlow(0f)
    val maxLeanLeft: StateFlow<Float> = _maxLeanLeft.asStateFlow()

    private val _maxLeanRight = MutableStateFlow(0f)
    val maxLeanRight: StateFlow<Float> = _maxLeanRight.asStateFlow()

    private var rawRollDeg = 0f
    private var zeroOffsetDeg = 0f
    private var leanSensorActive = false
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    fun startLeanSensor() {
        if (leanSensorActive) return
        val sensor = rotationSensor ?: gravitySensor ?: return
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        leanSensorActive = true
    }

    fun stopLeanSensor() {
        if (!leanSensorActive) return
        val sensor = rotationSensor ?: gravitySensor ?: return
        sensorManager.unregisterListener(this, sensor)
        leanSensorActive = false
    }

    /** Store the current orientation as the zero point (phone fixed on its mount). */
    fun calibrateLean() {
        zeroOffsetDeg = rawRollDeg
        _leanDeg.value = 0f
        _maxLeanLeft.value = 0f
        _maxLeanRight.value = 0f
    }

    fun resetLeanRecords() {
        _maxLeanLeft.value = 0f
        _maxLeanRight.value = 0f
    }

    private fun onRollSample(rollDeg: Float) {
        rawRollDeg = rollDeg
        var lean = rollDeg - zeroOffsetDeg
        // Normalize to -180..180 so calibration near the wrap point behaves.
        while (lean > 180f) lean -= 360f
        while (lean < -180f) lean += 360f
        val smoothed = _leanDeg.value * 0.75f + lean * 0.25f
        _leanDeg.value = smoothed
        if (smoothed < -_maxLeanLeft.value) _maxLeanLeft.value = -smoothed
        if (smoothed > _maxLeanRight.value) _maxLeanRight.value = smoothed
    }

    // ---------- 0-100 timer ----------

    private val _timerState = MutableStateFlow<TimerState>(TimerState.Idle)
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    private val _speedKmh = MutableStateFlow(0f)
    val speedKmh: StateFlow<Float> = _speedKmh.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    val runs: StateFlow<List<PerformanceRunEntity>> =
        performanceRunDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bestRun: StateFlow<PerformanceRunEntity?> =
        performanceRunDao.observeAll()
            .map { list -> list.filter { it.timeTo100Ms != null }.minByOrNull { it.timeTo100Ms!! } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var t0Nanos: Long? = null
    private var time60Ms: Long? = null
    private var time100Ms: Long? = null
    private var prevSpeedMps = 0f
    private var prevSampleNanos = 0L
    private var tickerJob: Job? = null
    private var accelListening = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (location in result.locations) {
                val speedMps = if (location.hasSpeed()) location.speed else 0f
                val nanos = location.elapsedRealtimeNanos
                _speedKmh.value = speedMps * 3.6f
                handleSpeedSample(speedMps, nanos)
                prevSpeedMps = speedMps
                prevSampleNanos = nanos
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Returns false when the location permission is missing. */
    @SuppressLint("MissingPermission")
    fun startTimer(): Boolean {
        if (!hasLocationPermission()) return false
        if (_timerState.value is TimerState.WaitingForStop ||
            _timerState.value is TimerState.Ready ||
            _timerState.value is TimerState.Running
        ) return true

        t0Nanos = null
        time60Ms = null
        time100Ms = null
        prevSpeedMps = 0f
        prevSampleNanos = 0L
        _elapsedMs.value = 0L
        _timerState.value = TimerState.WaitingForStop

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 200L)
            .setMinUpdateIntervalMillis(100L)
            .build()
        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())

        linearAccelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
            accelListening = true
        }
        return true
    }

    fun cancelTimer() {
        stopMeasuring()
        _timerState.value = TimerState.Idle
        _elapsedMs.value = 0L
        _speedKmh.value = 0f
    }

    private fun stopMeasuring() {
        fusedClient.removeLocationUpdates(locationCallback)
        if (accelListening) {
            linearAccelSensor?.let { sensorManager.unregisterListener(this, it) }
            accelListening = false
        }
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun launch(startNanos: Long) {
        if (t0Nanos != null) return
        t0Nanos = startNanos
        _timerState.value = TimerState.Running
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (_timerState.value is TimerState.Running) {
                val t0 = t0Nanos ?: break
                _elapsedMs.value = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000L
                delay(50L)
            }
        }
    }

    private fun handleSpeedSample(speedMps: Float, nanos: Long) {
        when (_timerState.value) {
            is TimerState.WaitingForStop -> {
                if (speedMps < 0.6f) _timerState.value = TimerState.Ready
            }
            is TimerState.Ready -> {
                // GPS fallback launch detection (accelerometer usually fires first).
                if (speedMps > 1.0f) launch(nanos)
            }
            is TimerState.Running -> {
                val t0 = t0Nanos ?: return
                if (time60Ms == null) {
                    crossingNanos(60f, speedMps, nanos)?.let { time60Ms = (it - t0) / 1_000_000L }
                }
                if (time100Ms == null) {
                    crossingNanos(100f, speedMps, nanos)?.let { time100Ms = (it - t0) / 1_000_000L }
                }
                val elapsed = (nanos - t0) / 1_000_000L
                when {
                    time100Ms != null -> finishRun()
                    // Rider gave up / had to stop: keep a 0-60 partial if we got one.
                    speedMps < 0.6f && elapsed > 3_000L -> finishRun()
                    else -> Unit
                }
            }
            else -> Unit
        }
    }

    /** Interpolated elapsedRealtimeNanos of crossing [targetKmh] between the previous and current GPS sample. */
    private fun crossingNanos(targetKmh: Float, speedMps: Float, nanos: Long): Long? {
        val targetMps = targetKmh / 3.6f
        if (prevSampleNanos == 0L) return if (speedMps >= targetMps) nanos else null
        if (prevSpeedMps >= targetMps || speedMps < targetMps) return null
        val fraction = (targetMps - prevSpeedMps) / (speedMps - prevSpeedMps)
        return prevSampleNanos + ((nanos - prevSampleNanos) * fraction).toLong()
    }

    private fun finishRun() {
        val t60 = time60Ms
        val t100 = time100Ms
        stopMeasuring()
        _timerState.value = TimerState.Finished(t60, t100)
        _elapsedMs.value = t100 ?: t60 ?: 0L
        if (t60 != null || t100 != null) {
            viewModelScope.launch {
                performanceRunDao.insert(
                    PerformanceRunEntity(
                        timeTo60Ms = t60,
                        timeTo100Ms = t100,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun deleteRun(run: PerformanceRunEntity) {
        viewModelScope.launch { performanceRunDao.delete(run) }
    }

    // ---------- SensorEventListener ----------

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                onRollSample(Math.toDegrees(orientation[2].toDouble()).toFloat())
            }
            Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                // Portrait mount assumption for the fallback path.
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val roll = Math.toDegrees(
                    atan2(-x.toDouble(), sqrt((y * y + z * z).toDouble()))
                ).toFloat()
                onRollSample(roll)
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                if (_timerState.value is TimerState.Ready) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val magnitude = sqrt(x * x + y * y + z * z)
                    // Millisecond-precision launch detection on the first strong push.
                    if (magnitude > 2.0f) launch(SystemClock.elapsedRealtimeNanos())
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onCleared() {
        stopMeasuring()
        stopLeanSensor()
        super.onCleared()
    }
}
