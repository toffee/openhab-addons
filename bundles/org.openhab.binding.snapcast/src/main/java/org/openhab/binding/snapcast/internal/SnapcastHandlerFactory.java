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
package org.openhab.binding.snapcast.internal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.snapcast.internal.discovery.ClientDiscoveryService;
import org.openhab.binding.snapcast.internal.handler.SnapcastClientHandler;
import org.openhab.binding.snapcast.internal.handler.SnapcastServerHandler;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Component;

/**
 * The {@link SnapcastHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Steffen Brandemann - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.snapcast", service = ThingHandlerFactory.class)
public class SnapcastHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = new HashSet<>(
            Arrays.asList(SnapcastBindingConstants.THING_TYPE_SERVER, SnapcastBindingConstants.THING_TYPE_CLIENT));

    private final Map<ThingUID, @Nullable ServiceRegistration<?>> discoveryServices = new HashMap<>();

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (SnapcastBindingConstants.THING_TYPE_SERVER.equals(thingTypeUID)) {
            SnapcastServerHandler serverHandler = new SnapcastServerHandler((Bridge) thing);
            activateClientDiscoveryService(serverHandler);
            return serverHandler;
        }

        if (SnapcastBindingConstants.THING_TYPE_CLIENT.equals(thingTypeUID)) {
            return new SnapcastClientHandler(thing);
        }

        return null;
    }

    @Override
    protected void removeHandler(ThingHandler thingHandler) {
        if (thingHandler instanceof SnapcastServerHandler) {
            deactivateClientDiscoveryService((SnapcastServerHandler) thingHandler);
        }
        super.removeHandler(thingHandler);
    }

    private synchronized void activateClientDiscoveryService(SnapcastServerHandler serverHandler) {
        ClientDiscoveryService discoveryService = new ClientDiscoveryService(serverHandler);
        discoveryService.activate();

        this.discoveryServices.put(serverHandler.getThing().getUID(), bundleContext
                .registerService(DiscoveryService.class.getName(), discoveryService, new Hashtable<String, Object>()));
    }

    private synchronized void deactivateClientDiscoveryService(SnapcastServerHandler serverHandler) {
        ServiceRegistration<?> serviceRegistration = this.discoveryServices.remove(serverHandler.getThing().getUID());
        if (serviceRegistration != null) {
            ClientDiscoveryService discoveryService = (ClientDiscoveryService) bundleContext
                    .getService(serviceRegistration.getReference());

            serviceRegistration.unregister();

            if (discoveryService != null) {
                discoveryService.deactivate();
            }
        }
    }
}
