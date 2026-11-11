package ai.pleos.petsanctuary.hal

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import ai.pleos.petsanctuary.model.InitialVehicleReading
import ai.pleos.petsanctuary.model.VehicleCapabilities
import ai.pleos.petsanctuary.model.VehicleGearState
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.EvBattery
import ai.pleos.playground.vehicle.api.Gear
import ai.pleos.playground.vehicle.api.HVAC
import ai.pleos.playground.vehicle.api.Light
import ai.pleos.playground.vehicle.api.Seat
import ai.pleos.playground.vehicle.api.Window
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.FanDirectionType
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.EvBatteryLevelListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureValueListener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production Hardware Abstraction Layer integrating the official Hyundai Motor Group
 * Pleos Vehicle SDK (`ai.pleos.playground:Vehicle:2.3.2`).
 *
 * Dynamically discovers the vehicle's VHAL-supported [SeatArea] and [WindowArea] zones via `check*Capabilities`
 * so every hardware setter (Light, HVAC, Window, Gear) succeeds without `IllegalArgumentException`.
 */
@SuppressLint("MissingPermission")
class PleosSdkVehicleHal(
    context: Context,
    val fallbackSimulator: SimulatedVehicleHal = SimulatedVehicleHal()
) : PetVehicleHal {

    companion object {
        private const val TAG = "PleosSdkVehicleHal"
    }

    private val appContext = context.applicationContext
    private var vehicle: Vehicle? = null
    private var lightApi: Light? = null
    private var hvacApi: HVAC? = null
    private var gearApi: Gear? = null
    private var windowApi: Window? = null
    private var seatApi: Seat? = null
    private var evBatteryApi: EvBattery? = null

    private val driverSeat = SeatArea(SeatValue.ROW_1_LEFT)     // areaId = 1
    private val passengerSeat = SeatArea(SeatValue.ROW_1_RIGHT) // areaId = 4
    private val rearPassengerSeats = SeatArea(                  // 2nd row left & right
        SeatValue.ROW_2_LEFT,
        SeatValue.ROW_2_RIGHT
    )
    private val fullFiveSeatCabin = SeatArea(                   // areaId = 117 (1+4+16+32+64)
        SeatValue.ROW_1_LEFT,
        SeatValue.ROW_1_RIGHT,
        SeatValue.ROW_2_LEFT,
        SeatValue.ROW_2_CENTER,
        SeatValue.ROW_2_RIGHT
    )

    private val allSideWindows = WindowArea(
        WindowValue.ROW_1_LEFT,
        WindowValue.ROW_1_RIGHT,
        WindowValue.ROW_2_LEFT,
        WindowValue.ROW_2_RIGHT
    )

    // Dynamically populated from Pleos VehicleService capability queries
    @Volatile
    private var hvacPowerAreas: List<SeatArea> = listOf(fullFiveSeatCabin, driverSeat, passengerSeat)

    @Volatile
    private var hvacTempAreas: List<SeatArea> = listOf(driverSeat, passengerSeat)

    @Volatile
    private var hvacFanAreas: List<SeatArea> = listOf(fullFiveSeatCabin, driverSeat, passengerSeat)

    @Volatile
    private var hvacAcAreas: List<SeatArea> = listOf(fullFiveSeatCabin, driverSeat, passengerSeat)

    @Volatile
    private var hvacAirCleanerAreas: List<SeatArea> = emptyList()

    @Volatile
    private var hvacRecirculationAreas: List<SeatArea> = listOf(fullFiveSeatCabin, driverSeat)

    @Volatile
    private var hvacFanDirectionAreas: List<SeatArea> = listOf(fullFiveSeatCabin, driverSeat)

    @Volatile
    private var ambientBrightnessAreas: List<SeatArea> = listOf(driverSeat, passengerSeat, fullFiveSeatCabin)

    @Volatile
    private var ambientColorAreas: List<SeatArea> = listOf(driverSeat, passengerSeat, fullFiveSeatCabin)

    @Volatile
    private var ambientSwitchAreas: List<SeatArea> = listOf(driverSeat, passengerSeat, fullFiveSeatCabin)

    @Volatile
    private var readingLightAreas: List<SeatArea> = listOf(driverSeat, passengerSeat)

    @Volatile
    private var windowLockAreas: List<WindowArea> = listOf(allSideWindows)

    @Volatile
    private var windowPositionAreas: List<WindowArea> = listOf(allSideWindows)

    @Volatile
    private var seatVentilationAreas: List<SeatArea> = listOf(rearPassengerSeats, driverSeat, passengerSeat)

    @Volatile
    private var seatVentilationMaxLevel: Int = 3

    @Volatile
    private var supportsAutoColorByDriveMode: Boolean = false

    private var gearSelectionListener: GearSelectionListener? = null
    private var hvacTempListener: HvacTemperatureValueListener? = null
    private var evBatteryListener: EvBatteryLevelListener? = null

    override fun initialize(
        onGearChanged: (VehicleGearState) -> Unit,
        onCabinTempChanged: (Float) -> Unit,
        onCapabilitiesLoaded: (VehicleCapabilities) -> Unit,
        onInitialBaselineRead: (InitialVehicleReading) -> Unit,
        onEvBatteryChanged: (Float) -> Unit
    ) {
        fallbackSimulator.initialize(
            onGearChanged,
            onCabinTempChanged,
            onCapabilitiesLoaded,
            onInitialBaselineRead,
            onEvBatteryChanged
        )

        try {
            val v = Vehicle(appContext)
            v.initialize()
            vehicle = v
            lightApi = v.getLight()
            hvacApi = v.getHVAC()
            gearApi = v.getGear()
            windowApi = v.getWindow()
            seatApi = v.getSeat()
            evBatteryApi = v.getEvBattery()

            Log.i(TAG, "Pleos Vehicle SDK (v2.3.2) initialized (Light, HVAC, Gear, Window, Seat, EvBattery). Discovering VHAL capabilities...")
            queryVehicleCapabilities(onCapabilitiesLoaded, onInitialBaselineRead)
            registerRealListeners(onGearChanged, onCabinTempChanged, onEvBatteryChanged)
        } catch (e: Exception) {
            Log.w(TAG, "Pleos Vehicle Service unavailable; using Virtual Console Simulator: ${e.message}")
        }
    }

    private fun queryVehicleCapabilities(
        onCapabilitiesLoaded: (VehicleCapabilities) -> Unit,
        onInitialBaselineRead: (InitialVehicleReading) -> Unit
    ) {
        val light = lightApi ?: return
        val hvac = hvacApi ?: return
        val window = windowApi

        light.checkAmbientColorAutoByDriveModeEnabledCapabilities(
            { config ->
                supportsAutoColorByDriveMode =
                    config.accessType == AccessType.WRITE || config.accessType == AccessType.READ_WRITE
            },
            { _ -> supportsAutoColorByDriveMode = false }
        )

        light.checkAmbientLightBrightnessCapabilities(
            { _, boundsMap ->
                if (boundsMap.isNotEmpty()) {
                    ambientBrightnessAreas = boundsMap.keys.toList()
                }
                val firstBound = boundsMap.values.firstOrNull()
                val updated = fallbackSimulator.getCapabilities().copy(
                    supportsAmbientLight = boundsMap.isNotEmpty(),
                    minBrightness = firstBound?.minValue ?: 0,
                    maxBrightness = firstBound?.maxValue ?: 100,
                    isConnectedToPleosService = true
                )
                fallbackSimulator.updateSimulatedCapabilities(updated)
                onCapabilitiesLoaded(updated)

                ambientBrightnessAreas.firstOrNull()?.let { firstArea ->
                    light.getAmbientLightBrightness(
                        firstArea,
                        { br ->
                            if (br != null) {
                                Log.i(TAG, "VHAL initial AmbientBrightness: $br%")
                                onInitialBaselineRead(InitialVehicleReading(ambientBrightness = br))
                            }
                        },
                        { e -> Log.d(TAG, "getAmbientLightBrightness fallback: ${e.message}") }
                    )
                }
            },
            { e -> Log.d(TAG, "checkAmbientLightBrightnessCapabilities: ${e.message}") }
        )

        light.checkAmbientLightBrightnessColorCapabilities(
            { _, areas ->
                if (areas.isNotEmpty()) {
                    ambientColorAreas = areas
                    areas.firstOrNull()?.let { firstArea ->
                        light.getAmbientLightBrightnessColor(
                            firstArea,
                            { colorIdx ->
                                if (colorIdx != null) {
                                    Log.i(TAG, "VHAL initial AmbientColorIndex: $colorIdx")
                                    onInitialBaselineRead(InitialVehicleReading(ambientPleosColorIndex = colorIdx))
                                }
                            },
                            { e -> Log.d(TAG, "getAmbientLightBrightnessColor fallback: ${e.message}") }
                        )
                    }
                }
            },
            { e -> Log.d(TAG, "checkAmbientLightBrightnessColorCapabilities: ${e.message}") }
        )

        light.checkAmbientLightSwitchCapabilities(
            { _, map ->
                if (map.isNotEmpty()) {
                    ambientSwitchAreas = map.keys.toList()
                    ambientSwitchAreas.firstOrNull()?.let { firstArea ->
                        light.getAmbientLightSwitch(
                            firstArea,
                            { sw ->
                                if (sw != null) {
                                    onInitialBaselineRead(InitialVehicleReading(isAmbientLightOn = (sw == LightSwitch.ON)))
                                }
                            },
                            { e -> Log.d(TAG, "getAmbientLightSwitch fallback: ${e.message}") }
                        )
                    }
                }
            },
            { e -> Log.d(TAG, "checkAmbientLightSwitchCapabilities: ${e.message}") }
        )

        light.checkReadingLightSwitchCapabilities(
            { _, map ->
                if (map.isNotEmpty()) readingLightAreas = map.keys.toList()
            },
            { e -> Log.d(TAG, "checkReadingLightSwitchCapabilities: ${e.message}") }
        )

        hvac.checkHvacPowerCapabilities(
            { _, areas -> if (areas.isNotEmpty()) hvacPowerAreas = areas },
            { e -> Log.d(TAG, "checkHvacPowerCapabilities: ${e.message}") }
        )

        hvac.checkHvacAcCapabilities(
            { _, areas ->
                if (areas.isNotEmpty()) {
                    hvacAcAreas = areas
                    areas.firstOrNull()?.let { firstArea ->
                        hvac.getHvacAcState(
                            firstArea,
                            { ac ->
                                if (ac != null) {
                                    onInitialBaselineRead(InitialVehicleReading(isAcEnabled = ac))
                                }
                            },
                            { e -> Log.d(TAG, "getHvacAcState fallback: ${e.message}") }
                        )
                    }
                }
            },
            { e -> Log.d(TAG, "checkHvacAcCapabilities: ${e.message}") }
        )

        hvac.checkHvacFanSpeedCapabilities(
            { _, boundsMap ->
                if (boundsMap.isNotEmpty()) hvacFanAreas = boundsMap.keys.toList()
                val firstBound = boundsMap.values.firstOrNull()
                if (firstBound != null) {
                    val updated = fallbackSimulator.getCapabilities().copy(
                        minFanSpeed = firstBound.minValue,
                        maxFanSpeed = firstBound.maxValue,
                        isConnectedToPleosService = true
                    )
                    fallbackSimulator.updateSimulatedCapabilities(updated)
                    onCapabilitiesLoaded(updated)
                }
            },
            { e -> Log.d(TAG, "checkHvacFanSpeedCapabilities: ${e.message}") }
        )

        hvac.checkHvacTemperatureCapabilities(
            { _, boundsMap, _ ->
                if (boundsMap.isNotEmpty()) hvacTempAreas = boundsMap.keys.toList()
                val firstBound = boundsMap.values.firstOrNull()
                if (firstBound != null) {
                    val updated = fallbackSimulator.getCapabilities().copy(
                        minTempCelsius = firstBound.minValue,
                        maxTempCelsius = firstBound.maxValue,
                        isConnectedToPleosService = true
                    )
                    fallbackSimulator.updateSimulatedCapabilities(updated)
                    onCapabilitiesLoaded(updated)
                }
                hvacTempAreas.firstOrNull()?.let { firstArea ->
                    hvac.getHvacTemperature(
                        firstArea,
                        TemperatureUnit.CELSIUS,
                        { targetC ->
                            if (targetC != null) {
                                Log.i(TAG, "VHAL initial TargetTemp: ${targetC}C")
                                onInitialBaselineRead(InitialVehicleReading(targetTempCelsius = targetC))
                            }
                        },
                        { e -> Log.d(TAG, "getHvacTemperature fallback: ${e.message}") }
                    )
                }
            },
            { e -> Log.d(TAG, "checkHvacTemperatureCapabilities: ${e.message}") }
        )

        hvac.checkHvacAirCleanerCapabilities(
            { _, seatAreas ->
                hvacAirCleanerAreas = seatAreas
                val updated = fallbackSimulator.getCapabilities().copy(
                    supportsAirCleaner = seatAreas.isNotEmpty(),
                    isConnectedToPleosService = true
                )
                fallbackSimulator.updateSimulatedCapabilities(updated)
                onCapabilitiesLoaded(updated)
            },
            { e -> Log.d(TAG, "checkHvacAirCleanerCapabilities: ${e.message}") }
        )

        hvac.checkHvacRecirculationCapabilities(
            { _, seatAreas ->
                if (seatAreas.isNotEmpty()) {
                    hvacRecirculationAreas = seatAreas
                    seatAreas.firstOrNull()?.let { firstArea ->
                        hvac.getHvacRecirculationState(
                            firstArea,
                            { rec ->
                                if (rec != null) {
                                    onInitialBaselineRead(InitialVehicleReading(isRecirculationOn = rec))
                                }
                            },
                            { e -> Log.d(TAG, "getHvacRecirculationState fallback: ${e.message}") }
                        )
                    }
                }
            },
            { e -> Log.d(TAG, "checkHvacRecirculationCapabilities: ${e.message}") }
        )

        hvac.checkHvacFanDirectionCapabilities(
            { _, map -> if (map.isNotEmpty()) hvacFanDirectionAreas = map.keys.toList() },
            { e -> Log.d(TAG, "checkHvacFanDirectionCapabilities: ${e.message}") }
        )

        window?.checkWindowLockCapabilities(
            { _, areas ->
                if (areas.isNotEmpty()) windowLockAreas = areas
                val updated = fallbackSimulator.getCapabilities().copy(
                    supportsWindowLock = areas.isNotEmpty(),
                    isConnectedToPleosService = true
                )
                fallbackSimulator.updateSimulatedCapabilities(updated)
                onCapabilitiesLoaded(updated)

                windowLockAreas.firstOrNull()?.let { firstArea ->
                    window.getCurrentWindowLockState(
                        firstArea,
                        { locked ->
                            if (locked != null) {
                                Log.i(TAG, "VHAL initial WindowLock: $locked")
                                onInitialBaselineRead(InitialVehicleReading(isWindowLockActive = locked))
                            }
                        },
                        { e -> Log.d(TAG, "getCurrentWindowLockState fallback: ${e.message}") }
                    )
                }
            },
            { e -> Log.d(TAG, "checkWindowLockCapabilities: ${e.message}") }
        )

        window?.checkWindowPositionCapabilities(
            { _, map ->
                if (map.isNotEmpty()) windowPositionAreas = map.keys.toList()
                val updated = fallbackSimulator.getCapabilities().copy(
                    supportsWindowPosition = map.isNotEmpty(),
                    isConnectedToPleosService = true
                )
                fallbackSimulator.updateSimulatedCapabilities(updated)
                onCapabilitiesLoaded(updated)
            },
            { e -> Log.d(TAG, "checkWindowPositionCapabilities: ${e.message}") }
        )

        val seat = seatApi
        seat?.checkSeatVentilationCapabilities(
            { _, boundsMap ->
                if (boundsMap.isNotEmpty()) {
                    seatVentilationAreas = boundsMap.keys.toList()
                    val firstMax = boundsMap.values.firstOrNull()?.maxValue ?: 3
                    if (firstMax > 0) seatVentilationMaxLevel = firstMax
                }
                val updated = fallbackSimulator.getCapabilities().copy(
                    supportsSeatVentilation = boundsMap.isNotEmpty(),
                    isConnectedToPleosService = true
                )
                fallbackSimulator.updateSimulatedCapabilities(updated)
                onCapabilitiesLoaded(updated)

                seatVentilationAreas.firstOrNull()?.let { firstArea ->
                    seat.getHvacSeatVentilationState(
                        firstArea,
                        { ventLvl ->
                            if (ventLvl != null) {
                                Log.i(TAG, "VHAL initial SeatVentilation: $ventLvl")
                                onInitialBaselineRead(InitialVehicleReading(isRearSeatVentilationOn = (ventLvl > 0)))
                            }
                        },
                        { e -> Log.d(TAG, "getHvacSeatVentilationState fallback: ${e.message}") }
                    )
                }
            },
            { e -> Log.d(TAG, "checkSeatVentilationCapabilities: ${e.message}") }
        )
    }

    private fun registerRealListeners(
        onGearChanged: (VehicleGearState) -> Unit,
        onCabinTempChanged: (Float) -> Unit,
        onEvBatteryChanged: (Float) -> Unit
    ) {
        val gear = gearApi
        if (gear != null) {
            gear.getCurrentGearSelectionState(
                { vg ->
                    if (vg != null) {
                        val mapped = mapPleosGear(vg)
                        Log.i(TAG, "VHAL initial Gear: $vg -> $mapped")
                        onGearChanged(mapped)
                    }
                },
                { e -> Log.d(TAG, "getCurrentGearSelectionState fallback: ${e.message}") }
            )
            val listener = object : GearSelectionListener {
                override fun onSelected(gear: VehicleGear?) {
                    if (gear != null) {
                        val mapped = mapPleosGear(gear)
                        Log.i(TAG, "VHAL GearSelectionListener event: $gear -> $mapped")
                        fallbackSimulator.simulateGearShift(mapped)
                    }
                }

                override fun onFailed(e: Exception) {
                    Log.d(TAG, "GearSelectionListener onFailed: ${e.message}")
                }
            }
            gearSelectionListener = listener
            try {
                gear.registerGearSelection(listener)
            } catch (e: Exception) {
                Log.d(TAG, "registerGearSelection fallback: ${e.message}")
            }
        }

        val hvac = hvacApi
        if (hvac != null) {
            hvac.getHvacTemperatureCurrent(
                driverSeat,
                TemperatureUnit.CELSIUS,
                { tempC ->
                    if (tempC != null) {
                        Log.i(TAG, "VHAL initial Cabin Temperature (driverSeat): ${tempC}C")
                        fallbackSimulator.simulateCabinTemperatureSensor(tempC)
                    }
                },
                { e -> Log.d(TAG, "getHvacTemperatureCurrent fallback: ${e.message}") }
            )
            val tempListener = object : HvacTemperatureValueListener {
                override fun onHvacTemperatureUpdated(celsius: Float?, fahrenheit: Float?) {
                    if (celsius != null) {
                        fallbackSimulator.simulateCabinTemperatureSensor(celsius)
                    }
                }

                override fun onFailed(e: Exception) {
                    Log.d(TAG, "HvacTemperatureValueListener onFailed: ${e.message}")
                }
            }
            hvacTempListener = tempListener
            try {
                hvac.registerHvacTemperatureCurrent(driverSeat, tempListener)
            } catch (e: Exception) {
                Log.d(TAG, "registerHvacTemperatureCurrent fallback: ${e.message}")
            }
        }

        val evBattery = evBatteryApi
        if (evBattery != null) {
            evBattery.getEvBatteryLevel(
                { levelPct ->
                    if (levelPct != null && levelPct in 0.0f..100.0f) {
                        Log.i(TAG, "VHAL initial EvBatteryLevel: ${levelPct}%")
                        fallbackSimulator.simulateEvBatteryLevel(levelPct)
                        onEvBatteryChanged(levelPct)
                    }
                },
                { e -> Log.d(TAG, "getEvBatteryLevel fallback: ${e.message}") }
            )
            val batListener = object : EvBatteryLevelListener {
                override fun onEvBatteryLevelUpdated(level: Float?) {
                    if (level != null && level in 0.0f..100.0f) {
                        fallbackSimulator.simulateEvBatteryLevel(level)
                        onEvBatteryChanged(level)
                    }
                }

                override fun onFailed(e: Exception) {
                    Log.d(TAG, "EvBatteryLevelListener onFailed: ${e.message}")
                }
            }
            evBatteryListener = batListener
            try {
                evBattery.registerEvBatteryLevel(batListener)
            } catch (e: Exception) {
                Log.d(TAG, "registerEvBatteryLevel fallback: ${e.message}")
            }
        }
    }

    private fun mapPleosGear(gear: VehicleGear): VehicleGearState {
        return when (gear) {
            VehicleGear.GEAR_PARK -> VehicleGearState.PARK
            VehicleGear.GEAR_REVERSE -> VehicleGearState.REVERSE
            VehicleGear.GEAR_NEUTRAL -> VehicleGearState.NEUTRAL
            else -> VehicleGearState.DRIVE
        }
    }

    override fun getCapabilities(): VehicleCapabilities = fallbackSimulator.getCapabilities()

    override fun setAmbientColorAutoByDriveMode(
        enabled: Boolean,
        onComplete: (Boolean) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        fallbackSimulator.setAmbientColorAutoByDriveMode(enabled, onComplete, onFailed)
        if (!supportsAutoColorByDriveMode) {
            Log.d(TAG, "Pleos SDK setAmbientColorAutoByDriveModeEnabled skipped (unsupported on VHAL trim)")
            return
        }
        lightApi?.setAmbientColorAutoByDriveModeEnabled(
            enabled,
            { res -> Log.i(TAG, "Pleos SDK setAmbientColorAutoByDriveModeEnabled($res) OK") },
            { err ->
                Log.d(TAG, "Pleos SDK setAmbientColorAutoByDriveModeEnabled: ${err.message}")
                onFailed(err)
            }
        )
    }

    override fun setAmbientLightSwitch(
        enabled: Boolean,
        onComplete: (Boolean) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        fallbackSimulator.setAmbientLightSwitch(enabled, onComplete, onFailed)
        val switchState = if (enabled) LightSwitch.ON else LightSwitch.OFF
        val reportedFail = AtomicBoolean(false)
        ambientSwitchAreas.forEach { area ->
            lightApi?.setAmbientLightSwitch(
                area,
                switchState,
                { _, sw -> Log.i(TAG, "Pleos SDK setAmbientLightSwitch($sw) OK") },
                { err ->
                    Log.d(TAG, "Pleos SDK setAmbientLightSwitch fallback: ${err.message}")
                    if (reportedFail.compareAndSet(false, true)) onFailed(err)
                }
            )
        }
    }

    override fun setAmbientLightColor(
        colorRgb: Int,
        pleosColorIndex: Int,
        onComplete: (Int) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        fallbackSimulator.setAmbientLightColor(colorRgb, pleosColorIndex, onComplete, onFailed)
        val reportedFail = AtomicBoolean(false)
        ambientColorAreas.forEach { area ->
            lightApi?.setAmbientLightBrightnessColor(
                area,
                pleosColorIndex,
                { _, idx -> Log.i(TAG, "Pleos SDK setAmbientLightBrightnessColor($idx) OK") },
                { err ->
                    Log.d(TAG, "Pleos SDK setAmbientLightBrightnessColor fallback: ${err.message}")
                    if (reportedFail.compareAndSet(false, true)) onFailed(err)
                }
            )
        }
    }

    override fun setAmbientLightBrightness(
        brightness: Int,
        onComplete: (Int) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        fallbackSimulator.setAmbientLightBrightness(brightness, onComplete, onFailed)
        val clamped = brightness.coerceIn(getCapabilities().minBrightness, getCapabilities().maxBrightness)
        val reportedFail = AtomicBoolean(false)
        ambientBrightnessAreas.forEach { area ->
            lightApi?.setAmbientLightBrightness(
                area,
                clamped,
                { _, br -> Log.i(TAG, "Pleos SDK setAmbientLightBrightness($br) OK") },
                { err ->
                    Log.d(TAG, "Pleos SDK setAmbientLightBrightness fallback: ${err.message}")
                    if (reportedFail.compareAndSet(false, true)) onFailed(err)
                }
            )
        }
    }

    override fun setRearReadingLightOff(
        turnOff: Boolean,
        onComplete: (Boolean) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        fallbackSimulator.setRearReadingLightOff(turnOff, onComplete, onFailed)
        val switchVal = if (turnOff) LightSwitch.OFF else LightSwitch.ON
        val reportedFail = AtomicBoolean(false)
        readingLightAreas.forEach { area ->
            lightApi?.setReadingLightSwitch(
                area,
                switchVal,
                { sw -> Log.i(TAG, "Pleos SDK setReadingLightSwitch($sw) OK") },
                { err ->
                    Log.d(TAG, "Pleos SDK setReadingLightSwitch fallback: ${err.message}")
                    if (reportedFail.compareAndSet(false, true)) onFailed(err)
                }
            )
        }
    }

    override fun setHvacPowerAndAc(
        powerOn: Boolean,
        acOn: Boolean,
        onComplete: (Boolean) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        fallbackSimulator.setHvacPowerAndAc(powerOn, acOn, onComplete, onFailed)
        hvacPowerAreas.forEach { area ->
            hvacApi?.setHvacPower(
                area,
                powerOn,
                { _, p -> Log.i(TAG, "Pleos SDK setHvacPower($p) OK") },
                { err -> Log.d(TAG, "Pleos SDK setHvacPower fallback: ${err.message}") }
            )
        }
        hvacAcAreas.forEach { area ->
            hvacApi?.setHvacAc(
                area,
                acOn,
                { _, ac -> Log.i(TAG, "Pleos SDK setHvacAc($ac) OK") },
                { err -> Log.d(TAG, "Pleos SDK setHvacAc fallback: ${err.message}") }
            )
        }
    }

    override fun setHvacTargetTemperature(
        tempCelsius: Float,
        onComplete: (Float) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        fallbackSimulator.setHvacTargetTemperature(tempCelsius, onComplete, onFailed)
        val clamped = tempCelsius.coerceIn(getCapabilities().minTempCelsius, getCapabilities().maxTempCelsius)
        val reportedFail = AtomicBoolean(false)
        hvacTempAreas.forEach { area ->
            hvacApi?.setHvacTemperature(
                area,
                clamped,
                TemperatureUnit.CELSIUS,
                { _, t -> Log.i(TAG, "Pleos SDK setHvacTemperature($t C) OK") },
                { err ->
                    Log.d(TAG, "Pleos SDK setHvacTemperature fallback: ${err.message}")
                    if (reportedFail.compareAndSet(false, true)) onFailed(err)
                }
            )
        }
    }

    override fun setHvacFanSpeed(
        speedLevel: Int,
        onComplete: (Int) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        fallbackSimulator.setHvacFanSpeed(speedLevel, onComplete, onFailed)
        val clamped = speedLevel.coerceIn(getCapabilities().minFanSpeed, getCapabilities().maxFanSpeed)
        val reportedFail = AtomicBoolean(false)
        hvacFanAreas.forEach { area ->
            hvacApi?.setHvacFanSpeed(
                area,
                clamped,
                { _, s -> Log.i(TAG, "Pleos SDK setHvacFanSpeed($s) OK") },
                { err ->
                    Log.d(TAG, "Pleos SDK setHvacFanSpeed fallback: ${err.message}")
                    if (reportedFail.compareAndSet(false, true)) onFailed(err)
                }
            )
        }
    }

    override fun setHvacAirCleaner(
        enabled: Boolean,
        onComplete: (Boolean) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        fallbackSimulator.setHvacAirCleaner(enabled, onComplete, onFailed)
        val reportedFail = AtomicBoolean(false)
        hvacAirCleanerAreas.forEach { area ->
            hvacApi?.setHvacAirCleaner(
                area,
                enabled,
                { _, en -> Log.i(TAG, "Pleos SDK setHvacAirCleaner($en) OK") },
                { err ->
                    Log.d(TAG, "Pleos SDK setHvacAirCleaner fallback: ${err.message}")
                    if (reportedFail.compareAndSet(false, true)) onFailed(err)
                }
            )
        }
    }

    override fun setHvacFanDirection(
        direction: FanDirectionType,
        onComplete: (FanDirectionType) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        fallbackSimulator.setHvacFanDirection(direction, onComplete, onFailed)
        val reportedFail = AtomicBoolean(false)
        hvacFanDirectionAreas.forEach { area ->
            hvacApi?.setHvacFanDirection(
                area,
                direction,
                { _, dir -> Log.i(TAG, "Pleos SDK setHvacFanDirection($dir) OK") },
                { err ->
                    Log.d(TAG, "Pleos SDK setHvacFanDirection fallback: ${err.message}")
                    if (reportedFail.compareAndSet(false, true)) onFailed(err)
                }
            )
        }
    }

    override fun setHvacRecirculation(
        enabled: Boolean,
        onComplete: (Boolean) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        fallbackSimulator.setHvacRecirculation(enabled, onComplete, onFailed)
        val reportedFail = AtomicBoolean(false)
        hvacRecirculationAreas.forEach { area ->
            hvacApi?.setHvacRecirculation(
                area,
                enabled,
                { _, rc -> Log.i(TAG, "Pleos SDK setHvacRecirculation($rc) OK") },
                { err ->
                    Log.d(TAG, "Pleos SDK setHvacRecirculation fallback: ${err.message}")
                    if (reportedFail.compareAndSet(false, true)) onFailed(err)
                }
            )
        }
    }

    override fun setWindowLock(
        locked: Boolean,
        onComplete: (Boolean) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        fallbackSimulator.setWindowLock(locked, onComplete, onFailed)
        val reportedFail = AtomicBoolean(false)
        windowLockAreas.forEach { area ->
            windowApi?.setWindowLock(
                area,
                locked,
                { _, lk -> Log.i(TAG, "Pleos SDK setWindowLock($lk) OK") },
                { err ->
                    Log.d(TAG, "Pleos SDK setWindowLock fallback: ${err.message}")
                    if (reportedFail.compareAndSet(false, true)) onFailed(err)
                }
            )
        }
    }

    override fun setWindowsClosed(
        closed: Boolean,
        onComplete: (Boolean) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        fallbackSimulator.setWindowsClosed(closed, onComplete, onFailed)
        val pos = if (closed) 0 else 100 // 0 = 100% closed
        val reportedFail = AtomicBoolean(false)
        windowPositionAreas.forEach { area ->
            windowApi?.setWindowPosition(
                area,
                pos,
                { _, p -> Log.i(TAG, "Pleos SDK setWindowPosition($p) OK") },
                { err ->
                    Log.d(TAG, "Pleos SDK setWindowPosition fallback: ${err.message}")
                    if (reportedFail.compareAndSet(false, true)) onFailed(err)
                }
            )
        }
    }

    override fun setRearSeatVentilation(
        enabled: Boolean,
        onComplete: (Boolean) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        fallbackSimulator.setRearSeatVentilation(enabled, onComplete, onFailed)
        val level = if (enabled) seatVentilationMaxLevel.coerceAtLeast(1) else 0
        val reportedFail = AtomicBoolean(false)
        seatVentilationAreas.forEach { area ->
            seatApi?.setHvacSeatVentilation(
                area,
                level,
                { _, lvl -> Log.i(TAG, "Pleos SDK setHvacSeatVentilation($lvl) OK") },
                { err ->
                    Log.d(TAG, "Pleos SDK setHvacSeatVentilation fallback: ${err.message}")
                    if (reportedFail.compareAndSet(false, true)) onFailed(err)
                }
            )
        }
    }

    override fun release() {
        try {
            gearSelectionListener?.let { gearApi?.unregisterGearSelection(it) }
            hvacTempListener?.let { hvacApi?.unregisterHvacTemperatureCurrent(driverSeat, it) }
            evBatteryListener?.let { evBatteryApi?.unregisterEvBatteryLevel(it) }
            vehicle?.release()
        } catch (e: Exception) {
            Log.d(TAG, "release warning: ${e.message}")
        }
        fallbackSimulator.release()
    }
}
