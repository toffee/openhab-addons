/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
package org.openhab.binding.unifi.internal.handler;

import static org.openhab.binding.unifi.internal.UniFiBindingConstants.CHANNEL_ENABLE_PARAMETER_MODE;
import static org.openhab.binding.unifi.internal.UniFiBindingConstants.CHANNEL_ENABLE_PARAMETER_MODE_AUTO;
import static org.openhab.binding.unifi.internal.UniFiBindingConstants.CHANNEL_ENABLE_PARAMETER_MODE_OFF;
import static org.openhab.binding.unifi.internal.UniFiBindingConstants.CHANNEL_ONLINE;
import static org.openhab.binding.unifi.internal.UniFiBindingConstants.CHANNEL_PORT_POE_CMD;
import static org.openhab.binding.unifi.internal.UniFiBindingConstants.CHANNEL_PORT_POE_CMD_POWER_CYCLE;
import static org.openhab.binding.unifi.internal.UniFiBindingConstants.CHANNEL_PORT_POE_CURRENT;
import static org.openhab.binding.unifi.internal.UniFiBindingConstants.CHANNEL_PORT_POE_ENABLE;
import static org.openhab.binding.unifi.internal.UniFiBindingConstants.CHANNEL_PORT_POE_MODE;
import static org.openhab.binding.unifi.internal.UniFiBindingConstants.CHANNEL_PORT_POE_POWER;
import static org.openhab.binding.unifi.internal.UniFiBindingConstants.CHANNEL_PORT_POE_VOLTAGE;
import static org.openhab.core.library.unit.MetricPrefix.MILLI;

import java.util.HashMap;
import java.util.Map;

import javax.measure.quantity.ElectricCurrent;
import javax.measure.quantity.ElectricPotential;
import javax.measure.quantity.Power;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.unifi.internal.UniFiPoePortThingConfig;
import org.openhab.binding.unifi.internal.api.UniFiController;
import org.openhab.binding.unifi.internal.api.UniFiException;
import org.openhab.binding.unifi.internal.api.cache.UniFiControllerCache;
import org.openhab.binding.unifi.internal.api.dto.UnfiPortOverride;
import org.openhab.binding.unifi.internal.api.dto.UniFiDevice;
import org.openhab.binding.unifi.internal.api.dto.UniFiPortTable;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Power Over Ethernet (PoE) port on a UniFi switch.
 *
 * @author Hilbrand Bouwkamp - Initial contribution
 */
@NonNullByDefault
public class UniFiPoePortThingHandler
        extends UniFiBaseThingHandler<Map<Integer, UniFiPortTable>, UniFiPoePortThingConfig> {

    private final Logger logger = LoggerFactory.getLogger(UniFiPoePortThingHandler.class);

    private UniFiPoePortThingConfig config = new UniFiPoePortThingConfig();
    private String poeEnableMode = "";

    public UniFiPoePortThingHandler(final Thing thing) {
        super(thing);
    }

    @Override
    protected boolean initialize(final UniFiPoePortThingConfig config) {
        this.config = config;
        if (!config.isValid()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/error.thing.poe.offline.configuration_error");
            return false;
        }
        final String channelConfigPoeEnableMode = (String) getThing().getChannel(CHANNEL_PORT_POE_ENABLE)
                .getConfiguration().get(CHANNEL_ENABLE_PARAMETER_MODE);
        poeEnableMode = channelConfigPoeEnableMode.isBlank() ? CHANNEL_ENABLE_PARAMETER_MODE_AUTO
                : channelConfigPoeEnableMode;
        return true;
    }

    @Override
    protected @Nullable Map<Integer, UniFiPortTable> getEntity(final UniFiControllerCache cache) {
        return cache.getSwitchPorts(config.getMacAddress());
    }

    @Override
    protected State getChannelState(final Map<Integer, UniFiPortTable> ports, final String channelId) {
        final UniFiPortTable port = getPort(ports);

        if (port == null) {
            logger.debug("No PoE port for thing '{}' could be found in the data. Refresh ignored.",
                    getThing().getUID());
            return UnDefType.NULL;
        }
        final State state;

        switch (channelId) {
            case CHANNEL_ONLINE:
                state = OnOffType.from(port.isUp());
                break;
            case CHANNEL_PORT_POE_ENABLE:
                state = OnOffType.from(port.isPoeEnabled());
                break;
            case CHANNEL_PORT_POE_MODE:
                state = StringType.valueOf(port.getPoeMode());
                break;
            case CHANNEL_PORT_POE_POWER:
                state = new QuantityType<Power>(Double.valueOf(port.getPoePower()), Units.WATT);
                break;
            case CHANNEL_PORT_POE_VOLTAGE:
                state = new QuantityType<ElectricPotential>(Double.valueOf(port.getPoeVoltage()), Units.VOLT);
                break;
            case CHANNEL_PORT_POE_CURRENT:
                state = new QuantityType<ElectricCurrent>(Double.valueOf(port.getPoeCurrent()), MILLI(Units.AMPERE));
                break;
            default:
                state = UnDefType.UNDEF;
        }
        return state;
    }

    private @Nullable UniFiPortTable getPort(final Map<Integer, UniFiPortTable> ports) {
        return ports.get(config.getPortNumber());
    }

    @Override
    protected boolean handleCommand(final UniFiController controller, final Map<Integer, UniFiPortTable> ports,
            final ChannelUID channelUID, final Command command) throws UniFiException {
        final String channelID = channelUID.getIdWithoutGroup();

        switch (channelID) {
            case CHANNEL_PORT_POE_ENABLE:
                if (command instanceof OnOffType) {
                    return handleModeCommand(controller, ports, getPort(ports),
                            OnOffType.ON == command ? poeEnableMode : CHANNEL_ENABLE_PARAMETER_MODE_OFF);
                }
                break;
            case CHANNEL_PORT_POE_MODE:
                if (command instanceof StringType) {
                    return handleModeCommand(controller, ports, getPort(ports), command.toFullString());
                }
                break;
            case CHANNEL_PORT_POE_CMD:
                if (command instanceof StringType) {
                    return handleCmd(controller, getPort(ports), command.toFullString());
                }
            default:
                return false;
        }
        return false;
    }

    private boolean handleModeCommand(final UniFiController controller, final Map<Integer, UniFiPortTable> ports,
            final @Nullable UniFiPortTable portToUpdate, final String poeMode) throws UniFiException {
        final UniFiDevice device = controller.getCache().getDevice(config.getMacAddress());

        if (device == null || portToUpdate == null) {
            logger.info("Could not change the PoE port state for thing '{}': device {} or portToUpdate {} null",
                    getThing().getUID(), device, portToUpdate);
            return false;
        } else {
            final UnfiPortOverride override = new UnfiPortOverride();
            override.setPortIdx(portToUpdate.getPortIdx());
            override.setPortconfId(portToUpdate.getPortconfId());
            override.setPoeMode(poeMode);
            final Map<Integer, UnfiPortOverride> newMap = new HashMap<>(ports);

            newMap.put(portToUpdate.getPortIdx(), override);
            controller.poeMode(device, newMap);
            refresh();
            return true;
        }
    }

    private boolean handleCmd(final UniFiController controller, @Nullable final UniFiPortTable portToUpdate,
            final String command) throws UniFiException {
        final UniFiDevice device = controller.getCache().getDevice(config.getMacAddress());
        if (device == null || portToUpdate == null) {
            logger.info("Could not change the PoE port state for thing '{}': device {} or portToUpdate {} null",
                    getThing().getUID(), device, portToUpdate);
            return false;
        } else {
            if (CHANNEL_PORT_POE_CMD_POWER_CYCLE.equalsIgnoreCase(command.replaceAll("[- ]", ""))) {
                controller.poePowerCycle(device, portToUpdate.getPortIdx());
                return true;
            } else {
                logger.info("Unknown command '{}' given to PoE port for thing '{}': device {} or portToUpdate {} null",
                        command, getThing().getUID(), device, portToUpdate);
                return false;
            }
        }
    }
}
