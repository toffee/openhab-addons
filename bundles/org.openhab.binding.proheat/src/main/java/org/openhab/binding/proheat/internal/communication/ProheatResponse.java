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
 * Common ancestor for all responses
 *
 * @author Olivian Daniel Tofan - Initial contribution
 *
 */
@NonNullByDefault
public interface ProheatResponse extends ProheatMessage {

    void accept(ProheatMessageHandler visitor);
}
