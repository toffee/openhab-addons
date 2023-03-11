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
package org.openhab.binding.proheat.internal.communication;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Message indicating communication status between device and Digiplex controller.
 *
 * @author Robert Michalak - Initial contribution
 *
 */
@NonNullByDefault
public class CommunicationStatus extends AbstractResponse {

    public static final CommunicationStatus OK = new CommunicationStatus(true);
    public static final CommunicationStatus FAILURE = new CommunicationStatus(false);

    private CommunicationStatus(boolean success) {
        super(success);
    }

    @Override
    public void accept(ProheatMessageHandler visitor) {
        visitor.handleCommunicationStatus(this);
    }
}
