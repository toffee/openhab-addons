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
package org.openhab.binding.snapcast.internal.protocol;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.snapcast.internal.data.Group;
import org.openhab.binding.snapcast.internal.data.Params;
import org.openhab.binding.snapcast.internal.data.Server;
import org.openhab.binding.snapcast.internal.data.Server.ServerAttributes;
import org.openhab.binding.snapcast.internal.data.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server protocol handler
 *
 * @author Steffen Brandemann - Initial contribution
 */
@NonNullByDefault
public class ServerController extends AbstractController<ServerAttributes, ServerListener> {

    private final Logger logger = LoggerFactory.getLogger(ServerController.class);

    /**
     * @param controller The main snapcast controller
     */
    ServerController(SnapcastController controller) {
        super(controller);

        controller.registerNotifyListener("Server.OnUpdate", Params.class, this::handleUpdate);
    }

    /**
     * Request the server status
     */
    public void getStatus() {
        getController().sendRequest("Server.GetStatus", Params.class, this::handleUpdate);
    }

    /**
     * Handles an established connection
     */
    void connected() {
        eachListener(null, listener -> listener.updateConnection(true));
        getStatus();
    }

    /**
     * Handles a closed connection
     */
    void disconnected() {
        eachListener(null, listener -> listener.updateConnection(false));
    }

    /**
     * Handle an incoming status update
     *
     * @param params the data structure from a response or notification
     */
    void handleUpdate(Params params) {
        Server server = params.getServer();
        if (server != null) {
            handleUpdate(server);
        }
    }

    /**
     * Handle an incoming status update
     *
     * @param server the data structure of a server
     */
    void handleUpdate(Server server) {
        StreamController streamController = getController().streamController();
        GroupController groupController = getController().groupController();
        ClientController clientController = getController().clientController();

        resetThingState();
        streamController.resetThingState();
        groupController.resetThingState();
        clientController.resetThingState();

        // update details
        ServerAttributes attributes = server.getServer();
        if (attributes != null) {
            handleUpdate(attributes);
        }

        // update streams
        List<Stream> streams = server.getStreams();
        if (streams != null) {
            for (Stream stream : streams) {
                streamController.handleUpdate(stream);
            }
        }

        // update groups
        List<Group> groups = server.getGroups();
        if (groups != null) {
            for (Group group : groups) {
                groupController.handleUpdate(group);
            }
        }
    }

    /**
     * Handle an incoming status update
     *
     * @param params data structure of a server
     */
    void handleUpdate(ServerAttributes params) {
        updateThingState(params);
    }
}
