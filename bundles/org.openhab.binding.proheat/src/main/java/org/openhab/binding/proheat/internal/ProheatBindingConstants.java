/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.proheat.internal;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link ProheatBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Olivian Daniel Tofan - Initial contribution
 */
@NonNullByDefault
public class ProheatBindingConstants {

    private static final String BINDING_ID = "proheat";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_HCC02 = new ThingTypeUID(BINDING_ID, "hcc02");

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Stream
            .of(ProheatBindingConstants.THING_TYPE_HCC02).collect(Collectors.toSet());

    // List of all Channel ids
    public static final String EVENTS_RECEIVED = "statistics#events_received";
    public static final String EVENTS_RECEIVED_LASTTIME = "statistics#events_received_lasttime";

    public static final String DEVICE_ENABLED = "device#enabled";
    public static final String DEVICE_MODE_AUTO = "device#mode_auto";
    public static final String DEVICE_MOISTURE_ENABLED = "device#moisture_enabled";
    public static final String DEVICE_TEMPERATURE_VALUE = "device#temperature_value";
    public static final String DEVICE_MOISTURE_VALUE = "device#moisture_value";
    public static final String DEVICE_MOISTURE_VALUE_TEXT = "device#moisture_value_text";
    public static final String DEVICE_HEATING = "device#heating";
    public static final String DEVICE_HEATING_PLANNED = "device#heating_planned";
    public static final String DEVICE_HEATING_REMAINED = "device#heating_remained";
}
