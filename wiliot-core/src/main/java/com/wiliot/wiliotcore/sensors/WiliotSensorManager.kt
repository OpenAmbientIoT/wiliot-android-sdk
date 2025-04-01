package com.wiliot.wiliotcore.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.wiliot.wiliotcore.model.Acceleration

object WiliotSensorManager : SensorEventListener {

    val acceleration: Acceleration = Acceleration(null, null, null)

    private lateinit var mSensorManager: SensorManager
    private var mAccelerometerSensor: Sensor? = null
    private var mAmbientTemperatureSensor: Sensor? = null
    private var mTemperatureSensor: Sensor? = null
    var ambientTemperature: Float? = null
    var temperature: Float? = null

    private const val temperatureSensorName = "temperature"

    fun start(context: Context) {
        mSensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        mAccelerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mAmbientTemperatureSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)

        mTemperatureSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_TEMPERATURE)
        if (mTemperatureSensor == null) {
            mTemperatureSensor = mSensorManager.getSensorList(Sensor.TYPE_ALL)
                .firstOrNull { sensor -> sensor.stringType.contains(temperatureSensorName) }
        }

        mSensorManager.registerListener(
            this,
            mAccelerometerSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        mSensorManager.registerListener(
            this,
            mAmbientTemperatureSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        mSensorManager.registerListener(this, mTemperatureSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        if (this::mSensorManager.isInitialized) mSensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // do nothing
    }

    override fun onSensorChanged(event: SensorEvent?) {

        event?.sensor?.let {
            when {
                it.type == Sensor.TYPE_ACCELEROMETER -> with(acceleration) {
                    x = event.values[0]
                    y = event.values[1]
                    z = event.values[2]
                }

                it.type == Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                    ambientTemperature = event.values.firstOrNull()
                }

                it.type == Sensor.TYPE_TEMPERATURE || it.stringType.contains(temperatureSensorName) -> {
                    temperature = event.values.firstOrNull()
                }
                else -> {
                    // do nothing}
                }
            }
        }
    }
}