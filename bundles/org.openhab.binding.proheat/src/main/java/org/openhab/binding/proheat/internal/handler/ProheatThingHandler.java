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
package org.openhab.binding.proheat.internal.handler;

import static org.openhab.binding.proheat.internal.ProheatBindingConstants.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.TooManyListenersException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.proheat.internal.ProheatThingConfiguration;
import org.openhab.binding.proheat.internal.communication.CommunicationStatus;
import org.openhab.binding.proheat.internal.communication.ProheatMessageHandler;
import org.openhab.binding.proheat.internal.communication.ProheatRequest;
import org.openhab.core.io.transport.serial.PortInUseException;
import org.openhab.core.io.transport.serial.SerialPort;
import org.openhab.core.io.transport.serial.SerialPortIdentifier;
import org.openhab.core.io.transport.serial.SerialPortManager;
import org.openhab.core.io.transport.serial.UnsupportedCommOperationException;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ProheatThingHandler} is responsible for handling serial communication with module
 *
 * @author Olivian Daniel Tofan - Initial contribution
 */
@NonNullByDefault
public class ProheatThingHandler extends BaseThingHandler {

    private static final int REINITIALIZE_DELAY = 1; // in minutes
    private static final int END_OF_MESSAGE_CR = '\r';
    private static final int END_OF_MESSAGE_NL = '\n';
    private static final int END_OF_STREAM = -1;

    private static final String HEADER_DATA = "^D210";
    private static final String HEADER_BEAT = "^KA9";

    private final static Logger logger = LoggerFactory.getLogger(ProheatThingHandler.class);

    private @Nullable ProheatThingConfiguration config;
    private @Nullable SerialPort serialPort;
    private @Nullable ProheatReceiverThread receiverThread;
    private @Nullable ProheatSenderThread senderThread;
    private final BlockingQueue<ProheatRequest> sendQueue = new LinkedBlockingQueue<>();
    private final SerialPortManager serialPortManager;
    private final Set<ProheatMessageHandler> handlers = ConcurrentHashMap.newKeySet();

    @Nullable
    private ScheduledFuture<?> reinitializeTask;

    private AtomicLong eventsReceived = new AtomicLong(0);

    public ProheatThingHandler(Thing thing, SerialPortManager serialPortManager) {
        super(thing);
        this.serialPortManager = serialPortManager;
    }

    @SuppressWarnings("null")
    @Override
    public void initialize() {
        config = getConfigAs(ProheatThingConfiguration.class);
        if (config.port == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR, "Port must be set!");
            return;
        }

        SerialPortIdentifier portId = serialPortManager.getIdentifier(config.port);
        if (portId == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                    "No such port: " + config.port);
            return;
        }

        try {
            serialPort = initializeSerialPort(portId);

            InputStream inputStream = serialPort.getInputStream();
            OutputStream outputStream = serialPort.getOutputStream();

            if (inputStream == null || outputStream == null) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                        "Input/Output stream null");
                return;
            }

            receiverThread = new ProheatReceiverThread(inputStream);
            senderThread = new ProheatSenderThread(outputStream);

            registerMessageHandler(new ThingMessageHandler());

            eventsReceived.set(0);

            receiverThread.start();
            senderThread.start();

            updateStatus(ThingStatus.ONLINE);
        } catch (PortInUseException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                    "Port in use: " + config.port);
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                    "Communication error: " + e.getMessage());
        }
    }

    @SuppressWarnings("null")
    private @Nullable SerialPort initializeSerialPort(SerialPortIdentifier portId)
            throws PortInUseException, TooManyListenersException, UnsupportedCommOperationException {
        SerialPort serialPort = portId.open(getThing().getUID().toString(), 2000);
        serialPort.setSerialPortParams(config.baudrate, SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
                SerialPort.PARITY_NONE);

        try {
            ExecCommand.stty("-F " + config.port + " sane");
        } catch (IOException ioe) {
            logger.info("TTY failed with: {}", ioe.getMessage());
        } catch (InterruptedException e) {
            logger.info("stty InterruptedException");
        }

        serialPort.enableReceiveThreshold(0);
        serialPort.enableReceiveTimeout(1000);

        return serialPort;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command == RefreshType.REFRESH && isLinked(channelUID.getId())) {
            switch (channelUID.getId()) {
                case EVENTS_RECEIVED:
                    updateState(EVENTS_RECEIVED, new DecimalType(eventsReceived.get()));
                    break;
            }
        }
    }

    public void sendRequest(ProheatRequest request) {
        sendQueue.add(request);
    }

    public void handleResponse(String message) {
        try {
            if (message.length() < 5) {
                return;
            }

            logger.debug("SNOW ALL  '{}'", message);
            String beginMessage = message.substring(0, 5);
            if (HEADER_DATA.equals(beginMessage)) {
                String bodyMessage = message.substring(5, message.length());
                logger.debug("SNOW BODY '{}'", bodyMessage);
                if (bodyMessage.length() < 16) {
                    return;
                }
                String textMessage = bodyMessage.substring(0, 16);
                logger.debug("SNOW TEXT '{}'", textMessage);

                if ("   AUTOMATICO   ".equals(textMessage)) {
                    updateState(DEVICE_ENABLED, OnOffType.ON);
                    updateState(DEVICE_MODE_AUTO, OnOffType.ON);
                    updateState(DEVICE_HEATING_PLANNED, StringType.EMPTY);
                    updateState(DEVICE_HEATING_REMAINED, StringType.EMPTY);
                }
                if ("CENTR.DISABILIT.".equals(textMessage)) {
                    updateState(DEVICE_ENABLED, OnOffType.OFF);
                    updateState(DEVICE_HEATING, OnOffType.OFF);
                    updateState(DEVICE_HEATING_PLANNED, StringType.EMPTY);
                    updateState(DEVICE_HEATING_REMAINED, StringType.EMPTY);
                }
                if (textMessage.startsWith("AUTO") || textMessage.startsWith("MANUALE")) {
                    if (textMessage.startsWith("AUTO")) {
                        updateState(DEVICE_MODE_AUTO, OnOffType.ON);
                    }
                    if (textMessage.startsWith("MANUALE")) {
                        updateState(DEVICE_MODE_AUTO, OnOffType.OFF);
                    }
                    int onPos = textMessage.indexOf("ON");
                    int offPos = textMessage.indexOf("OFF");
                    logger.debug("on pos '{}' off pos '{}'", onPos, offPos);
                    String timeText = null;
                    if (onPos != -1) {
                        updateState(DEVICE_HEATING, OnOffType.ON);
                        timeText = textMessage.substring(onPos + 2);
                        updateState(DEVICE_HEATING_PLANNED, new StringType(timeText));
                    }
                    if (offPos != -1) {
                        updateState(DEVICE_HEATING, OnOffType.OFF);
                        timeText = textMessage.substring(offPos + 3);
                        updateState(DEVICE_HEATING_PLANNED, StringType.EMPTY);
                        updateState(DEVICE_HEATING_REMAINED, StringType.EMPTY);
                    }
                }

                if (bodyMessage.length() < 22) {
                    return;
                }

                updateTemperature(bodyMessage);

                String endMessage = bodyMessage.substring(22, bodyMessage.length());
                logger.debug("SNOW END  '{}'", endMessage);

                int umPos = endMessage.indexOf("UM");
                if (umPos != -1) {
                    int umOnPos = endMessage.indexOf("ON");
                    int umOffPos = endMessage.indexOf("OFF");
                    logger.debug("umOn pos '{}' umOff pos '{}'", umOnPos, umOffPos);
                    String hexMoisture = "";
                    if (umOnPos != -1) {
                        updateState(DEVICE_MOISTURE_ENABLED, OnOffType.ON);
                        hexMoisture = endMessage.substring(umOnPos + 2);
                    }
                    if (umOffPos != -1) {
                        updateState(DEVICE_MOISTURE_ENABLED, OnOffType.OFF);
                        hexMoisture = endMessage.substring(umOffPos + 3);
                    }
                    updateMoisture(hexMoisture);
                }

                int offPos = endMessage.indexOf("OFF");
                if (offPos != -1) {
                    String timeText = endMessage.substring(offPos + 3, endMessage.length() - 2);
                    updateState(DEVICE_HEATING_REMAINED, new StringType(timeText));
                    String hexMoisture = endMessage.substring(endMessage.length() - 2, endMessage.length());
                    updateMoisture(hexMoisture);
                }

                updateState(EVENTS_RECEIVED, new DecimalType(eventsReceived.incrementAndGet()));
                updateState(EVENTS_RECEIVED_LASTTIME, new DateTimeType(ZonedDateTime.now()));
            } else if (!HEADER_BEAT.equals(beginMessage)) {
                logger.debug("SNOW UNKNOWN '{}'", message);
            }
        } catch (Exception e) {
            logger.error("handleResponse ERROR", e);
        }
    }

    private void updateTemperature(String bodyMessage) {
        int signPos = bodyMessage.indexOf('+');
        logger.debug("signPos positive '{}'", signPos);
        if (signPos == -1) {
            signPos = bodyMessage.indexOf('-');
            logger.debug("signPos negative '{}'", signPos);
            if (signPos == -1) {
                signPos = 16;
                logger.debug("signPos default '{}'", signPos);
            }
        }
        int degreePos = bodyMessage.indexOf('°');
        logger.debug("degreePos found '{}'", degreePos);
        if (degreePos == -1) {
            degreePos = 20;
            logger.debug("degreePos default '{}'", degreePos);
        }
        String floatTemperature = bodyMessage.substring(signPos, degreePos);
        logger.debug("float Temperature '{}'", floatTemperature);
        float temperature = Float.valueOf(floatTemperature).floatValue();
        updateState(DEVICE_TEMPERATURE_VALUE, new DecimalType(temperature));
    }

    private void updateMoisture(String hexMoisture) {
        logger.debug("hexMoisture '{}'", hexMoisture);
        Long longMoisture = Long.parseLong(hexMoisture, 16);
        float moisture = (float) ((Float.valueOf(longMoisture).floatValue() / 255.0) * 100.0);
        moisture = (float) (Math.round((moisture * 100.0)) / 100.0);
        logger.debug("moisture '{}'", moisture);
        String moistureText = String.format("hex {%s}, dec {%d}, val {%f}", hexMoisture, longMoisture, moisture);
        updateState(DEVICE_MOISTURE_VALUE, new DecimalType(moisture));
        updateState(DEVICE_MOISTURE_VALUE_TEXT, new StringType(moistureText));
    }

    public void registerMessageHandler(ProheatMessageHandler handler) {
        handlers.add(handler);
    }

    public void unregisterMessageHandler(ProheatMessageHandler handler) {
        handlers.remove(handler);
    }

    /**
     * Closes the serial connection to the snowmelting module.
     */
    @SuppressWarnings("null")
    @Override
    public void dispose() {
        stopThread(senderThread);
        stopThread(receiverThread);
        senderThread = null;
        receiverThread = null;
        if (serialPort != null) {
            try {
                InputStream inputStream = serialPort.getInputStream();
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                logger.debug("Error closing input stream", e);
            }

            try {
                OutputStream outputStream = serialPort.getOutputStream();
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                logger.debug("Error closing output stream", e);
            }

            serialPort.close();
            serialPort = null;
        }
        logger.info("Stopped Proheat serial handler");

        super.dispose();
    }

    private void stopThread(@Nullable Thread thread) {
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(1000);
            } catch (InterruptedException e) {
            }
        }
    }

    public void handleCommunicationError() {
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
        if (reinitializeTask == null) {
            reinitializeTask = scheduler.schedule(() -> {
                logger.info("Reconnecting to device...");
                thingUpdated(getThing());
                reinitializeTask = null;
            }, REINITIALIZE_DELAY, TimeUnit.MINUTES);
        }
    }

    private class ThingMessageHandler implements ProheatMessageHandler {

        @Override
        public void handleCommunicationStatus(CommunicationStatus response) {
            if (response.success) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            }
        }
    }

    private class ProheatReceiverThread extends Thread {

        private final Logger logger = LoggerFactory.getLogger(ProheatReceiverThread.class);

        private final InputStream stream;

        ProheatReceiverThread(InputStream stream) {
            super("ProheatReceiveThread");
            this.stream = stream;
        }

        @Override
        public void run() {
            logger.debug("Receiver thread started");
            Optional<String> lastMessage = Optional.empty();
            while (!interrupted()) {
                try {
                    Optional<String> message = readLineBlocking();
                    // ignore beat data
                    if (message.equals(Optional.of(HEADER_BEAT))) {
                        continue;
                    }
                    if (message.equals(lastMessage)) {
                        continue;
                    } else {
                        lastMessage = message;
                    }
                    message.ifPresent(m -> {
                        logger.info("message received: '{}'", m);
                        handleResponse(m);
                    });
                } catch (IOException e) {
                    handleCommunicationError();
                    break;
                }
            }
            logger.debug("Receiver thread finished");
        }

        private Optional<String> readLineBlocking() throws IOException {
            StringBuilder s = new StringBuilder();
            while (true) {
                int c = stream.read();
                if (c == END_OF_STREAM) {
                    return Optional.empty();
                }
                if (c == END_OF_MESSAGE_CR) {
                    break;
                }
                if (c == END_OF_MESSAGE_NL) {
                    break;
                }
                s.append((char) c);
            }
            return Optional.of(s.toString());
        }
    }

    private class ProheatSenderThread extends Thread {

        private static final int SLEEP_TIME = 150;

        private final Logger logger = LoggerFactory.getLogger(ProheatSenderThread.class);

        private OutputStream stream;

        public ProheatSenderThread(OutputStream stream) {
            super("ProheatSenderThread");
            this.stream = stream;
        }

        @Override
        public void run() {
            logger.debug("Sender thread started");
            while (!interrupted()) {
                try {
                    ProheatRequest request = sendQueue.take();
                    stream.write(request.getSerialMessage().getBytes());
                    stream.flush();
                    logger.debug("message sent: '{}'", request.getSerialMessage().replaceAll("\r", ""));
                    Thread.sleep(SLEEP_TIME);
                } catch (IOException e) {
                    handleCommunicationError();
                    break;
                } catch (InterruptedException e) {
                    break;
                }
            }
            logger.debug("Sender thread finished");
        }
    }

    private static class ExecCommand {

        public static String stty(final String args) throws IOException, InterruptedException {
            return exec("stty " + args).trim();
        }

        /**
         * Run a command and return the output
         *
         * @param cmd what to execute
         * @return output
         * @throws java.io.IOException stream
         * @throws InterruptedException stream
         */
        private static String exec(final String cmd) throws IOException, InterruptedException {
            return exec(new String[] { "sh", "-c", cmd });
        }

        /**
         * Run a command and return the output
         *
         * @param cmd the command
         * @return output
         * @throws IOException stream
         * @throws InterruptedException stream
         */
        private static String exec(final String[] cmd) throws IOException, InterruptedException {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();

            Process p = Runtime.getRuntime().exec(cmd);
            int c;
            InputStream in = null;
            InputStream err = null;
            OutputStream out = null;

            try {
                in = p.getInputStream();

                while ((c = in.read()) != -1) {
                    bout.write(c);
                }

                err = p.getErrorStream();

                while ((c = err.read()) != -1) {
                    bout.write(c);
                }

                out = p.getOutputStream();

                p.waitFor();
            } finally {
                try {
                    if (in != null)
                        in.close();
                    if (err != null)
                        err.close();
                    if (out != null)
                        out.close();
                } catch (Exception e) {
                    logger.error("Failed to close streams");
                }
            }

            return new String(bout.toByteArray());
        }
    }
}
