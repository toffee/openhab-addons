/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.io.homekit.internal.accessories;

import static org.openhab.io.homekit.internal.HomekitCharacteristicType.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.measure.Quantity;
import javax.measure.Unit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.items.GenericItem;
import org.openhab.core.items.Item;
import org.openhab.core.library.items.ColorItem;
import org.openhab.core.library.items.DimmerItem;
import org.openhab.core.library.items.NumberItem;
import org.openhab.core.library.items.RollershutterItem;
import org.openhab.core.library.items.StringItem;
import org.openhab.core.library.items.SwitchItem;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.HSBType;
import org.openhab.core.library.types.IncreaseDecreaseType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StopMoveType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.ImperialUnits;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.openhab.io.homekit.Homekit;
import org.openhab.io.homekit.internal.HomekitAccessoryUpdater;
import org.openhab.io.homekit.internal.HomekitCharacteristicType;
import org.openhab.io.homekit.internal.HomekitCommandType;
import org.openhab.io.homekit.internal.HomekitException;
import org.openhab.io.homekit.internal.HomekitImpl;
import org.openhab.io.homekit.internal.HomekitTaggedItem;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.hapjava.characteristics.Characteristic;
import io.github.hapjava.characteristics.CharacteristicEnum;
import io.github.hapjava.characteristics.ExceptionalConsumer;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.airquality.NitrogenDioxideDensityCharacteristic;
import io.github.hapjava.characteristics.impl.airquality.OzoneDensityCharacteristic;
import io.github.hapjava.characteristics.impl.airquality.PM10DensityCharacteristic;
import io.github.hapjava.characteristics.impl.airquality.PM25DensityCharacteristic;
import io.github.hapjava.characteristics.impl.airquality.SulphurDioxideDensityCharacteristic;
import io.github.hapjava.characteristics.impl.airquality.VOCDensityCharacteristic;
import io.github.hapjava.characteristics.impl.audio.MuteCharacteristic;
import io.github.hapjava.characteristics.impl.audio.VolumeCharacteristic;
import io.github.hapjava.characteristics.impl.battery.StatusLowBatteryCharacteristic;
import io.github.hapjava.characteristics.impl.battery.StatusLowBatteryEnum;
import io.github.hapjava.characteristics.impl.carbondioxidesensor.CarbonDioxideLevelCharacteristic;
import io.github.hapjava.characteristics.impl.carbondioxidesensor.CarbonDioxidePeakLevelCharacteristic;
import io.github.hapjava.characteristics.impl.carbonmonoxidesensor.CarbonMonoxideLevelCharacteristic;
import io.github.hapjava.characteristics.impl.carbonmonoxidesensor.CarbonMonoxidePeakLevelCharacteristic;
import io.github.hapjava.characteristics.impl.common.ActiveCharacteristic;
import io.github.hapjava.characteristics.impl.common.ActiveEnum;
import io.github.hapjava.characteristics.impl.common.ActiveIdentifierCharacteristic;
import io.github.hapjava.characteristics.impl.common.ConfiguredNameCharacteristic;
import io.github.hapjava.characteristics.impl.common.IdentifierCharacteristic;
import io.github.hapjava.characteristics.impl.common.IsConfiguredCharacteristic;
import io.github.hapjava.characteristics.impl.common.IsConfiguredEnum;
import io.github.hapjava.characteristics.impl.common.NameCharacteristic;
import io.github.hapjava.characteristics.impl.common.ObstructionDetectedCharacteristic;
import io.github.hapjava.characteristics.impl.common.StatusActiveCharacteristic;
import io.github.hapjava.characteristics.impl.common.StatusFaultCharacteristic;
import io.github.hapjava.characteristics.impl.common.StatusFaultEnum;
import io.github.hapjava.characteristics.impl.common.StatusTamperedCharacteristic;
import io.github.hapjava.characteristics.impl.common.StatusTamperedEnum;
import io.github.hapjava.characteristics.impl.fan.CurrentFanStateCharacteristic;
import io.github.hapjava.characteristics.impl.fan.CurrentFanStateEnum;
import io.github.hapjava.characteristics.impl.fan.LockPhysicalControlsCharacteristic;
import io.github.hapjava.characteristics.impl.fan.LockPhysicalControlsEnum;
import io.github.hapjava.characteristics.impl.fan.RotationDirectionCharacteristic;
import io.github.hapjava.characteristics.impl.fan.RotationDirectionEnum;
import io.github.hapjava.characteristics.impl.fan.RotationSpeedCharacteristic;
import io.github.hapjava.characteristics.impl.fan.SwingModeCharacteristic;
import io.github.hapjava.characteristics.impl.fan.SwingModeEnum;
import io.github.hapjava.characteristics.impl.fan.TargetFanStateCharacteristic;
import io.github.hapjava.characteristics.impl.fan.TargetFanStateEnum;
import io.github.hapjava.characteristics.impl.filtermaintenance.FilterLifeLevelCharacteristic;
import io.github.hapjava.characteristics.impl.filtermaintenance.ResetFilterIndicationCharacteristic;
import io.github.hapjava.characteristics.impl.humiditysensor.CurrentRelativeHumidityCharacteristic;
import io.github.hapjava.characteristics.impl.inputsource.CurrentVisibilityStateCharacteristic;
import io.github.hapjava.characteristics.impl.inputsource.CurrentVisibilityStateEnum;
import io.github.hapjava.characteristics.impl.inputsource.InputDeviceTypeCharacteristic;
import io.github.hapjava.characteristics.impl.inputsource.InputDeviceTypeEnum;
import io.github.hapjava.characteristics.impl.inputsource.InputSourceTypeCharacteristic;
import io.github.hapjava.characteristics.impl.inputsource.InputSourceTypeEnum;
import io.github.hapjava.characteristics.impl.inputsource.TargetVisibilityStateCharacteristic;
import io.github.hapjava.characteristics.impl.inputsource.TargetVisibilityStateEnum;
import io.github.hapjava.characteristics.impl.lightbulb.BrightnessCharacteristic;
import io.github.hapjava.characteristics.impl.lightbulb.ColorTemperatureCharacteristic;
import io.github.hapjava.characteristics.impl.lightbulb.HueCharacteristic;
import io.github.hapjava.characteristics.impl.lightbulb.SaturationCharacteristic;
import io.github.hapjava.characteristics.impl.slat.CurrentTiltAngleCharacteristic;
import io.github.hapjava.characteristics.impl.slat.TargetTiltAngleCharacteristic;
import io.github.hapjava.characteristics.impl.television.ClosedCaptionsCharacteristic;
import io.github.hapjava.characteristics.impl.television.ClosedCaptionsEnum;
import io.github.hapjava.characteristics.impl.television.CurrentMediaStateCharacteristic;
import io.github.hapjava.characteristics.impl.television.CurrentMediaStateEnum;
import io.github.hapjava.characteristics.impl.television.PictureModeCharacteristic;
import io.github.hapjava.characteristics.impl.television.PictureModeEnum;
import io.github.hapjava.characteristics.impl.television.PowerModeCharacteristic;
import io.github.hapjava.characteristics.impl.television.PowerModeEnum;
import io.github.hapjava.characteristics.impl.television.RemoteKeyCharacteristic;
import io.github.hapjava.characteristics.impl.television.RemoteKeyEnum;
import io.github.hapjava.characteristics.impl.television.SleepDiscoveryModeCharacteristic;
import io.github.hapjava.characteristics.impl.television.SleepDiscoveryModeEnum;
import io.github.hapjava.characteristics.impl.television.TargetMediaStateCharacteristic;
import io.github.hapjava.characteristics.impl.television.TargetMediaStateEnum;
import io.github.hapjava.characteristics.impl.televisionspeaker.VolumeControlTypeCharacteristic;
import io.github.hapjava.characteristics.impl.televisionspeaker.VolumeControlTypeEnum;
import io.github.hapjava.characteristics.impl.televisionspeaker.VolumeSelectorCharacteristic;
import io.github.hapjava.characteristics.impl.televisionspeaker.VolumeSelectorEnum;
import io.github.hapjava.characteristics.impl.thermostat.CoolingThresholdTemperatureCharacteristic;
import io.github.hapjava.characteristics.impl.thermostat.HeatingThresholdTemperatureCharacteristic;
import io.github.hapjava.characteristics.impl.valve.RemainingDurationCharacteristic;
import io.github.hapjava.characteristics.impl.valve.SetDurationCharacteristic;
import io.github.hapjava.characteristics.impl.windowcovering.CurrentHorizontalTiltAngleCharacteristic;
import io.github.hapjava.characteristics.impl.windowcovering.CurrentVerticalTiltAngleCharacteristic;
import io.github.hapjava.characteristics.impl.windowcovering.HoldPositionCharacteristic;
import io.github.hapjava.characteristics.impl.windowcovering.TargetHorizontalTiltAngleCharacteristic;
import io.github.hapjava.characteristics.impl.windowcovering.TargetVerticalTiltAngleCharacteristic;
import tech.units.indriya.unit.UnitDimension;

/**
 * Creates an optional characteristics .
 *
 * @author Eugen Freiter - Initial contribution
 */
@NonNullByDefault
public class HomekitCharacteristicFactory {
    private static final Logger logger = LoggerFactory.getLogger(HomekitCharacteristicFactory.class);

    // List of optional characteristics and corresponding method to create them.
    private static final Map<HomekitCharacteristicType, BiFunction<HomekitTaggedItem, HomekitAccessoryUpdater, Characteristic>> optional = new HashMap<HomekitCharacteristicType, BiFunction<HomekitTaggedItem, HomekitAccessoryUpdater, Characteristic>>() {
        {
            put(NAME, HomekitCharacteristicFactory::createNameCharacteristic);
            put(BATTERY_LOW_STATUS, HomekitCharacteristicFactory::createStatusLowBatteryCharacteristic);
            put(FAULT_STATUS, HomekitCharacteristicFactory::createStatusFaultCharacteristic);
            put(TAMPERED_STATUS, HomekitCharacteristicFactory::createStatusTamperedCharacteristic);
            put(ACTIVE_STATUS, HomekitCharacteristicFactory::createStatusActiveCharacteristic);
            put(CARBON_MONOXIDE_LEVEL, HomekitCharacteristicFactory::createCarbonMonoxideLevelCharacteristic);
            put(CARBON_MONOXIDE_PEAK_LEVEL, HomekitCharacteristicFactory::createCarbonMonoxidePeakLevelCharacteristic);
            put(CARBON_DIOXIDE_LEVEL, HomekitCharacteristicFactory::createCarbonDioxideLevelCharacteristic);
            put(CARBON_DIOXIDE_PEAK_LEVEL, HomekitCharacteristicFactory::createCarbonDioxidePeakLevelCharacteristic);
            put(HOLD_POSITION, HomekitCharacteristicFactory::createHoldPositionCharacteristic);
            put(OBSTRUCTION_STATUS, HomekitCharacteristicFactory::createObstructionDetectedCharacteristic);
            put(CURRENT_HORIZONTAL_TILT_ANGLE,
                    HomekitCharacteristicFactory::createCurrentHorizontalTiltAngleCharacteristic);
            put(CURRENT_VERTICAL_TILT_ANGLE,
                    HomekitCharacteristicFactory::createCurrentVerticalTiltAngleCharacteristic);
            put(TARGET_HORIZONTAL_TILT_ANGLE,
                    HomekitCharacteristicFactory::createTargetHorizontalTiltAngleCharacteristic);
            put(TARGET_VERTICAL_TILT_ANGLE, HomekitCharacteristicFactory::createTargetVerticalTiltAngleCharacteristic);
            put(CURRENT_TILT_ANGLE, HomekitCharacteristicFactory::createCurrentTiltAngleCharacteristic);
            put(TARGET_TILT_ANGLE, HomekitCharacteristicFactory::createTargetTiltAngleCharacteristic);
            put(HUE, HomekitCharacteristicFactory::createHueCharacteristic);
            put(BRIGHTNESS, HomekitCharacteristicFactory::createBrightnessCharacteristic);
            put(SATURATION, HomekitCharacteristicFactory::createSaturationCharacteristic);
            put(COLOR_TEMPERATURE, HomekitCharacteristicFactory::createColorTemperatureCharacteristic);
            put(CURRENT_FAN_STATE, HomekitCharacteristicFactory::createCurrentFanStateCharacteristic);
            put(TARGET_FAN_STATE, HomekitCharacteristicFactory::createTargetFanStateCharacteristic);
            put(ROTATION_DIRECTION, HomekitCharacteristicFactory::createRotationDirectionCharacteristic);
            put(ROTATION_SPEED, HomekitCharacteristicFactory::createRotationSpeedCharacteristic);
            put(SWING_MODE, HomekitCharacteristicFactory::createSwingModeCharacteristic);
            put(LOCK_CONTROL, HomekitCharacteristicFactory::createLockPhysicalControlsCharacteristic);
            put(DURATION, HomekitCharacteristicFactory::createDurationCharacteristic);
            put(VOLUME, HomekitCharacteristicFactory::createVolumeCharacteristic);
            put(COOLING_THRESHOLD_TEMPERATURE, HomekitCharacteristicFactory::createCoolingThresholdCharacteristic);
            put(HEATING_THRESHOLD_TEMPERATURE, HomekitCharacteristicFactory::createHeatingThresholdCharacteristic);
            put(RELATIVE_HUMIDITY, HomekitCharacteristicFactory::createRelativeHumidityCharacteristic);
            put(REMAINING_DURATION, HomekitCharacteristicFactory::createRemainingDurationCharacteristic);
            put(OZONE_DENSITY, HomekitCharacteristicFactory::createOzoneDensityCharacteristic);
            put(NITROGEN_DIOXIDE_DENSITY, HomekitCharacteristicFactory::createNitrogenDioxideDensityCharacteristic);
            put(SULPHUR_DIOXIDE_DENSITY, HomekitCharacteristicFactory::createSulphurDioxideDensityCharacteristic);
            put(PM25_DENSITY, HomekitCharacteristicFactory::createPM25DensityCharacteristic);
            put(PM10_DENSITY, HomekitCharacteristicFactory::createPM10DensityCharacteristic);
            put(VOC_DENSITY, HomekitCharacteristicFactory::createVOCDensityCharacteristic);
            put(FILTER_LIFE_LEVEL, HomekitCharacteristicFactory::createFilterLifeLevelCharacteristic);
            put(FILTER_RESET_INDICATION, HomekitCharacteristicFactory::createFilterResetCharacteristic);
            put(ACTIVE, HomekitCharacteristicFactory::createActiveCharacteristic);
            put(CONFIGURED_NAME, HomekitCharacteristicFactory::createConfiguredNameCharacteristic);
            put(ACTIVE_IDENTIFIER, HomekitCharacteristicFactory::createActiveIdentifierCharacteristic);
            put(REMOTE_KEY, HomekitCharacteristicFactory::createRemoteKeyCharacteristic);
            put(SLEEP_DISCOVERY_MODE, HomekitCharacteristicFactory::createSleepDiscoveryModeCharacteristic);
            put(POWER_MODE, HomekitCharacteristicFactory::createPowerModeCharacteristic);
            put(CLOSED_CAPTIONS, HomekitCharacteristicFactory::createClosedCaptionsCharacteristic);
            put(PICTURE_MODE, HomekitCharacteristicFactory::createPictureModeCharacteristic);
            put(CONFIGURED, HomekitCharacteristicFactory::createIsConfiguredCharacteristic);
            put(INPUT_SOURCE_TYPE, HomekitCharacteristicFactory::createInputSourceTypeCharacteristic);
            put(CURRENT_VISIBILITY, HomekitCharacteristicFactory::createCurrentVisibilityStateCharacteristic);
            put(IDENTIFIER, HomekitCharacteristicFactory::createIdentifierCharacteristic);
            put(INPUT_DEVICE_TYPE, HomekitCharacteristicFactory::createInputDeviceTypeCharacteristic);
            put(TARGET_VISIBILITY_STATE, HomekitCharacteristicFactory::createTargetVisibilityStateCharacteristic);
            put(VOLUME_SELECTOR, HomekitCharacteristicFactory::createVolumeSelectorCharacteristic);
            put(VOLUME_CONTROL_TYPE, HomekitCharacteristicFactory::createVolumeControlTypeCharacteristic);
            put(CURRENT_MEDIA_STATE, HomekitCharacteristicFactory::createCurrentMediaStateCharacteristic);
            put(TARGET_MEDIA_STATE, HomekitCharacteristicFactory::createTargetMediaStateCharacteristic);
            put(MUTE, HomekitCharacteristicFactory::createMuteCharacteristic);
        }
    };

    public static @Nullable Characteristic createNullableCharacteristic(HomekitTaggedItem item,
            HomekitAccessoryUpdater updater) {
        final @Nullable HomekitCharacteristicType type = item.getCharacteristicType();
        logger.trace("Create characteristic {}", item);
        if (optional.containsKey(type)) {
            return optional.get(type).apply(item, updater);
        }
        return null;
    }

    /**
     * Create HomeKit characteristic
     *
     * @param item corresponding OH item
     * @param updater update to keep OH item and HomeKit characteristic in sync
     * @return HomeKit characteristic
     */
    public static Characteristic createCharacteristic(HomekitTaggedItem item, HomekitAccessoryUpdater updater)
            throws HomekitException {
        Characteristic characteristic = createNullableCharacteristic(item, updater);
        if (characteristic != null) {
            return characteristic;
        }
        final @Nullable HomekitCharacteristicType type = item.getCharacteristicType();
        logger.warn("Unsupported optional characteristic from item {}. Accessory type {}, characteristic type {}",
                item.getName(), item.getAccessoryType(), type.getTag());
        throw new HomekitException(
                "Unsupported optional characteristic. Characteristic type \"" + type.getTag() + "\"");
    }

    public static <T extends Enum<T>> Map<T, String> createMapping(HomekitTaggedItem item, Class<T> klazz) {
        EnumMap<T, String> map = new EnumMap(klazz);
        for (var k : klazz.getEnumConstants()) {
            map.put(k, k.toString());
        }
        var configuration = item.getConfiguration();
        if (configuration != null) {
            updateMapping(configuration, map);
        }
        return map;
    }

    /**
     * Update mapping with values from item configuration.
     * It checks for all keys from the mapping whether there is configuration at item with the same key and if yes,
     * replace the value.
     *
     * @param configuration tagged item configuration
     * @param map mapping to update
     * @param customEnumList list to store custom state enumeration
     */
    public static <T> void updateMapping(Map<String, Object> configuration, Map<T, String> map,
            @Nullable List<T> customEnumList) {
        map.forEach((k, current_value) -> {
            final Object new_value = configuration.get(k.toString());
            if (new_value instanceof String) {
                map.put(k, (String) new_value);
                if (customEnumList != null) {
                    customEnumList.add(k);
                }
            }
        });
    }

    public static <T> void updateMapping(Map<String, Object> configuration, Map<T, String> map) {
        updateMapping(configuration, map, null);
    }

    /**
     * Takes item state as value and retrieves the key for that value from mapping.
     * E.g. used to map StringItem value to HomeKit Enum
     *
     * @param characteristicType characteristicType to identify item
     * @param mapping mapping
     * @param defaultValue default value if nothing found in mapping
     * @param <T> type of the result derived from
     * @return key for the value
     */
    public static <T> T getKeyFromMapping(HomekitTaggedItem item, Map<T, String> mapping, T defaultValue) {
        final State state = item.getItem().getState();
        logger.trace("getKeyFromMapping: characteristic {}, state {}, mapping {}", item.getAccessoryType().getTag(),
                state, mapping);
        if (state instanceof StringType) {
            return mapping.entrySet().stream().filter(entry -> state.toString().equalsIgnoreCase(entry.getValue()))
                    .findAny().map(Map.Entry::getKey).orElseGet(() -> {
                        logger.warn(
                                "Wrong value {} for {} characteristic of the item {}. Expected one of following {}. Returning {}.",
                                state.toString(), item.getAccessoryType().getTag(), item.getName(), mapping.values(),
                                defaultValue);
                        return defaultValue;
                    });
        }
        return defaultValue;
    }

    // METHODS TO CREATE SINGLE CHARACTERISTIC FROM OH ITEM

    // supporting methods

    public static boolean useFahrenheit() {
        return FrameworkUtil.getBundle(HomekitImpl.class).getBundleContext()
                .getServiceReference(Homekit.class.getName()).getProperty("useFahrenheitTemperature") == Boolean.TRUE;
    }

    private static <T extends CharacteristicEnum> CompletableFuture<T> getEnumFromItem(HomekitTaggedItem item,
            Map<T, String> mapping, T defaultValue) {
        return CompletableFuture.completedFuture(getKeyFromMapping(item, mapping, defaultValue));
    }

    private static <T extends CharacteristicEnum> CompletableFuture<T> getEnumFromItem(HomekitTaggedItem item,
            T offEnum, T onEnum, T defaultEnum) {
        final State state = item.getItem().getState();
        if (state instanceof OnOffType) {
            return CompletableFuture
                    .completedFuture(state.equals(item.isInverted() ? OnOffType.ON : OnOffType.OFF) ? offEnum : onEnum);
        } else if (state instanceof OpenClosedType) {
            return CompletableFuture.completedFuture(
                    state.equals(item.isInverted() ? OpenClosedType.OPEN : OpenClosedType.CLOSED) ? offEnum : onEnum);
        } else if (state instanceof DecimalType) {
            return CompletableFuture.completedFuture(((DecimalType) state).intValue() == 0 ? offEnum : onEnum);
        } else if (state instanceof UnDefType) {
            return CompletableFuture.completedFuture(defaultEnum);
        }
        logger.warn(
                "Item state {} is not supported. Only OnOffType,OpenClosedType and Decimal (0/1) are supported. Ignore item {}",
                state, item.getName());
        return CompletableFuture.completedFuture(defaultEnum);
    }

    private static <T extends Enum<T>> void setValueFromEnum(HomekitTaggedItem taggedItem, T value,
            Map<T, String> map) {
        taggedItem.send(new StringType(map.get(value)));
    }

    private static void setValueFromEnum(HomekitTaggedItem taggedItem, CharacteristicEnum value,
            CharacteristicEnum offEnum, CharacteristicEnum onEnum) {
        if (taggedItem.getBaseItem() instanceof SwitchItem) {
            if (value.equals(offEnum)) {
                taggedItem.send(taggedItem.isInverted() ? OnOffType.ON : OnOffType.OFF);
            } else if (value.equals(onEnum)) {
                taggedItem.send(taggedItem.isInverted() ? OnOffType.OFF : OnOffType.ON);
            } else {
                logger.warn("Enum value {} is not supported for {}. Only following values are supported: {},{}", value,
                        taggedItem.getName(), offEnum, onEnum);
            }
        } else if (taggedItem.getBaseItem() instanceof NumberItem) {
            taggedItem.send(new DecimalType(value.getCode()));
        } else {
            logger.warn("Item {} of type {} is not supported. Only Switch and Number item types are supported.",
                    taggedItem.getName(), taggedItem.getBaseItem().getType());
        }
    }

    private static int getIntFromItem(HomekitTaggedItem taggedItem, int defaultValue) {
        int value = defaultValue;
        final State state = taggedItem.getItem().getState();
        if (state instanceof PercentType) {
            value = ((PercentType) state).intValue();
        } else if (state instanceof DecimalType) {
            value = ((DecimalType) state).intValue();
        } else if (state instanceof UnDefType) {
            logger.debug("Item state {} is UNDEF {}. Returning default value {}", state, taggedItem.getName(),
                    defaultValue);
        } else {
            logger.warn(
                    "Item state {} is not supported for {}. Only PercentType and DecimalType (0/100) are supported.",
                    state, taggedItem.getName());
        }
        return value;
    }

    /** special method for tilts. it converts percentage to angle */
    private static int getAngleFromItem(HomekitTaggedItem taggedItem, int defaultValue) {
        int value = defaultValue;
        final State state = taggedItem.getItem().getState();
        if (state instanceof PercentType) {
            value = (int) ((((PercentType) state).intValue() * 90.0) / 50.0 - 90.0);
        } else {
            value = getIntFromItem(taggedItem, defaultValue);
        }
        return value;
    }

    private static <T extends Quantity<T>> double convertAndRound(double value, Unit<T> from, Unit<T> to) {
        double rawValue = from.equals(to) ? value : from.getConverterTo(to).convert(value);
        return new BigDecimal(rawValue).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    public static @Nullable Double stateAsTemperature(@Nullable State state) {
        if (state == null || state instanceof UnDefType) {
            return null;
        }

        if (state instanceof QuantityType<?>) {
            final QuantityType<?> qt = (QuantityType<?>) state;
            if (qt.getDimension().equals(UnitDimension.TEMPERATURE)) {
                return qt.toUnit(SIUnits.CELSIUS).doubleValue();
            }
        }

        return convertToCelsius(state.as(DecimalType.class).doubleValue());
    }

    public static double convertToCelsius(double degrees) {
        return convertAndRound(degrees, useFahrenheit() ? ImperialUnits.FAHRENHEIT : SIUnits.CELSIUS, SIUnits.CELSIUS);
    }

    public static double convertFromCelsius(double degrees) {
        return convertAndRound(degrees, SIUnits.CELSIUS, useFahrenheit() ? ImperialUnits.FAHRENHEIT : SIUnits.CELSIUS);
    }

    public static double getTemperatureStep(HomekitTaggedItem taggedItem, double defaultValue) {
        return taggedItem.getConfigurationAsQuantity(HomekitTaggedItem.STEP,
                new QuantityType(defaultValue, SIUnits.CELSIUS), true).doubleValue();
    }

    private static Supplier<CompletableFuture<Integer>> getAngleSupplier(HomekitTaggedItem taggedItem,
            int defaultValue) {
        return () -> CompletableFuture.completedFuture(getAngleFromItem(taggedItem, defaultValue));
    }

    private static Supplier<CompletableFuture<Integer>> getIntSupplier(HomekitTaggedItem taggedItem, int defaultValue) {
        return () -> CompletableFuture.completedFuture(getIntFromItem(taggedItem, defaultValue));
    }

    private static ExceptionalConsumer<Integer> setIntConsumer(HomekitTaggedItem taggedItem) {
        return (value) -> {
            if (taggedItem.getBaseItem() instanceof NumberItem) {
                taggedItem.send(new DecimalType(value));
            } else {
                logger.warn("Item type {} is not supported for {}. Only NumberItem is supported.",
                        taggedItem.getBaseItem().getType(), taggedItem.getName());
            }
        };
    }

    private static ExceptionalConsumer<Integer> setPercentConsumer(HomekitTaggedItem taggedItem) {
        return (value) -> {
            if (taggedItem.getBaseItem() instanceof NumberItem) {
                taggedItem.send(new DecimalType(value));
            } else if (taggedItem.getBaseItem() instanceof DimmerItem) {
                taggedItem.send(new PercentType(value));
            } else {
                logger.warn("Item type {} is not supported for {}. Only DimmerItem and NumberItem are supported.",
                        taggedItem.getBaseItem().getType(), taggedItem.getName());
            }
        };
    }

    private static ExceptionalConsumer<Integer> setAngleConsumer(HomekitTaggedItem taggedItem) {
        return (value) -> {
            if (taggedItem.getBaseItem() instanceof NumberItem) {
                taggedItem.send(new DecimalType(value));
            } else if (taggedItem.getBaseItem() instanceof DimmerItem) {
                value = (int) (value * 50.0 / 90.0 + 50.0);
                taggedItem.send(new PercentType(value));
            } else {
                logger.warn("Item type {} is not supported for {}. Only DimmerItem and NumberItem are supported.",
                        taggedItem.getBaseItem().getType(), taggedItem.getName());
            }
        };
    }

    private static Supplier<CompletableFuture<Double>> getDoubleSupplier(HomekitTaggedItem taggedItem,
            double defaultValue) {
        return () -> {
            final State state = taggedItem.getItem().getState();
            double value = defaultValue;
            if (state instanceof PercentType) {
                value = ((PercentType) state).doubleValue();
            } else if (state instanceof DecimalType) {
                value = ((DecimalType) state).doubleValue();
            } else if (state instanceof QuantityType) {
                value = ((QuantityType) state).doubleValue();
            }
            return CompletableFuture.completedFuture(value);
        };
    }

    private static ExceptionalConsumer<Double> setDoubleConsumer(HomekitTaggedItem taggedItem) {
        return (value) -> {
            if (taggedItem.getBaseItem() instanceof NumberItem) {
                taggedItem.send(new DecimalType(value.doubleValue()));
            } else if (taggedItem.getBaseItem() instanceof DimmerItem) {
                taggedItem.send(new PercentType(value.intValue()));
            } else {
                logger.warn("Item type {} is not supported for {}. Only Number and Dimmer type are supported.",
                        taggedItem.getBaseItem().getType(), taggedItem.getName());
            }
        };
    }

    private static Supplier<CompletableFuture<Double>> getTemperatureSupplier(HomekitTaggedItem taggedItem,
            double defaultValue) {
        return () -> {
            final @Nullable Double value = stateAsTemperature(taggedItem.getItem().getState());
            return CompletableFuture.completedFuture(value != null ? value : defaultValue);
        };
    }

    private static ExceptionalConsumer<Double> setTemperatureConsumer(HomekitTaggedItem taggedItem) {
        return (value) -> {
            if (taggedItem.getBaseItem() instanceof NumberItem) {
                taggedItem.send(new DecimalType(convertFromCelsius(value)));
            } else {
                logger.warn("Item type {} is not supported for {}. Only Number type is supported.",
                        taggedItem.getBaseItem().getType(), taggedItem.getName());
            }
        };
    }

    protected static Consumer<HomekitCharacteristicChangeCallback> getSubscriber(HomekitTaggedItem taggedItem,
            HomekitCharacteristicType key, HomekitAccessoryUpdater updater) {
        return (callback) -> updater.subscribe((GenericItem) taggedItem.getItem(), key.getTag(), callback);
    }

    protected static Runnable getUnsubscriber(HomekitTaggedItem taggedItem, HomekitCharacteristicType key,
            HomekitAccessoryUpdater updater) {
        return () -> updater.unsubscribe((GenericItem) taggedItem.getItem(), key.getTag());
    }

    // create method for characteristic
    private static StatusLowBatteryCharacteristic createStatusLowBatteryCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        BigDecimal lowThreshold = taggedItem.getConfiguration(HomekitTaggedItem.BATTERY_LOW_THRESHOLD,
                BigDecimal.valueOf(20));
        BooleanItemReader lowBatteryReader = new BooleanItemReader(taggedItem.getItem(),
                taggedItem.isInverted() ? OnOffType.OFF : OnOffType.ON,
                taggedItem.isInverted() ? OpenClosedType.CLOSED : OpenClosedType.OPEN, lowThreshold, true);
        return new StatusLowBatteryCharacteristic(
                () -> CompletableFuture.completedFuture(
                        lowBatteryReader.getValue() ? StatusLowBatteryEnum.LOW : StatusLowBatteryEnum.NORMAL),
                getSubscriber(taggedItem, BATTERY_LOW_STATUS, updater),
                getUnsubscriber(taggedItem, BATTERY_LOW_STATUS, updater));
    }

    private static StatusFaultCharacteristic createStatusFaultCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new StatusFaultCharacteristic(
                () -> getEnumFromItem(taggedItem, StatusFaultEnum.NO_FAULT, StatusFaultEnum.GENERAL_FAULT,
                        StatusFaultEnum.NO_FAULT),
                getSubscriber(taggedItem, FAULT_STATUS, updater), getUnsubscriber(taggedItem, FAULT_STATUS, updater));
    }

    private static StatusTamperedCharacteristic createStatusTamperedCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new StatusTamperedCharacteristic(
                () -> getEnumFromItem(taggedItem, StatusTamperedEnum.NOT_TAMPERED, StatusTamperedEnum.TAMPERED,
                        StatusTamperedEnum.NOT_TAMPERED),
                getSubscriber(taggedItem, TAMPERED_STATUS, updater),
                getUnsubscriber(taggedItem, TAMPERED_STATUS, updater));
    }

    private static ObstructionDetectedCharacteristic createObstructionDetectedCharacteristic(
            HomekitTaggedItem taggedItem, HomekitAccessoryUpdater updater) {
        return new ObstructionDetectedCharacteristic(
                () -> CompletableFuture.completedFuture(taggedItem.getItem().getState() == OnOffType.ON
                        || taggedItem.getItem().getState() == OpenClosedType.OPEN),
                getSubscriber(taggedItem, OBSTRUCTION_STATUS, updater),
                getUnsubscriber(taggedItem, OBSTRUCTION_STATUS, updater));
    }

    private static StatusActiveCharacteristic createStatusActiveCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new StatusActiveCharacteristic(
                () -> CompletableFuture.completedFuture(taggedItem.getItem().getState() == OnOffType.ON
                        || taggedItem.getItem().getState() == OpenClosedType.OPEN),
                getSubscriber(taggedItem, ACTIVE_STATUS, updater), getUnsubscriber(taggedItem, ACTIVE_STATUS, updater));
    }

    private static NameCharacteristic createNameCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new NameCharacteristic(() -> {
            final State state = taggedItem.getItem().getState();
            return CompletableFuture.completedFuture(state instanceof UnDefType ? "" : state.toString());
        });
    }

    private static HoldPositionCharacteristic createHoldPositionCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        final Item item = taggedItem.getBaseItem();
        if (!(item instanceof SwitchItem || item instanceof RollershutterItem)) {
            logger.warn(
                    "Item {} cannot be used for the HoldPosition characteristic; only SwitchItem and RollershutterItem are supported. Hold requests will be ignored.",
                    item.getName());
        }

        return new HoldPositionCharacteristic(value -> {
            if (!value) {
                return;
            }

            if (item instanceof SwitchItem) {
                ((SwitchItem) item).send(OnOffType.ON);
            } else if (item instanceof RollershutterItem) {
                ((RollershutterItem) item).send(StopMoveType.STOP);
            }
        });
    }

    private static CarbonMonoxideLevelCharacteristic createCarbonMonoxideLevelCharacteristic(
            HomekitTaggedItem taggedItem, HomekitAccessoryUpdater updater) {
        return new CarbonMonoxideLevelCharacteristic(
                getDoubleSupplier(taggedItem,
                        taggedItem.getConfigurationAsDouble(HomekitTaggedItem.MIN_VALUE,
                                CarbonMonoxideLevelCharacteristic.DEFAULT_MIN_VALUE)),
                getSubscriber(taggedItem, CARBON_DIOXIDE_LEVEL, updater),
                getUnsubscriber(taggedItem, CARBON_DIOXIDE_LEVEL, updater));
    }

    private static CarbonMonoxidePeakLevelCharacteristic createCarbonMonoxidePeakLevelCharacteristic(
            HomekitTaggedItem taggedItem, HomekitAccessoryUpdater updater) {
        return new CarbonMonoxidePeakLevelCharacteristic(
                getDoubleSupplier(taggedItem,
                        taggedItem.getConfigurationAsDouble(HomekitTaggedItem.MIN_VALUE,
                                CarbonMonoxidePeakLevelCharacteristic.DEFAULT_MIN_VALUE)),
                getSubscriber(taggedItem, CARBON_DIOXIDE_PEAK_LEVEL, updater),
                getUnsubscriber(taggedItem, CARBON_DIOXIDE_PEAK_LEVEL, updater));
    }

    private static CarbonDioxideLevelCharacteristic createCarbonDioxideLevelCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new CarbonDioxideLevelCharacteristic(
                getDoubleSupplier(taggedItem,
                        taggedItem.getConfigurationAsDouble(HomekitTaggedItem.MIN_VALUE,
                                CarbonDioxideLevelCharacteristic.DEFAULT_MIN_VALUE)),
                getSubscriber(taggedItem, CARBON_MONOXIDE_LEVEL, updater),
                getUnsubscriber(taggedItem, CARBON_MONOXIDE_LEVEL, updater));
    }

    private static CarbonDioxidePeakLevelCharacteristic createCarbonDioxidePeakLevelCharacteristic(
            HomekitTaggedItem taggedItem, HomekitAccessoryUpdater updater) {
        return new CarbonDioxidePeakLevelCharacteristic(
                getDoubleSupplier(taggedItem,
                        taggedItem.getConfigurationAsDouble(HomekitTaggedItem.MIN_VALUE,
                                CarbonDioxidePeakLevelCharacteristic.DEFAULT_MIN_VALUE)),
                getSubscriber(taggedItem, CARBON_MONOXIDE_PEAK_LEVEL, updater),
                getUnsubscriber(taggedItem, CARBON_MONOXIDE_PEAK_LEVEL, updater));
    }

    private static CurrentHorizontalTiltAngleCharacteristic createCurrentHorizontalTiltAngleCharacteristic(
            HomekitTaggedItem taggedItem, HomekitAccessoryUpdater updater) {
        return new CurrentHorizontalTiltAngleCharacteristic(getAngleSupplier(taggedItem, 0),
                getSubscriber(taggedItem, CURRENT_HORIZONTAL_TILT_ANGLE, updater),
                getUnsubscriber(taggedItem, CURRENT_HORIZONTAL_TILT_ANGLE, updater));
    }

    private static CurrentVerticalTiltAngleCharacteristic createCurrentVerticalTiltAngleCharacteristic(
            HomekitTaggedItem taggedItem, HomekitAccessoryUpdater updater) {
        return new CurrentVerticalTiltAngleCharacteristic(getAngleSupplier(taggedItem, 0),
                getSubscriber(taggedItem, CURRENT_VERTICAL_TILT_ANGLE, updater),
                getUnsubscriber(taggedItem, CURRENT_VERTICAL_TILT_ANGLE, updater));
    }

    private static TargetHorizontalTiltAngleCharacteristic createTargetHorizontalTiltAngleCharacteristic(
            HomekitTaggedItem taggedItem, HomekitAccessoryUpdater updater) {
        return new TargetHorizontalTiltAngleCharacteristic(getAngleSupplier(taggedItem, 0),
                setAngleConsumer(taggedItem), getSubscriber(taggedItem, TARGET_HORIZONTAL_TILT_ANGLE, updater),
                getUnsubscriber(taggedItem, TARGET_HORIZONTAL_TILT_ANGLE, updater));
    }

    private static TargetVerticalTiltAngleCharacteristic createTargetVerticalTiltAngleCharacteristic(
            HomekitTaggedItem taggedItem, HomekitAccessoryUpdater updater) {
        return new TargetVerticalTiltAngleCharacteristic(getAngleSupplier(taggedItem, 0), setAngleConsumer(taggedItem),
                getSubscriber(taggedItem, TARGET_HORIZONTAL_TILT_ANGLE, updater),
                getUnsubscriber(taggedItem, TARGET_HORIZONTAL_TILT_ANGLE, updater));
    }

    private static CurrentTiltAngleCharacteristic createCurrentTiltAngleCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new CurrentTiltAngleCharacteristic(getAngleSupplier(taggedItem, 0),
                getSubscriber(taggedItem, CURRENT_TILT_ANGLE, updater),
                getUnsubscriber(taggedItem, CURRENT_TILT_ANGLE, updater));
    }

    private static TargetTiltAngleCharacteristic createTargetTiltAngleCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new TargetTiltAngleCharacteristic(getAngleSupplier(taggedItem, 0), setAngleConsumer(taggedItem),
                getSubscriber(taggedItem, TARGET_TILT_ANGLE, updater),
                getUnsubscriber(taggedItem, TARGET_TILT_ANGLE, updater));
    }

    private static HueCharacteristic createHueCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new HueCharacteristic(() -> {
            double value = 0.0;
            State state = taggedItem.getItem().getState();
            if (state instanceof HSBType) {
                value = ((HSBType) state).getHue().doubleValue();
            }
            return CompletableFuture.completedFuture(value);
        }, (hue) -> {
            if (taggedItem.getBaseItem() instanceof ColorItem) {
                taggedItem.sendCommandProxy(HomekitCommandType.HUE_COMMAND, new DecimalType(hue));
            } else {
                logger.warn("Item type {} is not supported for {}. Only Color type is supported.",
                        taggedItem.getBaseItem().getType(), taggedItem.getName());
            }
        }, getSubscriber(taggedItem, HUE, updater), getUnsubscriber(taggedItem, HUE, updater));
    }

    private static BrightnessCharacteristic createBrightnessCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new BrightnessCharacteristic(() -> {
            int value = 0;
            final State state = taggedItem.getItem().getState();
            if (state instanceof HSBType) {
                value = ((HSBType) state).getBrightness().intValue();
            } else if (state instanceof PercentType) {
                value = ((PercentType) state).intValue();
            }
            return CompletableFuture.completedFuture(value);
        }, (brightness) -> {
            if (taggedItem.getBaseItem() instanceof DimmerItem) {
                taggedItem.sendCommandProxy(HomekitCommandType.BRIGHTNESS_COMMAND, new PercentType(brightness));
            } else {
                logger.warn("Item type {} is not supported for {}. Only ColorItem and DimmerItem are supported.",
                        taggedItem.getBaseItem().getType(), taggedItem.getName());
            }
        }, getSubscriber(taggedItem, BRIGHTNESS, updater), getUnsubscriber(taggedItem, BRIGHTNESS, updater));
    }

    private static SaturationCharacteristic createSaturationCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new SaturationCharacteristic(() -> {
            double value = 0.0;
            State state = taggedItem.getItem().getState();
            if (state instanceof HSBType) {
                value = ((HSBType) state).getSaturation().doubleValue();
            } else if (state instanceof PercentType) {
                value = ((PercentType) state).doubleValue();
            }
            return CompletableFuture.completedFuture(value);
        }, (saturation) -> {
            if (taggedItem.getBaseItem() instanceof ColorItem) {
                taggedItem.sendCommandProxy(HomekitCommandType.SATURATION_COMMAND,
                        new PercentType(saturation.intValue()));
            } else {
                logger.warn("Item type {} is not supported for {}. Only Color type is supported.",
                        taggedItem.getBaseItem().getType(), taggedItem.getName());
            }
        }, getSubscriber(taggedItem, SATURATION, updater), getUnsubscriber(taggedItem, SATURATION, updater));
    }

    private static ColorTemperatureCharacteristic createColorTemperatureCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        final boolean inverted = taggedItem.isInverted();

        int minValue = taggedItem
                .getConfigurationAsQuantity(HomekitTaggedItem.MIN_VALUE,
                        new QuantityType(ColorTemperatureCharacteristic.DEFAULT_MIN_VALUE, Units.MIRED), false)
                .intValue();
        int maxValue = taggedItem
                .getConfigurationAsQuantity(HomekitTaggedItem.MAX_VALUE,
                        new QuantityType(ColorTemperatureCharacteristic.DEFAULT_MAX_VALUE, Units.MIRED), false)
                .intValue();

        // It's common to swap these if you're providing in Kelvin instead of mired
        if (minValue > maxValue) {
            int temp = minValue;
            minValue = maxValue;
            maxValue = temp;
        }

        final int finalMinValue = minValue;
        final int range = maxValue - minValue;

        return new ColorTemperatureCharacteristic(minValue, maxValue, () -> {
            int value = finalMinValue;
            final State state = taggedItem.getItem().getState();
            if (state instanceof QuantityType<?>) {
                // Number:Temperature
                QuantityType<?> qt = (QuantityType<?>) state;
                qt = qt.toInvertibleUnit(Units.MIRED);
                if (qt == null) {
                    logger.warn("Item {}'s state '{}' is not convertible to mireds.", taggedItem.getName(), state);
                } else {
                    value = qt.intValue();
                }
            } else if (state instanceof PercentType) {
                double percent = ((PercentType) state).doubleValue();
                // invert so that 0% == coolest
                if (inverted) {
                    percent = 100.0 - percent;
                }

                // Dimmer
                // scale to the originally configured range
                value = (int) (percent * range / 100) + finalMinValue;
            } else if (state instanceof DecimalType) {
                value = ((DecimalType) state).intValue();
            }
            return CompletableFuture.completedFuture(value);
        }, (value) -> {
            if (taggedItem.getBaseItem() instanceof DimmerItem) {
                // scale to a percent
                double percent = (((double) value) - finalMinValue) * 100 / range;
                if (inverted) {
                    percent = 100.0 - percent;
                }
                taggedItem.send(new PercentType(BigDecimal.valueOf(percent)));
            } else if (taggedItem.getBaseItem() instanceof NumberItem) {
                taggedItem.send(new QuantityType(value, Units.MIRED));
            }
        }, getSubscriber(taggedItem, COLOR_TEMPERATURE, updater),
                getUnsubscriber(taggedItem, COLOR_TEMPERATURE, updater));
    }

    private static CurrentFanStateCharacteristic createCurrentFanStateCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new CurrentFanStateCharacteristic(() -> {
            final @Nullable DecimalType value = taggedItem.getItem().getStateAs(DecimalType.class);
            @Nullable
            CurrentFanStateEnum currentFanStateEnum = value != null ? CurrentFanStateEnum.fromCode(value.intValue())
                    : null;
            if (currentFanStateEnum == null) {
                currentFanStateEnum = CurrentFanStateEnum.INACTIVE;
            }
            return CompletableFuture.completedFuture(currentFanStateEnum);
        }, getSubscriber(taggedItem, CURRENT_FAN_STATE, updater),
                getUnsubscriber(taggedItem, CURRENT_FAN_STATE, updater));
    }

    private static TargetFanStateCharacteristic createTargetFanStateCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new TargetFanStateCharacteristic(
                () -> getEnumFromItem(taggedItem, TargetFanStateEnum.MANUAL, TargetFanStateEnum.AUTO,
                        TargetFanStateEnum.AUTO),
                (targetState) -> setValueFromEnum(taggedItem, targetState, TargetFanStateEnum.MANUAL,
                        TargetFanStateEnum.AUTO),
                getSubscriber(taggedItem, TARGET_FAN_STATE, updater),
                getUnsubscriber(taggedItem, TARGET_FAN_STATE, updater));
    }

    private static RotationDirectionCharacteristic createRotationDirectionCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new RotationDirectionCharacteristic(
                () -> getEnumFromItem(taggedItem, RotationDirectionEnum.CLOCKWISE,
                        RotationDirectionEnum.COUNTER_CLOCKWISE, RotationDirectionEnum.CLOCKWISE),
                (value) -> setValueFromEnum(taggedItem, value, RotationDirectionEnum.CLOCKWISE,
                        RotationDirectionEnum.COUNTER_CLOCKWISE),
                getSubscriber(taggedItem, ROTATION_DIRECTION, updater),
                getUnsubscriber(taggedItem, ROTATION_DIRECTION, updater));
    }

    private static SwingModeCharacteristic createSwingModeCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new SwingModeCharacteristic(
                () -> getEnumFromItem(taggedItem, SwingModeEnum.SWING_DISABLED, SwingModeEnum.SWING_ENABLED,
                        SwingModeEnum.SWING_DISABLED),
                (value) -> setValueFromEnum(taggedItem, value, SwingModeEnum.SWING_DISABLED,
                        SwingModeEnum.SWING_ENABLED),
                getSubscriber(taggedItem, SWING_MODE, updater), getUnsubscriber(taggedItem, SWING_MODE, updater));
    }

    private static LockPhysicalControlsCharacteristic createLockPhysicalControlsCharacteristic(
            HomekitTaggedItem taggedItem, HomekitAccessoryUpdater updater) {
        return new LockPhysicalControlsCharacteristic(
                () -> getEnumFromItem(taggedItem, LockPhysicalControlsEnum.CONTROL_LOCK_DISABLED,
                        LockPhysicalControlsEnum.CONTROL_LOCK_ENABLED, LockPhysicalControlsEnum.CONTROL_LOCK_DISABLED),
                (value) -> setValueFromEnum(taggedItem, value, LockPhysicalControlsEnum.CONTROL_LOCK_DISABLED,
                        LockPhysicalControlsEnum.CONTROL_LOCK_ENABLED),
                getSubscriber(taggedItem, LOCK_CONTROL, updater), getUnsubscriber(taggedItem, LOCK_CONTROL, updater));
    }

    private static RotationSpeedCharacteristic createRotationSpeedCharacteristic(HomekitTaggedItem item,
            HomekitAccessoryUpdater updater) {
        return new RotationSpeedCharacteristic(
                item.getConfigurationAsDouble(HomekitTaggedItem.MIN_VALUE,
                        RotationSpeedCharacteristic.DEFAULT_MIN_VALUE),
                item.getConfigurationAsDouble(HomekitTaggedItem.MAX_VALUE,
                        RotationSpeedCharacteristic.DEFAULT_MAX_VALUE),
                item.getConfigurationAsDouble(HomekitTaggedItem.STEP, RotationSpeedCharacteristic.DEFAULT_STEP),
                getDoubleSupplier(item, 0), setDoubleConsumer(item), getSubscriber(item, ROTATION_SPEED, updater),
                getUnsubscriber(item, ROTATION_SPEED, updater));
    }

    private static SetDurationCharacteristic createDurationCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new SetDurationCharacteristic(() -> {
            int value = getIntFromItem(taggedItem, 0);
            final @Nullable Map<String, Object> itemConfiguration = taggedItem.getConfiguration();
            if ((value == 0) && (itemConfiguration != null)) { // check for default duration
                final Object duration = itemConfiguration.get(HomekitValveImpl.CONFIG_DEFAULT_DURATION);
                if (duration instanceof BigDecimal) {
                    value = ((BigDecimal) duration).intValue();
                    if (taggedItem.getItem() instanceof NumberItem) {
                        ((NumberItem) taggedItem.getItem()).setState(new DecimalType(value));
                    }
                }
            }
            return CompletableFuture.completedFuture(value);
        }, setIntConsumer(taggedItem), getSubscriber(taggedItem, DURATION, updater),
                getUnsubscriber(taggedItem, DURATION, updater));
    }

    private static RemainingDurationCharacteristic createRemainingDurationCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new RemainingDurationCharacteristic(getIntSupplier(taggedItem, 0),
                getSubscriber(taggedItem, REMAINING_DURATION, updater),
                getUnsubscriber(taggedItem, REMAINING_DURATION, updater));
    }

    private static VolumeCharacteristic createVolumeCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new VolumeCharacteristic(getIntSupplier(taggedItem, 0),
                (volume) -> ((NumberItem) taggedItem.getItem()).send(new DecimalType(volume)),
                getSubscriber(taggedItem, DURATION, updater), getUnsubscriber(taggedItem, DURATION, updater));
    }

    private static CoolingThresholdTemperatureCharacteristic createCoolingThresholdCharacteristic(
            HomekitTaggedItem taggedItem, HomekitAccessoryUpdater updater) {
        double minValue = HomekitCharacteristicFactory.convertToCelsius(taggedItem.getConfigurationAsDouble(
                HomekitTaggedItem.MIN_VALUE, CoolingThresholdTemperatureCharacteristic.DEFAULT_MIN_VALUE));
        double maxValue = HomekitCharacteristicFactory.convertToCelsius(taggedItem.getConfigurationAsDouble(
                HomekitTaggedItem.MAX_VALUE, CoolingThresholdTemperatureCharacteristic.DEFAULT_MAX_VALUE));
        double step = getTemperatureStep(taggedItem, CoolingThresholdTemperatureCharacteristic.DEFAULT_STEP);
        return new CoolingThresholdTemperatureCharacteristic(minValue, maxValue, step,
                getTemperatureSupplier(taggedItem, minValue), setTemperatureConsumer(taggedItem),
                getSubscriber(taggedItem, COOLING_THRESHOLD_TEMPERATURE, updater),
                getUnsubscriber(taggedItem, COOLING_THRESHOLD_TEMPERATURE, updater));
    }

    private static HeatingThresholdTemperatureCharacteristic createHeatingThresholdCharacteristic(
            HomekitTaggedItem taggedItem, HomekitAccessoryUpdater updater) {
        double minValue = HomekitCharacteristicFactory.convertToCelsius(taggedItem.getConfigurationAsDouble(
                HomekitTaggedItem.MIN_VALUE, HeatingThresholdTemperatureCharacteristic.DEFAULT_MIN_VALUE));
        double maxValue = HomekitCharacteristicFactory.convertToCelsius(taggedItem.getConfigurationAsDouble(
                HomekitTaggedItem.MAX_VALUE, HeatingThresholdTemperatureCharacteristic.DEFAULT_MAX_VALUE));
        double step = getTemperatureStep(taggedItem, HeatingThresholdTemperatureCharacteristic.DEFAULT_STEP);
        return new HeatingThresholdTemperatureCharacteristic(minValue, maxValue, step,
                getTemperatureSupplier(taggedItem, minValue), setTemperatureConsumer(taggedItem),
                getSubscriber(taggedItem, HEATING_THRESHOLD_TEMPERATURE, updater),
                getUnsubscriber(taggedItem, HEATING_THRESHOLD_TEMPERATURE, updater));
    }

    private static CurrentRelativeHumidityCharacteristic createRelativeHumidityCharacteristic(
            HomekitTaggedItem taggedItem, HomekitAccessoryUpdater updater) {
        return new CurrentRelativeHumidityCharacteristic(getDoubleSupplier(taggedItem, 0.0),
                getSubscriber(taggedItem, RELATIVE_HUMIDITY, updater),
                getUnsubscriber(taggedItem, RELATIVE_HUMIDITY, updater));
    }

    private static OzoneDensityCharacteristic createOzoneDensityCharacteristic(final HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new OzoneDensityCharacteristic(
                getDoubleSupplier(taggedItem,
                        taggedItem.getConfigurationAsDouble(HomekitTaggedItem.MIN_VALUE,
                                OzoneDensityCharacteristic.DEFAULT_MIN_VALUE)),
                getSubscriber(taggedItem, OZONE_DENSITY, updater), getUnsubscriber(taggedItem, OZONE_DENSITY, updater));
    }

    private static NitrogenDioxideDensityCharacteristic createNitrogenDioxideDensityCharacteristic(
            final HomekitTaggedItem taggedItem, HomekitAccessoryUpdater updater) {
        return new NitrogenDioxideDensityCharacteristic(
                getDoubleSupplier(taggedItem,
                        taggedItem.getConfigurationAsDouble(HomekitTaggedItem.MIN_VALUE,
                                NitrogenDioxideDensityCharacteristic.DEFAULT_MIN_VALUE)),
                getSubscriber(taggedItem, NITROGEN_DIOXIDE_DENSITY, updater),
                getUnsubscriber(taggedItem, NITROGEN_DIOXIDE_DENSITY, updater));
    }

    private static SulphurDioxideDensityCharacteristic createSulphurDioxideDensityCharacteristic(
            final HomekitTaggedItem taggedItem, HomekitAccessoryUpdater updater) {
        return new SulphurDioxideDensityCharacteristic(
                getDoubleSupplier(taggedItem,
                        taggedItem.getConfigurationAsDouble(HomekitTaggedItem.MIN_VALUE,
                                SulphurDioxideDensityCharacteristic.DEFAULT_MIN_VALUE)),
                getSubscriber(taggedItem, SULPHUR_DIOXIDE_DENSITY, updater),
                getUnsubscriber(taggedItem, SULPHUR_DIOXIDE_DENSITY, updater));
    }

    private static PM25DensityCharacteristic createPM25DensityCharacteristic(final HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new PM25DensityCharacteristic(
                getDoubleSupplier(taggedItem,
                        taggedItem.getConfigurationAsDouble(HomekitTaggedItem.MIN_VALUE,
                                PM25DensityCharacteristic.DEFAULT_MIN_VALUE)),
                getSubscriber(taggedItem, PM25_DENSITY, updater), getUnsubscriber(taggedItem, PM25_DENSITY, updater));
    }

    private static PM10DensityCharacteristic createPM10DensityCharacteristic(final HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new PM10DensityCharacteristic(
                getDoubleSupplier(taggedItem,
                        taggedItem.getConfigurationAsDouble(HomekitTaggedItem.MIN_VALUE,
                                PM10DensityCharacteristic.DEFAULT_MIN_VALUE)),
                getSubscriber(taggedItem, PM10_DENSITY, updater), getUnsubscriber(taggedItem, PM10_DENSITY, updater));
    }

    private static VOCDensityCharacteristic createVOCDensityCharacteristic(final HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new VOCDensityCharacteristic(
                taggedItem.getConfigurationAsDouble(HomekitTaggedItem.MIN_VALUE,
                        VOCDensityCharacteristic.DEFAULT_MIN_VALUE),
                taggedItem.getConfigurationAsDouble(HomekitTaggedItem.MAX_VALUE,
                        VOCDensityCharacteristic.DEFAULT_MAX_VALUE),
                taggedItem.getConfigurationAsDouble(HomekitTaggedItem.STEP, VOCDensityCharacteristic.DEFAULT_STEP),
                getDoubleSupplier(taggedItem,
                        taggedItem.getConfigurationAsDouble(HomekitTaggedItem.MIN_VALUE,
                                VOCDensityCharacteristic.DEFAULT_MIN_VALUE)),
                getSubscriber(taggedItem, VOC_DENSITY, updater), getUnsubscriber(taggedItem, VOC_DENSITY, updater));
    }

    private static FilterLifeLevelCharacteristic createFilterLifeLevelCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new FilterLifeLevelCharacteristic(getDoubleSupplier(taggedItem, 0),
                getSubscriber(taggedItem, FILTER_LIFE_LEVEL, updater),
                getUnsubscriber(taggedItem, FILTER_LIFE_LEVEL, updater));
    }

    private static ResetFilterIndicationCharacteristic createFilterResetCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new ResetFilterIndicationCharacteristic(
                (value) -> ((SwitchItem) taggedItem.getItem()).send(OnOffType.ON));
    }

    private static ActiveCharacteristic createActiveCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new ActiveCharacteristic(
                () -> getEnumFromItem(taggedItem, ActiveEnum.INACTIVE, ActiveEnum.ACTIVE, ActiveEnum.INACTIVE),
                (value) -> setValueFromEnum(taggedItem, value, ActiveEnum.INACTIVE, ActiveEnum.ACTIVE),
                getSubscriber(taggedItem, ACTIVE, updater), getUnsubscriber(taggedItem, ACTIVE, updater));
    }

    private static ConfiguredNameCharacteristic createConfiguredNameCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new ConfiguredNameCharacteristic(() -> {
            final State state = taggedItem.getItem().getState();
            return CompletableFuture
                    .completedFuture(state instanceof UnDefType ? taggedItem.getName() : state.toString());
        }, (value) -> ((StringItem) taggedItem.getItem()).send(new StringType(value)),
                getSubscriber(taggedItem, CONFIGURED_NAME, updater),
                getUnsubscriber(taggedItem, CONFIGURED_NAME, updater));
    }

    private static ActiveIdentifierCharacteristic createActiveIdentifierCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new ActiveIdentifierCharacteristic(getIntSupplier(taggedItem, 1), setIntConsumer(taggedItem),
                getSubscriber(taggedItem, ACTIVE_IDENTIFIER, updater),
                getUnsubscriber(taggedItem, ACTIVE_IDENTIFIER, updater));
    }

    private static RemoteKeyCharacteristic createRemoteKeyCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        var map = createMapping(taggedItem, RemoteKeyEnum.class);
        return new RemoteKeyCharacteristic((value) -> setValueFromEnum(taggedItem, value, map));
    }

    private static SleepDiscoveryModeCharacteristic createSleepDiscoveryModeCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new SleepDiscoveryModeCharacteristic(
                () -> getEnumFromItem(taggedItem, SleepDiscoveryModeEnum.NOT_DISCOVERABLE,
                        SleepDiscoveryModeEnum.ALWAYS_DISCOVERABLE, SleepDiscoveryModeEnum.ALWAYS_DISCOVERABLE),
                getSubscriber(taggedItem, SLEEP_DISCOVERY_MODE, updater),
                getUnsubscriber(taggedItem, SLEEP_DISCOVERY_MODE, updater));
    }

    private static PowerModeCharacteristic createPowerModeCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new PowerModeCharacteristic(
                (value) -> setValueFromEnum(taggedItem, value, PowerModeEnum.HIDE, PowerModeEnum.SHOW));
    }

    private static ClosedCaptionsCharacteristic createClosedCaptionsCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new ClosedCaptionsCharacteristic(
                () -> getEnumFromItem(taggedItem, ClosedCaptionsEnum.DISABLED, ClosedCaptionsEnum.ENABLED,
                        ClosedCaptionsEnum.DISABLED),
                (value) -> setValueFromEnum(taggedItem, value, ClosedCaptionsEnum.DISABLED, ClosedCaptionsEnum.ENABLED),
                getSubscriber(taggedItem, CLOSED_CAPTIONS, updater),
                getUnsubscriber(taggedItem, CLOSED_CAPTIONS, updater));
    }

    private static PictureModeCharacteristic createPictureModeCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        var map = createMapping(taggedItem, PictureModeEnum.class);
        return new PictureModeCharacteristic(() -> getEnumFromItem(taggedItem, map, PictureModeEnum.OTHER),
                (value) -> setValueFromEnum(taggedItem, value, map), getSubscriber(taggedItem, PICTURE_MODE, updater),
                getUnsubscriber(taggedItem, PICTURE_MODE, updater));
    }

    private static IsConfiguredCharacteristic createIsConfiguredCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new IsConfiguredCharacteristic(
                () -> getEnumFromItem(taggedItem, IsConfiguredEnum.NOT_CONFIGURED, IsConfiguredEnum.CONFIGURED,
                        IsConfiguredEnum.NOT_CONFIGURED),
                (value) -> setValueFromEnum(taggedItem, value, IsConfiguredEnum.NOT_CONFIGURED,
                        IsConfiguredEnum.CONFIGURED),
                getSubscriber(taggedItem, CONFIGURED, updater), getUnsubscriber(taggedItem, CONFIGURED, updater));
    }

    private static InputSourceTypeCharacteristic createInputSourceTypeCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        var map = createMapping(taggedItem, InputSourceTypeEnum.class);
        return new InputSourceTypeCharacteristic(() -> getEnumFromItem(taggedItem, map, InputSourceTypeEnum.OTHER),
                getSubscriber(taggedItem, INPUT_SOURCE_TYPE, updater),
                getUnsubscriber(taggedItem, INPUT_SOURCE_TYPE, updater));
    }

    private static CurrentVisibilityStateCharacteristic createCurrentVisibilityStateCharacteristic(
            HomekitTaggedItem taggedItem, HomekitAccessoryUpdater updater) {
        return new CurrentVisibilityStateCharacteristic(
                () -> getEnumFromItem(taggedItem, CurrentVisibilityStateEnum.HIDDEN, CurrentVisibilityStateEnum.SHOWN,
                        CurrentVisibilityStateEnum.HIDDEN),
                getSubscriber(taggedItem, CURRENT_VISIBILITY, updater),
                getUnsubscriber(taggedItem, CURRENT_VISIBILITY, updater));
    }

    private static IdentifierCharacteristic createIdentifierCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        return new IdentifierCharacteristic(getIntSupplier(taggedItem, 1));
    }

    private static InputDeviceTypeCharacteristic createInputDeviceTypeCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        var mapping = createMapping(taggedItem, InputDeviceTypeEnum.class);
        return new InputDeviceTypeCharacteristic(() -> getEnumFromItem(taggedItem, mapping, InputDeviceTypeEnum.OTHER),
                getSubscriber(taggedItem, INPUT_DEVICE_TYPE, updater),
                getUnsubscriber(taggedItem, INPUT_DEVICE_TYPE, updater));
    }

    private static TargetVisibilityStateCharacteristic createTargetVisibilityStateCharacteristic(
            HomekitTaggedItem taggedItem, HomekitAccessoryUpdater updater) {
        return new TargetVisibilityStateCharacteristic(
                () -> getEnumFromItem(taggedItem, TargetVisibilityStateEnum.HIDDEN, TargetVisibilityStateEnum.SHOWN,
                        TargetVisibilityStateEnum.HIDDEN),
                (value) -> setValueFromEnum(taggedItem, value, TargetVisibilityStateEnum.HIDDEN,
                        TargetVisibilityStateEnum.SHOWN),
                getSubscriber(taggedItem, TARGET_VISIBILITY_STATE, updater),
                getUnsubscriber(taggedItem, TARGET_VISIBILITY_STATE, updater));
    }

    private static VolumeSelectorCharacteristic createVolumeSelectorCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        if (taggedItem.getItem() instanceof DimmerItem) {
            return new VolumeSelectorCharacteristic((value) -> taggedItem
                    .send(value.equals(VolumeSelectorEnum.INCREMENT) ? IncreaseDecreaseType.INCREASE
                            : IncreaseDecreaseType.DECREASE));
        } else {
            var map = createMapping(taggedItem, VolumeSelectorEnum.class);
            return new VolumeSelectorCharacteristic((value) -> setValueFromEnum(taggedItem, value, map));
        }
    }

    private static VolumeControlTypeCharacteristic createVolumeControlTypeCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        var map = createMapping(taggedItem, VolumeControlTypeEnum.class);
        return new VolumeControlTypeCharacteristic(() -> getEnumFromItem(taggedItem, map, VolumeControlTypeEnum.NONE),
                getSubscriber(taggedItem, VOLUME_CONTROL_TYPE, updater),
                getUnsubscriber(taggedItem, VOLUME_CONTROL_TYPE, updater));
    }

    private static CurrentMediaStateCharacteristic createCurrentMediaStateCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        var map = createMapping(taggedItem, CurrentMediaStateEnum.class);
        return new CurrentMediaStateCharacteristic(
                () -> getEnumFromItem(taggedItem, map, CurrentMediaStateEnum.UNKNOWN),
                getSubscriber(taggedItem, CURRENT_MEDIA_STATE, updater),
                getUnsubscriber(taggedItem, CURRENT_MEDIA_STATE, updater));
    }

    private static TargetMediaStateCharacteristic createTargetMediaStateCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        var map = createMapping(taggedItem, TargetMediaStateEnum.class);
        return new TargetMediaStateCharacteristic(() -> getEnumFromItem(taggedItem, map, TargetMediaStateEnum.STOP),
                (value) -> setValueFromEnum(taggedItem, value, map),
                getSubscriber(taggedItem, TARGET_MEDIA_STATE, updater),
                getUnsubscriber(taggedItem, TARGET_MEDIA_STATE, updater));
    }

    private static MuteCharacteristic createMuteCharacteristic(HomekitTaggedItem taggedItem,
            HomekitAccessoryUpdater updater) {
        BooleanItemReader muteReader = new BooleanItemReader(taggedItem.getItem(),
                taggedItem.isInverted() ? OnOffType.OFF : OnOffType.ON,
                taggedItem.isInverted() ? OpenClosedType.CLOSED : OpenClosedType.OPEN);
        return new MuteCharacteristic(() -> CompletableFuture.completedFuture(muteReader.getValue()),
                (value) -> taggedItem.send(value ? OnOffType.ON : OnOffType.OFF),
                getSubscriber(taggedItem, MUTE, updater), getUnsubscriber(taggedItem, MUTE, updater));
    }
}
