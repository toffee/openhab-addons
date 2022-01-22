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
package org.openhab.binding.hdpowerview.internal;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.hdpowerview.internal.api.ShadePosition;
import org.openhab.binding.hdpowerview.internal.api.requests.ShadeCalibrate;
import org.openhab.binding.hdpowerview.internal.api.requests.ShadeMove;
import org.openhab.binding.hdpowerview.internal.api.requests.ShadeStop;
import org.openhab.binding.hdpowerview.internal.api.responses.FirmwareVersion;
import org.openhab.binding.hdpowerview.internal.api.responses.FirmwareVersions;
import org.openhab.binding.hdpowerview.internal.api.responses.SceneCollections;
import org.openhab.binding.hdpowerview.internal.api.responses.SceneCollections.SceneCollection;
import org.openhab.binding.hdpowerview.internal.api.responses.Scenes;
import org.openhab.binding.hdpowerview.internal.api.responses.Scenes.Scene;
import org.openhab.binding.hdpowerview.internal.api.responses.ScheduledEvents;
import org.openhab.binding.hdpowerview.internal.api.responses.ScheduledEvents.ScheduledEvent;
import org.openhab.binding.hdpowerview.internal.api.responses.Shade;
import org.openhab.binding.hdpowerview.internal.api.responses.Shades;
import org.openhab.binding.hdpowerview.internal.api.responses.Shades.ShadeData;
import org.openhab.binding.hdpowerview.internal.api.responses.Survey;
import org.openhab.binding.hdpowerview.internal.exceptions.HubInvalidResponseException;
import org.openhab.binding.hdpowerview.internal.exceptions.HubMaintenanceException;
import org.openhab.binding.hdpowerview.internal.exceptions.HubProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * JAX-RS targets for communicating with an HD PowerView hub
 *
 * @author Andy Lintner - Initial contribution
 * @author Andrew Fiddian-Green - Added support for secondary rail positions
 * @author Jacob Laursen - Added support for scene groups and automations
 */
@NonNullByDefault
public class HDPowerViewWebTargets {

    private final Logger logger = LoggerFactory.getLogger(HDPowerViewWebTargets.class);

    /*
     * the hub returns a 423 error (resource locked) daily just after midnight;
     * which means it is temporarily undergoing maintenance; so we use "soft"
     * exception handling during the five minute maintenance period after a 423
     * error is received
     */
    private final int maintenancePeriod = 300;
    private Instant maintenanceScheduledEnd = Instant.now().minusSeconds(2 * maintenancePeriod);

    private final String base;
    private final String firmwareVersion;
    private final String shades;
    private final String sceneActivate;
    private final String scenes;
    private final String sceneCollectionActivate;
    private final String sceneCollections;
    private final String scheduledEvents;

    private final Gson gson = new Gson();
    private final HttpClient httpClient;

    /**
     * private helper class for passing http url query parameters
     */
    private static class Query {
        private final String key;
        private final String value;

        private Query(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public static Query of(String key, String value) {
            return new Query(key, value);
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.format("?%s=%s", key, value);
        }
    }

    /**
     * Initialize the web targets
     *
     * @param httpClient the HTTP client (the binding)
     * @param ipAddress the IP address of the server (the hub)
     */
    public HDPowerViewWebTargets(HttpClient httpClient, String ipAddress) {
        base = "http://" + ipAddress + "/api/";
        shades = base + "shades/";
        firmwareVersion = base + "fwversion/";
        sceneActivate = base + "scenes";
        scenes = base + "scenes/";

        // Hub v1 only supports "scenecollections". Hub v2 will redirect to "sceneCollections".
        sceneCollectionActivate = base + "scenecollections";
        sceneCollections = base + "scenecollections/";

        scheduledEvents = base + "scheduledevents";
        this.httpClient = httpClient;
    }

    /**
     * Fetches a JSON package with firmware information for the hub.
     *
     * @return FirmwareVersions class instance
     * @throws HubInvalidResponseException if response is invalid
     * @throws HubProcessingException if there is any processing error
     * @throws HubMaintenanceException if the hub is down for maintenance
     */
    public FirmwareVersions getFirmwareVersions()
            throws HubInvalidResponseException, HubProcessingException, HubMaintenanceException {
        String json = invoke(HttpMethod.GET, firmwareVersion, null, null);
        try {
            FirmwareVersion firmwareVersion = gson.fromJson(json, FirmwareVersion.class);
            if (firmwareVersion == null) {
                throw new HubInvalidResponseException("Missing firmware response");
            }
            FirmwareVersions firmwareVersions = firmwareVersion.firmware;
            if (firmwareVersions == null) {
                throw new HubInvalidResponseException("Missing 'firmware' element");
            }
            return firmwareVersions;
        } catch (JsonParseException e) {
            throw new HubInvalidResponseException("Error parsing firmware response", e);
        }
    }

    /**
     * Fetches a JSON package that describes all shades in the hub, and wraps it in
     * a Shades class instance
     *
     * @return Shades class instance
     * @throws HubInvalidResponseException if response is invalid
     * @throws HubProcessingException if there is any processing error
     * @throws HubMaintenanceException if the hub is down for maintenance
     */
    public Shades getShades() throws HubInvalidResponseException, HubProcessingException, HubMaintenanceException {
        String json = invoke(HttpMethod.GET, shades, null, null);
        try {
            Shades shades = gson.fromJson(json, Shades.class);
            if (shades == null) {
                throw new HubInvalidResponseException("Missing shades response");
            }
            List<ShadeData> shadeData = shades.shadeData;
            if (shadeData == null) {
                throw new HubInvalidResponseException("Missing 'shades.shadeData' element");
            }
            return shades;
        } catch (JsonParseException e) {
            throw new HubInvalidResponseException("Error parsing shades response", e);
        }
    }

    /**
     * Instructs the hub to move a specific shade
     *
     * @param shadeId id of the shade to be moved
     * @param position instance of ShadePosition containing the new position
     * @return ShadeData class instance (with new position)
     * @throws HubInvalidResponseException if response is invalid
     * @throws HubProcessingException if there is any processing error
     * @throws HubMaintenanceException if the hub is down for maintenance
     */
    public ShadeData moveShade(int shadeId, ShadePosition position)
            throws HubInvalidResponseException, HubProcessingException, HubMaintenanceException {
        String jsonRequest = gson.toJson(new ShadeMove(position));
        String jsonResponse = invoke(HttpMethod.PUT, shades + Integer.toString(shadeId), null, jsonRequest);
        return shadeDataFromJson(jsonResponse);
    }

    private ShadeData shadeDataFromJson(String json) throws HubInvalidResponseException {
        try {
            Shade shade = gson.fromJson(json, Shade.class);
            if (shade == null) {
                throw new HubInvalidResponseException("Missing shade response");
            }
            ShadeData shadeData = shade.shade;
            if (shadeData == null) {
                throw new HubInvalidResponseException("Missing 'shade.shade' element");
            }
            return shadeData;
        } catch (JsonParseException e) {
            throw new HubInvalidResponseException("Error parsing shade response", e);
        }
    }

    /**
     * Instructs the hub to stop movement of a specific shade
     *
     * @param shadeId id of the shade to be stopped
     * @return ShadeData class instance (new position cannot be relied upon)
     * @throws HubInvalidResponseException if response is invalid
     * @throws HubProcessingException if there is any processing error
     * @throws HubMaintenanceException if the hub is down for maintenance
     */
    public ShadeData stopShade(int shadeId)
            throws HubInvalidResponseException, HubProcessingException, HubMaintenanceException {
        String jsonRequest = gson.toJson(new ShadeStop());
        String jsonResponse = invoke(HttpMethod.PUT, shades + Integer.toString(shadeId), null, jsonRequest);
        return shadeDataFromJson(jsonResponse);
    }

    /**
     * Instructs the hub to calibrate a specific shade
     *
     * @param shadeId id of the shade to be calibrated
     * @return ShadeData class instance
     * @throws HubInvalidResponseException if response is invalid
     * @throws HubProcessingException if there is any processing error
     * @throws HubMaintenanceException if the hub is down for maintenance
     */
    public ShadeData calibrateShade(int shadeId)
            throws HubInvalidResponseException, HubProcessingException, HubMaintenanceException {
        String jsonRequest = gson.toJson(new ShadeCalibrate());
        String jsonResponse = invoke(HttpMethod.PUT, shades + Integer.toString(shadeId), null, jsonRequest);
        return shadeDataFromJson(jsonResponse);
    }

    /**
     * Fetches a JSON package that describes all scenes in the hub, and wraps it in
     * a Scenes class instance
     *
     * @return Scenes class instance
     * @throws HubInvalidResponseException if response is invalid
     * @throws HubProcessingException if there is any processing error
     * @throws HubMaintenanceException if the hub is down for maintenance
     */
    public Scenes getScenes() throws HubInvalidResponseException, HubProcessingException, HubMaintenanceException {
        String json = invoke(HttpMethod.GET, scenes, null, null);
        try {
            Scenes scenes = gson.fromJson(json, Scenes.class);
            if (scenes == null) {
                throw new HubInvalidResponseException("Missing scenes response");
            }
            List<Scene> sceneData = scenes.sceneData;
            if (sceneData == null) {
                throw new HubInvalidResponseException("Missing 'scenes.sceneData' element");
            }
            return scenes;
        } catch (JsonParseException e) {
            throw new HubInvalidResponseException("Error parsing scenes response", e);
        }
    }

    /**
     * Instructs the hub to execute a specific scene
     *
     * @param sceneId id of the scene to be executed
     * @throws HubProcessingException if there is any processing error
     * @throws HubMaintenanceException if the hub is down for maintenance
     */
    public void activateScene(int sceneId) throws HubProcessingException, HubMaintenanceException {
        invoke(HttpMethod.GET, sceneActivate, Query.of("sceneId", Integer.toString(sceneId)), null);
    }

    /**
     * Fetches a JSON package that describes all scene collections in the hub, and wraps it in
     * a SceneCollections class instance
     *
     * @return SceneCollections class instance
     * @throws HubInvalidResponseException if response is invalid
     * @throws HubProcessingException if there is any processing error
     * @throws HubMaintenanceException if the hub is down for maintenance
     */
    public SceneCollections getSceneCollections()
            throws HubInvalidResponseException, HubProcessingException, HubMaintenanceException {
        String json = invoke(HttpMethod.GET, sceneCollections, null, null);
        try {
            SceneCollections sceneCollections = gson.fromJson(json, SceneCollections.class);
            if (sceneCollections == null) {
                throw new HubInvalidResponseException("Missing sceneCollections response");
            }
            List<SceneCollection> sceneCollectionData = sceneCollections.sceneCollectionData;
            if (sceneCollectionData == null) {
                throw new HubInvalidResponseException("Missing 'sceneCollections.sceneCollectionData' element");
            }
            return sceneCollections;
        } catch (JsonParseException e) {
            throw new HubInvalidResponseException("Error parsing sceneCollections response", e);
        }
    }

    /**
     * Instructs the hub to execute a specific scene collection
     *
     * @param sceneCollectionId id of the scene collection to be executed
     * @throws HubProcessingException if there is any processing error
     * @throws HubMaintenanceException if the hub is down for maintenance
     */
    public void activateSceneCollection(int sceneCollectionId) throws HubProcessingException, HubMaintenanceException {
        invoke(HttpMethod.GET, sceneCollectionActivate,
                Query.of("sceneCollectionId", Integer.toString(sceneCollectionId)), null);
    }

    /**
     * Fetches a JSON package that describes all scheduled events in the hub, and wraps it in
     * a ScheduledEvents class instance
     *
     * @return ScheduledEvents class instance
     * @throws HubInvalidResponseException if response is invalid
     * @throws HubProcessingException if there is any processing error
     * @throws HubMaintenanceException if the hub is down for maintenance
     */
    public ScheduledEvents getScheduledEvents()
            throws HubInvalidResponseException, HubProcessingException, HubMaintenanceException {
        String json = invoke(HttpMethod.GET, scheduledEvents, null, null);
        try {
            ScheduledEvents scheduledEvents = gson.fromJson(json, ScheduledEvents.class);
            if (scheduledEvents == null) {
                throw new HubInvalidResponseException("Missing scheduledEvents response");
            }
            List<ScheduledEvent> scheduledEventData = scheduledEvents.scheduledEventData;
            if (scheduledEventData == null) {
                throw new HubInvalidResponseException("Missing 'scheduledEvents.scheduledEventData' element");
            }
            return scheduledEvents;
        } catch (JsonParseException e) {
            throw new HubInvalidResponseException("Error parsing scheduledEvents response", e);
        }
    }

    /**
     * Enables or disables a scheduled event in the hub.
     * 
     * @param scheduledEventId id of the scheduled event to be enabled or disabled
     * @param enable true to enable scheduled event, false to disable
     * @throws HubInvalidResponseException if response is invalid
     * @throws HubProcessingException if there is any processing error
     * @throws HubMaintenanceException if the hub is down for maintenance
     */
    public void enableScheduledEvent(int scheduledEventId, boolean enable)
            throws HubInvalidResponseException, HubProcessingException, HubMaintenanceException {
        String uri = scheduledEvents + "/" + scheduledEventId;
        String jsonResponse = invoke(HttpMethod.GET, uri, null, null);
        try {
            JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
            JsonElement scheduledEventElement = jsonObject.get("scheduledEvent");
            if (scheduledEventElement == null) {
                throw new HubInvalidResponseException("Missing 'scheduledEvent' element");
            }
            JsonObject scheduledEventObject = scheduledEventElement.getAsJsonObject();
            scheduledEventObject.addProperty("enabled", enable);
            invoke(HttpMethod.PUT, uri, null, jsonObject.toString());
        } catch (JsonParseException | IllegalStateException e) {
            throw new HubInvalidResponseException("Error parsing scheduledEvent response", e);
        }
    }

    /**
     * Invoke a call on the hub server to retrieve information or send a command
     *
     * @param method GET or PUT
     * @param url the host url to be called
     * @param query the http query parameter
     * @param jsonCommand the request command content (as a json string)
     * @return the response content (as a json string)
     * @throws HubMaintenanceException
     * @throws HubProcessingException
     */
    private synchronized String invoke(HttpMethod method, String url, @Nullable Query query,
            @Nullable String jsonCommand) throws HubMaintenanceException, HubProcessingException {
        if (logger.isTraceEnabled()) {
            if (query != null) {
                logger.trace("API command {} {}{}", method, url, query);
            } else {
                logger.trace("API command {} {}", method, url);
            }
            if (jsonCommand != null) {
                logger.trace("JSON command = {}", jsonCommand);
            }
        }
        Request request = httpClient.newRequest(url).method(method).header("Connection", "close").accept("*/*");
        if (query != null) {
            request.param(query.getKey(), query.getValue());
        }
        if (jsonCommand != null) {
            request.header(HttpHeader.CONTENT_TYPE, "application/json").content(new StringContentProvider(jsonCommand));
        }
        ContentResponse response;
        try {
            response = request.send();
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            if (Instant.now().isBefore(maintenanceScheduledEnd)) {
                // throw "softer" exception during maintenance window
                logger.debug("Hub still undergoing maintenance");
                throw new HubMaintenanceException("Hub still undergoing maintenance");
            }
            throw new HubProcessingException(String.format("%s: \"%s\"", e.getClass().getName(), e.getMessage()));
        }
        int statusCode = response.getStatus();
        if (statusCode == HttpStatus.LOCKED_423) {
            // set end of maintenance window, and throw a "softer" exception
            maintenanceScheduledEnd = Instant.now().plusSeconds(maintenancePeriod);
            logger.debug("Hub undergoing maintenance");
            throw new HubMaintenanceException("Hub undergoing maintenance");
        }
        if (statusCode != HttpStatus.OK_200) {
            logger.warn("Hub returned HTTP {} {}", statusCode, response.getReason());
            throw new HubProcessingException(String.format("HTTP %d error", statusCode));
        }
        String jsonResponse = response.getContentAsString();
        if ("".equals(jsonResponse)) {
            logger.warn("Hub returned no content");
            throw new HubProcessingException("Missing response entity");
        }
        if (logger.isTraceEnabled()) {
            logger.trace("JSON response = {}", jsonResponse);
        }
        return jsonResponse;
    }

    /**
     * Fetches a JSON package that describes a specific shade in the hub, and wraps it
     * in a Shade class instance
     *
     * @param shadeId id of the shade to be fetched
     * @return ShadeData class instance
     * @throws HubInvalidResponseException if response is invalid
     * @throws HubProcessingException if there is any processing error
     * @throws HubMaintenanceException if the hub is down for maintenance
     */
    public ShadeData getShade(int shadeId)
            throws HubInvalidResponseException, HubProcessingException, HubMaintenanceException {
        String jsonResponse = invoke(HttpMethod.GET, shades + Integer.toString(shadeId), null, null);
        return shadeDataFromJson(jsonResponse);
    }

    /**
     * Instructs the hub to do a hard refresh (discovery on the hubs RF network) on
     * a specific shade's position; fetches a JSON package that describes that shade,
     * and wraps it in a Shade class instance
     *
     * @param shadeId id of the shade to be refreshed
     * @return ShadeData class instance
     * @throws HubInvalidResponseException if response is invalid
     * @throws HubProcessingException if there is any processing error
     * @throws HubMaintenanceException if the hub is down for maintenance
     */
    public ShadeData refreshShadePosition(int shadeId)
            throws JsonParseException, HubProcessingException, HubMaintenanceException {
        String jsonResponse = invoke(HttpMethod.GET, shades + Integer.toString(shadeId),
                Query.of("refresh", Boolean.toString(true)), null);
        return shadeDataFromJson(jsonResponse);
    }

    /**
     * Instructs the hub to do a hard refresh (discovery on the hubs RF network) on
     * a specific shade's survey data, which will also refresh signal strength;
     * fetches a JSON package that describes that survey, and wraps it in a Survey
     * class instance
     *
     * @param shadeId id of the shade to be surveyed
     * @return Survey class instance
     * @throws HubInvalidResponseException if response is invalid
     * @throws HubProcessingException if there is any processing error
     * @throws HubMaintenanceException if the hub is down for maintenance
     */
    public Survey getShadeSurvey(int shadeId)
            throws HubInvalidResponseException, HubProcessingException, HubMaintenanceException {
        String jsonResponse = invoke(HttpMethod.GET, shades + Integer.toString(shadeId),
                Query.of("survey", Boolean.toString(true)), null);
        try {
            Survey survey = gson.fromJson(jsonResponse, Survey.class);
            if (survey == null) {
                throw new HubInvalidResponseException("Missing survey response");
            }
            return survey;
        } catch (JsonParseException e) {
            throw new HubInvalidResponseException("Error parsing survey response", e);
        }
    }

    /**
     * Instructs the hub to do a hard refresh (discovery on the hubs RF network) on
     * a specific shade's battery level; fetches a JSON package that describes that shade,
     * and wraps it in a Shade class instance
     *
     * @param shadeId id of the shade to be refreshed
     * @return ShadeData class instance
     * @throws HubInvalidResponseException if response is invalid
     * @throws HubProcessingException if there is any processing error
     * @throws HubMaintenanceException if the hub is down for maintenance
     */
    public ShadeData refreshShadeBatteryLevel(int shadeId)
            throws HubInvalidResponseException, HubProcessingException, HubMaintenanceException {
        String jsonResponse = invoke(HttpMethod.GET, shades + Integer.toString(shadeId),
                Query.of("updateBatteryLevel", Boolean.toString(true)), null);
        return shadeDataFromJson(jsonResponse);
    }
}
