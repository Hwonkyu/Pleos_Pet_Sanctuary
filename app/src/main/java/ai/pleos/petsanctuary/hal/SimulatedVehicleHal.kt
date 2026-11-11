package ai.pleos.petsanctuary.hal

import ai.pleos.petsanctuary.model.InitialVehicleReading
import ai.pleos.petsanctuary.model.VehicleCapabilities
import ai.pleos.petsanctuary.model.VehicleGearState
import ai.pleos.playground.vehicle.constant.control.FanDirectionType

/**
 * In-memory Vehicle Hardware Simulator for Unit Tests and In-App Virtual Console testing.
 */
class SimulatedVehicleHal(
    private var capabilities: VehicleCapabilities = VehicleCapabilities(
        supportsAmbientLight = true,
        supportsAmbientColorControl = true,
        supportsAirCleaner = true,
        supportsRearReadingLightControl = true,
        supportsWindowLock = true,
        supportsWindowPosition = true,
        minTempCelsius = 17.0f,
        maxTempCelsius = 30.0f,
        minFanSpeed = 1,
        maxFanSpeed = 7,
        minBrightness = 0,
        maxBrightness = 100,
        isConnectedToPleosService = false
    )
) : PetVehicleHal {

    var currentGear: VehicleGearState = VehicleGearState.PARK
        private set
    var currentCabinTemp: Float = 24.5f
        private set
    var targetTemp: Float = 24.0f
        private set
    var fanSpeed: Int = 2
        private set
    var fanDirection: FanDirectionType = FanDirectionType.FACE_AND_FLOOR
        private set
    var isRecirculationOn: Boolean = false
        private set
    var isPowerOn: Boolean = true
        private set
    var isAcOn: Boolean = true
        private set
    var isAirCleanerOn: Boolean = false
        private set
    var isAmbientOn: Boolean = true
        private set
    var ambientColorRgb: Int = 0xFF90CAF9.toInt()
        private set
    var pleosColorIdx: Int = 1
        private set
    var ambientBrightness: Int = 65
        private set
    var isRearReadingLightOff: Boolean = false
        private set
    var isAutoColorByDriveMode: Boolean = true
        private set
    var isWindowLocked: Boolean = false
        private set
    var isWindowsClosed: Boolean = false
        private set
    var isRearSeatVentilationOn: Boolean = false
        private set
    var currentEvBatteryPercent: Float = 82.0f
        private set

    private var gearListener: ((VehicleGearState) -> Unit)? = null
    private var tempListener: ((Float) -> Unit)? = null
    private var capabilitiesListener: ((VehicleCapabilities) -> Unit)? = null
    private var baselineListener: ((InitialVehicleReading) -> Unit)? = null
    private var batteryListener: ((Float) -> Unit)? = null

    override fun initialize(
        onGearChanged: (VehicleGearState) -> Unit,
        onCabinTempChanged: (Float) -> Unit,
        onCapabilitiesLoaded: (VehicleCapabilities) -> Unit,
        onInitialBaselineRead: (InitialVehicleReading) -> Unit,
        onEvBatteryChanged: (Float) -> Unit
    ) {
        this.gearListener = onGearChanged
        this.tempListener = onCabinTempChanged
        this.capabilitiesListener = onCapabilitiesLoaded
        this.baselineListener = onInitialBaselineRead
        this.batteryListener = onEvBatteryChanged
        onCapabilitiesLoaded(capabilities)
        onGearChanged(currentGear)
        onCabinTempChanged(currentCabinTemp)
        onEvBatteryChanged(currentEvBatteryPercent)
        onInitialBaselineRead(
            InitialVehicleReading(
                targetTempCelsius = targetTemp,
                fanSpeedLevel = fanSpeed,
                fanDirection = fanDirection,
                isRecirculationOn = isRecirculationOn,
                isAcEnabled = isAcOn,
                isAirCleanerActive = isAirCleanerOn,
                isAmbientLightOn = isAmbientOn,
                ambientPleosColorIndex = pleosColorIdx,
                ambientBrightness = ambientBrightness,
                isWindowLockActive = isWindowLocked,
                isRearSeatVentilationOn = isRearSeatVentilationOn,
                evBatteryLevelPercent = currentEvBatteryPercent
            )
        )
    }

    override fun getCapabilities(): VehicleCapabilities = capabilities

    fun updateSimulatedCapabilities(newCapabilities: VehicleCapabilities) {
        this.capabilities = newCapabilities
        capabilitiesListener?.invoke(newCapabilities)
    }

    fun simulateGearShift(gear: VehicleGearState) {
        this.currentGear = gear
        gearListener?.invoke(gear)
    }

    fun simulateCabinTemperatureSensor(tempCelsius: Float) {
        this.currentCabinTemp = tempCelsius
        tempListener?.invoke(tempCelsius)
    }

    fun simulateEvBatteryLevel(percent: Float) {
        val clamped = percent.coerceIn(0.0f, 100.0f)
        this.currentEvBatteryPercent = clamped
        batteryListener?.invoke(clamped)
    }

    override fun setAmbientColorAutoByDriveMode(
        enabled: Boolean,
        onComplete: (Boolean) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        if (!capabilities.supportsAmbientLight) {
            onFailed(UnsupportedOperationException("Ambient light not supported on this trim"))
            return
        }
        isAutoColorByDriveMode = enabled
        onComplete(enabled)
    }

    override fun setAmbientLightSwitch(
        enabled: Boolean,
        onComplete: (Boolean) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        if (!capabilities.supportsAmbientLight) {
            onFailed(UnsupportedOperationException("Ambient light not supported on this trim"))
            return
        }
        isAmbientOn = enabled
        onComplete(enabled)
    }

    override fun setAmbientLightColor(
        colorRgb: Int,
        pleosColorIndex: Int,
        onComplete: (Int) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        if (!capabilities.supportsAmbientLight || !capabilities.supportsAmbientColorControl) {
            onFailed(UnsupportedOperationException("Ambient color control not supported on this trim"))
            return
        }
        this.ambientColorRgb = colorRgb
        this.pleosColorIdx = pleosColorIndex
        onComplete(colorRgb)
    }

    override fun setAmbientLightBrightness(
        brightness: Int,
        onComplete: (Int) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        if (!capabilities.supportsAmbientLight) {
            onFailed(UnsupportedOperationException("Ambient light not supported on this trim"))
            return
        }
        val clamped = brightness.coerceIn(capabilities.minBrightness, capabilities.maxBrightness)
        this.ambientBrightness = clamped
        onComplete(clamped)
    }

    override fun setRearReadingLightOff(
        turnOff: Boolean,
        onComplete: (Boolean) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        if (!capabilities.supportsRearReadingLightControl) {
            onFailed(UnsupportedOperationException("Reading light control not supported"))
            return
        }
        this.isRearReadingLightOff = turnOff
        onComplete(turnOff)
    }

    override fun setHvacPowerAndAc(
        powerOn: Boolean,
        acOn: Boolean,
        onComplete: (Boolean) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        this.isPowerOn = powerOn
        this.isAcOn = acOn
        onComplete(acOn)
    }

    override fun setHvacTargetTemperature(
        tempCelsius: Float,
        onComplete: (Float) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        val clamped = tempCelsius.coerceIn(capabilities.minTempCelsius, capabilities.maxTempCelsius)
        this.targetTemp = clamped
        onComplete(clamped)
    }

    override fun setHvacFanSpeed(
        speedLevel: Int,
        onComplete: (Int) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        val clamped = speedLevel.coerceIn(capabilities.minFanSpeed, capabilities.maxFanSpeed)
        this.fanSpeed = clamped
        onComplete(clamped)
    }

    override fun setHvacAirCleaner(
        enabled: Boolean,
        onComplete: (Boolean) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        if (!capabilities.supportsAirCleaner) {
            onFailed(UnsupportedOperationException("Air cleaner not supported on this trim"))
            return
        }
        this.isAirCleanerOn = enabled
        onComplete(enabled)
    }

    override fun setHvacFanDirection(
        direction: FanDirectionType,
        onComplete: (FanDirectionType) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        this.fanDirection = direction
        onComplete(direction)
    }

    override fun setHvacRecirculation(
        enabled: Boolean,
        onComplete: (Boolean) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        this.isRecirculationOn = enabled
        onComplete(enabled)
    }

    override fun setWindowLock(
        locked: Boolean,
        onComplete: (Boolean) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        if (!capabilities.supportsWindowLock) {
            onFailed(UnsupportedOperationException("Window lock not supported"))
            return
        }
        this.isWindowLocked = locked
        onComplete(locked)
    }

    override fun setWindowsClosed(
        closed: Boolean,
        onComplete: (Boolean) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        if (!capabilities.supportsWindowPosition) {
            onFailed(UnsupportedOperationException("Window position not supported"))
            return
        }
        this.isWindowsClosed = closed
        onComplete(closed)
    }

    override fun setRearSeatVentilation(
        enabled: Boolean,
        onComplete: (Boolean) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        if (!capabilities.supportsSeatVentilation) {
            onFailed(UnsupportedOperationException("Seat ventilation not supported"))
            return
        }
        this.isRearSeatVentilationOn = enabled
        onComplete(enabled)
    }

    override fun release() {
        gearListener = null
        tempListener = null
        capabilitiesListener = null
        baselineListener = null
        batteryListener = null
    }
}
