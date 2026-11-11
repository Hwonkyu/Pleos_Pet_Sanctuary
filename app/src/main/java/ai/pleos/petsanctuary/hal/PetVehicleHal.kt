package ai.pleos.petsanctuary.hal

import ai.pleos.petsanctuary.model.InitialVehicleReading
import ai.pleos.petsanctuary.model.VehicleCapabilities
import ai.pleos.petsanctuary.model.VehicleGearState
import ai.pleos.playground.vehicle.constant.control.FanDirectionType

interface PetVehicleHal {
    fun initialize(
        onGearChanged: (VehicleGearState) -> Unit,
        onCabinTempChanged: (Float) -> Unit,
        onCapabilitiesLoaded: (VehicleCapabilities) -> Unit,
        onInitialBaselineRead: (InitialVehicleReading) -> Unit = {},
        onEvBatteryChanged: (Float) -> Unit = {}
    )

    fun getCapabilities(): VehicleCapabilities

    // Light APIs
    fun setAmbientColorAutoByDriveMode(
        enabled: Boolean,
        onComplete: (Boolean) -> Unit = {},
        onFailed: (Exception) -> Unit = {}
    )

    fun setAmbientLightSwitch(
        enabled: Boolean,
        onComplete: (Boolean) -> Unit = {},
        onFailed: (Exception) -> Unit = {}
    )

    fun setAmbientLightColor(
        colorRgb: Int,
        pleosColorIndex: Int,
        onComplete: (Int) -> Unit = {},
        onFailed: (Exception) -> Unit = {}
    )

    fun setAmbientLightBrightness(
        brightness: Int,
        onComplete: (Int) -> Unit = {},
        onFailed: (Exception) -> Unit = {}
    )

    fun setRearReadingLightOff(
        turnOff: Boolean,
        onComplete: (Boolean) -> Unit = {},
        onFailed: (Exception) -> Unit = {}
    )

    // HVAC APIs
    fun setHvacPowerAndAc(
        powerOn: Boolean,
        acOn: Boolean,
        onComplete: (Boolean) -> Unit = {},
        onFailed: (Exception) -> Unit = {}
    )

    fun setHvacTargetTemperature(
        tempCelsius: Float,
        onComplete: (Float) -> Unit = {},
        onFailed: (Exception) -> Unit = {}
    )

    fun setHvacFanSpeed(
        speedLevel: Int,
        onComplete: (Int) -> Unit = {},
        onFailed: (Exception) -> Unit = {}
    )

    fun setHvacAirCleaner(
        enabled: Boolean,
        onComplete: (Boolean) -> Unit = {},
        onFailed: (Exception) -> Unit = {}
    )

    fun setHvacFanDirection(
        direction: FanDirectionType,
        onComplete: (FanDirectionType) -> Unit = {},
        onFailed: (Exception) -> Unit = {}
    )

    fun setHvacRecirculation(
        enabled: Boolean,
        onComplete: (Boolean) -> Unit = {},
        onFailed: (Exception) -> Unit = {}
    )

    // Window APIs
    fun setWindowLock(
        locked: Boolean,
        onComplete: (Boolean) -> Unit = {},
        onFailed: (Exception) -> Unit = {}
    )

    fun setWindowsClosed(
        closed: Boolean,
        onComplete: (Boolean) -> Unit = {},
        onFailed: (Exception) -> Unit = {}
    )

    // Seat APIs (2nd-row pet seat ventilation)
    fun setRearSeatVentilation(
        enabled: Boolean,
        onComplete: (Boolean) -> Unit = {},
        onFailed: (Exception) -> Unit = {}
    )

    fun release()
}
