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
 * Base class for all responses retrieved from snowmelting module.
 *
 * Handles success/failure.
 *
 * @author Olivian Daniel Tofan - Initial contribution
 *
 */
@NonNullByDefault
public abstract class AbstractResponse implements ProheatResponse {

    public final boolean success;

    public AbstractResponse() {
        this.success = true;
    }

    public AbstractResponse(boolean success) {
        this.success = success;
    }
}
